package gr.pgetsos.cftunnelupdater;
import com.google.gson.*;
public final class AutoIpPolicy {
    public static String automaticRange(String host) {
        if (host == null || host.contains("/")) throw new IllegalArgumentException("IP checker must return a host address");
        return IpNameKey.of(host.trim() + (host.contains(":") ? "/64" : "/32"));
    }
    public static String replacementOwner(String owned, String name) {
        return name != null && !name.trim().isEmpty() ? "" : owned;
    }
    public static boolean shouldUpdate(String current, String owned, boolean blocked, boolean deleted) {
        // A replaced address may return later; a remote deletion of the currently owned address must stick.
        return !blocked && (!deleted || !AccessService.same(current, owned));
    }
    public static final class Change {
        public JsonArray rules = new JsonArray(); public String owned = ""; public boolean changed; public boolean added;
    }
    public static Change apply(JsonArray rules, String current, String previousOwned, boolean replace) {
        Change result = new Change(); boolean exists = false;
        for (JsonElement rule : rules) if (AccessService.same(AccessService.ip(rule), current)) exists = true;
        for (JsonElement rule : rules) {
            if (replace && !AccessService.same(previousOwned, current) && AccessService.same(AccessService.ip(rule), previousOwned)) result.changed = true;
            else result.rules.add(rule);
        }
        if (!exists) { result.rules.add(AccessService.rule(current)); result.owned = current; result.changed = true; result.added = true; }
        else if (AccessService.same(current, previousOwned)) result.owned = previousOwned;
        return result;
    }
}
