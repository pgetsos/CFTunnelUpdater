package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Local names when sync is disabled; a separate cache for each Worker/account/group. */
public class IpNameStore {
    private final SharedPreferences cache;
    private final Map<String, java.util.List<String>> legacyKeys = new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> REVISIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger revision;
    private final CloudflareSaveApiClient api;
    private final boolean sync;
    private final String account;
    private final String group;

    public IpNameStore(Context context) {
        SettingsManager settings = new SettingsManager(context);
        account = settings.getAccountId();
        group = settings.getGroupId();
        sync = !settings.getWorkerUrl().trim().isEmpty() || !settings.getWorkerApiKey().trim().isEmpty();
        String scope = android.net.Uri.encode(settings.getWorkerUrl().trim().replaceAll("/+$", "")
                + "|" + account + "|" + group);
        revision = REVISIONS.computeIfAbsent(scope, ignored -> new java.util.concurrent.atomic.AtomicInteger());
        cache = context.getSharedPreferences("ip_names_" + scope, Context.MODE_PRIVATE);
        api = new CloudflareSaveApiClient(context);
    }

    public Map<String, String> cached() {
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, ?> entry : cache.getAll().entrySet()) {
            if (entry.getValue() instanceof String) names.put(entry.getKey(), (String) entry.getValue());
        }
        return names;
    }

    public void load(WorkerApiCallbacks.WorkerGetAllNamesApiCallback callback) {
        if (!sync) { callback.onAllNamesRetrieved(cached()); return; }
        int requestedRevision = revision.get();
        api.getAllIpNamesForGroup(account, group, new WorkerApiCallbacks.WorkerGetAllNamesApiCallback() {
            public void onAllNamesRetrieved(Map<String, String> names) {
                if (requestedRevision != revision.get()) { callback.onAllNamesRetrieved(cached()); return; }
                legacyKeys.clear();
                Map<String, String> normalized = new HashMap<>();
                for (Map.Entry<String, String> entry : names.entrySet()) {
                    try {
                        String name = entry.getValue();
                        // Read names written by the old prototype without its timestamp suffix.
                        if (name == null || name.matches("Added:[0-9]+")) continue;
                        name = name.replaceFirst("DELAdded:[0-9]+$", "");
                        String key = IpNameKey.of(entry.getKey());
                        if (!key.equals(entry.getKey())) {
                            legacyKeys.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(entry.getKey());
                            normalized.putIfAbsent(key, name);
                        } else normalized.put(key, name);
                    } catch (IllegalArgumentException ignored) { }
                }
                SharedPreferences.Editor editor = cache.edit().clear();
                normalized.forEach(editor::putString);
                editor.apply();
                callback.onAllNamesRetrieved(normalized);
            }
            public void onError(String error) { callback.onError(error); }
        });
    }

    public void save(String ip, String name, WorkerApiCallbacks.GenericWorkerApiCallback callback) {
        String key = IpNameKey.of(ip);
        String value = name.trim();
        if (value.length() > 100) { callback.onError("Names can contain up to 100 characters."); return; }
        revision.incrementAndGet();
        WorkerApiCallbacks.GenericWorkerApiCallback saved = new WorkerApiCallbacks.GenericWorkerApiCallback() {
            public void onSuccess(String message) {
                if (value.isEmpty()) cache.edit().remove(key).apply();
                else cache.edit().putString(key, value).apply();
                callback.onSuccess(sync ? "Name synced." : "Name saved on this phone.");
            }
            public void onError(String error) { callback.onError(error); }
        };
        if (!sync) saved.onSuccess("");
        else {
            WorkerApiCallbacks.GenericWorkerApiCallback cleanup = new WorkerApiCallbacks.GenericWorkerApiCallback() {
                public void onSuccess(String message) {
                    java.util.List<String> aliases = legacyKeys.get(key);
                    removeLegacy(aliases == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(aliases), saved);
                }
                public void onError(String error) { saved.onError(error); }
            };
            if (value.isEmpty()) api.deleteIpName(account, group, key, cleanup);
            else api.setIpName(account, group, key, value, cleanup);
        }
    }
    private void removeLegacy(java.util.List<String> aliases, WorkerApiCallbacks.GenericWorkerApiCallback callback) {
        if (aliases.isEmpty()) { callback.onSuccess(""); return; }
        api.deleteIpName(account, group, aliases.remove(0), new WorkerApiCallbacks.GenericWorkerApiCallback() {
            public void onSuccess(String message) { removeLegacy(aliases, callback); }
            public void onError(String error) { callback.onError("Could not remove an older label: " + error); }
        });
    }
}
