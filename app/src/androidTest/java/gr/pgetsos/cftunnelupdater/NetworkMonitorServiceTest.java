package gr.pgetsos.cftunnelupdater;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.lifecycle.Lifecycle;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkMonitorServiceTest {
    private StatusBarNotification notification(Context context) {
        for (StatusBarNotification item : context.getSystemService(NotificationManager.class).getActiveNotifications())
            if (item.getId() == 24) return item;
        return null;
    }
    private void awaitNotification(Context context, boolean present) throws Exception {
        long until = System.currentTimeMillis() + 8000;
        while ((notification(context) != null) != present && System.currentTimeMillis() < until) Thread.sleep(100);
        assertEquals(present, notification(context) != null);
    }
    @Test public void staysActiveInBackgroundAndNotificationStopDisablesIt() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsManager settings = new SettingsManager(context);
        boolean auto = settings.isAutoUpdateEnabled(), enabled = settings.isBackgroundMonitorEnabled();
        // This test only runs in the credential-free debug package on the emulator.
        assertTrue(settings.getApiToken().isEmpty());
        try {
            settings.setAutoUpdateEnabled(true); settings.setBackgroundMonitorEnabled(true);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitNotification(context, true);
                scenario.moveToState(Lifecycle.State.CREATED);
                assertNotNull(notification(context));
                notification(context).getNotification().actions[0].actionIntent.send();
                awaitNotification(context, false);
                assertFalse(settings.isBackgroundMonitorEnabled());
                assertTrue(settings.isAutoUpdateEnabled());
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertNull(notification(context));
            }
        } finally {
            context.stopService(new Intent(context, NetworkMonitorService.class));
            settings.setAutoUpdateEnabled(auto); settings.setBackgroundMonitorEnabled(enabled);
        }
    }
    @Test public void validatedNetworkQueuesCheckWithoutWaitingForPeriodicWork() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        androidx.work.WorkManager work = androidx.work.WorkManager.getInstance(context);
        java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
        for (androidx.work.WorkInfo info : work.getWorkInfosForUniqueWork("ip-network-change").get()) before.add(info.getId());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long until = System.currentTimeMillis() + 15000;
            boolean queued = false;
            while (!queued && System.currentTimeMillis() < until) {
                for (androidx.work.WorkInfo info : work.getWorkInfosForUniqueWork("ip-network-change").get())
                    if (!before.contains(info.getId())) queued = true;
                if (!queued) Thread.sleep(100);
            }
            assertTrue("Validated network should queue an event check", queued);
        }
    }
    @Test public void disablingAutoUpdateStopsMonitor() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsManager settings = new SettingsManager(context);
        boolean auto = settings.isAutoUpdateEnabled(), enabled = settings.isBackgroundMonitorEnabled();
        assertTrue(settings.getApiToken().isEmpty());
        try {
            settings.setAutoUpdateEnabled(true); settings.setBackgroundMonitorEnabled(true);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitNotification(context, true);
                scenario.onActivity(activity -> { settings.setAutoUpdateEnabled(false); NetworkMonitorService.reconcile(activity); });
                awaitNotification(context, false);
            }
        } finally {
            context.stopService(new Intent(context, NetworkMonitorService.class));
            settings.setAutoUpdateEnabled(auto); settings.setBackgroundMonitorEnabled(enabled);
        }
    }
}
