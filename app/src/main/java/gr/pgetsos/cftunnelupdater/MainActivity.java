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

    private static final String IP_MONITOR_WORK_TAG = "ipMonitorWork";
    public static final String IP_CHECKER_TYPE_IPIFY = "ipify";
    public static final String IP_CHECKER_TYPE_CUSTOM = "custom";

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

        if (savedInstanceState != null) {
            selectedNavItemId = savedInstanceState.getInt("selectedNavItemId", R.id.nav_add_ip);
        }

        AddIpFragment addIpFragment = new AddIpFragment();
        ListIpsFragment listIpsFragment = new ListIpsFragment();
        activeFragment = addIpFragment;
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, listIpsFragment, "2").hide(listIpsFragment)
                .add(R.id.container, addIpFragment, "1")
        .commit();

        showFragment(selectedNavItemId == R.id.nav_list_ips ? listIpsFragment : addIpFragment);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == selectedNavItemId) return true;
            if (item.getItemId() == R.id.nav_add_ip) {
                selectedNavItemId = R.id.nav_add_ip;
                showFragment(addIpFragment);
                return true;
            } else if (item.getItemId() == R.id.nav_list_ips) {
                selectedNavItemId = R.id.nav_list_ips;
                showFragment(listIpsFragment);
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(selectedNavItemId);

        boolean autoUpdateEnabled = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .getBoolean(PublicIpMonitorWorker.PREF_AUTO_UPDATE_ENABLED, false);

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
                        .addTag(IP_MONITOR_WORK_TAG)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                IP_MONITOR_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                ipMonitorWorkRequest);

        Log.i("MainActivity", "Public IP Monitor work scheduled.");
    }

    public void cancelPublicIpMonitor() {
        WorkManager.getInstance(this).cancelUniqueWork(IP_MONITOR_WORK_TAG);
        Log.i("MainActivity", "Public IP Monitor work canceled.");
    }

    private void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                        .hide(activeFragment)
                        .show(fragment)
                        .commit();
        activeFragment = fragment;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("selectedNavItemId", selectedNavItemId);
        super.onSaveInstanceState(outState);
    }

}
