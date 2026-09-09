package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.*;
import okhttp3.*;

/** Durable local metadata and retryable, account/group-scoped KV synchronization. */
public class IpMetadataStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    private final String base, account, group, secret;
    private final StatusStore status;
    private final Gson gson = new Gson();
    private final OkHttpClient client = new OkHttpClient();
    public IpMetadataStore(Context context) {
        SettingsManager s = new SettingsManager(context);
        status = new StatusStore(context);
        base = s.getWorkerUrl().trim().replaceAll("/+$", "");
        account = s.getAccountId(); group = s.getGroupId(); secret = s.getWorkerApiKey().trim();
        prefs = context.getSharedPreferences("records_" + android.net.Uri.encode(base + "|" + account + "|" + group), Context.MODE_PRIVATE);
        // Preserve local names/cache from the previous app without inventing an added date.
        SharedPreferences old = context.getSharedPreferences("ip_names_" + android.net.Uri.encode(base + "|" + account + "|" + group), Context.MODE_PRIVATE);
        synchronized (LOCK) {
            if (!prefs.getBoolean("migrated", false)) {
                for (Map.Entry<String, ?> entry : old.getAll().entrySet()) if (entry.getValue() instanceof String) {
                    IpRecord r = new IpRecord(); r.name = (String) entry.getValue(); put(entry.getKey(), r);
                }
                prefs.edit().putBoolean("migrated", true).apply();
            }
        }
    }
    public Map<String, IpRecord> all() {
        synchronized (LOCK) {
            Map<String, IpRecord> result = new HashMap<>();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (entry.getValue() instanceof String) {
                try { result.put(entry.getKey(), gson.fromJson((String)entry.getValue(), IpRecord.class)); } catch (JsonParseException ignored) { }
            }
            return result;
        }
    }
    public IpRecord get(String ip) { IpRecord r = all().get(IpNameKey.of(ip)); return r == null ? new IpRecord() : r; }
    private void put(String ip, IpRecord r) { prefs.edit().putString(IpNameKey.of(ip), gson.toJson(r)).commit(); }
    public void saveLocal(String ip, String name, Long expiry, boolean addedNow) {
        synchronized (LOCK) {
            IpRecord r = get(ip);
            if (r.deleted) r = new IpRecord();
            if (name != null) r.name = name.trim();
            if (r.name.length() > 100) throw new IllegalArgumentException("Names can contain up to 100 characters.");
            if (addedNow && r.addedAt == null) r.addedAt = System.currentTimeMillis();
            r.expiresAt = expiry; r.deleted = false; r.updatedAt = System.currentTimeMillis(); r.pending = true;
            put(ip, r);
        }
    }
    public void deleted(String ip) {
        synchronized (LOCK) {
            IpRecord r = get(ip); r.deleted = true; r.expiresAt = null; r.name = "";
            r.updatedAt = System.currentTimeMillis(); r.pending = true; put(ip, r);
        }
    }
    private JsonElement request(String route, String ip, IpRecord record) throws IOException {
        HttpUrl parsed = HttpUrl.parse(base);
        if (parsed == null || !parsed.isHttps() || secret.isEmpty()) throw new IOException("Set an HTTPS Worker URL and API key.");
        HttpUrl.Builder b = parsed.newBuilder().addPathSegment(route).addPathSegment(account).addPathSegment(group);
        if (ip != null) b.addPathSegment(ip.replace(".", "_").replace(":", "-"));
        Request.Builder request = new Request.Builder().url(b.build()).header("X-API-Key", secret);
        if (record != null) request.put(RequestBody.create(gson.toJson(record), MediaType.get("application/json")));
        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 401 || response.code() == 403)
                throw new IOException("Metadata authentication failed (HTTP " + response.code() + "). In Settings, the Worker API key must match the Worker's API_SECRET, not the Access API token. Details remain saved on this phone.");
            if (response.code() == 404)
                throw new IOException("Metadata endpoint not found (HTTP 404). Check the Worker base URL and deploy the matching CFWorker.js. Details remain saved on this phone.");
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("Metadata sync HTTP " + response.code() + ". Details remain saved on this phone; sync will retry.");
            return JsonParser.parseString(response.body().string());
        }
    }
    public void sync() throws IOException {
        if (base.isEmpty() && secret.isEmpty()) return;
        try { syncRecords(); status.syncSuccess(); }
        catch (IOException | RuntimeException e) { status.syncFailure(e); throw e; }
    }
    private void syncRecords() throws IOException {
        // Pending edits survive offline failures; only acknowledge the exact version sent.
        for (Map.Entry<String, IpRecord> entry : all().entrySet()) if (entry.getValue().pending) {
            IpRecord sent = entry.getValue(); IpRecord acknowledged = gson.fromJson(request("records", entry.getKey(), sent), IpRecord.class);
            synchronized (LOCK) {
                IpRecord current = get(entry.getKey());
                if (current.updatedAt == sent.updatedAt && acknowledged != null) { acknowledged.pending = false; put(entry.getKey(), acknowledged); }
            }
        }
        Map<String, IpRecord> remote;
        try { remote = gson.fromJson(request("records", null, null), new TypeToken<Map<String, IpRecord>>(){}.getType()); }
        catch (IOException e) {
            // A previous Worker can still supply names. Added/expiry metadata remains local until upgraded.
            try {
                JsonObject old = request("names", null, null).getAsJsonObject();
                synchronized (LOCK) {
                    for (Map.Entry<String, JsonElement> entry : old.entrySet()) {
                        IpRecord r = get(entry.getKey());
                        if (!r.pending && r.updatedAt == 0) { r.name = entry.getValue().getAsString().replaceFirst("DELAdded:[0-9]+$", ""); put(entry.getKey(), r); }
                    }
                }
            } catch (Exception ignored) { }
            throw e;
        }
        if (remote == null) throw new IOException("Invalid metadata response");
        synchronized (LOCK) {
            for (Map.Entry<String, IpRecord> entry : remote.entrySet()) {
                IpRecord local = get(entry.getKey()), incoming = entry.getValue();
                if (incoming != null && !local.pending && incoming.updatedAt >= local.updatedAt) {
                    incoming.pending = false; put(entry.getKey(), incoming);
                }
            }
        }
    }
}
