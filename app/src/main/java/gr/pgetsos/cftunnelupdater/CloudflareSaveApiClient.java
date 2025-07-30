package gr.pgetsos.cftunnelupdater;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CloudflareSaveApiClient {

    private static final String TAG = "CloudflareSaveApiClient";

    private static final String CF_WORKER_BASE_URL = "https://your-worker-name.your-account.workers.dev"; // TODO: Replace
    private static final String CF_WORKER_API_KEY = "YOUR_SUPER_SECRET_API_KEY_CHANGE_ME"; // TODO: Replace
    private static final String CF_WORKER_AUTH_HEADER = "X-API-Key";

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;

    public CloudflareSaveApiClient(OkHttpClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public String encodeIpForUrlPath(String ipAddress) {
        if (ipAddress == null) return "";
        return ipAddress.replace(".", "_").replace(":", "-");
    }

    public void setIpName(String accountId, String groupId, String ipAddress, String name, @NonNull WorkerApiCallbacks.GenericWorkerApiCallback callback) {
        String encodedIp = encodeIpForUrlPath(ipAddress);
        String url = CF_WORKER_BASE_URL + "/names/" + accountId + "/" + groupId + "/" + encodedIp;

        IpNameModels.SetNameRequest setNameRequest = new IpNameModels.SetNameRequest(name);
        String jsonBody = gson.toJson(setNameRequest);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .addHeader(CF_WORKER_AUTH_HEADER, CF_WORKER_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "setIpName onFailure: URL=" + call.request().url(), e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error setting IP name"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String responseBodyString = responseBody != null ? responseBody.string() : "";
                    if (response.isSuccessful()) {
                        mainHandler.post(() -> callback.onSuccess("Name set successfully"));
                    } else {
                        Log.e(TAG, "setIpName error: " + response.code() + " URL=" + call.request().url() + " Body=" + responseBodyString);
                        mainHandler.post(() -> callback.onError("Error [" + response.code() + "] setting IP name"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "setIpName onResponse IOException: URL=" + call.request().url(), e);
                    mainHandler.post(() -> callback.onError("Error reading response: " + e.getMessage()));
                }
            }
        });
    }

    public void getIpName(String accountId, String groupId, String ipAddress, @NonNull WorkerApiCallbacks.WorkerGetNameApiCallback callback) {
        String encodedIp = encodeIpForUrlPath(ipAddress);
        String url = CF_WORKER_BASE_URL + "/names/" + accountId + "/" + groupId + "/" + encodedIp;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader(CF_WORKER_AUTH_HEADER, CF_WORKER_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "getIpName onFailure: URL=" + call.request().url(), e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error getting IP name"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String responseBodyString = responseBody != null ? responseBody.string() : "";
                    if (response.isSuccessful()) {
                        try {
                            IpNameModels.GetNameResponse getNameResponse = gson.fromJson(responseBodyString, IpNameModels.GetNameResponse.class);
                            mainHandler.post(() -> callback.onNameRetrieved(getNameResponse != null ? getNameResponse.name : null));
                        } catch (JsonSyntaxException e) {
                            Log.e(TAG, "getIpName JSONException: URL=" + call.request().url() + " Body=" + responseBodyString, e);
                            mainHandler.post(() -> callback.onError("Error parsing name response"));
                        }
                    } else if (response.code() == 404) {
                        Log.i(TAG, "getIpName: Name not found (404) for URL=" + call.request().url());
                        mainHandler.post(() -> callback.onNameRetrieved(null));
                    } else {
                        Log.e(TAG, "getIpName error: " + response.code() + " URL=" + call.request().url() + " Body=" + responseBodyString);
                        mainHandler.post(() -> callback.onError("Error [" + response.code() + "] getting IP name"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "getIpName onResponse IOException: URL=" + call.request().url(), e);
                    mainHandler.post(() -> callback.onError("Error reading response: " + e.getMessage()));
                }
            }
        });
    }

    public void deleteIpName(String accountId, String groupId, String ipAddress, @NonNull WorkerApiCallbacks.GenericWorkerApiCallback callback) {
        String encodedIp = encodeIpForUrlPath(ipAddress);
        String url = CF_WORKER_BASE_URL + "/names/" + accountId + "/" + groupId + "/" + encodedIp;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader(CF_WORKER_AUTH_HEADER, CF_WORKER_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "deleteIpName onFailure: URL=" + call.request().url(), e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error deleting IP name"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String responseBodyString = responseBody != null ? responseBody.string() : "";
                    if (response.isSuccessful()) {
                        mainHandler.post(() -> callback.onSuccess("Name deleted successfully"));
                    } else {
                        Log.e(TAG, "deleteIpName error: " + response.code() + " URL=" + call.request().url() + " Body=" + responseBodyString);
                        mainHandler.post(() -> callback.onError("Error [" + response.code() + "] deleting IP name"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "deleteIpName onResponse IOException: URL=" + call.request().url(), e);
                    mainHandler.post(() -> callback.onError("Error reading response: " + e.getMessage()));
                }
            }
        });
    }

    public void getAllIpNamesForGroup(String accountId, String groupId, @NonNull WorkerApiCallbacks.WorkerGetAllNamesApiCallback callback) {
        String url = CF_WORKER_BASE_URL + "/names/" + accountId + "/" + groupId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader(CF_WORKER_AUTH_HEADER, CF_WORKER_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "getAllIpNamesForGroup onFailure: URL=" + call.request().url(), e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error getting all IP names"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String responseBodyString = responseBody != null ? responseBody.string() : "";
                    if (response.isSuccessful()) {
                        try {
                            Type type = new TypeToken<Map<String, String>>() {}.getType();
                            Map<String, String> namesMap = gson.fromJson(responseBodyString, type);
                            mainHandler.post(() -> callback.onAllNamesRetrieved(namesMap != null ? namesMap : Collections.emptyMap()));
                        } catch (JsonSyntaxException e) {
                            Log.e(TAG, "getAllIpNamesForGroup JSONException: URL=" + call.request().url() + " Body=" + responseBodyString, e);
                            mainHandler.post(() -> callback.onError("Error parsing all names response"));
                        }
                    } else {
                        Log.e(TAG, "getAllIpNamesForGroup error: " + response.code() + " URL=" + call.request().url() + " Body=" + responseBodyString);
                        mainHandler.post(() -> callback.onError("Error [" + response.code() + "] getting all IP names"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "getAllIpNamesForGroup onResponse IOException: URL=" + call.request().url(), e);
                    mainHandler.post(() -> callback.onError("Error reading response: " + e.getMessage()));
                }
            }
        });
    }
}
