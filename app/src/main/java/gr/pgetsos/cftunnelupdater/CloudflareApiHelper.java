package gr.pgetsos.cftunnelupdater;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudflareApiHelper {
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String AUTHORIZATION_VALUE = "Bearer ";

    public interface ApiCallback<T> {
        void onSuccess(T result);

        void onError(Exception e);
    }

    private final Gson gson = new Gson();
    private final OkHttpClient client = new OkHttpClient();

    public void fetchIpsFromCloudflare(String accountID, String groupID, String apiToken, ApiCallback<List<String>> callback) {
        Request request = new Request.Builder()
                .url(getCloudflareURL(accountID, groupID))
                .get()
                .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE + apiToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Failed: " + response.code() + ", " + response.message()));
                    }

                    String body = response.body().string();
                    AccessGroupResponse result = gson.fromJson(body, AccessGroupResponse.class);

                    if (result == null || result.result == null || result.result.include == null) {
                        callback.onError(new IOException("Failed to parse Cloudflare API response."));
                        return;
                    }

                    List<String> ips = result.result.include.stream()
                            .filter(item -> item != null && item.ip != null && item.ip.ip != null)
                            .map(item -> item.ip.ip)
                            .collect(Collectors.toList());

                    callback.onSuccess(ips);
                }
            }
        });
    }

    public void deleteIpFromCloudflare(String accountID, String groupID, String apiToken, String ipToDelete, ApiCallback<Boolean> callback) {
        Request getRequest = new Request.Builder()
                .url(getCloudflareURL(accountID, groupID))
                .get()
                .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE + apiToken)
                .build();

        client.newCall(getRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody;
                try (response) {
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Error getting group for deletion: " + response.code()));
                        return;
                    }
                    responseBody = response.body().string();
                }

                AccessGroupResponse currentGroup = gson.fromJson(responseBody, AccessGroupResponse.class);
                if (currentGroup == null || currentGroup.result == null || currentGroup.result.include == null) {
                    callback.onError(new Exception("Invalid group data received for deletion."));
                    return;
                }

                List<AccessGroupResponse.IncludeItem> updatedIncludeList = new ArrayList<>();
                boolean ipFound = false;
                for (AccessGroupResponse.IncludeItem item : currentGroup.result.include) {
                    if (item.ip != null && item.ip.ip != null && item.ip.ip.equals(ipToDelete)) {
                        ipFound = true;
                    } else {
                        updatedIncludeList.add(item);
                    }
                }
                if (!ipFound) {
                    callback.onSuccess(false);
                    return;
                }
                AccessGroupUpdateRequest updateRequestPayload = new AccessGroupUpdateRequest();
                updateRequestPayload.include = updatedIncludeList;

                String bodyJson = gson.toJson(updateRequestPayload);
                RequestBody requestBody = RequestBody.create(bodyJson, MediaType.parse("application/json; charset=utf-8"));
                Request putRequest = new Request.Builder()
                        .url(getCloudflareURL(accountID, groupID))
                        .put(requestBody)
                        .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE + apiToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                client.newCall(putRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        callback.onError(e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        boolean ok = response.isSuccessful();
                        response.close();
                        if (ok) {
                            callback.onSuccess(true);
                        } else {
                            callback.onError(new IOException("Failed to delete IP: " + response.code()));
                        }
                    }
                });
            }
        });
    }

    public void getCurrentGroup(String accountID, String groupID, String apiToken, ApiCallback<AccessGroupResponse> callback) {
        Request request = new Request.Builder()
                .url(getCloudflareURL(accountID, groupID))
                .get()
                .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE + apiToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (response) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        AccessGroupResponse group = gson.fromJson(responseBody, AccessGroupResponse.class);
                        callback.onSuccess(group);
                    } else {
                        callback.onError(new IOException("Failed to get current group: " + response.code()));
                    }
                }
            }
        });
    }

    public void updateAccessGroup(String accountID, String groupID, String apiToken, List<AccessGroupResponse.IncludeItem> includeList, ApiCallback<Boolean> callback) {
        AccessGroupUpdateRequest updateRequestPayload = new AccessGroupUpdateRequest();
        updateRequestPayload.include = includeList;
        String bodyJson = gson.toJson(updateRequestPayload);

        RequestBody body = RequestBody.create(bodyJson, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(getCloudflareURL(accountID, groupID))
                .put(body)
                .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE + apiToken)
                .addHeader("Content-Type", "application/json")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                boolean ok = response.isSuccessful();
                response.close();
                if (ok) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(new IOException("Failed updating group: " + response.code()));
                }
            }
        });
    }

    private String getCloudflareURL(String accountID, String groupID) {
        return "https://api.cloudflare.com/client/v4/accounts/" + accountID + "/access/groups/" + groupID;
    }
}
