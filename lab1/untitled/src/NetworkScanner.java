import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
class NetworkScanner {
    private static final String INDENT = "  ";
    record Host(String IP, String MAC, String Name) {}
    public void scanNetwork() {
        try {
            printOwnInfo();
            NetworkInterface.networkInterfaces()
                    .filter(this::isUsableInterface)
                    .filter(this::hasIPv4Address)
                    .forEach(this::processInterface);
        } catch (SocketException e) {
            throw new RuntimeException("Не получилось прочитать интерфейсы", e);
        }

    }

    private void printOwnInfo(){
        try {
            InetAddress addr = InetAddress.getLocalHost();
            IO.println("Этот компьютер:");
            IO.println(getIndent(1) + "Имя: " + addr.getCanonicalHostName());
            IO.println(getIndent(1) + "IP: " + addr.getHostAddress());

        }
        catch(UnknownHostException e){
            throw new RuntimeException("Не удалось вывести информацию об устройстве");
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
        IO.println(getIndent(1) + "IP подсети: " + ia.getAddress().getHostAddress());
        pingAll(ia);
        List<Host> l = getHostsForInterface(ia);
        l.forEach(this::printHost);
    }

    public void printHost(Host host){
        IO.println(getIndent(2) + "Узел: ");
        IO.println(getIndent(3) + "IP: " + host.IP);
        IO.println(getIndent(3) + "MAC: " + host.MAC);
        IO.println(getIndent(3) + "Имя: " + host.Name);
    }

    private Map<String, String> getArpMapForInterface(InterfaceAddress addr) {
        String targetIp = addr.getAddress().getHostAddress();
        Map<String, String> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                Pattern interfacePattern = Pattern.compile("^(?:Интерфейс|Interface):\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                Pattern entryPattern = Pattern.compile("\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2})");
                String currentInterface = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher interfaceMatcher = interfacePattern.matcher(line);
                    if (interfaceMatcher.find()) {
                        currentInterface = interfaceMatcher.group(1);
                        continue;
                    }
                    if (targetIp.equals(currentInterface)) {
                        Matcher entryMatcher = entryPattern.matcher(line);
                        if (entryMatcher.find()) {
                            String ip = entryMatcher.group(1);
                            String mac = entryMatcher.group(2).toUpperCase();
                            map.put(ip, mac);
                        }
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("arp -a завершилась с кодом " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Прервано ожидание процесса arp", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при выполнении arp -a: " + e.getMessage(), e);
        }
        return map;
    }

    public List<Host> getHostsForInterface(InterfaceAddress addr) {
        Map<String, String> arpMap = getArpMapForInterface(addr);
        List<Host> hosts = new ArrayList<>();
        for (Map.Entry<String, String> entry : arpMap.entrySet()) {
            String ip = entry.getKey();
            String mac = entry.getValue();
            String hostname = getHostname(ip);
            hosts.add(new Host(ip, mac, hostname));
        }
        return hosts;
    }

    /**
     * Пытается получить имя хоста по IP. Если не удаётся, возвращает "N/A".
     */
    private static String getHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getHostName();
            // Если вернулся IP (не имя), считаем что имени нет
            if (hostname.equals(ip)) {
                return "N/A";
            }
            return hostname;
        } catch (UnknownHostException e) {
            return "N/A";
        }
    }

    public void pingAll(@NotNull InterfaceAddress ia) {
        InetAddress addr = ia.getAddress();
        short prefix = ia.getNetworkPrefixLength();
        byte[] ipBytes = addr.getAddress();
        int ipInt = ((ipBytes[0] & 0xFF) << 24) |
                ((ipBytes[1] & 0xFF) << 16) |
                ((ipBytes[2] & 0xFF) << 8)  |
                (ipBytes[3] & 0xFF);
        int mask = 0xFFFFFFFF << (32 - prefix);
        int network = ipInt & mask;
        int broadcast = network | ~mask;
        int start = network + 1;
        int end = broadcast - 1;
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = start; i <= end; i++) {
                final int currentIP = i;
                executor.submit(() -> {
                    try {
                        InetAddress target = InetAddress.getByAddress(intToBytes(currentIP));
                        target.isReachable(200);
                    } catch (IOException ignored) {}
                });
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }

    @Contract(value = "_ -> new", pure = true)
    private byte @NotNull [] intToBytes(int value) {
        return ByteBuffer
                .allocate(4)
                .putInt(value)
                .array();
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
        return INDENT.repeat(Math.max(n, 0));
    }

    private @NotNull String getMAC(byte @NotNull [] addr) {
        StringBuilder mac = new StringBuilder(String.format("%02X", (int) addr[0]));
        for (int i = 1; i < addr.length; i++) {
            mac.append(String.format("-%02X", addr[i] & 0xFF));
        }
        return mac.toString();
    }

}