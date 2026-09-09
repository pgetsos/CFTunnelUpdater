package gr.pgetsos.cftunnelupdater;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
public class MetadataPersistenceTest {
    private Context context;
    @Before public void setup() {
        final String suffix="_test_"+java.util.UUID.randomUUID();
        context=new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(name+suffix,mode);}
        };
        SettingsManager s=new SettingsManager(context);s.setAccountId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");s.setGroupId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }
    @Test public void metadataSurvivesRecreationAndEditingPreservesAddedDate() {
        IpMetadataStore a=new IpMetadataStore(context);
        a.saveLocal("192.0.2.1","Home",System.currentTimeMillis()+60000,true);
        Long added=a.get("192.0.2.1").addedAt;assertNotNull(added);
        IpMetadataStore b=new IpMetadataStore(context);
        assertEquals("Home",b.get("192.0.2.1/32").name);
        b.saveLocal("192.0.2.1","Work",null,false);
        assertEquals(added,b.get("192.0.2.1").addedAt);assertNull(b.get("192.0.2.1").expiresAt);
        assertTrue(b.get("192.0.2.1").pending);
    }
    @Test public void unknownDateStaysUnknownAndDeleteIsRetryable() {
        IpMetadataStore a=new IpMetadataStore(context);a.saveLocal("192.0.2.2","Existing",null,false);
        assertNull(a.get("192.0.2.2").addedAt);
        a.deleted("192.0.2.2");assertTrue(new IpMetadataStore(context).get("192.0.2.2").deleted);
        assertTrue(a.get("192.0.2.2").pending);
    }
    @Test public void groupMetadataAndOwnershipAreIsolated() {
        SettingsManager first=new SettingsManager(context);first.setOwnedAutoIp("192.0.2.3/32");
        IpMetadataStore a=new IpMetadataStore(context);a.saveLocal("192.0.2.3","First",null,true);
        first.setGroupId("cccccccccccccccccccccccccccccccc");
        SettingsManager second=new SettingsManager(context);
        assertEquals("",second.getOwnedAutoIp());assertTrue(new IpMetadataStore(context).all().isEmpty());
        assertEquals("192.0.2.3/32",first.getOwnedAutoIp());
    }
}
