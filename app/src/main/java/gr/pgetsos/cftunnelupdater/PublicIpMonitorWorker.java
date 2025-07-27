package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class PublicIpMonitorWorker extends Worker {

    private static final String TAG = "PublicIpMonitorWorker";
    public static final String PREF_LAST_AUTO_ADDED_IP = "last_auto_added_ip";
    public static final String PREF_AUTO_UPDATE_ENABLED = "auto_update_enabled";

    public PublicIpMonitorWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);

        boolean autoUpdateEnabled = sharedPreferences.getBoolean(PREF_AUTO_UPDATE_ENABLED, false);
        if (!autoUpdateEnabled) {
            Log.i(TAG, "Auto-update disabled, skipping work.");
            return Result.success(); // Or Result.failure() if this shouldn't have been scheduled
        }

        String accountID = sharedPreferences.getString("accountID", "");
        String groupID = sharedPreferences.getString("groupID", "");
        String apiToken = sharedPreferences.getString("apiToken", "");

        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            Log.e(TAG, "Credentials not set. Cannot perform auto-update.");
            // Send a notification to the user to set credentials (Someday)
            return Result.failure();
        }

        String currentPublicIp = fetchPublicIPAddress();
        if (currentPublicIp == null || currentPublicIp.isEmpty()) {
            Log.e(TAG, "Failed to fetch current public IP.");
            return Result.retry();
        }
        Log.i(TAG, "Current Public IP: " + currentPublicIp);

        String lastAutoAddedIp = sharedPreferences.getString(PREF_LAST_AUTO_ADDED_IP, "");
        Log.i(TAG, "Last Auto-Added IP: " + lastAutoAddedIp);


        String baseCurrentPublicIp = currentPublicIp.split("/")[0];
        String baseLastAutoAddedIp = !lastAutoAddedIp.isBlank() ? lastAutoAddedIp.split("/")[0] : "";

        if (baseCurrentPublicIp.equals(baseLastAutoAddedIp)) {
            Log.i(TAG, "Public IP (" + baseCurrentPublicIp + ") has not changed since last auto-update.");
            return Result.success();
        }

        Log.i(TAG, "Public IP changed to " + baseCurrentPublicIp + ". Updating Cloudflare.");

        OkHttpClient client = new OkHttpClient();
        Gson gson = new Gson();

        AccessGroupResponse currentGroup = fetchCloudflareGroup(client, accountID, groupID, apiToken, gson);
        if (currentGroup == null || currentGroup.result == null) {
            Log.e(TAG, "Failed to fetch current Cloudflare group details.");
            return Result.retry();
        }
        if (currentGroup.result.include == null) {
            currentGroup.result.include = new ArrayList<>();
        }

        List<AccessGroupResponse.IncludeItem> updatedIncludeList = new ArrayList<>();
        boolean newIpAlreadyInListManually = false;

        for (AccessGroupResponse.IncludeItem item : currentGroup.result.include) {
            if (item.ip != null && item.ip.ip != null) {
                if (item.ip.ip.equals(lastAutoAddedIp)) {
                    Log.i(TAG, "Removing old auto-added IP: " + lastAutoAddedIp);
                    continue;
                }

                String normalizedNewPublicIpWithCidr = normalizeIpWithCidr(baseCurrentPublicIp);
                if (item.ip.ip.equals(normalizedNewPublicIpWithCidr)) {
                    newIpAlreadyInListManually = true;
                    Log.i(TAG, "New public IP " + normalizedNewPublicIpWithCidr + " was already in the group.");
                }
                updatedIncludeList.add(item);
            }
        }


        if (!newIpAlreadyInListManually) {
            String newIpWithCidr = normalizeIpWithCidr(baseCurrentPublicIp);
            if (!newIpWithCidr.isBlank()) {
                AccessGroupResponse.IncludeItem newIpItem = new AccessGroupResponse.IncludeItem();
                AccessGroupResponse.Ip ipDetails = new AccessGroupResponse.Ip();
                ipDetails.ip = newIpWithCidr;
                newIpItem.ip = ipDetails;
                updatedIncludeList.add(newIpItem);
                Log.i(TAG, "Adding new public IP: " + newIpWithCidr);
            } else {
                Log.e(TAG, "Could not normalize new public IP: " + baseCurrentPublicIp);
                return Result.failure();
            }
        }


        AccessGroupUpdateRequest updateRequestPayload = new AccessGroupUpdateRequest();
        updateRequestPayload.include = updatedIncludeList;

        String bodyJson = gson.toJson(updateRequestPayload);

        boolean updateSuccess = updateCloudflareGroup(client, accountID, groupID, apiToken, bodyJson);

        if (updateSuccess) {
            Log.i(TAG, "Cloudflare group updated successfully with new IP: " + baseCurrentPublicIp);
            sharedPreferences.edit().putString(PREF_LAST_AUTO_ADDED_IP, normalizeIpWithCidr(baseCurrentPublicIp)).apply();
            return Result.success();
        } else {
            Log.e(TAG, "Failed to update Cloudflare group.");
            return Result.retry();
        }
    }

    private String normalizeIpWithCidr(String baseIp) {
        try {
            InetAddress address = InetAddress.getByName(baseIp);
            if (address instanceof Inet6Address) {
                return address.getHostAddress() + "/64";
            } else if (address instanceof Inet4Address) {
                return address.getHostAddress() + "/32";
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "Invalid IP for normalization: " + baseIp, e);
        }
        return "";
    }


    private String fetchPublicIPAddress() {
        try {
            URL url = new URL("https://api64.ipify.org"); // Or your preferred IP service
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "CFTunnelUpdater-AutoIP/1.0");
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);    // 5 seconds
            try (Scanner s = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                if (s.hasNext()) {
                    return s.next();
                }
            }
            connection.disconnect();
        } catch (IOException e) {
            Log.e(TAG, "Error fetching public IP", e);
        }
        return null;
    }

    private AccessGroupResponse fetchCloudflareGroup(OkHttpClient client, String accountId, String groupId, String apiToken, Gson gson) {
        Request request = new Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/access/groups/" + groupId)
                .get()
                .addHeader("authorization", "Bearer " + apiToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return gson.fromJson(response.body().string(), AccessGroupResponse.class);
            } else {
                Log.e(TAG, "Fetch CF Group Error: " + response.code() + " - " + (response.body() != null ? response.body().string() : "No body"));
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException fetching CF Group", e);
        }
        return null;
    }

    private boolean updateCloudflareGroup(OkHttpClient client, String accountId, String groupId, String apiToken, String jsonPayload) {
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/access/groups/" + groupId)
                .put(body)
                .addHeader("authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            } else {
                Log.e(TAG, "Update CF Group Error: " + response.code() + " - " + (response.body() != null ? response.body().string() : "No body"));
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException updating CF Group", e);
        }
        return false;
    }
}

