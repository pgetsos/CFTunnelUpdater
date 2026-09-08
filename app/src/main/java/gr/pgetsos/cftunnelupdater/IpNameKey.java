package gr.pgetsos.cftunnelupdater;

import java.net.InetAddress;

/** Stable keys for equivalent host and CIDR spellings, without DNS lookups. */
public final class IpNameKey {
    private IpNameKey() {}

    public static String of(String ip) {
        try {
            String[] parts = ip.trim().split("/", -1);
            if (parts.length > 2 || !parts[0].matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("Invalid IP address");
            }
            if (!parts[0].contains(":")) {
                String[] octets = parts[0].split("\\.", -1);
                if (octets.length != 4) throw new IllegalArgumentException("Invalid IPv4 address");
                for (String octet : octets) {
                    if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255)
                        throw new IllegalArgumentException("Invalid IPv4 address");
                }
            }
            byte[] bytes = InetAddress.getByName(parts[0]).getAddress();
            int bits = bytes.length * 8;
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid prefix");
            for (int i = 0; i < bytes.length; i++) {
                int keep = Math.max(0, Math.min(8, prefix - i * 8));
                bytes[i] &= (byte) (0xff << (8 - keep));
            }
            return InetAddress.getByAddress(bytes).getHostAddress() + "/" + prefix;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IP address or range", e);
        }
    }
}
