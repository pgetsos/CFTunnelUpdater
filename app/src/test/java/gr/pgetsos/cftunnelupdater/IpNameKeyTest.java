package gr.pgetsos.cftunnelupdater;

import org.junit.Test;
import static org.junit.Assert.*;

public class IpNameKeyTest {
    @Test public void hostAndFullPrefixMatch() {
        assertEquals(IpNameKey.of("192.0.2.1"), IpNameKey.of("192.0.2.1/32"));
    }
    @Test public void ipv6SpellingsAndNetworkHostBitsMatch() {
        assertEquals(IpNameKey.of("2001:db8::1234/64"), IpNameKey.of("2001:0db8:0:0::/64"));
    }
    @Test public void differentRangesStaySeparate() {
        assertNotEquals(IpNameKey.of("192.0.2.1/24"), IpNameKey.of("192.0.2.1/32"));
    }
    @Test public void invalidInputDoesNotResolveDns() {
        for (String value : new String[]{"example.com", "192.0.2.1/33", "::1/-1", "::1/129", "::1/64/4"}) {
            try { IpNameKey.of(value); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
