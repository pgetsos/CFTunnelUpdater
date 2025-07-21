package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog; // Import AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnIpLongPressListener {

    private AtomicReference<String> publicIp = new AtomicReference<>();
    private String accountID;
    private String groupID;
    private String apiToken;
    private String ipAddr;
    private EditText ipEditText;
    private int selectedNavItemId = R.id.nav_add_ip;
    private IPAdapter ipAdapter;
    private List<String> lastFetchedIps = new ArrayList<>();
    private TextView currentIpStatusTextView;
    private boolean hasFetchedIps = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        accountID = sharedPreferences.getString("accountID", "");
        groupID = sharedPreferences.getString("groupID", "");
        apiToken = sharedPreferences.getString("apiToken", "");

        if (savedInstanceState != null) {
            selectedNavItemId = savedInstanceState.getInt("selectedNavItemId", R.id.nav_add_ip);
        }

        ipAdapter = new IPAdapter(new ArrayList<>(), this);

        // Fetch IPs from Cloudflare when the app starts to check the current IP against them
        fetchIpsOnAppStart();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == selectedNavItemId) return true;
            if (item.getItemId() == R.id.nav_add_ip) {
                selectedNavItemId = R.id.nav_add_ip;
                showAddIpView();
                return true;
            } else if (item.getItemId() == R.id.nav_list_ips) {
                selectedNavItemId = R.id.nav_list_ips;
                showListIpsView();
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(selectedNavItemId);
        if (selectedNavItemId == R.id.nav_list_ips) {
            showListIpsView();
        } else {
            showAddIpView();
        }
    }

    @Override
    public void onIpLongPressed(String ipAddress, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete IP")
                .setMessage("Are you sure you want to delete this IP address: " + ipAddress + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteIpFromCloudflare(ipAddress, position))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert) // Optional icon
                .show();
    }

    private void deleteIpFromCloudflare(String ipToDelete, int position) {
        // If the IP list is populated, this Toast should never be shown, but let's be extra careful
        if (areCredentialsUnavailable()) {
            Toast.makeText(this, "Credentials not set.", Toast.LENGTH_SHORT).show();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request getRequest = new Request.Builder()
                .url(getCloudflareURL())
                .get()
                .addHeader("authorization", "Bearer " + apiToken)
                .build();

        client.newCall(getRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("DeleteIP", "Failed to get current group for deletion", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get group data: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e("DeleteIP", "Error getting group for deletion: " + response.code() + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error fetching group: " + response.code(), Toast.LENGTH_LONG).show());
                    response.close();
                    return;
                }

                String responseBody = response.body().string();
                response.close();

                Gson gson = new Gson();
                AccessGroupResponse currentGroup = gson.fromJson(responseBody, AccessGroupResponse.class);

                if (currentGroup == null || currentGroup.result == null || currentGroup.result.include == null) {
                    Log.e("DeleteIP", "Invalid group data received for deletion.");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Invalid group data for deletion.", Toast.LENGTH_SHORT).show());
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
                     runOnUiThread(() -> Toast.makeText(MainActivity.this, "IP not found in the current group.", Toast.LENGTH_SHORT).show());
                     fetchIpsOnAppStart();
                     return;
                }

                AccessGroupUpdateRequest updateRequestPayload = new AccessGroupUpdateRequest();
                updateRequestPayload.include = updatedIncludeList;

                String bodyJson = gson.toJson(updateRequestPayload);

                RequestBody requestBody = RequestBody.create(bodyJson, MediaType.parse("application/json; charset=utf-8"));
                Request putRequest = new Request.Builder()
                        .url(getCloudflareURL())
                        .put(requestBody)
                        .addHeader("authorization", "Bearer " + apiToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                client.newCall(putRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("DeleteIP", "Update request failed during deletion", e);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Deletion failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "IP " + ipToDelete + " deleted successfully.", Toast.LENGTH_SHORT).show();
                                // Refresh the list from Cloudflare to ensure consistency
                                fetchIpsOnAppStart();
                            });
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "No error body";
                            Log.e("DeleteIP", "Error deleting IP: " + response.code() + " - " + errorBody);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error deleting IP: " + response.code(), Toast.LENGTH_LONG).show());
                        }
                        response.close();
                    }
                });
            }
        });
    }

    private void fetchIpsOnAppStart() {
        if (areCredentialsUnavailable()) {
            Log.w("MainActivity", "Credentials not available, skipping initial IP fetch.");
            // TODO prompt the user to enter credentials on App start
            return;
        }

        new Thread(() -> {
            List<String> ips = fetchIpsFromCloudflare();
            if (ips != null) {
                synchronized (lastFetchedIps) {
                    lastFetchedIps.clear();
                    lastFetchedIps.addAll(ips);
                }
                // If the List IPs view is already visible, update it
                // This check helps for if the user navigates to List IPs before fetch is completed
                if (selectedNavItemId == R.id.nav_list_ips && findViewById(R.id.ips_recycler) != null) {
                    runOnUiThread(() -> {
                        if (ipAdapter != null) {
                            ipAdapter.updateList(new ArrayList<>(lastFetchedIps));
                            TextView emptyText = findViewById(R.id.empty_text);
                            if (emptyText != null) {
                                emptyText.setVisibility(lastFetchedIps.isEmpty() ? View.VISIBLE : View.GONE);
                                if (lastFetchedIps.isEmpty()) {
                                    emptyText.setText("No IPs present");
                                }
                            }
                        }
                    });
                }
                if (selectedNavItemId == R.id.nav_add_ip) {
                    runOnUiThread(this::updateCurrentIpStatus);
                }
            } else {
                Log.e("MainActivity", "Failed to fetch IPs on app start.");
                 runOnUiThread(() -> {
                     Toast.makeText(MainActivity.this, "Failed to fetch IPs.", Toast.LENGTH_SHORT).show();
                     if (selectedNavItemId == R.id.nav_add_ip) {
                         updateCurrentIpStatus();
                     }
                     if (selectedNavItemId == R.id.nav_list_ips && findViewById(R.id.ips_recycler) != null) {
                         synchronized(lastFetchedIps) {
                             lastFetchedIps.clear();
                         }
                         if (ipAdapter != null) {
                             ipAdapter.updateList(new ArrayList<>(lastFetchedIps));
                             TextView emptyText = findViewById(R.id.empty_text);
                             if (emptyText != null) {
                                 emptyText.setVisibility(View.VISIBLE);
                                 emptyText.setText("Failed to load IPs. Check connection or credentials.");
                             }
                         }
                     }
                 });
            }
        }).start();
    }
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("selectedNavItemId", selectedNavItemId);
        super.onSaveInstanceState(outState);
    }

    private void showAddIpView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        ViewGroup container = findViewById(R.id.container);
        container.removeAllViews();
        View addIpView = inflater.inflate(R.layout.fragment_add_ip, container, false);
        container.addView(addIpView);
        // Ensure credentials are up-to-date. May be changed if central credential management is implemented
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        accountID = sharedPreferences.getString("accountID", "");
        groupID = sharedPreferences.getString("groupID", "");
        apiToken = sharedPreferences.getString("apiToken", "");

        ipEditText = addIpView.findViewById(R.id.ip_et);
        ((EditText) addIpView.findViewById(R.id.account_et)).setText(accountID);
        ((EditText) addIpView.findViewById(R.id.group_et)).setText(groupID);
        ((EditText) addIpView.findViewById(R.id.api_et)).setText(apiToken);
        currentIpStatusTextView = addIpView.findViewById(R.id.current_ip_status_tv);
        if (publicIp.get() == null || publicIp.get().isBlank())
            getPublicIP();
        else {
            ipEditText.setText(publicIp.get());
            updateCurrentIpStatus();
        }

        Button button = addIpView.findViewById(R.id.save_button);
        Button ipButton = addIpView.findViewById(R.id.ip_button);
        button.setOnClickListener(view -> {
            accountID = ((EditText) addIpView.findViewById(R.id.account_et)).getText().toString();
            groupID = ((EditText) addIpView.findViewById(R.id.group_et)).getText().toString();
            apiToken = ((EditText) addIpView.findViewById(R.id.api_et)).getText().toString();
            ipAddr = ipEditText.getText().toString();
            if(ipAddr.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if(accountID.isBlank() || groupID.isBlank() || apiToken.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter Cloudflare credentials", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("accountID", accountID);
            editor.putString("groupID", groupID);
            editor.putString("apiToken", apiToken);
            editor.apply();
            getCurrentGroup();
        });
        ipButton.setOnClickListener(view -> getPublicIP());
    }

    private void showListIpsView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        ViewGroup container = findViewById(R.id.container);
        container.removeAllViews();
        View listIpsView = inflater.inflate(R.layout.list_ips, container, false);
        container.addView(listIpsView);
        RecyclerView recyclerView = listIpsView.findViewById(R.id.ips_recycler);
        TextView emptyText = listIpsView.findViewById(R.id.empty_text);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                layoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);

        recyclerView.setAdapter(ipAdapter);

        List<String> currentIps;
        synchronized (lastFetchedIps) {
            currentIps = new ArrayList<>(lastFetchedIps);
        }
        ipAdapter.updateList(currentIps);

        if (currentIps.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            if (areCredentialsUnavailable()) {
                emptyText.setText("Please set credentials in the 'Add IP' tab to fetch IPs.");
            } else {
                emptyText.setText("No IPs present or still loading..."); // Or handle loading state more explicitly (someday)
            }
        } else {
            emptyText.setVisibility(View.GONE);
        }

        // Maybe someday: Add a swipe-to-refresh or a button to manually refresh the list
    }

    private void getCurrentGroup() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(getCloudflareURL())
                .get()
                .addHeader("authorization", "Bearer "+apiToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NetworkError", "Request failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get current group: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        String updatedJson = changeAccessGroup(responseBody);
                        updateAccessGroup(updatedJson);
                    }  else {
                        Log.e("NetworkError", "Get current group response body is null");
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get current group: Empty response", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e("NetworkError", "Unexpected code " + response + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error getting group: " + response.code() + " " + response.message(), Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    private String changeAccessGroup(String jsonString) {
        Gson gson = new Gson();
        AccessGroupResponse response = gson.fromJson(jsonString, AccessGroupResponse.class);
        if (response == null || response.result == null || response.result.include == null) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Invalid group data received", Toast.LENGTH_SHORT).show());
            return null;
        }

        AccessGroupResponse.IncludeItem ii = new AccessGroupResponse.IncludeItem();
        AccessGroupResponse.Ip newIp = new AccessGroupResponse.Ip();
        InetAddress address = null;
        try {
            address = InetAddress.getByName(ipAddr);
        } catch (UnknownHostException e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Invalid IP address", Toast.LENGTH_SHORT).show());
            return null;
        }
        if (address instanceof Inet6Address) {
            newIp.ip = ipAddr + "/64";
        } else if (address instanceof Inet4Address) {
            newIp.ip = ipAddr + "/32";
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unknown IP address type", Toast.LENGTH_SHORT).show());
            return null;
        }
        ii.ip = newIp;

        if (response.result.include == null) {
            response.result.include = new ArrayList<>();
        }
        response.result.include.add(ii);

        // Deduplicate
        List<AccessGroupResponse.IncludeItem> newList = new ArrayList<>();
        for (AccessGroupResponse.IncludeItem element : response.result.include) {
            boolean found = false;
            for(AccessGroupResponse.IncludeItem newItem : newList) {
                if (newItem.ip != null && newItem.ip.ip != null && newItem.ip.ip.equals(element.ip.ip)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                newList.add(element);
            }
        }
        response.result.include = newList;

        return gson.toJson(response);
    }

    private void updateAccessGroup(String jsonString) {
        if (jsonString == null) {
            return;
        }
        // Extract the "include" part for the PUT request body
        // The API expects a body like: {"include": [...]}
        Gson gson = new Gson();
        AccessGroupResponse parsedResponse = gson.fromJson(jsonString, AccessGroupResponse.class);
        if (parsedResponse == null || parsedResponse.result == null) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to parse data for update", Toast.LENGTH_SHORT).show());
            return;
        }
        AccessGroupUpdateRequest updateRequestPayload = new AccessGroupUpdateRequest();
        updateRequestPayload.include = parsedResponse.result.include;
        String bodyJson = gson.toJson(updateRequestPayload);

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(bodyJson, mediaType);
        Request request = new Request.Builder()
                .url(getCloudflareURL())
                .put(body)
                .addHeader("authorization", "Bearer "+apiToken)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NetworkError", "Update request failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    successfulUpdate();
                    fetchIpsOnAppStart(); // After successful update, refresh the IP list to include the new IP
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e("NetworkError", "Unexpected code " + response + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error updating group: " + response.code() + " " + response.message() , Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    static class AccessGroupUpdateRequest {
        List<AccessGroupResponse.IncludeItem> include;
    }

    private void successfulUpdate() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Added IP successfully", Toast.LENGTH_SHORT).show());
        updateCurrentIpStatus();
    }

    private void getPublicIP() {
        Thread thread = new Thread(() -> {
            try {
                URL url = new URL("https://api64.ipify.org");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0"); // Set a User-Agent to avoid HTTP 403 Forbidden error

                try (Scanner s = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    publicIp.set(s.next());
                }
                runOnUiThread(() -> {
                    if (ipEditText != null) {
                        ipEditText.setText(publicIp.get());
                    }
                    updateCurrentIpStatus();
                });
                connection.disconnect();
            } catch (Exception e) {
                Log.e("GetPublicIP", "Error fetching public IP", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not fetch public IP", Toast.LENGTH_SHORT).show());
            }
        });
        thread.start();
    }

    private List<String> fetchIpsFromCloudflare() {
        if (areCredentialsUnavailable()) {
            Log.w("CloudflareFetch", "Credentials not set, cannot fetch IPs.");
            return null; // Indicate error
        }
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getCloudflareURL())
                    .get()
                    .addHeader("authorization", "Bearer " + apiToken)
                    .build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                Gson gson = new Gson();
                AccessGroupResponse result = gson.fromJson(body, AccessGroupResponse.class);
                List<String> ips = new ArrayList<>();
                if (result != null && result.result != null && result.result.include != null) {
                    for (AccessGroupResponse.IncludeItem item : result.result.include) {
                        if (item.ip != null && item.ip.ip != null) {
                            ips.add(item.ip.ip);
                        }
                    }
                }
                // Sort the IPs before returning
                ips.sort(new IPAddressComparator());
                response.close();
                hasFetchedIps = true;
                return ips;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                Log.e("CloudflareFetch", "Failed to fetch IPs: " + response.code() + " " + response.message() + " - " + errorBody);
                response.close();
                return null; // Indicate error
            }
        } catch (Exception e) {
            Log.e("CloudflareFetch", "Exception fetching IPs", e);
            return null; // Indicate error
        }
    }

    private String getCloudflareURL() {
        return "https://api.cloudflare.com/client/v4/accounts/" + accountID + "/access/groups/" + groupID;
    }

    // Custom Comparator for IP Addresses (handles IPv4 and IPv6, and CIDR notation)
    static class IPAddressComparator implements Comparator<String> {
        @Override
        public int compare(String ip1, String ip2) {
            try {
                String addr1Str = ip1.split("/")[0];
                String addr2Str = ip2.split("/")[0];
                InetAddress addr1 = InetAddress.getByName(addr1Str);
                InetAddress addr2 = InetAddress.getByName(addr2Str);

                // Prefer IPv4 over IPv6 if types are different for grouping
                if (addr1 instanceof Inet4Address && addr2 instanceof Inet6Address) {
                    return -1;
                }
                if (addr1 instanceof Inet6Address && addr2 instanceof Inet4Address) {
                    return 1;
                }

                // If same type, compare bytes
                byte[] ba1 = addr1.getAddress();
                byte[] ba2 = addr2.getAddress();

                // General byte-wise comparison
                for (int i = 0; i < Math.min(ba1.length, ba2.length); i++) {
                    int b1 = Byte.toUnsignedInt(ba1[i]);
                    int b2 = Byte.toUnsignedInt(ba2[i]);
                    if (b1 != b2) return b1 - b2;
                }
                int lenCompare = Integer.compare(ba1.length, ba2.length);
                if (lenCompare != 0) return lenCompare;

                // Compare original strings if addresses are effectively equal but CIDR differs
                return ip1.compareTo(ip2);
            } catch (UnknownHostException e) {
                return ip1.compareTo(ip2);
            }
        }
    }

    private boolean areCredentialsUnavailable() {
        return accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty();
    }
    
    private void updateCurrentIpStatus() {
        if (currentIpStatusTextView == null || selectedNavItemId != R.id.nav_add_ip) {
            return;
        }

        currentIpStatusTextView.setBackgroundColor(getResources().getColor(android.R.color.transparent, getTheme()));

        String currentPublicIp = ipEditText.getText().toString(); 

        if (currentPublicIp == null || currentPublicIp.isEmpty()) {
            currentIpStatusTextView.setText("Fetching your current IP...");
            return;
        }

        if (areCredentialsUnavailable()) {
            currentIpStatusTextView.setText("Set credentials to check IP status.");
            currentIpStatusTextView.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            return;
        }

        boolean isIpInList = false;
        synchronized (lastFetchedIps) {
            if (lastFetchedIps != null && hasFetchedIps) {
                for (String listedIp : lastFetchedIps) {
                    if (listedIp.startsWith(currentPublicIp)) {
                        isIpInList = true;
                        break;
                    }
                }
            } else {
                currentIpStatusTextView.setText("Checking IP status...");
                currentIpStatusTextView.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
                return;
            }
        }

        if (isIpInList) {
            currentIpStatusTextView.setText("Your current IP (" + currentPublicIp + ") is in the Cloudflare group.");
            currentIpStatusTextView.setTextColor(getResources().getColor(R.color.status_green, getTheme()));
        } else {
            currentIpStatusTextView.setText("Your current IP (" + currentPublicIp + ") is NOT in the Cloudflare group.");
            currentIpStatusTextView.setTextColor(getResources().getColor(R.color.black, getTheme()));
            currentIpStatusTextView.setBackgroundColor(getResources().getColor(R.color.status_orange, getTheme()));
        }
    }

}
