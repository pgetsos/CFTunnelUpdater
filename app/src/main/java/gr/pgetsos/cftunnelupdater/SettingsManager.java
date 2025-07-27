package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_FILE = "MyPrefs";
    private static final String KEY_ACCOUNT_ID = "accountID";
    private static final String KEY_GROUP_ID = "groupID";
    private static final String KEY_API_TOKEN = "apiToken";
    public static final String PREF_IP_CHECKER_TYPE = "ip_checker_type";
    public static final String PREF_CUSTOM_IP_CHECKER_URL = "custom_ip_checker_url";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    public String getAccountId() {
        return prefs.getString(KEY_ACCOUNT_ID, "");
    }

    public void setAccountId(String accountId) {
        prefs.edit().putString(KEY_ACCOUNT_ID, accountId).apply();
    }

    public String getGroupId() {
        return prefs.getString(KEY_GROUP_ID, "");
    }

    public void setGroupId(String groupId) {
        prefs.edit().putString(KEY_GROUP_ID, groupId).apply();
    }

    public String getApiToken() {
        return prefs.getString(KEY_API_TOKEN, "");
    }

    public void setApiToken(String apiToken) {
        prefs.edit().putString(KEY_API_TOKEN, apiToken).apply();
    }

    public String getIpCheckerType() {
        return prefs.getString(PREF_IP_CHECKER_TYPE, "ipify");
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
        editor.putString(KEY_ACCOUNT_ID, accountId);
        editor.putString(KEY_GROUP_ID, groupId);
        editor.putString(KEY_API_TOKEN, apiToken);
        editor.putString(PREF_IP_CHECKER_TYPE, ipCheckerType);
        editor.putString(PREF_CUSTOM_IP_CHECKER_URL, customIpCheckerUrl);
        editor.apply();
    }
}
