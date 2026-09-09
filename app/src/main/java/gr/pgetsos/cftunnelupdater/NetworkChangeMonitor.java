package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.net.*;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;

/** Debounces default-network events; only checks after Internet validation. */
public final class NetworkChangeMonitor implements AutoCloseable {
    private final ConnectivityManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check;
    private Network current;
    private boolean validated, closed;
    private String addresses = "";
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network network) {
            handler.post(() -> { if (closed) return; current = network; validated = false; addresses = ""; handler.removeCallbacks(check); });
        }
        @Override public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            final boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            handler.post(() -> {
                if (closed || !network.equals(current)) return;
                boolean becameOnline = online && !validated; validated = online;
                if (becameOnline) changed();
                else if (!online) handler.removeCallbacks(check);
            });
        }
        @Override public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties properties) {
            ArrayList<String> values = new ArrayList<>();
            for (LinkAddress address : properties.getLinkAddresses()) values.add(address.toString());
            Collections.sort(values); final String next = values.toString();
            handler.post(() -> { if (closed || !network.equals(current)) return;
                if (!next.equals(addresses)) { addresses = next; changed(); }
            });
        }
        @Override public void onLost(@NonNull Network network) {
            handler.post(() -> { if (network.equals(current)) { current = null; validated = false; handler.removeCallbacks(check); } });
        }
    };
    public NetworkChangeMonitor(Context context) {
        Context app = context.getApplicationContext();
        manager = (ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        check = () -> { if (!closed && validated) MaintenanceScheduler.networkChanged(app); };
        manager.registerDefaultNetworkCallback(callback);
    }
    private void changed() {
        if (!validated || closed) return;
        handler.removeCallbacks(check); handler.postDelayed(check, 2000);
    }
    @Override public void close() {
        closed = true; handler.removeCallbacks(check); manager.unregisterNetworkCallback(callback);
    }
}
