package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {

    private TextInputEditText accessGroupIdEditText;
    private TextInputEditText accessGroupKeyEditText;
    private TextInputEditText accountIdEditText;
    private TextInputEditText kvNamespaceIdEditText;
    private TextInputEditText kvApiKeyEditText;
    private SwitchMaterial autoUpdateSwitch;
    private Button saveButton;

    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        sharedPreferences = requireActivity().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE);

        accessGroupIdEditText = view.findViewById(R.id.et_access_group_id);
        accessGroupKeyEditText = view.findViewById(R.id.et_access_group_key);
        accountIdEditText = view.findViewById(R.id.et_account_id);
        kvNamespaceIdEditText = view.findViewById(R.id.et_kv_namespace_id);
        kvApiKeyEditText = view.findViewById(R.id.et_kv_api_key);
        autoUpdateSwitch = view.findViewById(R.id.switch_auto_update);
        saveButton = view.findViewById(R.id.btn_save);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());

        return view;
    }

    private void loadSettings() {
        accessGroupIdEditText.setText(sharedPreferences.getString(SettingsManager.PREF_GROUP_ID, ""));
        accessGroupKeyEditText.setText(sharedPreferences.getString(SettingsManager.PREF_ACCESS_GROUP_KEY, ""));
        accountIdEditText.setText(sharedPreferences.getString(SettingsManager.PREF_ACCOUNT_ID, ""));
        kvNamespaceIdEditText.setText(sharedPreferences.getString(SettingsManager.PREF_KV_NAMESPACE_ID, ""));
        kvApiKeyEditText.setText(sharedPreferences.getString(SettingsManager.PREF_KV_API_KEY, ""));
        autoUpdateSwitch.setChecked(sharedPreferences.getBoolean(SettingsManager.PREF_AUTO_UPDATE_ENABLED, false));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SettingsManager.PREF_GROUP_ID, accessGroupIdEditText.getText().toString());
        editor.putString(SettingsManager.PREF_ACCESS_GROUP_KEY, accessGroupKeyEditText.getText().toString());
        editor.putString(SettingsManager.PREF_ACCOUNT_ID, accountIdEditText.getText().toString());
        editor.putString(SettingsManager.PREF_KV_NAMESPACE_ID, kvNamespaceIdEditText.getText().toString());
        editor.putString(SettingsManager.PREF_KV_API_KEY, kvApiKeyEditText.getText().toString());
        editor.putBoolean(SettingsManager.PREF_AUTO_UPDATE_ENABLED, autoUpdateSwitch.isChecked());
        editor.apply();

        if (autoUpdateSwitch.isChecked()) {
            ((MainActivity) requireActivity()).schedulePublicIpMonitor();
        } else {
            ((MainActivity) requireActivity()).cancelPublicIpMonitor();
        }
    }
}