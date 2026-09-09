package gr.pgetsos.cftunnelupdater;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** User-enabled continuous network observation, with visible notification and Stop action. */
public class NetworkMonitorService extends Service {
    private static final String CHANNEL = "network-monitor";
    private static final int NOTIFICATION_ID = 24;
    private static final String STOP = "stop-network-monitor";
    public static volatile boolean running;
    private NetworkChangeMonitor monitor;
    public static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    public static void reconcile(Context context) {
        SettingsManager settings = new SettingsManager(context);
        Intent intent = new Intent(context, NetworkMonitorService.class);
        if (settings.isAutoUpdateEnabled() && settings.isBackgroundMonitorEnabled() && notificationsAllowed(context)) {
            try { ContextCompat.startForegroundService(context, intent); }
            catch (RuntimeException e) {
                android.widget.Toast.makeText(context, "Background monitor could not start. Open the app and enable it again; periodic checks remain active.", android.widget.Toast.LENGTH_LONG).show();
            }
        } else context.stopService(intent);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SettingsManager settings = new SettingsManager(this);
        if (intent != null && STOP.equals(intent.getAction())) {
            settings.setBackgroundMonitorEnabled(false); stopSelf(); return START_NOT_STICKY;
        }
        if (!settings.isAutoUpdateEnabled() || !settings.isBackgroundMonitorEnabled() || !notificationsAllowed(this)) {
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL, "IP change monitoring", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, NetworkMonitorService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_network_monitor).setContentTitle("IP change monitoring active")
            .setContentText("Checks when networks change. Tap Stop to use periodic checks only.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop).build();
        startForeground(NOTIFICATION_ID, notification);
        if (monitor == null) monitor = new NetworkChangeMonitor(this);
        running = true;
        MaintenanceScheduler.schedule(this);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        running = false;
        if (monitor != null) monitor.close();
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
