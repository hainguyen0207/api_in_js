import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;

import javax.swing.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyHttpHandler implements HttpHandler {
    private final Logging logging;
    private final APITab tab;
    private final MontoyaApi api;

    // Chống trùng theo cặp (pageUrl|apiPath thô)
    private final Set<String> seenPairs = ConcurrentHashMap.newKeySet();
    // Chống trùng toàn cục theo API tuyệt đối (host[:port] + path), để 2 JS không trùng nhau
    private final Set<String> seenApiGlobal = ConcurrentHashMap.newKeySet();

    // ========= TÙY CHỌN =========

    // Bỏ slash cuối cùng để gộp /buildings và /buildings/
    private static final boolean STRIP_TRAILING_SLASH = true;

    // ========= REGEX CHÍNH =========

    // url: '/path' (cho phép xuống dòng & khoảng trắng)
    private static final Pattern P_URL_FIELD = Pattern.compile("\\burl\\s*:\\s*(['\"])\\s*(\\/?[A-Za-z0-9_\\-./?&=%]+)\\s*\\1", Pattern.CASE_INSENSITIVE);

    // url: '/path'.concat(...)
    private static final Pattern P_URL_FIELD_CONCAT = Pattern.compile("\\burl\\s*:\\s*(['\"])\\s*(\\/?[A-Za-z0-9_\\-./?&=%]*)\\s*\\1\\s*\\.\\s*concat\\s*\\(", Pattern.CASE_INSENSITIVE);

    // axios.get('/path'), axios.post('/path')
    private static final Pattern P_AXIOS = Pattern.compile("\\baxios\\.(get|post|put|patch|delete|head|options)\\s*\\(\\s*(['\"])\\s*(\\/?[A-Za-z0-9_\\-./?&=%]+)\\s*\\2", Pattern.CASE_INSENSITIVE);

    // fetch('/path')
    private static final Pattern P_FETCH = Pattern.compile("\\bfetch\\s*\\(\\s*(['\"])\\s*(\\/?[A-Za-z0-9_\\-./?&=%]+)\\s*\\1", Pattern.CASE_INSENSITIVE);

    // backup: mọi chuỗi '/path' nằm trong cặp quote
    private static final Pattern P_REL_GENERIC = Pattern.compile("(['\"])\\s*(\\/[A-Za-z0-9_\\-./?&=%]+)\\s*\\1");

    // URL tuyệt đối
    private static final Pattern P_ABS = Pattern.compile("(https?://[A-Za-z0-9_\\-.:]+\\/[A-Za-z0-9_\\-./?&=%]+)");

    // Bộ lọc file tĩnh & source
    private static final Pattern P_STATIC_EXT = Pattern.compile("(?i).+\\.(png|jpg|jpeg|gif|webp|bmp|ico|svg|xml|txt|map|css|scss|sass|less|js|mjs|cjs|ts|tsx|vue|otf|ttf|eot|woff2?|pdf|xhtml)(/.*)?$");

    private static final Pattern P_SOURCE_DIR = Pattern.compile("(?i)^/(src|node_modules|assets|static|vendor|lib)(/.*)?$");

    // các chuỗi trong backtick `.../path...`
    private static final Pattern P_TEMPLATE_BACKTICK = Pattern.compile("`([^`]*(/[-A-Za-z0-9_\\-./?&=%]+)[^`]*)`");

    // pattern bắt `/path` xuất hiện ngay sau ${...} như `${this.api}/path...`
    private static final Pattern P_TEMPLATE_AFTER_EXPR = Pattern.compile("\\$\\{[^}]+}\\s*/\\s*([A-Za-z0-9_\\-./?&=%]+)");

    // (tùy chọn, cẩn trọng) backup: tìm /path không nằm trong quote (có thể tạo false positives)
    //private static final Pattern P_REL_UNQUOTED = Pattern.compile("(?<!['\"`])(/[-A-Za-z0-9_\\-./?&=%]+)(?!['\"`])");

    // Noise khác
    private static final Pattern P_MIME = Pattern.compile("(?i)^(application|text|image|audio|video)/.*");
    private static final Pattern P_DATE1 = Pattern.compile("(?i)^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$");
    private static final Pattern P_DATE2 = Pattern.compile("(?i)^(M|MM|D|DD)/?(M|MM)?/?(Y|YY|YYYY)$");
    private static final Pattern P_DATE3 = Pattern.compile("(?i)^(M{1,2}|D{1,2})/(M{1,2}|D{1,2})/Y{2,4}$");
    private static final Pattern P_BASE64ISH = Pattern.compile("^[A-Za-z0-9+/=]{16,}$"); // chuỗi base64 dài
    private static final Pattern P_NOISE_PREFIX = Pattern.compile("(?i)^(null|undefined|n/a)(/|$).*");

    public MyHttpHandler(MontoyaApi api, APITab tab) {
        this.logging = api.logging();
        this.tab = tab;
        this.api = api;
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        HttpRequest req = responseReceived.initiatingRequest();
        String fullUrl = (req != null) ? req.url() : null;

        // Chỉ xử lý nếu URL hợp lệ + IN SCOPE + Content-Type phù hợp để trích
        if (!isValidHttpUrl(fullUrl) || !safeIsInScope(fullUrl) || !isExtractableContentType(responseReceived)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        new Thread(() -> processResponse(responseReceived), "ApiJS-Worker").start();
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    private static void scan(String text, Pattern p, int groupIdx, Set<String> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String s = m.group(groupIdx);
            if (s != null && !s.isEmpty()) {
                out.add(s);
            }
        }
    }

    private static boolean isDynamicEndpoint(String path) {
        // ${var}, :id, {id}, {anything}
        if (path.matches(".*\\$\\{[^}]+}.*")) return true;
        if (path.matches(".*/:[A-Za-z0-9_\\-]+.*")) return true;
        if (path.matches(".*\\{[^}]+}.*")) return true;
        return false;
    }

    private static String stripQuery(String url) {
        return url.replaceAll("\\?.*$", "");
    }

    // Chuẩn hoá & lọc mạnh tay. LƯU Ý: Giữ nguyên URL tuyệt đối (scheme+host+port+path),
    // không convert về path thuần để tránh mất host cross-origin.
    private static Set<String> normalizeAndFilter(Set<String> raw) {
        LinkedHashSet<String> keep = new LinkedHashSet<>();
        for (String s : raw) {
            String cleaned = s;

            // loại sớm các giá trị không phải endpoint
            if (P_MIME.matcher(cleaned).matches()) continue;
            if (P_DATE1.matcher(cleaned).matches()) continue;
            if (P_DATE2.matcher(cleaned).matches()) continue;
            if (P_DATE3.matcher(cleaned).matches()) continue;
            if (P_BASE64ISH.matcher(cleaned).matches()) continue;
            if (P_NOISE_PREFIX.matcher(cleaned).matches()) continue;

            // bỏ query string
            cleaned = stripQuery(cleaned);

            boolean isAbsolute = cleaned.startsWith("http://") || cleaned.startsWith("https://");

            if (isAbsolute) {
                try {
                    URL u = new URL(cleaned);
                    String path = u.getPath();

                    // loại file tĩnh theo path
                    if (P_STATIC_EXT.matcher(path).matches()) continue;
                    if (P_SOURCE_DIR.matcher(path).matches()) continue;
                    if (isDynamicEndpoint(path)) continue;

                    // chuẩn hoá nhiều dấu '/' liền nhau ở path
                    path = path.replaceAll("/{2,}", "/");

                    // cắt slash cuối (tuỳ chọn)
                    if (STRIP_TRAILING_SLASH && path.length() > 1 && path.endsWith("/")) {
                        path = path.substring(0, path.length() - 1);
                    }

                    // Build lại absolute (giữ scheme, host, port nếu có)
                    String hostPort = u.getHost();
                    int port = u.getPort();
                    if (port != -1 && port != u.getDefaultPort()) {
                        hostPort = hostPort + ":" + port;
                    }
                    cleaned = u.getProtocol() + "://" + hostPort + (path.isEmpty() ? "/" : path);
                } catch (Exception e) {
                    // URL lỗi -> bỏ
                    continue;
                }
            } else {
                // Relative-like -> chuẩn hoá như path
                if (!cleaned.startsWith("/")) cleaned = "/" + cleaned;
                cleaned = cleaned.replaceAll("/{2,}", "/");

                if (STRIP_TRAILING_SLASH && cleaned.length() > 1 && cleaned.endsWith("/")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 1);
                }
                if (P_STATIC_EXT.matcher(cleaned).matches()) continue;
                if (P_SOURCE_DIR.matcher(cleaned).matches()) continue;
                if (isDynamicEndpoint(cleaned)) continue;

                // phải có ký tự chữ/c -, tránh toàn số /id
                if (cleaned.length() < 2 || !cleaned.contains("/") || cleaned.matches("^(/\\d+)+$") || !cleaned.matches(".*[A-Za-z_\\-].*")) {
                    continue;
                }
            }

            keep.add(cleaned);
        }
        return keep;
    }

    private static Set<String> extractEndpointsFromText(String text) {
        LinkedHashSet<String> hits = new LinkedHashSet<>();

        // 1) url: '/path'
        scan(text, P_URL_FIELD, 2, hits);

        // 2) url: '/path'.concat(...)
        scan(text, P_URL_FIELD_CONCAT, 2, hits);

        // 3) axios/fetch
        scan(text, P_AXIOS, 3, hits);
        scan(text, P_FETCH, 2, hits);

        // 4) URL tuyệt đối
        scan(text, P_ABS, 0, hits);

        // 5) template backtick `.../path...`
        scan(text, P_TEMPLATE_BACKTICK, 2, hits);

        // 6) template expression like ${this.api}/path...
        scan(text, P_TEMPLATE_AFTER_EXPR, 1, hits);

        // 7) backup: mọi '/path' trong quote
        scan(text, P_REL_GENERIC, 2, hits);

        // 8) backup: mọi '/path' trong quote
        scan(text, P_REL_GENERIC, 2, hits);

        // 9) unquoted /path catch-all (cẩn thận)
        //scan(text, P_REL_UNQUOTED, 1, hits);

        // Chuẩn hóa + lọc mạnh tay (GIỮ absolute URL)
        return normalizeAndFilter(hits);
    }

    private boolean isExtractableContentType(HttpResponseReceived r) {
        String ct = Optional.ofNullable(r.headerValue("Content-Type")).orElse("").toLowerCase(Locale.ROOT);

        if (ct.isEmpty()) return true; // nhiều server không set CT -> cứ xử lý
        if (ct.contains("html")) return true;
        if (ct.contains("javascript") || ct.contains("ecmascript") || ct.contains("x-javascript")) return true;
        //if (ct.contains("json")) return true;
        if (ct.startsWith("text/")) return true;

        // Loại nhanh các binary/phổ biến không trích
        if (ct.startsWith("image/") || ct.startsWith("video/") || ct.startsWith("audio/")) return false;
        if (ct.contains("octet-stream") || ct.contains("pdf") || ct.contains("font")) return false;

        // Mặc định: không chắc -> bỏ
        return false;
    }

    private boolean safeIsInScope(String url) {
        try {
            return api.scope().isInScope(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidHttpUrl(String fullUrl) {
        try {
            if (fullUrl == null || fullUrl.isEmpty()) return false;
            URL u = new URL(fullUrl);
            return u.getProtocol().startsWith("http");
        } catch (Exception e) {
            return false;
        }
    }

    // Resolve endpoint về URL tuyệt đối dựa trên origin của trang
    private String resolveToAbsolute(String pageUrl, String apiCandidate) {
        try {
            if (apiCandidate.startsWith("http://") || apiCandidate.startsWith("https://")) {
                return apiCandidate; // đã tuyệt đối
            }
            URL base = new URL(pageUrl);
            URL abs = new URL(base, apiCandidate); // xử lý /path, ./path, ../path, ...
            // bỏ query cho khoá trùng & hiển thị nhất quán
            return stripQuery(abs.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String hostPortOf(URL u) {
        String host = u.getHost();
        int port = u.getPort();
        if (port != -1 && port != u.getDefaultPort()) {
            return host + ":" + port;
        }
        return host;
    }

    // Tạo key toàn cục cho endpoint để chống trùng giữa nhiều JS
    private String apiGlobalKey(String absUrl) {
        try {
            URL u = new URL(absUrl);
            String path = u.getPath();
            // Chuẩn hoá path tương tự phần normalize
            path = path.replaceAll("/{2,}", "/");
            if (STRIP_TRAILING_SLASH && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return (hostPortOf(u) + path).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return absUrl.toLowerCase(Locale.ROOT);
        }
    }

    private boolean isUrlInSiteMapExact(String absoluteUrl) {
        try {
            URL target = new URL(absoluteUrl);
            String host = hostPortOf(target);
            String path = target.getPath();

            return api.siteMap().requestResponses().stream().filter(item -> item.response() != null && item.request() != null).map(item -> item.request().url()).filter(Objects::nonNull).anyMatch(u -> {
                try {
                    URL x = new URL(u);
                    return hostPortOf(x).equalsIgnoreCase(host) && x.getPath().equals(path);
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            return false;
        }
    }

    private void processResponse(HttpResponseReceived rr) {
        try {
            HttpRequest req = rr.initiatingRequest();
            if (req == null) return;

            String fullUrl = req.url();
            if (!isValidHttpUrl(fullUrl) || !safeIsInScope(fullUrl)) return;

            String body = rr.bodyToString();
            if (body == null || body.isEmpty()) return;

            // unescape đơn giản trước khi match
            String text = body.replace("\\u002F", "/").replace("\\/", "/");

            // gọi extractor
            Set<String> endpoints = extractEndpointsFromText(text);
            if (endpoints.isEmpty()) return;

            final String reqStr = req.toString();
            final String resStr = rr.toString();

            for (String apiPath : endpoints) {
                // Resolve về URL tuyệt đối
                String absUrl = resolveToAbsolute(fullUrl, apiPath);
                if (absUrl == null) continue;

                // Lọc scope cho từng endpoint
                if (!safeIsInScope(absUrl)) continue;

                // Chống trùng toàn cục (nếu 2 JS cùng trích một API)
                String globalKey = apiGlobalKey(absUrl);
                if (!seenApiGlobal.add(globalKey)) {
                    continue; // đã thấy ở JS khác -> bỏ
                }

                // Chống trùng theo cặp (pageUrl|apiPath thô) – giữ để tránh lặp trong cùng trang
                String pairKey = fullUrl + "|" + apiPath;
                if (!seenPairs.add(pairKey)) continue;

                boolean seenInSiteMap = isUrlInSiteMapExact(absUrl);

                final String fFullUrl = fullUrl;
                final String fApiPath = apiPath; // hiển thị path thô người dùng dễ đọc
                final boolean fSeen = seenInSiteMap;

                SwingUtilities.invokeLater(() -> tab.addEntry(fFullUrl, fApiPath, reqStr, resStr, fSeen));
            }

        } catch (Throwable t) {
            logging.logToError("❌ Extractor error: " + t.getMessage());
        }
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Bỏ qua, chỉ pass request cho Burp xử lý
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }
}
