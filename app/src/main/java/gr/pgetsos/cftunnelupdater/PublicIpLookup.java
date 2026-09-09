package gr.pgetsos.cftunnelupdater;
import okhttp3.*;
import java.io.IOException;
import gr.pgetsos.cftunnelupdater.utils.NetworkUtils;
public final class PublicIpLookup {
    private static final OkHttpClient CLIENT = new OkHttpClient();
    public static String fetch(SettingsManager s) throws IOException {
        boolean custom = "CUSTOM".equals(s.getIpCheckerType());
        HttpUrl url = HttpUrl.parse(custom ? s.getCustomIpCheckerUrl().trim() : "https://api64.ipify.org");
        if (url == null || !url.isHttps()) throw new IOException("Set an HTTPS IP checker URL in Add IP.");
        try (Response r = CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful() || r.body()==null) throw new IOException("IP checker HTTP "+r.code());
            String host = r.body().string().trim();
            if (custom) host = NetworkUtils.extractIpAddressFromJson(host);
            try { AutoIpPolicy.automaticRange(host); } catch (Exception e) { throw new IOException("IP checker did not return a valid IP address."); }
            return host;
        }
    }
}
