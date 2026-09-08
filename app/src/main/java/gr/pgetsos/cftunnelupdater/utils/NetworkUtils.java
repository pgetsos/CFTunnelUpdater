package gr.pgetsos.cftunnelupdater.utils;

import android.util.Log;
import java.net.InetAddress;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class NetworkUtils {

    private static final String TAG = "NetworkUtils"; // Tag for logging

    /**
     * Checks if a given host IP address is within a specified CIDR network range.
     *
     * @param cidrNetworkStr The network range in CIDR notation (e.g., "192.168.1.0/24").
     * @param hostIpStr      The host IP address to check (e.g., "192.168.1.100").
     * @return True if the host IP is within the network range, false otherwise.
     */
    public static boolean isIpInNetwork(String cidrNetworkStr, String hostIpStr) {
        try {
            if (cidrNetworkStr == null || hostIpStr == null) {
                return false;
            }
            String[] parts = cidrNetworkStr.split("/");
            if (parts.length != 2) {
                // If not CIDR, check for direct equality of the address part
                return parts[0].equals(hostIpStr.split("/")[0]);
            }
            String networkAddressStr = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            InetAddress networkAddress = InetAddress.getByName(networkAddressStr);
            InetAddress hostAddress = InetAddress.getByName(hostIpStr.split("/")[0]);

            if (networkAddress.getClass() != hostAddress.getClass()) {
                return false; // IPv4 vs IPv6 mismatch
            }

            byte[] networkBytes = networkAddress.getAddress();
            byte[] hostBytes = hostAddress.getAddress();
            byte[] maskBytes = new byte[networkBytes.length];

            for (int i = 0; i < maskBytes.length; i++) {
                if (prefixLength > 8) {
                    maskBytes[i] = (byte) 0xFF;
                    prefixLength -= 8;
                } else if (prefixLength > 0) {
                    maskBytes[i] = (byte) ((0xFF << (8 - prefixLength)) & 0xFF);
                    prefixLength = 0;
                } else {
                    maskBytes[i] = (byte) 0x00;
                }
            }

            byte[] maskedNetwork = new byte[networkBytes.length];
            byte[] maskedHost = new byte[hostBytes.length];

            for (int i = 0; i < networkBytes.length; i++) {
                maskedNetwork[i] = (byte) (networkBytes[i] & maskBytes[i]);
                maskedHost[i] = (byte) (hostBytes[i] & maskBytes[i]);
            }

            return java.util.Arrays.equals(maskedNetwork, maskedHost);

        } catch (Exception e) { // Catching generic Exception as in original code
            Log.e("IPNetworkCheck", String.format("Error checking if IP %s is in network %s", hostIpStr, cidrNetworkStr), e);
            return false;
        }
    }

    /**
     * Validates if the given string is a correct IP address format (IPv4 or IPv6).
     *
     * @param ipAddress The IP address string to validate.
     * @return True if the IP address format is correct, false otherwise.
     */
    public static boolean isCorrectIPFormat(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        try {
            // Attempt to create an InetAddress object from the address part.
            InetAddress.getByName(ipAddress.split("/")[0]);
            return true;
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "Invalid IP address format: " + ipAddress, e);
            return false;
        }
    }

    /**
     * Extracts an IP address from a JSON string.
     * Assumes the IP address is under a key "IP".
     *
     * @param jsonString The JSON string to parse.
     * @return The extracted IP address as a String, or null if not found or on error.
     */
    public static String extractIpAddressFromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        Gson gson = new Gson();
        String extractedIpAddress = null;
        try {
            JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
            if (jsonObject.has("IP")) { // Assuming the key is "IP" as in AddIpFragment
                JsonElement ipElement = jsonObject.get("IP");
                if (ipElement != null && !ipElement.isJsonNull() && ipElement.isJsonPrimitive() && ipElement.getAsJsonPrimitive().isString()) {
                    extractedIpAddress = ipElement.getAsString();
                    Log.d(TAG, "IP Address from Gson (JsonObject): " + extractedIpAddress);
                } else {
                    Log.w(TAG, "Key 'IP' found but value is null, not a primitive, or not a string.");
                }
            } else {
                Log.w(TAG, "Key 'IP' not found in JSON response.");
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Error parsing JSON with Gson: " + e.getMessage());
        }
        return extractedIpAddress;
    }
}
