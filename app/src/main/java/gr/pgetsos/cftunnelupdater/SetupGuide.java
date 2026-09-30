package gr.pgetsos.cftunnelupdater;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
public final class SetupGuide {
    public static void show(Context context) {
        TextView text=new TextView(context); int p=(int)(20*context.getResources().getDisplayMetrics().density);text.setPadding(p,p,p,p);
        text.setText("1. Create an Access group\nIn Cloudflare Zero Trust, open Access > Access groups. Create a group with an IP ranges selector. Copy its Group ID and your Account ID.\n\n"
            + "2. Protect your application\nSelect this group in your application's Access policy. A Bypass policy allows matching IPs without login. Check that other policies do not unintentionally allow access.\n\n"
            + "3. Create an API token\nUse a custom token scoped to your account with Access: Organizations, Identity Providers, and Groups - Edit. Paste it into the app with Account ID and Group ID, then Save. Never share the token publicly.\n\n"
            + "4. Add or update an IP\nGet My IP fills your public address. You can enter IPv4, IPv6, or CIDR, a name, and optional expiry. IPv6 entries without a prefix use /64. Tap a list entry to edit details or choose Use current phone IP to replace that address. Confirm the old and new IPs; its name, added date and expiry are kept. If the new address is already saved, both entries are kept. Hold to delete. Dates use your phone's time zone. Existing IPs show an unknown added date.\n\n"
            + "5. Optional shared names and dates\nCreate a Worker and KV namespace. Deploy this version's CFWorker.js; bind the namespace as kv_cfupdater. Add an encrypted API_SECRET. Enter its HTTPS URL and that secret as the Worker API key on every phone, with the same account and group. The Worker key differs from the Access API token. Details save locally offline and retry syncing later. KV changes may take a minute or more to reach other phones.\n\n"
            + "6. Automatic IP replacement\nEnable Auto-Update, then optionally Replace previous IP. Only entries newly added by this phone are tracked for replacement. Tracking stays local and is scoped to the account/group; a new phone never adopts another phone's entry. Manually adding or naming an auto entry retains it when the phone IP changes. Automatic replacement waits if shared metadata cannot sync. Enable Monitor network changes in background for event-driven checks with an ongoing notification and Stop action. Allow notification permission, then Save. Reopen the app after reboot or force-stop. The 15-minute fallback still catches router-only public IP changes; Android can delay checks. Deleting or expiring the current IP prevents auto-add until you manually add it again.\n\n"
            + "7. Expiry\nAndroid checks about every 15 minutes and schedules a check for your chosen time. Offline, battery restrictions, or force-stopping the app can delay removal. For cleanup while phones are offline, deploy the updated Worker, add CF_ACCESS_TOKEN (an Access group Edit token), set CF_ACCOUNT_ID and CF_GROUP_ID, and add a cron trigger: */5 * * * *. Only that configured group is cleaned. Deleting metadata alone does not revoke access.\n\n"
            + "8. Releases\nThe repository includes a manual GitHub Actions Release workflow. Set its signing secrets once, choose a version and version code, then run it to create a draft release. It never runs on ordinary commits.");
        ScrollView scroll=new ScrollView(context);scroll.addView(text);
        new AlertDialog.Builder(context).setTitle("CFTunnelUpdater setup").setView(scroll).setPositiveButton("Done",null).show();
    }
}
