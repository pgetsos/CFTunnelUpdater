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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import gr.pgetsos.cftunnelupdater.utils.NetworkUtils;

public class AddIpFragment extends Fragment {
    public static final String IP_CHECKER_TYPE_CUSTOM = "CUSTOM";
    public static final String IP_CHECKER_TYPE_IPIFY = "IPIFY";

    private EditText ipEditText;
    private TextInputEditText ipNameEditText;
    private TextView currentIpStatusTextView;
    private String accountID;
    private String groupID;
    private String apiToken;
    private String currentIpCheckerType;
    private String currentCustomIpCheckerUrl;
    private String ipAddress;
    private final AtomicReference<String> publicIp = new AtomicReference<>();

    private SettingsManager settingsManager;
    private CloudflareApiHelper cloudflareApiHelper;
    private CloudflareViewModel cloudflareViewModel;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_ip, container, false);
        settingsManager = new SettingsManager(requireContext());
        cloudflareApiHelper = new CloudflareApiHelper();

        cloudflareViewModel = new ViewModelProvider(requireActivity()).get(CloudflareViewModel.class);
        setupViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cloudflareViewModel.getCloudflareIpsLiveData().observe(getViewLifecycleOwner(), ips -> {
            if (ips != null) {
                updateCurrentIpStatus();
            }
        });

        cloudflareViewModel.getIsLoadingLiveData().observe(getViewLifecycleOwner(), isLoading -> updateCurrentIpStatus());

        cloudflareViewModel.getErrorLiveData().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                 Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        if (!accountID.isEmpty() && !groupID.isEmpty() && !apiToken.isEmpty()){
            cloudflareViewModel.fetchIps(accountID, groupID, apiToken);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && settingsManager != null) {
            accountID = settingsManager.getAccountId();
            groupID = settingsManager.getGroupId();
            apiToken = settingsManager.getApiToken();
            if (!accountID.isEmpty() && !groupID.isEmpty() && !apiToken.isEmpty())
                cloudflareViewModel.fetchIps(accountID, groupID, apiToken);
        }
    }

    private void setupViews(View addIpView) {
        accountID = settingsManager.getAccountId();
        groupID = settingsManager.getGroupId();
        apiToken = settingsManager.getApiToken();
        currentIpCheckerType = settingsManager.getIpCheckerType();
        currentCustomIpCheckerUrl = settingsManager.getCustomIpCheckerUrl();

        ipEditText = addIpView.findViewById(R.id.ip_et);
        ipNameEditText = addIpView.findViewById(R.id.ipNameEditText);
        currentIpStatusTextView = addIpView.findViewById(R.id.current_ip_status_tv);
        Button expiryButton = addIpView.findViewById(R.id.ip_expiry_button);
        expiryButton.setOnClickListener(v -> ExpiryPicker.show(requireContext(), selectedExpiry, at -> {
            selectedExpiry = at; expiryButton.setText(ExpiryPicker.label(at));
        }));
        SwitchMaterial useCustomIpCheckerSwitch = addIpView.findViewById(R.id.custom_ip_checker_switch);
        TextInputLayout customIpCheckerUrlTil = addIpView.findViewById(R.id.custom_ip_checker_url_til);
        EditText customIpCheckerUrlEditText = addIpView.findViewById(R.id.custom_ip_checker_url_et);

        if (publicIp.get() == null || publicIp.get().isBlank()) {
            getPublicIP();
        } else {
            ipEditText.setText(publicIp.get());
            updateCurrentIpStatus();
        }

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

        Button addIpToCfButton = addIpView.findViewById(R.id.add_ip_to_cf_button);
        Button getIpButton = addIpView.findViewById(R.id.ip_button);
        Button saveSettingsButton = addIpView.findViewById(R.id.save_settings_button);

        addIpToCfButton.setOnClickListener(view -> {
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
            String customIpCheckerUrl = customIpCheckerUrlEditText.getText().toString();
            currentCustomIpCheckerUrl = customIpCheckerUrl;
            settingsManager.setIpCheckerType(currentIpCheckerType);
            settingsManager.setCustomIpCheckerUrl(customIpCheckerUrl);
            Toast.makeText(getContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });

        getIpButton.setOnClickListener(view -> {
            currentCustomIpCheckerUrl = customIpCheckerUrlEditText.getText().toString();
            getPublicIP();
        });
    }

    private Long selectedExpiry;
    private void addCurrentIpToGroup() {
        final String submittedName = ipNameEditText.getText() == null ? "" : ipNameEditText.getText().toString().trim();
        final String submittedIp;
        try {
            String value = ipAddress.trim();
            submittedIp = IpNameKey.of(value.contains("/") ? value : value + (value.contains(":") ? "/64" : "/32"));
        } catch (Exception e) { toastUi("Invalid IP or CIDR range"); return; }
        if (submittedName.length() > 100) { toastUi("Names can contain up to 100 characters."); return; }
        final Long expiry = selectedExpiry;
        if (expiry != null && expiry <= System.currentTimeMillis()) { toastUi("Choose a future expiry time."); return; }
        final android.content.Context context = requireContext().getApplicationContext();
        final SettingsManager settings = new SettingsManager(context);
        final IpMetadataStore records = new IpMetadataStore(context);
        final String account = settings.getAccountId(), group = settings.getGroupId(), token = settings.getApiToken();
        new Thread(() -> {
            try {
                boolean added = new AccessService(account, group, token).add(submittedIp);
                IpRecord old = records.get(submittedIp);
                records.saveLocal(submittedIp, submittedName.isEmpty() ? null : submittedName,
                    expiry == null && !added ? old.expiresAt : expiry, added);
                settings.clearExpiredAutoIp(submittedIp);
                // Explicitly adding an auto-managed entry makes it a manually retained entry.
                if (AccessService.same(settings.getOwnedAutoIp(), submittedIp)) settings.setOwnedAutoIp("");
                MaintenanceScheduler.expiry(context, submittedIp, records.get(submittedIp).expiresAt);
                cloudflareViewModel.refreshIps();
                toastUi(added ? "IP added." : "IP already present; details saved.");
                try { records.sync(); } catch (Exception e) { toastUi("Details saved locally. " + e.getMessage()); }
            } catch (Exception e) { toastUi("Could not save IP: " + e.getMessage()); }
        }).start();
    }

    private void getPublicIP() {
        new Thread(() -> {
            String urlString = "https://api64.ipify.org";
            if (IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
                urlString = currentCustomIpCheckerUrl;
                if (urlString.trim().isEmpty()) {
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
                        String responseBody = s.next();
                        if (IP_CHECKER_TYPE_IPIFY.equals(currentIpCheckerType)) {
                            publicIp.set(responseBody);
                        } else {
                            publicIp.set(NetworkUtils.extractIpAddressFromJson(responseBody));
                        }
                    } else {
                        throw new IOException("No content received from IP checker.");
                    }
                }

                if (publicIp.get() == null || publicIp.get().trim().isEmpty()) {
                    if (IP_CHECKER_TYPE_CUSTOM.equals(currentIpCheckerType)) {
                        toastUi(getString(R.string.failed_to_parse_ip_from_custom_url));
                    } else {
                        toastUi(getString(R.string.failed_to_fetch_public_ip));
                    }
                    return;
                }

                if (!NetworkUtils.isCorrectIPFormat(publicIp.get())) {
                    final String errorMsg = String.format(getString(R.string.invalid_ip_address_format_received_s), publicIp.get());
                    toastUi(errorMsg);
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

    private void updateCurrentIpStatus() {
        if (currentIpStatusTextView == null) {
            return;
        }

        if (getContext() != null) {
            currentIpStatusTextView.setBackgroundColor(requireContext().getResources().getColor(android.R.color.transparent, requireContext().getTheme()));
        }

        String currentPublicIp = (ipEditText != null) ? ipEditText.getText().toString() : "";
        if (currentPublicIp.isEmpty()) {
            currentIpStatusTextView.setText(R.string.fetching_your_current_ip);
            return;
        }

        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            currentIpStatusTextView.setText(R.string.set_credentials_to_check_ip_status);
            if (getContext() != null) {
                currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.app_text_secondary, requireContext().getTheme()));
            }
            return;
        }

        if (Boolean.TRUE.equals(cloudflareViewModel.getIsLoadingLiveData().getValue())) {
            currentIpStatusTextView.setText(R.string.checking_ip_status);
            return;
        }

        if (getContext() != null) {
            currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.app_text_secondary, requireContext().getTheme()));
        }

        List<String> ips = cloudflareViewModel.getCloudflareIpsLiveData().getValue();

        if (ips == null) {
            currentIpStatusTextView.setText(String.format("Failed to check IP status (%s)", "Cloudflare returned with error"));
            currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.app_text_secondary, requireContext().getTheme()));
            return;
        }

        boolean isIpInList = false;
        for (String listedIp : ips) {
            if (NetworkUtils.isIpInNetwork(listedIp, currentPublicIp)) {
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
                currentIpStatusTextView.setTextColor(requireContext().getResources().getColor(R.color.app_warning, requireContext().getTheme()));
                currentIpStatusTextView.setBackgroundColor(requireContext().getResources().getColor(R.color.app_warning_container, requireContext().getTheme()));
            }
        });
    }

    private void toastUi(String s) {
        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
        });
    }
}
