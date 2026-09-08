package gr.pgetsos.cftunnelupdater;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ListIpsFragment extends Fragment {
    private TextView emptyText;
    private IpNameStore names;
    private int namesRequest;
    private IPAdapter ipAdapter;
    String accountID;
    String groupID;
    String apiToken;
    private CloudflareApiHelper cloudflareApiHelper;
    private CloudflareViewModel cloudflareViewModel;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_ips, container, false);
        SettingsManager settingsManager = new SettingsManager(requireContext());
        cloudflareApiHelper = new CloudflareApiHelper();
        cloudflareViewModel = new ViewModelProvider(requireActivity()).get(CloudflareViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.ips_recycler);
        emptyText = view.findViewById(R.id.empty_text);
        ipAdapter = new IPAdapter(new ArrayList<>(), this::onIpLongPressed);
        ipAdapter.setClickListener((ip, position) -> editName(ip));
        view.findViewById(R.id.refresh_ips).setOnClickListener(v -> reload());

        accountID = settingsManager.getAccountId();
        groupID = settingsManager.getGroupId();
        apiToken = settingsManager.getApiToken();

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);
        recyclerView.setAdapter(ipAdapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cloudflareViewModel.getCloudflareIpsLiveData().observe(getViewLifecycleOwner(), ips -> {
            if (ips != null) {
                updateList(ips);
            }
        });

        cloudflareViewModel.getIsLoadingLiveData().observe(getViewLifecycleOwner(), isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) setEmptyState("Loading IPs...");
        });

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
    public void onResume() {
        super.onResume();
        if (!isHidden()) reload();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) reload();
    }

    private void reload() {
        SettingsManager settings = new SettingsManager(requireContext());
        accountID = settings.getAccountId();
        groupID = settings.getGroupId();
        apiToken = settings.getApiToken();
        names = new IpNameStore(requireContext());
        ipAdapter.updateNames(names.cached());
        int request = ++namesRequest;
        names.load(new WorkerApiCallbacks.WorkerGetAllNamesApiCallback() {
            public void onAllNamesRetrieved(java.util.Map<String, String> loaded) {
                if (getView() != null && request == namesRequest) ipAdapter.updateNames(loaded);
            }
            public void onError(String error) {
                if (getView() != null && request == namesRequest) showMessage("Names could not sync; showing saved names. " + error);
            }
        });
        if (!accountID.isEmpty() && !groupID.isEmpty() && !apiToken.isEmpty()) {
            cloudflareViewModel.refreshIps(accountID, groupID, apiToken);
        } else {
            ipAdapter.updateList(new ArrayList<>());
            setEmptyState("Set Cloudflare credentials in Settings.");
        }
    }

    private void editName(String ip) {
        if (names == null) return;
        final IpNameStore store = names;
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("Name (optional)");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        input.setText(store.cached().get(IpNameKey.of(ip)));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Name for " + ip)
                .setMessage("Leave blank to remove the name.")
                .setView(input)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ++namesRequest; // Ignore any older in-flight name read.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            store.save(ip, input.getText().toString(), new WorkerApiCallbacks.GenericWorkerApiCallback() {
                public void onSuccess(String message) {
                    if (getView() != null && names == store) ipAdapter.updateNames(store.cached());
                    showMessage(message);
                    dialog.dismiss();
                }
                public void onError(String error) {
                    showMessage("Name was not saved: " + error);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
        }));
        dialog.show();
    }

    private void showMessage(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private void onIpLongPressed(String ip, int pos) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Delete IP")
                .setMessage("Are you sure you want to delete this IP address: " + ip + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteIpFromCloudflare(ip))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteIpFromCloudflare(String ipToDelete) {
        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            Toast.makeText(getContext(), "Credentials not set", Toast.LENGTH_SHORT).show();
            return;
        }
        final IpNameStore store = names != null ? names : new IpNameStore(requireContext());
        cloudflareApiHelper.deleteIpFromCloudflare(accountID, groupID, apiToken, ipToDelete,
                new CloudflareApiHelper.ApiCallback<>() {
                    @Override
                    public void onSuccess(Boolean deleted) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (getView() == null) return;
                            ++namesRequest;
                            store.save(ipToDelete, "", new WorkerApiCallbacks.GenericWorkerApiCallback() {
                                public void onSuccess(String message) { }
                                public void onError(String error) { showMessage("IP removed, but its name could not be removed: " + error); }
                            });
                            if (Boolean.TRUE.equals(deleted)) {
                                Toast.makeText(getContext(), String.format(getString(R.string.ip_deleted_successfully), ipToDelete), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), R.string.ip_not_in_current_group, Toast.LENGTH_SHORT).show();
                            }
                            cloudflareViewModel.refreshIps();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), String.format(getString(R.string.deletion_failed), e.getMessage()), Toast.LENGTH_LONG).show());
                    }
                }
        );
    }

    private void updateList(List<String> ips) {
        requireActivity().runOnUiThread(() -> {
            ipAdapter.updateList(ips);
            if (ips.isEmpty()) {
                setEmptyState(getString(R.string.no_ips_present_or_still_loading));
            } else {
                emptyText.setVisibility(View.GONE);
            }
        });
    }

    private void setEmptyState(String message) {
        requireActivity().runOnUiThread(() -> {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(message);
        });
    }
}
