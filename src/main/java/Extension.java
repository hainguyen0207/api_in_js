import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;


public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("API In JS");

        APITab apiTab = new APITab(api);
        api.userInterface().registerSuiteTab("API In JS", apiTab.getComponent());

        api.http().registerHttpHandler(new MyHttpHandler(api, apiTab));

        api.extension().registerUnloadingHandler(apiTab::saveData);
    }
}

