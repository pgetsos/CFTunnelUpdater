package gr.pgetsos.cftunnelupdater;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import java.util.*;

public class ListIpsFragment extends Fragment {
    private IPAdapter adapter; private TextView status; private int generation;
    private IpMetadataStore records;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.list_ips, container, false);
        status = v.findViewById(R.id.empty_text);
        adapter = new IPAdapter(new ArrayList<>(), (ip,pos) -> confirmDelete(ip));
        adapter.setClickListener((ip,pos) -> edit(ip));
        RecyclerView list = v.findViewById(R.id.ips_recycler);
        list.setLayoutManager(new LinearLayoutManager(requireContext())); list.setAdapter(adapter);
        v.findViewById(R.id.refresh_ips).setOnClickListener(button -> reload());
        return v;
    }
    @Override public void onResume() { super.onResume(); if (!isHidden()) reload(); }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden && getView()!=null) reload(); }
    @Override public void onDestroyView() { generation++; super.onDestroyView(); }
    private void message(String text) { if (getContext()!=null) Toast.makeText(getContext(),text,Toast.LENGTH_LONG).show(); }
    private void ui(int request, Runnable action) {
        if (getActivity()!=null) getActivity().runOnUiThread(() -> { if (getView()!=null && generation==request) action.run(); });
    }
    private void reload() {
        if (getContext()==null) return;
        int request = ++generation;
        SettingsManager s = new SettingsManager(requireContext());
        records = new IpMetadataStore(requireContext()); final IpMetadataStore store = records;
        adapter.updateRecords(store.all()); status.setText("Loading IPs…"); status.setVisibility(View.VISIBLE);
        final String account=s.getAccountId(), group=s.getGroupId(), token=s.getApiToken();
        new Thread(() -> {
            try {
                List<String> ips = new AccessService(account,group,token).list();
                ui(request, () -> { adapter.updateList(ips); status.setText(ips.isEmpty()?"No IPs in this group":""); status.setVisibility(ips.isEmpty()?View.VISIBLE:View.GONE); });
                try { store.sync(); } catch (Exception e) { ui(request, () -> message("Using local details. " + e.getMessage())); }
                ui(request, () -> adapter.updateRecords(store.all()));
            } catch (Exception e) { ui(request, () -> { status.setText(e.getMessage()); status.setVisibility(View.VISIBLE); }); }
        }).start();
    }
    private void edit(String ip) {
        final IpMetadataStore store=records; final IpRecord record=store.get(ip); final Long[] expiry={record.expiresAt};
        LinearLayout form=new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(20*getResources().getDisplayMetrics().density); form.setPadding(padding,0,padding,0);
        EditText name=new EditText(requireContext()); name.setSingleLine(); name.setHint("Name (optional)"); name.setText(record.name);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)}); form.addView(name);
        TextView added=new TextView(requireContext()); added.setText("Added: " + IPAdapter.date(record.addedAt)); form.addView(added);
        Button choose=new Button(requireContext()); choose.setText(ExpiryPicker.label(expiry[0])); form.addView(choose);
        choose.setOnClickListener(v -> ExpiryPicker.show(requireContext(),expiry[0],at->{expiry[0]=at;choose.setText(ExpiryPicker.label(at));}));
        TextView note=new TextView(requireContext()); note.setText("Expiry is removed automatically when a phone can run online. Worker scheduled cleanup can run while phones are offline.");form.addView(note);
        new AlertDialog.Builder(requireContext()).setTitle(ip).setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(dialog,which)->{
                store.saveLocal(ip,name.getText().toString(),expiry[0],false);
                MaintenanceScheduler.expiry(requireContext(),ip,expiry[0]);
                if (expiry[0]==null || expiry[0]>System.currentTimeMillis()) new SettingsManager(requireContext()).clearExpiredAutoIp(ip);
                adapter.updateRecords(store.all()); message("Details saved.");
                MaintenanceScheduler.now(requireContext());
            }).show();
    }
    private void confirmDelete(String ip) {
        new AlertDialog.Builder(requireContext()).setTitle("Delete IP?").setMessage(ip + " will lose access to the protected applications.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                SettingsManager s=new SettingsManager(requireContext()); IpMetadataStore store=records; int request=generation;
                android.content.Context context=requireContext().getApplicationContext();
                new Thread(()->{
                    try {
                        new AccessService(s.getAccountId(),s.getGroupId(),s.getApiToken()).delete(ip);
                        store.deleted(ip); s.blockExpiredAutoIp(ip);
                        if(AccessService.same(s.getOwnedAutoIp(),ip)) s.setOwnedAutoIp("");
                        MaintenanceScheduler.expiry(context,ip,null);
                        try {store.sync();} catch(Exception e){ui(request,()->message("IP deleted; metadata cleanup will retry."));}
                        ui(request,this::reload);
                    }catch(Exception e){ui(request,()->message(e.getMessage()));}
                }).start();
            }).show();
    }
}
