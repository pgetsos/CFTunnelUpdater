package gr.pgetsos.cftunnelupdater;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import okhttp3.*;

/** Serializes this process's Access mutations and preserves all other group rules. */
public class AccessService {
    public static final Object MUTATION_LOCK = new Object();
    private final OkHttpClient client = new OkHttpClient();
    private final String url, token;
    public AccessService(String account, String group, String token) {
        if (!account.matches("[a-fA-F0-9]{32}") || !group.matches("[a-fA-F0-9-]{32,36}") || token.isEmpty())
            throw new IllegalArgumentException("Set valid Cloudflare account, group and token in Settings.");
        this.url = "https://api.cloudflare.com/client/v4/accounts/" + account + "/access/groups/" + group;
        this.token = token;
    }
    private JsonObject call(String method, JsonObject body) throws IOException {
        Request.Builder b = new Request.Builder().url(url).header("Authorization", "Bearer " + token);
        if (body != null) b.method(method, RequestBody.create(body.toString(), MediaType.get("application/json")));
        try (Response response = client.newCall(b.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Cloudflare HTTP " + response.code());
            JsonObject result = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (!result.has("success") || !result.get("success").getAsBoolean()) throw new IOException("Cloudflare rejected the request.");
            return result.getAsJsonObject("result");
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("Invalid Cloudflare response", e); }
    }
    public JsonObject read() throws IOException { return call("GET", null); }
    public void write(JsonObject group) throws IOException {
        JsonObject payload = new JsonObject();
        for (String field : new String[]{"name", "include", "exclude", "require", "is_default"})
            if (group.has(field)) payload.add(field, group.get(field));
        call("PUT", payload);
    }
    public static String ip(JsonElement rule) {
        try { return rule.getAsJsonObject().getAsJsonObject("ip").get("ip").getAsString(); }
        catch (Exception e) { return null; }
    }
    public static boolean same(String a, String b) {
        if (a == null || b == null || b.isEmpty()) return false;
        try { return IpNameKey.of(a).equals(IpNameKey.of(b)); } catch (IllegalArgumentException e) { return false; }
    }
    public static JsonArray includes(JsonObject group) {
        if (!group.has("include") || !group.get("include").isJsonArray()) group.add("include", new JsonArray());
        return group.getAsJsonArray("include");
    }
    public List<String> list() throws IOException {
        List<String> ips = new ArrayList<>();
        for (JsonElement rule : includes(read())) if (ip(rule) != null) ips.add(ip(rule));
        return ips;
    }
    public static JsonObject rule(String ip) {
        JsonObject detail = new JsonObject(); detail.addProperty("ip", IpNameKey.of(ip));
        JsonObject rule = new JsonObject(); rule.add("ip", detail); return rule;
    }
    public boolean add(String ip) throws IOException {
        synchronized (MUTATION_LOCK) {
            JsonObject group = read(); JsonArray rules = includes(group);
            for (JsonElement rule : rules) if (same(ip(rule), ip)) return false;
            rules.add(rule(ip)); write(group); return true;
        }
    }
    public boolean delete(String ip) throws IOException {
        synchronized (MUTATION_LOCK) {
            JsonObject group = read(); JsonArray keep = new JsonArray(); boolean removed = false;
            for (JsonElement rule : includes(group)) {
                if (same(ip(rule), ip)) removed = true; else keep.add(rule);
            }
            if (removed) { group.add("include", keep); write(group); }
            return removed;
        }
    }
    public boolean replace(String previousIp, String currentIp) throws IOException {
        String replacement = IpNameKey.of(currentIp);
        synchronized (MUTATION_LOCK) {
            JsonObject group = read(); JsonArray rules = includes(group);
            boolean found = false;
            for (JsonElement rule : rules) if (same(ip(rule), previousIp)) found = true;
            if (!found) throw new IOException("This saved IP is no longer in the group. Refresh the list and try again.");
            if (same(previousIp, replacement)) return false;
            for (JsonElement rule : rules) if (same(ip(rule), replacement))
                throw new IOException("Your current phone IP is already saved in this group. Both entries were kept.");
            JsonArray updated = new JsonArray(); boolean inserted = false;
            for (JsonElement rule : rules) {
                if (same(ip(rule), previousIp)) {
                    if (!inserted) { updated.add(rule(replacement)); inserted = true; }
                } else updated.add(rule);
            }
            group.add("include", updated); write(group); return true;
        }
    }
}
