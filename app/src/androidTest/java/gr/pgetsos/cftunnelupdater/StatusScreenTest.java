package gr.pgetsos.cftunnelupdater;
import android.content.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import static org.junit.Assert.*;
public class StatusScreenTest {
    @Test public void statusIsDefaultAndNavigationSurvivesRecreation() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom_nav);assertEquals(R.id.nav_status,nav.getSelectedItemId());assertEquals(R.id.nav_status,nav.getMenu().getItem(0).getItemId());assertEquals(4,nav.getMenu().size());a.selectTab(R.id.nav_list_ips);});
            scenario.recreate();
            scenario.onActivity(a->{assertEquals(R.id.nav_list_ips,((BottomNavigationView)a.findViewById(R.id.bottom_nav)).getSelectedItemId());a.selectTab(R.id.nav_status);});
            scenario.recreate();
            scenario.onActivity(a->assertEquals(R.id.nav_status,((BottomNavigationView)a.findViewById(R.id.bottom_nav)).getSelectedItemId()));
        }
    }
    @Test public void failedChecksPreserveLastSuccessAndSettingsAreIsolated() {
        Context original=ApplicationProvider.getApplicationContext();String suffix="status-test-"+System.nanoTime();
        Context isolated=new ContextWrapper(original){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(suffix+name,mode);}};
        StatusStore first=new StatusStore(isolated);first.access("192.0.2.1",true);first.syncSuccess();
        long checked=first.prefs.getLong("checked",0),synced=first.prefs.getLong("synced",0);
        first.checkFailure(new java.io.IOException("Offline"));first.syncFailure(new java.io.IOException("HTTP 401"));
        StatusStore reopened=new StatusStore(isolated);
        assertEquals(checked,reopened.prefs.getLong("checked",0));assertEquals(synced,reopened.prefs.getLong("synced",0));
        assertEquals("192.0.2.1",reopened.prefs.getString("host",""));assertEquals("HTTP 401",reopened.prefs.getString("syncError",""));
        reopened.syncSuccess();assertFalse(reopened.prefs.contains("syncError"));
        new SettingsManager(isolated).setGroupId("another-group");assertFalse(new StatusStore(isolated).prefs.contains("host"));
        first.prefs.edit().clear().commit();isolated.getSharedPreferences(SettingsManager.PREFS_NAME,0).edit().clear().commit();
    }
}
