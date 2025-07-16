import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;

import javax.swing.*;
import java.net.URL;
import java.util.*;
import java.util.regex.*;

public class MyHttpHandler implements HttpHandler {
    private final Logging logging;
    private final APITab tab;
    private final MontoyaApi api;
    private boolean warnedNoScope = false;
    private boolean scopeReady = false;


    public MyHttpHandler(MontoyaApi api, APITab tab) {
        this.logging = api.logging();
        this.tab = tab;
        this.api = api;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        new Thread(() -> {
            try {
                HttpRequest req = responseReceived.initiatingRequest();
                HttpResponse resp = responseReceived;

                String fullUrl = req.url();


                if (scopeReady) {
                    if (!isValidHttpUrl(fullUrl) || !safeIsInScope(fullUrl)) {
                        if (!warnedNoScope) {
                            warnedNoScope = true;
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(tab.getComponent(), "⚠️ Vui lòng thêm scope trong Burp để trích API từ response.", "Chưa cấu hình Scope", JOptionPane.WARNING_MESSAGE));
                        }
                        return;
                    } else {
                        scopeReady = true;
                    }
                }

                String reqBody = req.toString();
                String respBody = resp.toString();

                String contentType = resp.headerValue("Content-Type");
                if (contentType != null && (contentType.contains("html") || contentType.contains("javascript"))) {
                    Set<String> extractedEndpoints = extractEndpoints(resp.bodyToString());

                    for (String apiPath : extractedEndpoints) {
                        String fullApiUrl = combineUrl(fullUrl, apiPath);
                        if (isValidHttpUrl(fullApiUrl) && safeIsInScope(fullApiUrl)) {
                            boolean inSiteMap = isUrlInSiteMap(apiPath);
                            tab.addEntry(fullUrl, apiPath, reqBody, respBody, inSiteMap);
                        }
                    }
                }

            } catch (Exception e) {
                logging.logToError("❌ Error handling response (threaded): " + e.getMessage());
            }
        }).start();

        return ResponseReceivedAction.continueWith(responseReceived);
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

    private String combineUrl(String baseUrl, String apiPath) {
        try {
            if (apiPath.startsWith("http")) {
                return apiPath;
            }
            URL base = new URL(baseUrl);
            String cleanPath = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
            return base.getProtocol() + "://" + base.getHost() + (base.getPort() != -1 ? ":" + base.getPort() : "") + cleanPath;
        } catch (Exception e) {
            return ""; // Trả về rỗng nếu lỗi
        }
    }


    private Set<String> extractEndpoints(String content) {
        Set<String> endpoints = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("(['\"`])((https?:)?[\\\\/\\w:?=.&+%;\\-{}$]+)\\1");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String url = matcher.group(2);

            // Lọc bỏ giá trị không phải endpoint
            if (url.matches("(?i)^(application|text|image|audio|video)/.*")) continue;
            if (url.matches("(?i)^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$")) continue;
            if (url.matches("(?i)^(M|MM|D|DD)/?(M|MM)?/?(Y|YY|YYYY)$")) continue;
            if (url.matches("(?i)^(M{1,2}|D{1,2})/(M{1,2}|D{1,2})/Y{2,4}$")) continue;
            if (url.matches("(?i).+\\.(png|jpg|jpeg|gif|css|js|ico|woff2|svg|xml|otf|txt|vue?)(\\?.*)?$")) continue;
            if (url.matches("(?i)^(data|blob):.*")) continue;


            if (url.contains("/")) {
                String cleaned = url.replaceAll("\\\\/", "/").replaceAll("\\?.*", "");

                // ❌ Bỏ qua nếu không chứa ký tự `/` thực sự hoặc toàn ký tự base64 hoặc không giống API
                if (cleaned.matches("(?i)^(null|undefined|n/a|static|vendor|blob|lib|script)(/|$).*")) continue;

                // Bỏ các endpoint như ./
                if (cleaned.matches("(?i)^(null|undefined|n/a|static|vendor|blob|lib|script)(/|$).*")) continue;
                if (cleaned.length() < 3 || cleaned.matches("^[A-Za-z0-9+/=]{10,}$")) continue;

                if (!isDynamicEndpoint(cleaned)) {
                    endpoints.add(cleaned);
                }
            }
        }

        return endpoints;
    }

    private boolean isDynamicEndpoint(String path) {
        String[] segments = path.split("/");
        for (String seg : segments) {
            if (seg.length() > 30 && seg.matches("[a-zA-Z0-9]+")) {
                return true;
            }
            if (seg.matches("\\d{6,}")) {
                return true;
            }
        }
        return false;
    }

    private boolean isUrlInSiteMap(String apiPath) {
        for (HttpRequestResponse item : api.siteMap().requestResponses()) {
            if (item.response() == null) continue;
            String siteMapUrl = item.request().url();
            if (siteMapUrl.contains(apiPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean safeIsInScope(String url) {
        try {
            return api.scope().isInScope(url);
        } catch (IllegalArgumentException e) {
            logging.logToError("❌ Invalid URL passed to isInScope: " + url);
            return false;
        }
    }

}