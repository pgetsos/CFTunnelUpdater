package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import java.util.Collections;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

public class SavedIpScreenTest {
    @Test public void tapOpensUpdateActionAndFailedLookupCanBeRetriedWithoutSaving() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        SettingsManager settings=new SettingsManager(context);
        // The debug emulator has no live Access credentials; use an invalid checker to avoid any API calls.
        assertTrue(settings.getApiToken().isEmpty());
        String account=settings.getAccountId(),group=settings.getGroupId();
        String type=settings.getIpCheckerType(),url=settings.getCustomIpCheckerUrl();
        boolean auto=settings.isAutoUpdateEnabled();
        try {
            settings.setAutoUpdateEnabled(false);
            try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    activity.selectTab(R.id.nav_list_ips);
                    activity.getSupportFragmentManager().executePendingTransactions();
                    RecyclerView list=activity.findViewById(R.id.ips_recycler);
                    ((IPAdapter)list.getAdapter()).updateList(Collections.singletonList("192.0.2.1/32"));
                });
                // Navigation captured empty credentials. Only the replacement lookup sees these test values.
                settings.setAccountId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                settings.setGroupId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
                settings.setApiToken("test-only");
                settings.setIpCheckerType("CUSTOM");settings.setCustomIpCheckerUrl("http://example.invalid");
                onView(withText(org.hamcrest.Matchers.containsString("192.0.2.1/32"))).perform(click());
                onView(withText(R.string.use_current_phone_ip)).check(matches(isDisplayed())).perform(click());
                long until=System.currentTimeMillis()+5000;
                while (true) {
                    try { onView(withText(R.string.use_current_phone_ip)).check(matches(isEnabled())); break; }
                    catch (AssertionError | androidx.test.espresso.NoMatchingViewException e) {
                        if (System.currentTimeMillis()>=until) throw e;
                        Thread.sleep(100);
                    }
                }
                onView(withText("Save")).check(matches(isEnabled()));
                onView(withText("Cancel")).perform(click());
                assertTrue(new IpMetadataStore(context).all().isEmpty());
            }
        } finally {
            settings.setApiToken("");settings.setAccountId(account);settings.setGroupId(group);
            settings.setIpCheckerType(type);settings.setCustomIpCheckerUrl(url);settings.setAutoUpdateEnabled(auto);
        }
    }
}
