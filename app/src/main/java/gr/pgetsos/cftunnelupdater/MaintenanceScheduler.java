package gr.pgetsos.cftunnelupdater;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class MaintenanceScheduler {
    private static Constraints online() { return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(); }
    public static void schedule(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("IP_MONITOR_WORK");
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ip-maintenance", ExistingPeriodicWorkPolicy.UPDATE,
            new PeriodicWorkRequest.Builder(PublicIpMonitorWorker.class, 15, TimeUnit.MINUTES).setConstraints(online()).build());
    }
    public static void now(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("ip-maintenance-now", ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(PublicIpMonitorWorker.class).setConstraints(online()).build());
    }
    public static void networkChanged(Context context) {
        OneTimeWorkRequest.Builder work = new OneTimeWorkRequest.Builder(PublicIpMonitorWorker.class).setConstraints(online());
        if (android.os.Build.VERSION.SDK_INT >= 31) work.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        // Keep an event arriving during a running check, so the newest network is checked too.
        WorkManager.getInstance(context).enqueueUniqueWork("ip-network-change", ExistingWorkPolicy.APPEND_OR_REPLACE, work.build());
    }
    public static void expiry(Context context, String ip, Long at) {
        SettingsManager s = new SettingsManager(context);
        String name = "expiry:" + s.getAccountId() + ":" + s.getGroupId() + ":" + IpNameKey.of(ip);
        if (at == null) { WorkManager.getInstance(context).cancelUniqueWork(name); return; }
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(PublicIpMonitorWorker.class).setConstraints(online())
                .setInitialDelay(Math.max(0, at - System.currentTimeMillis()), TimeUnit.MILLISECONDS).build());
    }
}
