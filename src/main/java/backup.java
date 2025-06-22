//import burp.api.montoya.BurpExtension;
//import burp.api.montoya.MontoyaApi;
//
//import burp.api.montoya.BurpExtension;
//import burp.api.montoya.MontoyaApi;
//
//public class Extension implements BurpExtension {
//    @Override
//    public void initialize(MontoyaApi api) {
//        api.extension().setName("API In js");
//
//        // Khởi tạo tab UI
//        DecryptedHistoryTab decryptedTab = new DecryptedHistoryTab(api);
//        api.userInterface().registerSuiteTab("API In js", decryptedTab.getComponent());
//
//        // Đăng ký HTTP handler
//        api.http().registerHttpHandler(new MyHttpHandler(api, decryptedTab));
//    }
//}
//
