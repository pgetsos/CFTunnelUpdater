package gr.pgetsos.cftunnelupdater;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class AddIpFragment extends Fragment {
    private EditText accountEt;
    private EditText groupEt;
    private EditText apiEt;
    private EditText ipEditText;
    private EditText customIpCheckerUrlEditText;
    private SwitchMaterial useCustomIpCheckerSwitch;
    private TextInputLayout customIpCheckerUrlTil;
    private TextView currentIpStatusTextView;
    private String accountID;
    private String groupID;
    private String apiToken;
    private String currentIpCheckerType;
    private String currentCustomIpCheckerUrl;
    private String ipAddress;
    private AtomicReference<String> publicIp = new AtomicReference<>();

    private SettingsManager settingsManager;
    private CloudflareApiHelper cloudflareApiHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_ip, container, false);
        settingsManager = new SettingsManager(requireContext());
        cloudflareApiHelper = new CloudflareApiHelper();
        setupViews(view);
        return view;
    }

    private void setupViews(View addIpView) {
        accountID = settingsManager.getAccountId();
        groupID = settingsManager.getGroupId();
        apiToken = settingsManager.getApiToken();
        currentIpCheckerType = settingsManager.getIpCheckerType();
        currentCustomIpCheckerUrl = settingsManager.getCustomIpCheckerUrl();

        accountEt = addIpView.findViewById(R.id.account_et);
        groupEt = addIpView.findViewById(R.id.group_et);
        apiEt = addIpView.findViewById(R.id.api_et);
        ipEditText = addIpView.findViewById(R.id.ip_et);
        currentIpStatusTextView = addIpView.findViewById(R.id.current_ip_status_tv);
        useCustomIpCheckerSwitch = addIpView.findViewById(R.id.custom_ip_checker_switch);
        customIpCheckerUrlTil = addIpView.findViewById(R.id.custom_ip_checker_url_til);
        customIpCheckerUrlEditText = addIpView.findViewById(R.id.custom_ip_checker_url_et);

        accountEt.setText(accountID);
        groupEt.setText(groupID);
        apiEt.setText(apiToken);
        if (publicIp.get() == null || publicIp.get().isBlank()) getPublicIP();
        else {
            ipEditText.setText(publicIp.get());
            updateCurrentIpStatus();
        }

        if (MainActivity.IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
            useCustomIpCheckerSwitch.setChecked(true);
            customIpCheckerUrlTil.setVisibility(View.VISIBLE);
            customIpCheckerUrlEditText.setText(currentCustomIpCheckerUrl);
        } else {
            useCustomIpCheckerSwitch.setChecked(false);
            customIpCheckerUrlTil.setVisibility(View.GONE);
        }
        useCustomIpCheckerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentIpCheckerType = MainActivity.IP_CHECKER_TYPE_CUSTOM;
                customIpCheckerUrlTil.setVisibility(View.VISIBLE);
            } else {
                currentIpCheckerType = MainActivity.IP_CHECKER_TYPE_IPIFY;
                customIpCheckerUrlTil.setVisibility(View.GONE);
            }
        });

        Button button = addIpView.findViewById(R.id.add_ip_to_cf_button);
        Button ipButton = addIpView.findViewById(R.id.ip_button);
        Button saveSettingsButton = addIpView.findViewById(R.id.save_settings_button);

        button.setOnClickListener(view -> {
            saveCurrentSettings();
            ipAddress = ipEditText.getText().toString();
            if (ipAddress.isBlank()) {
                Toast.makeText(getContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (accountID.isBlank() || groupID.isBlank() || apiToken.isBlank()) {
                Toast.makeText(getContext(), "Please enter Cloudflare credentials", Toast.LENGTH_SHORT).show();
                return;
            }
            addCurrentIpToGroup();
        });

        saveSettingsButton.setOnClickListener(view -> {
            saveCurrentSettings();
            Toast.makeText(getContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });

        ipButton.setOnClickListener(view -> getPublicIP());
    }

    private void saveCurrentSettings() {
        if (accountEt == null || groupEt == null || apiEt == null) {
            Log.e("SaveSettings", getString(R.string.credential_et_not_found_cannot_save));
        } else {
            accountID = accountEt.getText().toString().trim();
            groupID = groupEt.getText().toString().trim();
            apiToken = apiEt.getText().toString().trim();
        }
        if (useCustomIpCheckerSwitch != null && useCustomIpCheckerSwitch.isChecked()) {
            currentIpCheckerType = MainActivity.IP_CHECKER_TYPE_CUSTOM;
            if (customIpCheckerUrlEditText != null) {
                currentCustomIpCheckerUrl = customIpCheckerUrlEditText.getText().toString().trim();
                if (currentCustomIpCheckerUrl.isEmpty()) {
                    Toast.makeText(getContext(), "Custom URL is empty. Please provide a URL or disable custom checker.", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                Log.w("SaveSettings", "customIpCheckerUrlEditText is null when trying to save custom URL.");
            }
        } else {
            currentIpCheckerType = MainActivity.IP_CHECKER_TYPE_IPIFY;
        }
        settingsManager.saveAll(accountID, groupID, apiToken, currentIpCheckerType, currentCustomIpCheckerUrl);
        Log.i("SaveSettings", "Settings saved: accountID=" + accountID + ", groupID=" + groupID + ", apiToken=" + apiToken +
                ", currentIpCheckerType=" + currentIpCheckerType + ", currentCustomIpCheckerUrl=" + currentCustomIpCheckerUrl);
        updateCurrentIpStatus();
    }

    private void addCurrentIpToGroup() {
        cloudflareApiHelper.getCurrentGroup(accountID, groupID, apiToken, new CloudflareApiHelper.ApiCallback<AccessGroupResponse>() {
            @Override
            public void onSuccess(AccessGroupResponse response) {
                AccessGroupResponse.IncludeItem ii = new AccessGroupResponse.IncludeItem();
                AccessGroupResponse.Ip newIp = new AccessGroupResponse.Ip();
                InetAddress address = null;
                try {
                    address = InetAddress.getByName(ipAddress);
                } catch (UnknownHostException e) {
                    toastUi("Invalid IP address");
                    return;
                }
                if (address instanceof Inet6Address) {
                    newIp.ip = ipAddress + "/64";
                } else if (address instanceof Inet4Address) {
                    newIp.ip = ipAddress + "/32";
                } else {
                    toastUi("Unknown IP address type");
                    return;
                }
                ii.ip = newIp;

                if (response.result.include == null) response.result.include = new ArrayList<>();
                response.result.include.add(ii);

                // Deduplicate
                List<AccessGroupResponse.IncludeItem> newList = new ArrayList<>();
                for (AccessGroupResponse.IncludeItem element : response.result.include) {
                    boolean found = false;
                    for (AccessGroupResponse.IncludeItem newItem : newList) {
                        if (newItem.ip != null && newItem.ip.ip != null && isIpInNetwork(element.ip.ip, newItem.ip.ip)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) newList.add(element);
                }

                cloudflareApiHelper.updateAccessGroup(accountID, groupID, apiToken, newList, new CloudflareApiHelper.ApiCallback<>() {
                    @Override
                    public void onSuccess(Boolean ok) {
                        requireActivity().runOnUiThread(() -> {
                            toastUi(getString(R.string.added_ip_successfully));
                            updateCurrentIpStatus();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        toastUi("Error updating group: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                toastUi("Failed to get current group: " + e.getMessage());
            }
        });
    }

    private void getPublicIP() {
        new Thread(() -> {
            String urlString = "https://api64.ipify.org";
            if (MainActivity.IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
                urlString = currentCustomIpCheckerUrl;
                if (urlString == null || urlString.trim().isEmpty()) {
                    toastUi(getString(R.string.custom_ip_checker_url_is_not_set));
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
                        if (MainActivity.IP_CHECKER_TYPE_IPIFY.equals(currentIpCheckerType)) {
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

                requireActivity().runOnUiThread(() -> {
                    if (ipEditText != null) {
                        ipEditText.setText(publicIp.get());
                    }
                    updateCurrentIpStatus();
                });
                connection.disconnect();
            } catch (Exception e) {
                Log.e("GetPublicIP", "Error fetching public IP", e);
                toastUi("Could not fetch public IP");
            }
        }).start();
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
            } else {
                Log.w("JSON_Parse", "Key 'IP' not found in JSON response");
            }
        } catch (JsonSyntaxException e) {
            Log.e("JSON_Parse", "Error parsing JSON with Gson: " + e.getMessage());
        }
        return ipAddress;
    }

    private boolean isCorrectIPFormat(String ipAddress) {
        if (ipAddress == null) return false;
        try {
            java.net.InetAddress.getByName(ipAddress.split("/")[0]);
            return true;
        } catch (java.net.UnknownHostException e) {
            final String errorMsg = String.format(getString(R.string.invalid_ip_address_format_received_s), ipAddress);
            Log.e("GetPublicIP", errorMsg, e);
            toastUi(errorMsg);
            return false;
        }
    }

    private void updateCurrentIpStatus() {
        if (currentIpStatusTextView == null) return;

        if (getContext() != null)
            currentIpStatusTextView.setBackgroundColor(requireContext().getResources().getColor(android.R.color.transparent, requireContext().getTheme()));

        String currentPublicIp = (ipEditText != null) ? ipEditText.getText().toString() : "";
        if (currentPublicIp.isEmpty()) {
            currentIpStatusTextView.setText(R.string.fetching_your_current_ip);
            return;
        }

        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            currentIpStatusTextView.setText(R.string.set_credentials_to_check_ip_status);
            if (getContext() != null)
                currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(android.R.color.darker_gray, requireContext().getTheme()));
            return;
        }

        currentIpStatusTextView.setText(R.string.checking_ip_status);
        if (getContext() != null)
            currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(android.R.color.darker_gray, requireContext().getTheme()));

        cloudflareApiHelper.fetchIpsFromCloudflare(accountID, groupID, apiToken, new CloudflareApiHelper.ApiCallback<>() {
            @Override
            public void onSuccess(List<String> ips) {
                boolean isIpInList = false;
                for (String listedIp : ips) {
                    if (isIpInNetwork(listedIp, currentPublicIp)) {
                        isIpInList = true;
                        break;
                    }
                }
                final boolean finalIsIpInList = isIpInList;
                requireActivity().runOnUiThread(() -> {
                    if (finalIsIpInList) {
                        currentIpStatusTextView.setText(String.format("Your current IP (%s) is in the Cloudflare group.", currentPublicIp));
                        currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.status_green, requireContext().getTheme()));
                        currentIpStatusTextView.setBackgroundColor(requireContext().getResources().getColor(android.R.color.transparent, requireContext().getTheme()));
                    } else {
                        currentIpStatusTextView.setText(String.format("Your current IP (%s) is NOT in the Cloudflare group.", currentPublicIp));
                        currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.black, requireContext().getTheme()));
                        currentIpStatusTextView.setBackgroundColor(requireContext().getResources().getColor(R.color.status_orange, requireContext().getTheme()));
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                requireActivity().runOnUiThread(() -> {
                    currentIpStatusTextView.setText(String.format("Failed to check IP status (%s)", e.getMessage()));
                    currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(android.R.color.darker_gray, requireContext().getTheme()));
                });
            }
        });
    }

    private boolean isIpInNetwork(String cidrNetworkStr, String hostIpStr) {
        try {
            if (cidrNetworkStr == null || hostIpStr == null) return false;
            String[] parts = cidrNetworkStr.split("/");
            if (parts.length != 2) {
                return parts[0].equals(hostIpStr.split("/")[0]);
            }
            String networkAddressStr = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            InetAddress networkAddress = InetAddress.getByName(networkAddressStr);
            InetAddress hostAddress = InetAddress.getByName(hostIpStr.split("/")[0]); // Remove CIDR if present
            if (networkAddress.getClass() != hostAddress.getClass()) {
                return false;
            }
            byte[] networkBytes = networkAddress.getAddress();
            byte[] hostBytes = hostAddress.getAddress();
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
            byte[] maskedNetwork = new byte[networkBytes.length];
            byte[] maskedHost = new byte[hostBytes.length];
            for (int i = 0; i < networkBytes.length; i++) {
                maskedNetwork[i] = (byte) (networkBytes[i] & maskBytes[i]);
                maskedHost[i] = (byte) (hostBytes[i] & maskBytes[i]);
            }
            return java.util.Arrays.equals(maskedNetwork, maskedHost);
        } catch (Exception e) {
            Log.e("IPNetworkCheck", String.format("Error checking if IP %s is in network %s", hostIpStr, cidrNetworkStr), e);
            return false;
        }
    }

    private void toastUi(String s) {
            requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show());
    }
}
