//import burp.api.montoya.MontoyaApi;
//import burp.api.montoya.http.handler.*;
//import burp.api.montoya.http.message.requests.HttpRequest;
//import burp.api.montoya.http.message.responses.HttpResponse;
//import burp.api.montoya.logging.Logging;
//
//import javax.swing.*;
//import java.net.URL;
//import java.util.*;
//import java.util.regex.*;
//
//public class MyHttpHandler implements HttpHandler {
//    private final Logging logging;
//    private final DecryptedHistoryTab tab;
//    private final MontoyaApi api;
//    private boolean warnedNoScope = false;
//
//    public MyHttpHandler(MontoyaApi api, DecryptedHistoryTab tab) {
//        this.logging = api.logging();
//        this.tab = tab;
//        this.api = api;
//    }
//
//    @Override
//    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
//        return RequestToBeSentAction.continueWith(requestToBeSent);
//    }
//
//    @Override
//    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
//        try {
//            HttpRequest req = responseReceived.initiatingRequest();
//            HttpResponse resp = responseReceived;
//
//
//            String fullUrl = req.url();
//            String reqBody = req.toString();
//            String respBody = resp.toString();
//
//            // Nếu là HTML hoặc JavaScript → trích endpoint
//            String contentType = resp.headerValue("Content-Type");
//            if (contentType != null && (contentType.contains("html") || contentType.contains("javascript"))) {
//                Set<String> extractedEndpoints = extractEndpoints(resp.bodyToString());
//
//                // Hiển thị cảnh báo nếu scope rỗng
//                if (isValidHttpUrl(fullUrl)) {
//                    if (!api.scope().isInScope(fullUrl) && !warnedNoScope) {
//                        api.http();
//                        warnedNoScope = true;
//                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(tab.getComponent(), "Vui lòng thêm scope trong Burp để trích API từ response.", "⚠️ Chưa cấu hình Scope", JOptionPane.WARNING_MESSAGE));
//                        return ResponseReceivedAction.continueWith(responseReceived);
//                    }
//                }
//
//
//                for (String apiPath : extractedEndpoints) {
//                    String fullApiUrl = combineUrl(fullUrl, apiPath);
//                    if (api.scope().isInScope(fullApiUrl)) {
//                        tab.addEntry(fullUrl, apiPath, reqBody, respBody);
//                    }
//                }
//            }
//
//        } catch (Exception e) {
//            logging.logToError("❌ Error handling response: " + e.getMessage());
//        }
//
//        return ResponseReceivedAction.continueWith(responseReceived);
//    }
//
//    private boolean isValidHttpUrl(String fullUrl) {
//        try {
//            if (fullUrl == null || fullUrl.isEmpty()) return false;
//            URL u = new URL(fullUrl);
//            return u.getProtocol().startsWith("http");
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    private String combineUrl(String baseUrl, String apiPath) {
//        try {
//            if (apiPath.startsWith("http")) {
//                return apiPath;
//            }
//            URL base = new URL(baseUrl);
//            return base.getProtocol() + "://" + base.getHost() + (base.getPort() != -1 ? ":" + base.getPort() : "") + apiPath;
//        } catch (Exception e) {
//            return apiPath;
//        }
//    }
//
//    private Set<String> extractEndpoints(String content) {
//        Set<String> endpoints = new LinkedHashSet<>();
//        Pattern pattern = Pattern.compile("(['\"`])((https?:)?[\\w:/?=.&+%;\\-{}$]+)\\1");
//        Matcher matcher = pattern.matcher(content);
//
//        while (matcher.find()) {
//            String url = matcher.group(2);
//
//            if (url.matches("(?i)^(application|text|image|audio|video)/.*")) continue;
//            if (url.matches("(?i)^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$")) continue;
//            if (url.matches("(?i)^(M|MM|D|DD)/?(M|MM)?/?(Y|YY|YYYY)$")) continue;
//            if (url.matches("(?i)^(M{1,2}|D{1,2})/(M{1,2}|D{1,2})/Y{2,4}$")) continue;
//            if (url.matches("(?i).+\\.(png|jpg|jpeg|gif|css|js|ico|woff2|svg|xml|otf|txt?)(\\?.*)?$")) continue;
//            if (url.matches("(?i)^(data|blob):.*")) continue;
//
//            if (url.contains("/")) {
//                endpoints.add(url.replaceAll("\\?.*", ""));
//            }
//        }
//
//        return endpoints;
//    }
//}