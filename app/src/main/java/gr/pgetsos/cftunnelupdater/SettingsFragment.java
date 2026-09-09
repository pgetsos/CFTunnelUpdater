package gr.pgetsos.cftunnelupdater;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

public class SettingsFragment extends Fragment {

    private TextInputEditText accessGroupIdEditText;
    private TextInputEditText accessGroupKeyEditText;
    private TextInputEditText accountIdEditText;
    private TextInputEditText cfWorkerUrlEditText;
    private TextInputEditText workerApiKeyEditText;
    private SwitchMaterial darkThemeSwitch;
    private SwitchMaterial autoUpdateSwitch;
    private SwitchMaterial replaceAutoSwitch;
    private SwitchMaterial backgroundSwitch;
    private SettingsManager settingsManager;
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
            if (getContext() == null) return;
            settingsManager.setBackgroundMonitorEnabled(granted && settingsManager.isAutoUpdateEnabled());
            if (getView() != null) backgroundSwitch.setChecked(settingsManager.isBackgroundMonitorEnabled());
            NetworkMonitorService.reconcile(requireContext());
            if (!granted) Toast.makeText(requireContext(), "Allow notifications to enable background monitoring. Periodic checks remain active.", Toast.LENGTH_LONG).show();
        });


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        settingsManager = new SettingsManager(requireContext());

        accountIdEditText = view.findViewById(R.id.et_account_id);
        accessGroupIdEditText = view.findViewById(R.id.et_access_group_id);
        accessGroupKeyEditText = view.findViewById(R.id.et_access_group_key);
        cfWorkerUrlEditText = view.findViewById(R.id.et_cf_worker_url);
        workerApiKeyEditText = view.findViewById(R.id.et_worker_api_key);
        darkThemeSwitch = view.findViewById(R.id.switch_dark_theme);
        darkThemeSwitch.setChecked((getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        autoUpdateSwitch = view.findViewById(R.id.switch_auto_update);
        replaceAutoSwitch = view.findViewById(R.id.switch_replace_auto);
        backgroundSwitch = view.findViewById(R.id.switch_background_monitor);
        backgroundSwitch.setEnabled(settingsManager.isAutoUpdateEnabled());
        replaceAutoSwitch.setEnabled(settingsManager.isAutoUpdateEnabled());
        autoUpdateSwitch.setOnCheckedChangeListener((button, checked) -> { replaceAutoSwitch.setEnabled(checked); backgroundSwitch.setEnabled(checked); });
        view.findViewById(R.id.setup_guide).setOnClickListener(v -> SetupGuide.show(requireContext()));
		Button saveButton = view.findViewById(R.id.btn_save);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());

        return view;
    }

    @Override public void onResume() {
        super.onResume();
        if (backgroundSwitch != null) backgroundSwitch.setChecked(settingsManager.isBackgroundMonitorEnabled());
    }
    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && backgroundSwitch != null) backgroundSwitch.setChecked(settingsManager.isBackgroundMonitorEnabled());
    }
    private void loadSettings() {
        accessGroupIdEditText.setText(settingsManager.getGroupId());
        accessGroupKeyEditText.setText(settingsManager.getApiToken());
        accountIdEditText.setText(settingsManager.getAccountId());
        cfWorkerUrlEditText.setText(settingsManager.getWorkerUrl());
        workerApiKeyEditText.setText(settingsManager.getWorkerApiKey());
        autoUpdateSwitch.setChecked(settingsManager.isAutoUpdateEnabled());
        replaceAutoSwitch.setChecked(settingsManager.isReplaceAutoIpEnabled());
        backgroundSwitch.setChecked(settingsManager.isBackgroundMonitorEnabled());
    }

    private void saveSettings() {
        String accountId = Objects.requireNonNull(accountIdEditText.getText()).toString().trim();
        String accessGroupId = Objects.requireNonNull(accessGroupIdEditText.getText()).toString().trim();
        String accessGroupKey = Objects.requireNonNull(accessGroupKeyEditText.getText()).toString().trim();
        String cfWorkerUrl = Objects.requireNonNull(cfWorkerUrlEditText.getText()).toString().trim();
        String workerApiKey = Objects.requireNonNull(workerApiKeyEditText.getText()).toString().trim();
        boolean autoUpdateEnabled = autoUpdateSwitch.isChecked();


        settingsManager.saveAll(accountId, accessGroupId, accessGroupKey, cfWorkerUrl,
                workerApiKey, autoUpdateEnabled);

        settingsManager.setReplaceAutoIpEnabled(replaceAutoSwitch.isChecked());
        boolean background = autoUpdateEnabled && backgroundSwitch.isChecked();
        if (background && !NetworkMonitorService.notificationsAllowed(requireContext())) {
            settingsManager.setBackgroundMonitorEnabled(false);
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            settingsManager.setBackgroundMonitorEnabled(background);
            NetworkMonitorService.reconcile(requireContext());
        }
        MaintenanceScheduler.schedule(requireContext());
        MaintenanceScheduler.now(requireContext());

        if (autoUpdateSwitch.isChecked()) {
            ((MainActivity) requireActivity()).schedulePublicIpMonitor();
        } else {
            ((MainActivity) requireActivity()).cancelPublicIpMonitor();
        }

        int themeMode = darkThemeSwitch.isChecked() ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        settingsManager.setThemeMode(themeMode);
        Toast.makeText(getContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(themeMode);
    }
}
