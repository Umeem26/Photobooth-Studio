package share;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;

/** Mendeteksi IPv4 privat non-loopback untuk URL berbagi (QR). */
public final class NetworkAddress {

    private NetworkAddress() {
    }

    public static Optional<String> detectPrivateIPv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address v4 && isPrivate(v4)) return Optional.of(v4.getHostAddress());
                }
            }
        } catch (SocketException e) {
            // tidak ada jaringan
        }
        return Optional.empty();
    }

    /** 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16. */
    public static boolean isPrivate(Inet4Address addr) {
        byte[] b = addr.getAddress();
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 10 || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168);
    }
}
