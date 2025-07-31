package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    public static final String PREFS_NAME = "MyPrefs";
    public static final String PREF_ACCOUNT_ID = "accountId";
    public static final String PREF_GROUP_ID = "groupId";
    public static final String PREF_ACCESS_GROUP_KEY = "accessGroupKey";
    public static final String PREF_IP_CHECKER_TYPE = "ipCheckerType";
    public static final String PREF_CUSTOM_IP_CHECKER_URL = "customIpCheckerUrl";
    public static final String PREF_AUTO_UPDATE_ENABLED = "autoUpdateEnabled";
    public static final String PREF_KV_ACCOUNT_ID = "kvAccountId";
    public static final String PREF_KV_NAMESPACE_ID = "kvNamespaceId";
    public static final String PREF_KV_API_KEY = "kvApiKey";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAccountId() {
        return prefs.getString(PREF_ACCOUNT_ID, "");
    }

    public void setAccountId(String accountId) {
        prefs.edit().putString(PREF_ACCOUNT_ID, accountId).apply();
    }

    public String getGroupId() {
        return prefs.getString(PREF_GROUP_ID, "");
    }

    public void setGroupId(String groupId) {
        prefs.edit().putString(PREF_GROUP_ID, groupId).apply();
    }

    public String getApiToken() {
        return prefs.getString(PREF_ACCESS_GROUP_KEY, "");
    }

    public void setApiToken(String apiToken) {
        prefs.edit().putString(PREF_ACCESS_GROUP_KEY, apiToken).apply();
    }

    public String getIpCheckerType() {
        return prefs.getString(PREF_IP_CHECKER_TYPE, "IPIFY");
    }

    public void setIpCheckerType(String type) {
        prefs.edit().putString(PREF_IP_CHECKER_TYPE, type).apply();
    }

    public String getCustomIpCheckerUrl() {
        return prefs.getString(PREF_CUSTOM_IP_CHECKER_URL, "");
    }

    public void setCustomIpCheckerUrl(String url) {
        prefs.edit().putString(PREF_CUSTOM_IP_CHECKER_URL, url).apply();
    }

    public void saveAll(String accountId, String groupId, String apiToken, String ipCheckerType, String customIpCheckerUrl) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_ACCOUNT_ID, accountId);
        editor.putString(PREF_GROUP_ID, groupId);
        editor.putString(PREF_ACCESS_GROUP_KEY, apiToken);
        editor.putString(PREF_IP_CHECKER_TYPE, ipCheckerType);
        editor.putString(PREF_CUSTOM_IP_CHECKER_URL, customIpCheckerUrl);
        editor.apply();
    }
}