//import burp.api.montoya.MontoyaApi;
//import burp.api.montoya.BurpExtension;
//import burp.api.montoya.http.message.requests.HttpRequest;
//import burp.api.montoya.http.message.HttpRequestResponse;
//
//import javax.swing.*;
//import java.awt.*;
//import java.net.URL;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
//public class Extension implements BurpExtension {
//    @Override
//    public void initialize(MontoyaApi api) {
//        api.extension().setName("Unique Clean SiteMap URLs");
//
//        SwingUtilities.invokeLater(() -> {
//            JFrame frame = new JFrame("Filtered Unique Site Map URLs");
//            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//            frame.setSize(800, 600);
//
//            JTextArea textArea = new JTextArea();
//            textArea.setEditable(false);
//            textArea.setFont(new Font("Consolas", Font.PLAIN, 14));
//            JScrollPane scrollPane = new JScrollPane(textArea);
//            frame.add(scrollPane);
//
//            String[] staticExtensions = {".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot", ".otf", ".mp4", ".webm", ".pdf"};
//
//            Set<String> uniqueUrls = new HashSet<>();
//            StringBuilder urls = new StringBuilder();
//
//            List<HttpRequestResponse> siteMapItems = api.siteMap().requestResponses();
//            for (HttpRequestResponse item : siteMapItems) {
//                if (item.response() == null) continue;
//
//                HttpRequest request = item.request();
//                String url = request.url();
//
//                if (!api.scope().isInScope(url)) continue;
//
//                try {
//                    URL parsedUrl = new URL(url);
//
//                    if (parsedUrl.getQuery() != null) continue;
//
//                    String path = parsedUrl.getPath().toLowerCase();
//                    boolean isStatic = false;
//                    for (String ext : staticExtensions) {
//                        if (path.endsWith(ext)) {
//                            isStatic = true;
//                            break;
//                        }
//                    }
//                    if (isStatic) continue;
//
//                    // Nếu chưa từng thấy URL này thì thêm
//                    String cleanUrl = new URL(parsedUrl.getProtocol(), parsedUrl.getHost(), parsedUrl.getPort(), parsedUrl.getPath()).toString();
//                    if (uniqueUrls.add(cleanUrl)) {
//                        urls.append(cleanUrl).append("\n");
//                    }
//
//                } catch (Exception ignored) {
//                }
//            }
//
//            textArea.setText(urls.toString());
//            frame.setVisible(true);
//        });
//    }
//}
