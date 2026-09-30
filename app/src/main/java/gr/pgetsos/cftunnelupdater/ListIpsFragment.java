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
    private AlertDialog editor, updateConfirmation;
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
    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) { generation++; closeDialogs(); }
        else if (getView()!=null) reload();
    }
    @Override public void onDestroyView() { generation++; closeDialogs(); super.onDestroyView(); }
    private void closeDialogs() {
        if (updateConfirmation!=null) updateConfirmation.dismiss();
        if (editor!=null) editor.dismiss();
        updateConfirmation=null; editor=null;
    }
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
        Button update=new Button(requireContext()); update.setText(R.string.use_current_phone_ip); form.addView(update);
        TextView note=new TextView(requireContext()); note.setText("Expiry is removed automatically when a phone can run online. Worker scheduled cleanup can run while phones are offline.");form.addView(note);
        ScrollView scroll=new ScrollView(requireContext()); scroll.addView(form);
        editor=new AlertDialog.Builder(requireContext()).setTitle(ip).setView(scroll).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(dialog,which)->{
                store.saveLocal(ip,name.getText().toString(),expiry[0],false);
                MaintenanceScheduler.expiry(requireContext(),ip,expiry[0]);
                if (expiry[0]==null || expiry[0]>System.currentTimeMillis()) new SettingsManager(requireContext()).clearExpiredAutoIp(ip);
                adapter.updateRecords(store.all()); message("Details saved.");
                MaintenanceScheduler.now(requireContext());
            }).create();
        final AlertDialog dialog=editor;
        update.setOnClickListener(v -> lookupReplacement(ip,store,name,choose,expiry[0],update,dialog));
        dialog.show();
    }
    private void updateBusy(AlertDialog dialog, EditText name, Button choose, Button update, boolean busy, int label) {
        name.setEnabled(!busy); choose.setEnabled(!busy); update.setEnabled(!busy); update.setText(label);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
    }
    private void lookupReplacement(String ip, IpMetadataStore store, EditText name, Button choose,
                                   Long expiry, Button update, AlertDialog dialog) {
        if (expiry!=null && expiry<=System.currentTimeMillis()) { message("Choose a future expiry time before updating the IP."); return; }
        final android.content.Context context=requireContext().getApplicationContext();
        final SettingsManager settings=new SettingsManager(context);
        final AccessService service;
        try { service=new AccessService(settings.getAccountId(),settings.getGroupId(),settings.getApiToken()); }
        catch (Exception e) { message(e.getMessage()); return; }
        final int request=generation;
        final String submittedName=name.getText().toString().trim();
        updateBusy(dialog,name,choose,update,true,R.string.fetching_your_current_ip);
        new Thread(() -> {
            try {
                String current=AutoIpPolicy.automaticRange(PublicIpLookup.fetch(settings));
                ui(request,() -> {
                    if (!dialog.isShowing()) return;
                    updateBusy(dialog,name,choose,update,false,R.string.use_current_phone_ip);
                    if (AccessService.same(ip,current)) { message(getString(R.string.saved_ip_already_current)); return; }
                    updateConfirmation=new AlertDialog.Builder(requireContext()).setTitle(R.string.update_saved_ip)
                        .setMessage(getString(R.string.confirm_update_saved_ip,ip,current)).setNegativeButton("Cancel",null)
                        .setPositiveButton("Update",(d,w) -> replaceSavedIp(ip,current,submittedName,expiry,store,settings,service,
                            context,request,name,choose,update,dialog)).show();
                });
            } catch (Exception e) {
                ui(request,() -> {
                    if (!dialog.isShowing()) return;
                    updateBusy(dialog,name,choose,update,false,R.string.use_current_phone_ip);
                    message(getString(R.string.saved_ip_lookup_failed,e.getMessage()));
                });
            }
        }).start();
    }
    private void replaceSavedIp(String ip, String current, String nameValue, Long expiry, IpMetadataStore store,
                                SettingsManager settings, AccessService service, android.content.Context context,
                                int request, EditText name, Button choose, Button update, AlertDialog dialog) {
        if (expiry!=null && expiry<=System.currentTimeMillis()) { message("Choose a future expiry time before updating the IP."); return; }
        updateBusy(dialog,name,choose,update,true,R.string.updating_saved_ip);
        dialog.setCancelable(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        final CloudflareViewModel model=new androidx.lifecycle.ViewModelProvider(requireActivity()).get(CloudflareViewModel.class);
        new Thread(() -> {
            try {
                synchronized (AccessService.MUTATION_LOCK) {
                    if (!service.replace(ip,current)) throw new java.io.IOException("This saved IP already matches your current phone IP.");
                    store.replaceLocal(ip,current,nameValue,expiry);
                    settings.blockExpiredAutoIp(ip); settings.clearExpiredAutoIp(current);
                    // An explicit update keeps the entry even when it is unnamed and the phone moves again.
                    if (AccessService.same(settings.getOwnedAutoIp(),ip) || AccessService.same(settings.getOwnedAutoIp(),current))
                        settings.setOwnedAutoIp("");
                    MaintenanceScheduler.expiry(context,ip,null); MaintenanceScheduler.expiry(context,current,expiry);
                }
                boolean synced;
                try { store.sync(); synced=true; } catch (Exception e) { synced=false; }
                MaintenanceScheduler.now(context);
                final int result=synced?R.string.saved_ip_updated:R.string.saved_ip_sync_pending;
                model.refreshIps();
                ui(request,() -> { dialog.dismiss(); message(getString(result)); reload(); });
            } catch (Exception e) {
                ui(request,() -> {
                    if (!dialog.isShowing()) return;
                    dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    updateBusy(dialog,name,choose,update,false,R.string.use_current_phone_ip);
                    message(getString(R.string.saved_ip_update_failed,e.getMessage()));
                });
            }
        }).start();
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
