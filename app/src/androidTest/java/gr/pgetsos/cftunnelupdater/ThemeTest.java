package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeTest {
    @Test public void savedToggleChangesEveryScreenAndSurvivesRelaunch() {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsManager settings = new SettingsManager(context);
        int original = settings.getThemeMode();
        try {
            settings.setThemeMode(AppCompatDelegate.MODE_NIGHT_NO);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> {
                    assertEquals(Configuration.UI_MODE_NIGHT_NO, a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    a.selectTab(R.id.nav_settings);
                    ((SwitchMaterial) a.findViewById(R.id.switch_dark_theme)).setChecked(true);
                    a.findViewById(R.id.btn_save).performClick();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.recreate();
                scenario.onActivity(a -> {
                    assertEquals(Configuration.UI_MODE_NIGHT_YES, a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    assertEquals(R.id.nav_settings, ((BottomNavigationView) a.findViewById(R.id.bottom_nav)).getSelectedItemId());
                    assertTrue(((SwitchMaterial) a.findViewById(R.id.switch_dark_theme)).isChecked());
                    assertEquals(AppCompatDelegate.MODE_NIGHT_YES, new SettingsManager(a).getThemeMode());
                });
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> {
                    assertEquals(Configuration.UI_MODE_NIGHT_YES, a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    a.selectTab(R.id.nav_settings);
                    ((SwitchMaterial) a.findViewById(R.id.switch_dark_theme)).setChecked(false);
                    a.findViewById(R.id.btn_save).performClick();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.recreate();
                scenario.onActivity(a -> assertEquals(Configuration.UI_MODE_NIGHT_NO, a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK));
            }
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, settings.getThemeMode());
        } finally {
            settings.setThemeMode(original);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(original));
        }
    }
}
