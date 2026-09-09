package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.junit.Test;
import static org.junit.Assert.*;

public class IpCheckerSettingsTest {
    @Test public void customCheckerPersistsWithoutSaveAndRetainsUrlWhenDisabled() {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsManager settings = new SettingsManager(context);
        String oldType = settings.getIpCheckerType(), oldUrl = settings.getCustomIpCheckerUrl();
        String url = "https://example.invalid/api/geoip";
        try {
            settings.setIpCheckerType(AddIpFragment.IP_CHECKER_TYPE_IPIFY);
            settings.setCustomIpCheckerUrl("");
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> {
                    a.selectTab(R.id.nav_add_ip);
                    ((SwitchMaterial)a.findViewById(R.id.custom_ip_checker_switch)).setChecked(true);
                    ((EditText)a.findViewById(R.id.custom_ip_checker_url_et)).setText(url);
                });
                assertEquals(AddIpFragment.IP_CHECKER_TYPE_CUSTOM, new SettingsManager(context).getIpCheckerType());
                assertEquals(url, new SettingsManager(context).getCustomIpCheckerUrl());
                scenario.recreate();
                scenario.onActivity(a -> assertTrue(((SwitchMaterial)a.findViewById(R.id.custom_ip_checker_switch)).isChecked()));
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> {
                    a.selectTab(R.id.nav_add_ip);
                    assertTrue(((SwitchMaterial)a.findViewById(R.id.custom_ip_checker_switch)).isChecked());
                    assertEquals(url, ((EditText)a.findViewById(R.id.custom_ip_checker_url_et)).getText().toString());
                    ((SwitchMaterial)a.findViewById(R.id.custom_ip_checker_switch)).setChecked(false);
                });
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> {
                    a.selectTab(R.id.nav_add_ip);
                    SwitchMaterial toggle = a.findViewById(R.id.custom_ip_checker_switch);
                    assertFalse(toggle.isChecked());
                    toggle.setChecked(true);
                    assertEquals(url, ((EditText)a.findViewById(R.id.custom_ip_checker_url_et)).getText().toString());
                });
            }
        } finally {
            settings.setIpCheckerType(oldType);
            settings.setCustomIpCheckerUrl(oldUrl);
        }
    }
}
