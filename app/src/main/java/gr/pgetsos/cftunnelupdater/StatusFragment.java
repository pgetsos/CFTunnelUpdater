package gr.pgetsos.cftunnelupdater;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import gr.pgetsos.cftunnelupdater.utils.NetworkUtils;

public class StatusFragment extends Fragment {
    private int INK, MUTED, TEAL, GREEN, AMBER;
    private int color(int id) { return requireContext().getColor(id); }
    private LinearLayout content;
    private StatusStore status;
    private SettingsManager settings;
    private IpMetadataStore metadata;
    private boolean busy;
    private int generation;
    private String rendered = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p,k) -> handler.post(this::render);
    private final Runnable refresh = new Runnable() { public void run() { if (getView()!=null && !isHidden()) render(); handler.postDelayed(this,5000); } };
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state) {
        INK=color(R.color.app_text); MUTED=color(R.color.app_text_secondary); TEAL=color(R.color.app_primary); GREEN=color(R.color.app_success); AMBER=color(R.color.app_warning);
        ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setBackgroundColor(color(R.color.app_background));
        content=new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(20),dp(20),dp(12));scroll.addView(content);
        return scroll;
    }
    @Override public void onResume(){super.onResume();bind();handler.post(refresh);if(!isHidden() && status.prefs.getLong("checked",0)==0) check(false);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(!hidden && getView()!=null){bind();render();}}
    private void bind(){
        if(status!=null)status.prefs.unregisterOnSharedPreferenceChangeListener(listener);
        settings=new SettingsManager(requireContext());metadata=new IpMetadataStore(requireContext());status=new StatusStore(requireContext());
        status.prefs.registerOnSharedPreferenceChangeListener(listener);rendered="";render();
    }
    @Override public void onDestroyView(){generation++;busy=false;if(status!=null)status.prefs.unregisterOnSharedPreferenceChangeListener(listener);handler.removeCallbacks(refresh);content=null;super.onDestroyView();}
    @Override public void onDestroy(){executor.shutdown();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));d.setStroke(dp(1),color(R.color.app_outline));return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(requireContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(5),0,dp(5));return t;}
    private LinearLayout card(int color){LinearLayout c=new LinearLayout(requireContext());c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(14),dp(18),dp(14));c.setBackground(background(color));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);content.addView(c,p);return c;}
    private void title(LinearLayout c,String label,String badge,int color){LinearLayout row=new LinearLayout(requireContext());row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(label,18,INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView tag=text(badge,12,color,true);tag.setPadding(dp(10),dp(5),dp(10),dp(5));tag.setBackground(background(color==AMBER?color(R.color.app_warning_container):color(R.color.app_primary_container)));row.addView(tag);c.addView(row);}
    private void row(LinearLayout c,String label,String value){LinearLayout r=new LinearLayout(requireContext());r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(requireContext());int drawable=label.equals("Network changes")?R.drawable.ic_network_monitor:label.equals("Pending changes")?android.R.drawable.ic_menu_agenda:android.R.drawable.ic_menu_recent_history;icon.setImageResource(drawable);icon.setColorFilter(MUTED);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(18),dp(18));iconSize.rightMargin=dp(10);r.addView(icon,iconSize);r.setPadding(0,dp(7),0,dp(7));r.addView(text(label,14,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));TextView v=text(value,14,INK,false);v.setGravity(Gravity.END);r.addView(v,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);}
    private com.google.android.material.button.MaterialButton button(LinearLayout c,String label,boolean primary,Runnable action){com.google.android.material.button.MaterialButton b=new com.google.android.material.button.MaterialButton(requireContext());b.setText(label);b.setTextColor(primary?color(R.color.app_on_primary):TEAL);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?TEAL:color(R.color.app_primary_container)));b.setCornerRadius(dp(12));b.setMinHeight(dp(50));b.setEnabled(!busy);b.setOnClickListener(v->action.run());c.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private String when(long at){if(at==0)return "Never";long minutes=Math.max(0,(System.currentTimeMillis()-at)/60000);if(minutes==0)return "Just now";if(minutes<60)return minutes+" min ago";return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(at));}
    private void navigate(int id){((MainActivity)requireActivity()).selectTab(id);}
    private void render(){
        if(content==null || status==null || !isAdded())return;
        String key=status.prefs.getAll().toString()+new com.google.gson.Gson().toJson(metadata.all())+NetworkMonitorService.running+busy+System.currentTimeMillis()/60000;
        if(key.equals(rendered))return;rendered=key;
        ScrollView scroll=(ScrollView)content.getParent();int scrollY=scroll.getScrollY();
        content.removeAllViews();content.addView(text("CFTunnelUpdater",14,INK,false));content.addView(text("Status",32,INK,true));
        SharedPreferences p=status.prefs;String host=p.getString("host","");String error=p.getString("checkError","");
        boolean configured=!settings.getAccountId().isEmpty()&&!settings.getGroupId().isEmpty()&&!settings.getApiToken().isEmpty();
        long checked=p.getLong("checked",0);boolean allowed=p.getBoolean("allowed",false);
        LinearLayout access=card(color(R.color.app_surface));
        String heading=!configured?"Finish Access setup":!error.isEmpty()?"IP check needs attention":checked==0?"Ready to check":allowed?"Access is up to date":"IP is not in your group";
        LinearLayout headline=new LinearLayout(requireContext());headline.setGravity(Gravity.CENTER_VERTICAL);
        if(configured&&error.isEmpty()&&checked>0&&allowed){TextView tick=text("\u2713",24,color(R.color.app_on_success),true);tick.setGravity(Gravity.CENTER);tick.setBackground(background(GREEN));LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(dp(40),dp(40));badge.rightMargin=dp(12);headline.addView(tick,badge);}
        headline.addView(text(heading,20,configured&&error.isEmpty()&&checked>0&&allowed?GREEN:INK,true),new LinearLayout.LayoutParams(0,-2,1));access.addView(headline);
        access.addView(text("Current public IP",14,MUTED,false));
        TextView ip=text(host.isEmpty()?"Not checked yet":host,18,INK,true);ip.setTypeface(Typeface.MONOSPACE);ip.setTextIsSelectable(true);ip.setPadding(dp(10),dp(10),dp(10),dp(10));ip.setBackground(background(color(R.color.app_surface_variant)));access.addView(ip);
        if(!host.isEmpty()){access.addView(text("Managed range: "+AutoIpPolicy.automaticRange(host),12,MUTED,false));ip.setOnLongClickListener(v->{((ClipboardManager)requireContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Public IP",host));Toast.makeText(requireContext(),"IP copied",Toast.LENGTH_SHORT).show();return true;});}
        access.addView(text(!configured?"Add your account, group and API token in Settings.":!error.isEmpty()?error:checked==0?"Tap Check now to read your IP and group.":allowed?"Allowed in your Access group":"Add this IP to your Access group to allow it.",14,MUTED,false));
        access.addView(text("Last successful check: "+when(checked),12,MUTED,false));
        if(!configured)button(access,"Open settings",false,()->navigate(R.id.nav_settings));
        LinearLayout monitor=card(color(R.color.app_surface));boolean active=NetworkMonitorService.running;
        title(monitor,"Monitoring",active?"Active":settings.isAutoUpdateEnabled()?"Periodic":"Off",TEAL);
        row(monitor,"Network changes",active?"Background monitoring on":"While app is open");row(monitor,"Fallback check","Every 15 min or later");
        monitor.addView(text("Router IP changes may wait for a periodic check.",12,MUTED,false));
        if(settings.isBackgroundMonitorEnabled()&&!active)monitor.addView(text("Background monitor is not running. Check Settings and notification permission.",12,AMBER,false));
        String syncError=p.getString("syncError","");boolean shared=!settings.getWorkerUrl().isEmpty()||!settings.getWorkerApiKey().isEmpty();
        int pending=0;IpRecord next=null;String nextIp="";
        for(Map.Entry<String,IpRecord> entry:metadata.all().entrySet()){IpRecord r=entry.getValue();if(shared&&r.pending)pending++;if(!r.deleted&&r.expiresAt!=null&&(next==null||r.expiresAt<next.expiresAt)){next=r;nextIp=entry.getKey();}}
        LinearLayout sync=card(shared&&!syncError.isEmpty()?color(R.color.app_warning_surface):color(R.color.app_surface));
        title(sync,"Shared details",!shared?"Local only":!syncError.isEmpty()?"Sync failed":pending>0?"Pending":p.getLong("synced",0)==0?"Not synced":"Synced",shared&&!syncError.isEmpty()?AMBER:TEAL);
        if(shared&&!syncError.isEmpty()){
            sync.addView(text(syncError.contains("401")||syncError.contains("403")?"Worker API key was rejected":syncError,16,INK,true));
            sync.addView(text("Your changes are saved on this phone.",13,MUTED,false));
        }
        row(sync,"Last successful sync",shared?when(p.getLong("synced",0)):"Not configured");row(sync,"Pending changes",Integer.toString(pending));
        if(!shared)sync.addView(text("Names and dates are saved on this phone. Configure Worker settings to share them.",13,MUTED,false));
        else if(!syncError.isEmpty()){
            LinearLayout actions=new LinearLayout(requireContext());sync.addView(actions);
            View fix=button(actions,"Fix settings",false,()->navigate(R.id.nav_settings));View retry=button(actions,"Retry sync",false,()->check(true));
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(6);fix.setLayoutParams(left);retry.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        }
        else sync.addView(text("Names, added dates and expiry",13,MUTED,false));
        if(shared&&!syncError.isEmpty()&&settings.isAutoUpdateEnabled())content.addView(text("Automatic IP updates are paused until details sync.",13,MUTED,false));
        LinearLayout expiry=card(color(R.color.app_surface));row(expiry,"Next expiry",next==null?"None scheduled":(next.name.isEmpty()?nextIp:next.name)+" - "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(next.expiresAt)));
        if(next!=null&&next.expired(System.currentTimeMillis()))expiry.addView(text("Removal pending an online cleanup check",12,AMBER,false));
        button(content,busy?"Checking...":"Check now",true,()->check(false));button(content,"View IP list",false,()->navigate(R.id.nav_list_ips));
        scroll.post(()->scroll.scrollTo(0,scrollY));
    }
    private void check(boolean syncOnly){
        if(busy||getContext()==null)return;busy=true;render();final int request=generation;
        final Context context=requireContext().getApplicationContext();final SettingsManager s=new SettingsManager(context);final StatusStore target=new StatusStore(context);
        executor.execute(()->{
            if(!syncOnly)try{
                String host=PublicIpLookup.fetch(s);
                if(s.getAccountId().isEmpty()||s.getGroupId().isEmpty()||s.getApiToken().isEmpty())target.prefs.edit().putString("host",host).apply();
                else {boolean allowed=false;for(String ip:new AccessService(s.getAccountId(),s.getGroupId(),s.getApiToken()).list())if(NetworkUtils.isIpInNetwork(ip,host))allowed=true;target.access(host,allowed);}
            }catch(Exception e){target.checkFailure(e);}
            try{new IpMetadataStore(context).sync();}catch(Exception ignored){}
            if(syncOnly)MaintenanceScheduler.now(context);
            handler.post(()->{if(generation==request&&getView()!=null){busy=false;render();}});
        });
    }
}
