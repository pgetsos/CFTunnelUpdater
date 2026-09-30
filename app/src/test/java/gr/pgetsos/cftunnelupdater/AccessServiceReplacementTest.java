package gr.pgetsos.cftunnelupdater;

import com.google.gson.*;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessServiceReplacementTest {
    private static class FakeAccessService extends AccessService {
        final JsonObject original;
        JsonObject written;
        int writes;
        boolean failWrite;
        FakeAccessService(String includes) {
            super("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "test");
            original = JsonParser.parseString("{\"name\":\"Home\",\"include\":" + includes
                + ",\"exclude\":[{\"country\":{\"country\":\"XX\"}}],\"require\":[{\"everyone\":{}}],\"is_default\":false}").getAsJsonObject();
        }
        @Override public JsonObject read() { return original.deepCopy(); }
        @Override public void write(JsonObject group) throws IOException {
            if (failWrite) throw new IOException("Offline");
            writes++; written = group.deepCopy();
        }
    }

    @Test public void replacesOnlySelectedIpInOneWriteAndKeepsAllOtherRules() throws Exception {
        FakeAccessService service = new FakeAccessService("[{\"ip\":{\"ip\":\"192.0.2.1\"}},"
            + "{\"email\":{\"email\":\"keep@example.com\"}},{\"ip\":{\"ip\":\"192.0.2.3/32\"}}]");
        assertTrue(service.replace("192.0.2.1/32", "192.0.2.2"));
        assertEquals(1, service.writes);
        JsonArray rules = AccessService.includes(service.written);
        assertEquals(3, rules.size());
        assertEquals("192.0.2.2/32", AccessService.ip(rules.get(0)));
        assertEquals(service.original.getAsJsonArray("include").get(1), rules.get(1));
        assertEquals(service.original.getAsJsonArray("include").get(2), rules.get(2));
        for (String field : new String[]{"name", "exclude", "require", "is_default"})
            assertEquals(service.original.get(field), service.written.get(field));
        assertEquals("192.0.2.1", AccessService.ip(service.original.getAsJsonArray("include").get(0)));
    }

    @Test public void alreadyCurrentCanonicalIpv6DoesNotWrite() throws Exception {
        FakeAccessService service = new FakeAccessService("[{\"ip\":{\"ip\":\"2001:db8:1234:5678::/64\"}}]");
        assertFalse(service.replace("2001:db8:1234:5678::/64", AutoIpPolicy.automaticRange("2001:db8:1234:5678::abcd")));
        assertEquals(0, service.writes);
    }

    @Test public void existingDestinationAndMissingSourceNeverWrite() {
        FakeAccessService service = new FakeAccessService("[{\"ip\":{\"ip\":\"192.0.2.1/32\"}},{\"ip\":{\"ip\":\"192.0.2.2\"}}]");
        IOException duplicate = assertThrows(IOException.class, () -> service.replace("192.0.2.1", "192.0.2.2/32"));
        assertTrue(duplicate.getMessage().contains("already saved"));
        IOException missing = assertThrows(IOException.class, () -> service.replace("192.0.2.3", "192.0.2.4"));
        assertTrue(missing.getMessage().contains("no longer"));
        assertEquals(0, service.writes);
    }

    @Test public void writeFailureIsPropagatedAndInvalidDestinationIsRejected() {
        FakeAccessService service = new FakeAccessService("[{\"ip\":{\"ip\":\"192.0.2.1/32\"}}]");
        service.failWrite = true;
        assertThrows(IOException.class, () -> service.replace("192.0.2.1", "192.0.2.2"));
        assertThrows(IllegalArgumentException.class, () -> service.replace("192.0.2.1", "example.com"));
        assertEquals(0, service.writes);
    }
}
