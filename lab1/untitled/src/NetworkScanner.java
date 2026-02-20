import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
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

    public List<Host> getHostsForInterface(InterfaceAddress addr) {
        String targetIp = addr.getAddress().getHostAddress();
        List<Host> hosts = new ArrayList<>();

        try {
            Process process = Runtime.getRuntime().exec("arp -a");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                // Паттерн для строки интерфейса (русский/английский)
                Pattern interfacePattern = Pattern.compile(
                        "^(?:Интерфейс|Interface):\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                // Паттерн для записи ARP: IP и MAC через дефисы
                Pattern entryPattern = Pattern.compile(
                        "\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2})");

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
                            String hostname = getHostname(ip);
                            hosts.add(new Host(ip, mac, hostname));
                        }
                    }
                }

                // Дожидаемся завершения процесса
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Предупреждение: arp -a завершилась с кодом " + exitCode);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Прервано ожидание процесса arp");
            }

        } catch (IOException e) {
            System.err.println("Ошибка при выполнении arp -a: " + e.getMessage());
        }

        return hosts;
    }

    /**
     * Пытается получить имя хоста по IP. Если не удаётся, возвращает "N/A".
     */
    private static String getHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getCanonicalHostName();
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
        int broadcast = network | (~mask & 0xFFFFFFFF);
        int start = network + 1;
        int end = broadcast - 1;
        int threadCount = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = start; i <= end; i++) {
            final int currentIP = i;
            executor.submit(() -> {
                try {
                    InetAddress target = InetAddress.getByAddress(intToBytes(currentIP));
                    target.isReachable(200);
                } catch (IOException _) {
                }
            });
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Contract(value = "_ -> new", pure = true)
    private byte @NotNull [] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    @Contract(pure = true)
    private @NotNull String ipIntToAddress(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
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

    private @NotNull String getMAC(byte @NotNull [] addr){
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < addr.length - 1; i++)
            mac.append(String.format("%02X.", addr[i] & 0xFF));
        mac.append(String.format("%02X", addr[addr.length - 1]));
        return mac.toString();
    }

    public static Map<String, String> getArpTable(InterfaceAddress addr) {
        String targetIp = addr.getAddress().getHostAddress();
        Map<String, String> result = new HashMap<>();
        try {
            final Process process = Runtime.getRuntime().exec("arp -a");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                Pattern interfacePattern = Pattern.compile(
                        "^(?:Интерфейс|Interface):\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                Pattern entryPattern = Pattern.compile(
                        "\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2}-[0-9a-fA-F]{2})");

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
                            result.put(ip, mac);
                        }
                    }
                }

                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Вызов arp завершился с кодом " + exitCode);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // восстанавливаем статус прерывания
                throw new RuntimeException("Прервано ожидание процесса arp");
            }

        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения arp");
        }
        return result;
    }
}