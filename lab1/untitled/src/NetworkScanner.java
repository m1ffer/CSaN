import java.net.*;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
class NetworkScanner {
    private static final String INDENT = "  ";
    public void scanNetwork() {
        try {
            NetworkInterface.networkInterfaces()
                    .filter(this::isUsableInterface)
                    .filter(this::hasIPv4Address)
                    .forEach(this::processInterface);
        } catch (SocketException e) {
            throw new RuntimeException("Не получилось прочитать интерфейсы", e);
        }
    }

    private void processInterface(@NotNull NetworkInterface ni) {
        IO.println(getIndent(0) + "Сканирование интерфейса: " + ni.getDisplayName());
        try {
            IO.println(getIndent(0) + "MAC-адрес: " + getMAC(ni.getHardwareAddress()));
        }
        catch(SocketException e){
            throw new RuntimeException("Случилось невозможное", e);
        }
        ni.getInterfaceAddresses().
                stream().
                filter(this::isIPv4Address).
                forEach(this::processInterfaceAddress);
    }

    private void processInterfaceAddress(@NotNull InterfaceAddress ia) {
        IO.println(getIndent(1) + "IP: " + ia.getAddress());
    }

    private boolean isUsableInterface(@NotNull NetworkInterface ni) {
        try {
            return ni.isUp() &&
                    !ni.isLoopback() &&
                    !ni.isVirtual() &&
                    ni.getHardwareAddress() != null;
        } catch (SocketException _) {
            return false;
        }
    }

    private boolean hasIPv4Address(@NotNull NetworkInterface ni) {
        return ni.getInterfaceAddresses().
                stream().
                anyMatch(this::isIPv4Address);
    }

    @Contract(pure = true)
    private boolean isIPv4Address(@NotNull InterfaceAddress addr) {
        return addr.getAddress() instanceof Inet4Address;
    }

    private @NotNull String getIndent(int n){
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < n; i++)
            indent.append(INDENT);
        return indent.toString();
    }

    private String getMAC(byte[] addr){
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < addr.length - 1; i++)
            mac.append(String.format("%02X.", addr[i] & 0xFF));
        mac.append(String.format("%02X", addr[addr.length - 1]));
        return mac.toString();
    }
}