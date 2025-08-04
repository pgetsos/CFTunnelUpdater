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
    private SwitchMaterial autoUpdateSwitch;

    private SettingsManager settingsManager;

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
        autoUpdateSwitch = view.findViewById(R.id.switch_auto_update);
		Button saveButton = view.findViewById(R.id.btn_save);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());

        return view;
    }

    private void loadSettings() {
        accessGroupIdEditText.setText(settingsManager.getGroupId());
        accessGroupKeyEditText.setText(settingsManager.getApiToken());
        accountIdEditText.setText(settingsManager.getAccountId());
        cfWorkerUrlEditText.setText(settingsManager.getWorkerUrl());
        workerApiKeyEditText.setText(settingsManager.getWorkerApiKey());
        autoUpdateSwitch.setChecked(settingsManager.isAutoUpdateEnabled());
    }

    private void saveSettings() {
        String accountId = Objects.requireNonNull(accountIdEditText.getText()).toString();
        String accessGroupId = Objects.requireNonNull(accessGroupIdEditText.getText()).toString();
        String accessGroupKey = Objects.requireNonNull(accessGroupKeyEditText.getText()).toString();
        String cfWorkerUrl = Objects.requireNonNull(cfWorkerUrlEditText.getText()).toString();
        String workerApiKey = Objects.requireNonNull(workerApiKeyEditText.getText()).toString();
        boolean autoUpdateEnabled = autoUpdateSwitch.isChecked();


        settingsManager.saveAll(accountId, accessGroupId, accessGroupKey, cfWorkerUrl,
                workerApiKey, autoUpdateEnabled);

        if (autoUpdateSwitch.isChecked()) {
            ((MainActivity) requireActivity()).schedulePublicIpMonitor();
        } else {
            ((MainActivity) requireActivity()).cancelPublicIpMonitor();
        }

        Toast.makeText(getContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }
}
