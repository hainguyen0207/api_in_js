import java.io.*;
import java.util.regex.*;
import java.util.*;

public class test {
    public static void main(String[] args) {
        File file = new File("C:\\Users\\hainguyen\\Downloads\\ExtensionTemplateProject\\src\\main\\java\\response.js");

        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file: " + e.getMessage());
            return;
        }

        // Cho phép dấu {}, dùng cho RESTful path param

        Pattern pattern = Pattern.compile("(['\"`])((https?:)?[\\w:/?=.&+%;\\-{}$]+)\\1");

        Matcher matcher = pattern.matcher(content.toString());

        Set<String> endpoints = new LinkedHashSet<>();
        while (matcher.find()) {
            String url = matcher.group(2);

            // Bỏ MIME types
            if (url.matches("(?i)^(application|text|image|audio|video)/.*")) continue;

            // Bỏ định dạng ngày
            if (url.matches("(?i)^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$")) continue;
            if (url.matches("(?i)^(M|MM|D|DD)/?(M|MM)?/?(Y|YY|YYYY)$")) continue;
            if (url.matches("(?i)^(M{1,2}|D{1,2})/(M{1,2}|D{1,2})/Y{2,4}$")) continue;
            if (url.matches("(?i).+\\.(png|jpg|jpeg|gif|css|js|ico|woff2|svg|xml|otf|txt?)(\\?.*)?$")) continue;
            if (url.matches("(?i).+\\.(png|jpg|jpeg|gif|css|js|ico|woff2|svg|xml|otf|txt?)$")) continue;
            if (url.matches("(?i)^(data|blob):.*")) continue;

            // Giữ lại nếu có ít nhất 1 dấu /
            if (url.contains("/")) {
                endpoints.add(url.replaceAll("\\?.*", ""));
            }
        }

        System.out.println("📦 Các endpoint trích được:");
        for (String endpoint : endpoints) {
            System.out.println("→ " + endpoint);
        }
    }
}
