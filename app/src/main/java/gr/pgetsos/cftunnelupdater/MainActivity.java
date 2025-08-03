package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;


import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_IP_MONITOR_WORK = "IP_MONITOR_WORK";

    private static final String TAG_ADD_IP_FRAGMENT = "TAG_ADD_IP_FRAGMENT";
    private static final String TAG_LIST_IPS_FRAGMENT = "TAG_LIST_IPS_FRAGMENT";
    private static final String TAG_SETTINGS_FRAGMENT = "TAG_SETTINGS_FRAGMENT";
    private static final String KEY_SELECTED_NAV_ITEM_ID = "KEY_SELECTED_NAV_ITEM_ID";

    private int selectedNavItemId = R.id.nav_add_ip;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SettingsFragment settingsFragment;
        ListIpsFragment listIpsFragment;
        AddIpFragment addIpFragment;

        if (savedInstanceState == null) {
            addIpFragment = new AddIpFragment();
            listIpsFragment = new ListIpsFragment();
            settingsFragment = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, settingsFragment, TAG_SETTINGS_FRAGMENT).hide(settingsFragment)
                    .add(R.id.container, listIpsFragment, TAG_LIST_IPS_FRAGMENT).hide(listIpsFragment)
                    .add(R.id.container, addIpFragment, TAG_ADD_IP_FRAGMENT)
                    .commit();
            activeFragment = addIpFragment;
        } else {
            selectedNavItemId = savedInstanceState.getInt(KEY_SELECTED_NAV_ITEM_ID, R.id.nav_add_ip);
            addIpFragment = (AddIpFragment) getSupportFragmentManager().findFragmentByTag(TAG_ADD_IP_FRAGMENT);
            listIpsFragment = (ListIpsFragment) getSupportFragmentManager().findFragmentByTag(TAG_LIST_IPS_FRAGMENT);
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag(TAG_SETTINGS_FRAGMENT);

            if (selectedNavItemId == R.id.nav_list_ips) {
                activeFragment = listIpsFragment;
            } else if (selectedNavItemId == R.id.nav_settings) {
                activeFragment = settingsFragment;
            }
            else {
                activeFragment = addIpFragment;
            }
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == selectedNavItemId) {
                return true;
            }
            Fragment targetFragment = null;
            if (item.getItemId() == R.id.nav_add_ip) {
                targetFragment = addIpFragment;
            } else if (item.getItemId() == R.id.nav_list_ips) {
                targetFragment = listIpsFragment;
            } else if (item.getItemId() == R.id.nav_settings) {
                targetFragment = settingsFragment;
            }

            if (targetFragment != null) {
                selectedNavItemId = item.getItemId();
                showFragment(targetFragment);
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(selectedNavItemId);

        boolean autoUpdateEnabled = getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SettingsManager.PREF_AUTO_UPDATE_ENABLED, false);

        if (autoUpdateEnabled) {
            schedulePublicIpMonitor();
        }
    }

    public void schedulePublicIpMonitor() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest ipMonitorWorkRequest =
                new PeriodicWorkRequest.Builder(PublicIpMonitorWorker.class,
                        15, TimeUnit.MINUTES) // Minimum interval for PeriodicWork
                        .setConstraints(constraints)
                        .addTag(TAG_IP_MONITOR_WORK)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                TAG_IP_MONITOR_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                ipMonitorWorkRequest);

        Log.i("MainActivity", "Public IP Monitor work scheduled.");
    }

    public void cancelPublicIpMonitor() {
        WorkManager.getInstance(this).cancelUniqueWork(TAG_IP_MONITOR_WORK);
        Log.i("MainActivity", "Public IP Monitor work canceled.");
    }

    private void showFragment(Fragment fragment) {
        if (fragment == null || activeFragment == null) {
            return;
        }
        if (fragment == activeFragment) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                        .hide(activeFragment)
                        .show(fragment)
                        .commit();
        activeFragment = fragment;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(KEY_SELECTED_NAV_ITEM_ID, selectedNavItemId);
        super.onSaveInstanceState(outState);
    }

}