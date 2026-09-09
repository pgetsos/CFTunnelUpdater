package gr.pgetsos.cftunnelupdater;
import android.content.*;
/** Operational status, scoped to the current connection settings; contains no credentials. */
public final class StatusStore {
    public final SharedPreferences prefs;
    public StatusStore(Context context) {
        SettingsManager s = new SettingsManager(context);
        String scope = s.getAccountId()+"|"+s.getGroupId()+"|"+s.getWorkerUrl()+"|"+s.getIpCheckerType()+"|"+s.getCustomIpCheckerUrl();
        prefs = context.getSharedPreferences("status_"+android.net.Uri.encode(scope), Context.MODE_PRIVATE);
    }
    public void syncSuccess() { prefs.edit().putLong("synced",System.currentTimeMillis()).remove("syncError").apply(); }
    public void syncFailure(Exception e) { prefs.edit().putString("syncError",e.getMessage()==null?"Sync failed":e.getMessage()).apply(); }
    public void access(String host, boolean allowed) {
        prefs.edit().putString("host",host).putBoolean("allowed",allowed).putLong("checked",System.currentTimeMillis()).remove("checkError").apply();
    }
    public void checkFailure(Exception e) { prefs.edit().putString("checkError",e.getMessage()==null?"Check failed":e.getMessage()).apply(); }
}
