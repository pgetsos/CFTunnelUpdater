package gr.pgetsos.cftunnelupdater;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
public class IpNameStore {
    private final IpMetadataStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    public IpNameStore(Context context) { store = new IpMetadataStore(context); }
    public Map<String,String> cached() {
        Map<String,String> names = new HashMap<>();
        store.all().forEach((ip,r) -> { if (!r.deleted) names.put(ip, r.name); }); return names;
    }
    public void load(WorkerApiCallbacks.WorkerGetAllNamesApiCallback cb) {
        new Thread(() -> { try { store.sync(); main.post(() -> cb.onAllNamesRetrieved(cached())); }
            catch (Exception e) { main.post(() -> cb.onError(e.getMessage())); } }).start();
    }
    public void save(String ip, String name, WorkerApiCallbacks.GenericWorkerApiCallback cb) {
        store.saveLocal(ip, name, store.get(ip).expiresAt, false);
        new Thread(() -> { try { store.sync(); main.post(() -> cb.onSuccess("Name saved.")); }
            catch (Exception e) { main.post(() -> cb.onError("Saved locally. " + e.getMessage())); } }).start();
    }
}
