package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnIpLongPressListener {

    private static final String IP_MONITOR_WORK_TAG = "ipMonitorWork";
    public static final String PREF_IP_CHECKER_TYPE = "ip_checker_type";
    public static final String PREF_CUSTOM_IP_CHECKER_URL = "custom_ip_checker_url";
    public static final String IP_CHECKER_TYPE_IPIFY = "ipify";
    public static final String IP_CHECKER_TYPE_CUSTOM = "custom";

    private AtomicReference<String> publicIp = new AtomicReference<>();
    private String accountID;
    private String groupID;
    private String apiToken;
    private String ipAddr;
    private EditText accountEt;
    private EditText groupEt;
    private EditText apiEt;
    private EditText ipEditText;
    private int selectedNavItemId = R.id.nav_add_ip;
    private IPAdapter ipAdapter;
    private List<String> lastFetchedIps = new ArrayList<>();
    private TextView currentIpStatusTextView;
    private boolean hasFetchedIps = false;
    private String IPCheckerSite = "local";
    private SwitchMaterial useCustomIpCheckerSwitch;
    private TextInputLayout customIpCheckerUrlTil;
    private EditText customIpCheckerUrlEditText;
    private String currentIpCheckerType = IP_CHECKER_TYPE_IPIFY;
    private String currentCustomIpCheckerUrl = "";

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

        boolean autoUpdateEnabled = sharedPreferences.getBoolean(PublicIpMonitorWorker.PREF_AUTO_UPDATE_ENABLED, false);

        if (autoUpdateEnabled) {
            schedulePublicIpMonitor();
        }
    }

    public void schedulePublicIpMonitor() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest ipMonitorWorkRequest =
                new PeriodicWorkRequest.Builder(PublicIpMonitorWorker.class,
                        15, TimeUnit.MINUTES) // Minimum interval for PeriodicWork
                        .setConstraints(constraints)
                        .addTag(IP_MONITOR_WORK_TAG)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                IP_MONITOR_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                ipMonitorWorkRequest);

        Log.i("MainActivity", "Public IP Monitor work scheduled.");
    }

    public void cancelPublicIpMonitor() {
        WorkManager.getInstance(this).cancelUniqueWork(IP_MONITOR_WORK_TAG);
        Log.i("MainActivity", "Public IP Monitor work canceled.");
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
        currentIpCheckerType = sharedPreferences.getString(PREF_IP_CHECKER_TYPE, IP_CHECKER_TYPE_IPIFY);
        currentCustomIpCheckerUrl = sharedPreferences.getString(PREF_CUSTOM_IP_CHECKER_URL, "");

        accountEt = findViewById(R.id.account_et);
        groupEt = findViewById(R.id.group_et);
        apiEt = findViewById(R.id.api_et);
        ipEditText = addIpView.findViewById(R.id.ip_et);
        accountEt.setText(accountID);
        groupEt.setText(groupID);
        apiEt.setText(apiToken);
        currentIpStatusTextView = addIpView.findViewById(R.id.current_ip_status_tv);
        if (publicIp.get() == null || publicIp.get().isBlank())
            getPublicIP();
        else {
            ipEditText.setText(publicIp.get());
            updateCurrentIpStatus();
        }

        useCustomIpCheckerSwitch = addIpView.findViewById(R.id.custom_ip_checker_switch);
        customIpCheckerUrlTil = addIpView.findViewById(R.id.custom_ip_checker_url_til);
        customIpCheckerUrlEditText = addIpView.findViewById(R.id.custom_ip_checker_url_et);


        if (IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
            useCustomIpCheckerSwitch.setChecked(true);
            customIpCheckerUrlTil.setVisibility(View.VISIBLE);
            customIpCheckerUrlEditText.setText(currentCustomIpCheckerUrl);
        } else {
            useCustomIpCheckerSwitch.setChecked(false);
            customIpCheckerUrlTil.setVisibility(View.GONE);
        }

        useCustomIpCheckerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentIpCheckerType = IP_CHECKER_TYPE_CUSTOM;
                customIpCheckerUrlTil.setVisibility(View.VISIBLE);
            } else {
                currentIpCheckerType = IP_CHECKER_TYPE_IPIFY;
                customIpCheckerUrlTil.setVisibility(View.GONE);
            }
        });

        Button button = addIpView.findViewById(R.id.add_ip_to_cf_button);
        Button ipButton = addIpView.findViewById(R.id.ip_button);
        Button saveSettingsButton = addIpView.findViewById(R.id.save_settings_button);

        button.setOnClickListener(view -> {
            saveCurrentSettings();

            ipAddr = ipEditText.getText().toString();
            if(ipAddr.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if(accountID.isBlank() || groupID.isBlank() || apiToken.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter Cloudflare credentials", Toast.LENGTH_SHORT).show();
                return;
            }

            getCurrentGroup();
        });

        saveSettingsButton.setOnClickListener(view -> {
            saveCurrentSettings();
            Toast.makeText(MainActivity.this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });

        ipButton.setOnClickListener(view -> getPublicIP());
    }

    private void saveCurrentSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if(accountEt == null || groupEt == null || apiEt == null) {
            Log.e("SaveSettings", "One or more credential EditTexts not found. Cannot save credentials.");
        } else {
            accountID = accountEt.getText().toString().trim();
            groupID = groupEt.getText().toString().trim();
            apiToken = apiEt.getText().toString().trim();

            editor.putString("accountID", accountID);
            editor.putString("groupID", groupID);
            editor.putString("apiToken", apiToken);
        }

        if (useCustomIpCheckerSwitch != null && useCustomIpCheckerSwitch.isChecked()) {
            currentIpCheckerType = IP_CHECKER_TYPE_CUSTOM;
            if (customIpCheckerUrlEditText != null) {
                currentCustomIpCheckerUrl = customIpCheckerUrlEditText.getText().toString().trim();
                if (currentCustomIpCheckerUrl.isEmpty()) {
                    Toast.makeText(this, "Custom URL is empty. Please provide a URL or disable custom checker.", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                Log.w("SaveSettings", "customIpCheckerUrlEditText is null when trying to save custom URL.");
            }
        } else {
            currentIpCheckerType = IP_CHECKER_TYPE_IPIFY;
        }

        editor.putString(PREF_IP_CHECKER_TYPE, currentIpCheckerType);
        editor.putString(PREF_CUSTOM_IP_CHECKER_URL, currentCustomIpCheckerUrl);

        editor.apply();
        Log.i("SaveSettings", "Settings saved: accountID=" + accountID + ", groupID=" + groupID + ", apiToken=" + apiToken +
                ", currentIpCheckerType=" + currentIpCheckerType + ", currentCustomIpCheckerUrl=" + currentCustomIpCheckerUrl);
        fetchIpsOnAppStart();
        updateCurrentIpStatus();
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
                if (newItem.ip != null && newItem.ip.ip != null && isIpInNetwork(element.ip.ip, newItem.ip.ip)) {
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
                    fetchIpsOnAppStart();
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
            String urlString = "https://api64.ipify.org";
            if (IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
                urlString = currentCustomIpCheckerUrl;
                if (urlString == null || urlString.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Custom IP Checker URL is not set.", Toast.LENGTH_LONG).show());
                    return;
                }
            }
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                try (Scanner s = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    if (s.hasNext()) {
                        if (IP_CHECKER_TYPE_IPIFY.equals(currentIpCheckerType)) {
                            publicIp.set(s.next());
                        } else {
                            publicIp.set(readIPFromJSON(s.next()));
                        }
                    } else {
                        throw new IOException("No content received from IP checker.");
                    }
                }

                if (!isCorrectIPFormat(publicIp.get())) {
                    return;
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
            if (response.isSuccessful()) {
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

                ips.sort(new IPAddressComparator());
                response.close();
                hasFetchedIps = true;
                return ips;
            } else {
                String errorBody = response.body().string();
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

        if (currentPublicIp.isEmpty()) {
            currentIpStatusTextView.setText(R.string.fetching_your_current_ip);
            return;
        }

        if (areCredentialsUnavailable()) {
            currentIpStatusTextView.setText(R.string.set_credentials_to_check_ip_status);
            currentIpStatusTextView.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            return;
        }

        boolean isIpInList = false;
        synchronized (lastFetchedIps) {
            if (lastFetchedIps != null && hasFetchedIps) {
                for (String listedIp : lastFetchedIps) {
                    if (isIpInNetwork(listedIp, currentPublicIp)) {
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

    private boolean isIpInNetwork(String cidrNetworkStr, String hostIpStr) {
        try {
            String[] parts = cidrNetworkStr.split("/");
            if (parts.length != 2) {
                return parts[0].equals(hostIpStr.split("/")[0]);
            }
            String networkAddressStr = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress networkAddress = InetAddress.getByName(networkAddressStr);
            InetAddress hostAddress = InetAddress.getByName(hostIpStr.split("/")[0]); // Remove CIDR from host if present

            if (networkAddress.getClass() != hostAddress.getClass()) {
                return false;
            }

            byte[] networkBytes = networkAddress.getAddress();
            byte[] hostBytes = hostAddress.getAddress();

            // Create a mask based on the prefix length
            byte[] maskBytes = new byte[networkBytes.length];
            for (int i = 0; i < maskBytes.length; i++) {
                if (prefixLength > 8) {
                    maskBytes[i] = (byte) 0xFF;
                    prefixLength -= 8;
                } else if (prefixLength > 0) {
                    maskBytes[i] = (byte) ((0xFF << (8 - prefixLength)) & 0xFF);
                    prefixLength = 0;
                } else {
                    maskBytes[i] = (byte) 0x00;
                }
            }

            // Apply mask to both addresses
            byte[] maskedNetwork = new byte[networkBytes.length];
            byte[] maskedHost = new byte[hostBytes.length];

            for (int i = 0; i < networkBytes.length; i++) {
                maskedNetwork[i] = (byte) (networkBytes[i] & maskBytes[i]);
                maskedHost[i] = (byte) (hostBytes[i] & maskBytes[i]);
            }

            return java.util.Arrays.equals(maskedNetwork, maskedHost);

        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.e("IPNetworkCheck", "Error checking if IP " + hostIpStr + " is in network " + cidrNetworkStr, e);
            return false;
        }
    }

    private ConnectivityManager.NetworkCallback networkCallback;

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.i("NetworkCallback", "Network available. Triggering IP check.");
                 OneTimeWorkRequest oneTimeCheck = new OneTimeWorkRequest.Builder(PublicIpMonitorWorker.class)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build();
                 WorkManager.getInstance(getApplicationContext()).enqueue(oneTimeCheck);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.i("NetworkCallback", "Network lost.");
            }
        };

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        } catch (SecurityException e) {
            Log.e("NetworkCallback", "Permission denied for network callback", e);
            // Handle lack of ACCESS_NETWORK_STATE?
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                } catch (Exception e) {
                    Log.e("NetworkCallback", "Error unregistering network callback", e);
                }
            }
            networkCallback = null;
        }
    }

    private String readIPFromJSON(String json) {
        Gson gson = new Gson();
        String ipAddress = null;
        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            if (jsonObject.has("IP")) {
                JsonElement ipElement = jsonObject.get("IP");
                if (ipElement != null && !ipElement.isJsonNull()) {
                    ipAddress = ipElement.getAsString();
                    Log.d("JSON_Parse", "IP Address from Gson (JsonObject): " + ipAddress);
                } else {
                    Log.w("JSON_Parse", "Key 'ip' found but value is null or not a string (Gson JsonObject)");
                }
            }
            else {
                Log.w("JSON_Parse", "Key 'IP' not found in JSON response");
            }
        } catch (JsonSyntaxException e) {
            Log.e("JSON_Parse", "Error parsing JSON with Gson: " + e.getMessage());
        }
        return ipAddress;
    }

    private boolean isCorrectIPFormat(String ipAddress) {
        try {
            InetAddress.getByName(ipAddress.split("/")[0]);
            return true;
        } catch (UnknownHostException e) {
            final String errorMsg = "Invalid IP address format received: " + ipAddress;
            Log.e("GetPublicIP", errorMsg, e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show());
            return false;
        }
    }

}
