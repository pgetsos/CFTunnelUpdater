package gr.pgetsos.cftunnelupdater;
import org.junit.Test;
import static org.junit.Assert.*;
import com.google.gson.*;
public class AutoIpPolicyTest {
    @Test public void automaticIpv6UsesStable64Network() {
        assertEquals(IpNameKey.of("2001:db8:1234:5678::/64"), AutoIpPolicy.automaticRange("2001:db8:1234:5678::abcd"));
        assertEquals(AutoIpPolicy.automaticRange("2001:db8::1"), AutoIpPolicy.automaticRange("2001:db8::ffff"));
        assertEquals("192.0.2.1/32", AutoIpPolicy.automaticRange("192.0.2.1"));
    }
    @Test public void namedPreviousEntryIsRetainedWhenIpChanges() {
        String old = "192.0.2.1/32";
        AutoIpPolicy.Change r = AutoIpPolicy.apply(rules("[{\"ip\":{\"ip\":\"192.0.2.1/32\"}}]"), "192.0.2.2/32",
            AutoIpPolicy.replacementOwner(old, "Home"), true);
        assertEquals(2, r.rules.size()); assertTrue(r.added);
        assertEquals("192.0.2.2/32", r.owned);
        assertEquals(old, AutoIpPolicy.replacementOwner(old, "  "));
    }
    @Test public void legacyOwned128IsReplacedBy64WhenUnnamed() {
        AutoIpPolicy.Change r = AutoIpPolicy.apply(rules("[{\"ip\":{\"ip\":\"2001:db8::1/128\"}}]"), AutoIpPolicy.automaticRange("2001:db8::1"), "2001:db8::1/128", true);
        assertEquals(1, r.rules.size()); assertTrue(r.added);
        assertEquals(IpNameKey.of("2001:db8::/64"), r.owned);
    }
    @Test public void returningAddressWorksButExplicitRemovalStaysBlocked() {
        assertTrue(AutoIpPolicy.shouldUpdate("192.0.2.1/32", "192.0.2.2/32", false, true));
        assertFalse(AutoIpPolicy.shouldUpdate("192.0.2.1/32", "", true, true));
        assertFalse(AutoIpPolicy.shouldUpdate("192.0.2.1/32", "192.0.2.1", false, true));
    }
    private JsonArray rules(String json) { return JsonParser.parseString(json).getAsJsonArray(); }
    @Test public void replacesOnlyOwnedEntryAndPreservesOtherSelectors() {
        JsonArray original=rules("[{\"ip\":{\"ip\":\"192.0.2.1/32\"}},{\"email\":{\"email\":\"keep@example.com\"}}]");
        AutoIpPolicy.Change result=AutoIpPolicy.apply(original,"192.0.2.2/32","192.0.2.1/32",true);
        assertTrue(result.added);assertEquals(2,result.rules.size());assertEquals(original.get(1),result.rules.get(0));
        assertEquals("192.0.2.2/32",result.owned);
    }
    @Test public void doesNotClaimAnExistingManualOrOtherPhoneIp() {
        AutoIpPolicy.Change r=AutoIpPolicy.apply(rules("[{\"ip\":{\"ip\":\"192.0.2.2\"}}]"),"192.0.2.2/32","",true);
        assertFalse(r.changed);assertEquals("",r.owned);
    }
    @Test public void appendModeKeepsPreviousEntry() {
        AutoIpPolicy.Change r=AutoIpPolicy.apply(rules("[{\"ip\":{\"ip\":\"192.0.2.1/32\"}}]"),"192.0.2.2/32","192.0.2.1/32",false);
        assertEquals(2,r.rules.size());
    }
    @Test public void sameCanonicalIpDoesNotChurn() {
        AutoIpPolicy.Change r=AutoIpPolicy.apply(rules("[{\"ip\":{\"ip\":\"2001:db8::1/128\"}}]"),"2001:db8:0:0:0:0:0:1/128","2001:db8::1/128",true);
        assertFalse(r.changed);assertFalse(r.added);
    }
    @Test public void expiryRequiresARealDueDate() {
        IpRecord r=new IpRecord();assertFalse(r.expired(100));r.expiresAt=100L;
        assertFalse(r.expired(99));assertTrue(r.expired(100));r.deleted=true;assertFalse(r.expired(101));
    }
}
