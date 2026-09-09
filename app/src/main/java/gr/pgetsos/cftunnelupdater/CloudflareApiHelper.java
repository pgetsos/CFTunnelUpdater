package gr.pgetsos.cftunnelupdater;
import java.util.List;
public class CloudflareApiHelper {
    public interface ApiCallback<T> { void onSuccess(T result); void onError(Exception e); }
    public void fetchIpsFromCloudflare(String account, String group, String token, ApiCallback<List<String>> callback) {
        new Thread(() -> { try { callback.onSuccess(new AccessService(account, group, token).list()); }
            catch (Exception e) { callback.onError(e); } }).start();
    }
    public void deleteIpFromCloudflare(String account, String group, String token, String ip, ApiCallback<Boolean> callback) {
        new Thread(() -> { try { callback.onSuccess(new AccessService(account, group, token).delete(ip)); }
            catch (Exception e) { callback.onError(e); } }).start();
    }
}
