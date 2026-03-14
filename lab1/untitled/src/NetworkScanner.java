import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Сканер локальной сети, собирающий информацию об узлах (IP, MAC, имя)
 * для всех активных сетевых интерфейсов компьютера.
 */
class NetworkScanner {
    private static final String INDENT = "  ";

    /**
     * Запись, представляющая узел сети.
     *
     * @param IP   IP-адрес узла
     * @param MAC  MAC-адрес узла
     * @param Name имя узла (или "N/A", если не определено)
     */
    record Host(String IP, String MAC, String Name) {}

    /**
     * Запускает процесс сканирования: выводит информацию о собственном компьютере,
     * затем для каждого подходящего интерфейса выполняет сканирование его подсети.
     *
     * @throws RuntimeException если не удаётся получить список сетевых интерфейсов
     */
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

    /**
     * Выводит информацию о собственном компьютере: имя и IP-адрес,
     * полученные через {@link InetAddress#getLocalHost()}.
     *
     * @throws RuntimeException если не удаётся определить локальный хост
     */
    private void printOwnInfo() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            IO.println("Этот компьютер:");
            IO.println(getIndent(1) + "Имя: " + addr.getCanonicalHostName());
            IO.println(getIndent(1) + "IP: " + addr.getHostAddress());
            IO.println(getIndent(1) + "MAC: " + getMAC(NetworkInterface.getByInetAddress(addr).getHardwareAddress()));
        } catch (UnknownHostException | SocketException e) {
            throw new RuntimeException("Не удалось вывести информацию об устройстве", e);
        }
    }

    /**
     * Обрабатывает один сетевой интерфейс: выводит его MAC-адрес и
     * запускает сканирование для каждой IPv4-подсети, к которой он принадлежит.
     *
     * @param ni сетевой интерфейс (не {@code null})
     * @throws RuntimeException если не удаётся получить MAC-адрес интерфейса
     */
    private void processInterface(@NotNull NetworkInterface ni) {
        IO.println(getIndent(0) + "Сканирование интерфейса: " + ni.getDisplayName());
        try {
            IO.println(getIndent(0) + "MAC-адрес: " + getMAC(ni.getHardwareAddress()));
        } catch (SocketException e) {
            throw new RuntimeException("Ошибка получения MAC для интерфейса " + ni.getDisplayName(), e);
        }
        ni.getInterfaceAddresses().stream()
                .filter(this::isIPv4Address)
                .forEach(this::processInterfaceAddress);
    }

    /**
     * Обрабатывает один IPv4-адрес интерфейса:
     * пингует все IP в соответствующей подсети, затем получает и выводит список активных узлов.
     *
     * @param ia объект {@link InterfaceAddress}, содержащий IP и маску подсети
     */
    private void processInterfaceAddress(@NotNull InterfaceAddress ia) {
        IO.println(getIndent(1) + "IP подсети: " + ia.getAddress().getHostAddress());
        pingAll(ia);
        List<Host> hosts = getHostsForInterface(ia);
        hosts.forEach(this::printHost);
    }

    /**
     * Выводит информацию об одном узле (IP, MAC, имя) с отступами.
     *
     * @param host объект {@link Host}
     */
    private void printHost(Host host) {
        IO.println(getIndent(2) + "Узел: ");
        IO.println(getIndent(3) + "IP: " + host.IP);
        IO.println(getIndent(3) + "MAC: " + host.MAC);
        IO.println(getIndent(3) + "Имя: " + host.Name);
    }

    // ---------- Работа с ARP ----------

    /**
     * Возвращает карту соответствий IP -> MAC для указанного интерфейса,
     * полученную из системной команды {@code arp -a}.
     *
     * @param addr объект {@link InterfaceAddress}, задающий интерфейс
     * @return {@code Map<String, String>}, где ключ — IP-адрес, значение — MAC-адрес
     * @throws RuntimeException если не удаётся выполнить или прочитать вывод команды
     */
    private @NotNull Map<String, String> getArpMapForInterface(@NotNull InterfaceAddress addr) {
        String targetIp = addr.getAddress().getHostAddress();
        Map<String, String> map = new HashMap<>();
        Process process = startArpProcess();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            parseArpOutput(reader, targetIp, map);
            waitForArpProcess(process);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения вывода arp -a", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Прервано ожидание процесса arp", e);
        }
        return map;
    }

    /**
     * Запускает процесс {@code arp -a}.
     *
     * @return объект {@link Process} запущенной команды
     * @throws RuntimeException если не удаётся запустить процесс
     */
    private Process startArpProcess() {
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            return pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось запустить arp -a", e);
        }
    }

    /**
     * Парсит вывод команды {@code arp -a} и заполняет переданную карту парами IP -> MAC
     * только для записей, относящихся к интерфейсу с заданным IP.
     *
     * @param reader    {@link BufferedReader} для чтения вывода
     * @param targetIp  IP-адрес целевого интерфейса
     * @param map       карта, в которую будут добавлены найденные соответствия
     * @throws IOException если возникает ошибка ввода-вывода
     */
    private void parseArpOutput(@NotNull BufferedReader reader, String targetIp, Map<String, String> map) throws IOException {
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
    }

    /**
     * Ожидает завершения процесса {@code arp -a} и проверяет код возврата.
     *
     * @param process объект {@link Process}
     * @throws InterruptedException если текущий поток был прерван во время ожидания
     * @throws RuntimeException    если процесс завершился с ненулевым кодом
     */
    private void waitForArpProcess(Process process) throws InterruptedException {
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("arp -a завершилась с кодом " + exitCode);
        }
    }

    /**
     * Возвращает список узлов ({@link Host}) для указанного интерфейса,
     * используя данные из ARP-таблицы и reverse DNS lookup.
     *
     * @param addr объект {@link InterfaceAddress} интерфейса
     * @return список {@link Host} (может быть пустым)
     */
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
     * Пытается получить имя узла по его IP-адресу.
     *
     * @param ip IP-адрес в виде строки
     * @return имя узла или {@code "N/A"}, если имя не определено
     */
    private static String getHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getHostName();
            if (hostname.equals(ip)) {
                return "N/A";
            }
            return hostname;
        } catch (UnknownHostException e) {
            return "N/A";
        }
    }


    /**
     * Выполняет параллельный пинг всех IP-адресов в подсети указанного интерфейса
     * для обновления ARP-кэша операционной системы.
     *
     * @param ia объект {@link InterfaceAddress}, задающий подсеть
     */
    public void pingAll(@NotNull InterfaceAddress ia) {
        int[] range = calculateIpRange(ia);
        int start = range[0];
        int end = range[1];
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            submitPingTasks(executor, start, end);
            shutdownAndAwaitTermination(executor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            forceShutdownIfNeeded(executor);
        }
    }

    /**
     * Вычисляет диапазон IP-адресов (исключая сетевой и широковещательный)
     * для подсети, заданной интерфейсом.
     *
     * @param ia объект {@link InterfaceAddress}
     * @return массив из двух целых чисел: начальный и конечный IP (включительно)
     */
    private int[] calculateIpRange(InterfaceAddress ia) {
        InetAddress addr = ia.getAddress();
        short prefix = ia.getNetworkPrefixLength();
        byte[] ipBytes = addr.getAddress();
        int ipInt = ByteBuffer.wrap(ipBytes).getInt();
        int mask = 0xFFFFFFFF << (32 - prefix);
        int network = ipInt & mask;
        int broadcast = network | ~mask;
        int start = network + 1;
        int end = broadcast - 1;
        return new int[]{start, end};
    }

    /**
     * Отправляет задачи на пинг для каждого IP в заданном диапазоне.
     *
     * @param executor сервис выполнения задач
     * @param start    начальный IP (целое число)
     * @param end      конечный IP (целое число)
     */
    private void submitPingTasks(ExecutorService executor, int start, int end) {
        for (int i = start; i <= end; i++) {
            final int currentIP = i;
            executor.submit(() -> {
                try {
                    InetAddress target = InetAddress.getByAddress(intToBytes(currentIP));
                    target.isReachable(200);
                } catch (IOException ignored) {
                    // Игнорируем, так как пинг нужен только для заполнения ARP-кэша
                }
            });
        }
    }

    /**
     * Завершает работу пула, ожидая завершения задач в течение 5 секунд,
     * при необходимости принудительно прерывает.
     *
     * @param executor сервис выполнения задач
     * @throws InterruptedException если текущий поток прерван во время ожидания
     */
    private void shutdownAndAwaitTermination(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    /**
     * Принудительно завершает пул, если он ещё не остановлен.
     *
     * @param executor сервис выполнения задач
     */
    private void forceShutdownIfNeeded(ExecutorService executor) {
        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    /**
     * Преобразует целое число в массив байт (порядок big-endian).
     *
     * @param value целое число
     * @return массив из 4 байт
     */
    private byte @NotNull [] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * Проверяет, пригоден ли сетевой интерфейс для сканирования.
     * Критерии: поднят, не loopback, не виртуальный, имеет MAC-адрес.
     *
     * @param ni сетевой интерфейс
     * @return {@code true}, если интерфейс подходит
     */
    private boolean isUsableInterface(@NotNull NetworkInterface ni) {
        try {
            return ni.isUp() &&
                    !ni.isLoopback() &&
                    ni.getHardwareAddress() != null;
        } catch (SocketException e) {
            return false;
        }
    }

    /**
     * Проверяет, имеет ли интерфейс хотя бы один IPv4-адрес.
     *
     * @param ni сетевой интерфейс
     * @return {@code true}, если есть IPv4-адрес
     */
    private boolean hasIPv4Address(@NotNull NetworkInterface ni) {
        return ni.getInterfaceAddresses().stream()
                .anyMatch(this::isIPv4Address);
    }

    /**
     * Проверяет, является ли адрес в объекте {@link InterfaceAddress} IPv4.
     *
     * @param addr объект {@link InterfaceAddress}
     * @return {@code true}, если адрес IPv4
     */
    private boolean isIPv4Address(@NotNull InterfaceAddress addr) {
        return addr.getAddress() instanceof Inet4Address;
    }

    /**
     * Возвращает строку отступа заданной глубины (повторяет {@link #INDENT} n раз).
     *
     * @param n количество отступов (неотрицательное)
     * @return строка из n копий {@link #INDENT}
     */
    private @NotNull String getIndent(int n) {
        return INDENT.repeat(Math.max(n, 0));
    }

    /**
     * Форматирует MAC-адрес из массива байт в строку вида {@code AA-BB-CC-DD-EE-FF}.
     *
     * @param addr массив байт MAC-адреса
     * @return отформатированная строка
     */
    private @NotNull String getMAC(byte @NotNull [] addr) {
        StringBuilder mac = new StringBuilder(String.format("%02X", addr[0] & 0xFF));
        for (int i = 1; i < addr.length; i++) {
            mac.append(String.format("-%02X", addr[i] & 0xFF));
        }
        return mac.toString();
    }
}