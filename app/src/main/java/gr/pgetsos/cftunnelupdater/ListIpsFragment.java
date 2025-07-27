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
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ListIpsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyText;
    private IPAdapter ipAdapter;
    private List<String> lastFetchedIps = new ArrayList<>();
    private SettingsManager settingsManager;
    private CloudflareApiHelper cloudflareApiHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_ips, container, false);
        settingsManager = new SettingsManager(requireContext());
        cloudflareApiHelper = new CloudflareApiHelper();
        recyclerView = view.findViewById(R.id.ips_recycler);
        emptyText = view.findViewById(R.id.empty_text);
        ipAdapter = new IPAdapter(new ArrayList<>(), this::onIpLongPressed);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);
        recyclerView.setAdapter(ipAdapter);

        fetchAndShowIps();
        return view;
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
        String accountID = settingsManager.getAccountId();
        String groupID = settingsManager.getGroupId();
        String apiToken = settingsManager.getApiToken();
        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            Toast.makeText(getContext(), "Credentials not set.", Toast.LENGTH_SHORT).show();
            return;
        }
        cloudflareApiHelper.deleteIpFromCloudflare(accountID, groupID, apiToken, ipToDelete,
                new CloudflareApiHelper.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean deleted) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (deleted) {
                                Toast.makeText(getContext(), "IP " + ipToDelete + " deleted successfully.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "IP not found in the current group.", Toast.LENGTH_SHORT).show();
                            }
                            fetchAndShowIps();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Deletion failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }
        );
    }

    public void fetchAndShowIps() {
        String accountID = settingsManager.getAccountId();
        String groupID = settingsManager.getGroupId();
        String apiToken = settingsManager.getApiToken();
        if (accountID.isEmpty() || groupID.isEmpty() || apiToken.isEmpty()) {
            setEmptyState("Please set credentials in the 'Add IP' tab to fetch IPs.");
            ipAdapter.updateList(new ArrayList<>());
            return;
        }
        cloudflareApiHelper.fetchIpsFromCloudflare(accountID, groupID, apiToken, new CloudflareApiHelper.ApiCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> ips) {
                lastFetchedIps.clear();
                lastFetchedIps.addAll(ips);
                updateList(ips);
            }

            @Override
            public void onError(Exception e) {
                setEmptyState("Failed to load IPs. Check connection or credentials.");
                updateList(new ArrayList<>());
            }
        });
    }

    private void updateList(List<String> ips) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            ipAdapter.updateList(ips);
            if (ips.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                emptyText.setText(lastFetchedIps.isEmpty()
                        ? "No IPs present or still loading..."
                        : emptyText.getText());
            } else {
                emptyText.setVisibility(View.GONE);
            }
        });
    }

    private void setEmptyState(String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(message);
        });
    }
}
