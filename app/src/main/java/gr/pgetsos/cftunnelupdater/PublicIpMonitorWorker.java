package gr.pgetsos.cftunnelupdater;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.gson.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import gr.pgetsos.cftunnelupdater.utils.NetworkUtils;

public class PublicIpMonitorWorker extends Worker {
    public static final String PREF_LAST_AUTO_ADDED_IP = "last_auto_added_ip";
    public PublicIpMonitorWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        SettingsManager s = new SettingsManager(getApplicationContext());
        if (s.getAccountId().isEmpty() || s.getGroupId().isEmpty() || s.getApiToken().isEmpty()) return Result.success();
        IpMetadataStore metadata = new IpMetadataStore(getApplicationContext());
        boolean syncFailed = false;
        try { metadata.sync(); } catch (Exception e) { syncFailed = true; }
        try {
            AccessService service = new AccessService(s.getAccountId(), s.getGroupId(), s.getApiToken());
            // Expiry cleanup is independent of the auto-IP setting.
            for (Map.Entry<String, IpRecord> entry : metadata.all().entrySet()) if (entry.getValue().expired(System.currentTimeMillis())) {
                service.delete(entry.getKey()); metadata.deleted(entry.getKey());
                s.blockExpiredAutoIp(entry.getKey());
                if (AccessService.same(entry.getKey(), s.getOwnedAutoIp())) s.setOwnedAutoIp("");
            }
            // Do not remove an entry before shared names can be checked.
            if (s.isAutoUpdateEnabled() && !syncFailed) {
                String host = PublicIpLookup.fetch(s);
                String current = AutoIpPolicy.automaticRange(host);
                if (AutoIpPolicy.shouldUpdate(current, s.getOwnedAutoIp(), s.isExpiredAutoIpBlocked(current), metadata.get(current).deleted)) {
                    synchronized (AccessService.MUTATION_LOCK) {
                        JsonObject group = service.read();
                        String owned = s.getOwnedAutoIp();
                        String previous = AutoIpPolicy.replacementOwner(owned, owned.isEmpty() ? "" : metadata.get(owned).name);
                        AutoIpPolicy.Change change = AutoIpPolicy.apply(AccessService.includes(group), current, previous, s.isReplaceAutoIpEnabled());
                        if (change.changed) { group.add("include", change.rules); service.write(group); }
                        new StatusStore(getApplicationContext()).access(host, true);
                        s.setOwnedAutoIp(change.owned);
                        if (change.added) metadata.saveLocal(current, null, null, true);
                        if (s.isReplaceAutoIpEnabled() && !previous.isEmpty() && !AccessService.same(previous, current)) metadata.deleted(previous);
                    }
                }
            }
            try { metadata.sync(); } catch (Exception e) { syncFailed = true; }
            return syncFailed ? Result.retry() : Result.success();
        } catch (Exception e) { new StatusStore(getApplicationContext()).checkFailure(e); return Result.retry(); }
    }
}
