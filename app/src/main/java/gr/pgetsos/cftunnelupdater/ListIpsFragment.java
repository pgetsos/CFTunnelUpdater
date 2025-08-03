package gr.pgetsos.cftunnelupdater;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
        cloudflareApiHelper.deleteIpFromCloudflare(accountID, groupID, apiToken, ipToDelete,
                new CloudflareApiHelper.ApiCallback<>() {
                    @Override
                    public void onSuccess(Boolean deleted) {
                        requireActivity().runOnUiThread(() -> {
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
