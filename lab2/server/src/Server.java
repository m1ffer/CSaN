import Server.Message.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Server implements AutoCloseable{
    private final static Path USERNAMES_FILE = Path.of("usernames.txt");
    private final Set<Client> clients;
    private final Set<String> usernames;
    private final ServerSocket server;

    private volatile boolean running;

    public Server(int port) throws IOException {
        server = new ServerSocket(port);
        clients = new CopyOnWriteArraySet<>();
        usernames = makeUsernamesSet();
        running = true;
    }

    public void start() throws IOException {
        IO.println("Сервер запущен");
        try {
            while (running) {
                Socket newUser = server.accept();
                try {
                    Client client = new Client(newUser);
                    clients.add(client);
                    IO.println("Пользователь подключился");
                    new ClientHandler(client).
                            start();
                } catch (IOException _) {
                    IO.println("У этого пользователя не получилось");
                    try {
                        newUser.close();
                    } catch (IOException _) {}
                }
            }
        }
        catch(Exception e){
            if (running)
                IO.println("Сервер упал " + e.getMessage());
            else
                IO.println("Сервер корректно завершил работу");
        }
    }

    public void shutdown(){
        running = false;
        try {
            server.close();
        }
        catch(IOException _){
            IO.println("Что-то пошло не так при закрытии сервера");
        }
    }

    private class ClientHandler extends Thread{
        private final Client client;

        public ClientHandler(Client client){
            super();
            this.client = Objects.requireNonNull(client,
                    "Клиент не может быть null");
        }

        @Override
        public void run(){
            try {
                while (true) {
                    Message message = client.read();
                    IO.println("Пришло сообщение");
                    sendMessage(message);
                }
            }
            catch(Exception e){
                IO.println("Ошибка " + e.getMessage());
                e.printStackTrace();
                removeClient(client);
            }
        }
    }

    private void removeClient(Client client){
        if (!clients.remove(client))
            return;
        IO.println("Клиент отключился");
        try{
            client.close();
        }
        catch(IOException _){
            IO.println("Что-то пошло не так при отключении клиента");
        }
    }

    private void sendMessage(Message message){
        addUsername(message.username());
        clients.forEach(i -> {
            try{
                i.write(message);
            }
            catch(IOException _){
                IO.println("Не удалось отправить сообщение");
                removeClient(i);
            }
        });
    }

    private synchronized void addUsername(String username){
        if (!usernames.contains(username)){
            usernames.add(username);
            try {
                Files.writeString(
                        USERNAMES_FILE,
                        username + '\n',
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
            catch(IOException _){
                IO.println("Не получилось записать пользователя в файл");
            }
        }
    }

    private Set<String> makeUsernamesSet() throws IOException {
        if (!Files.exists(USERNAMES_FILE))
            Files.createFile(USERNAMES_FILE);
        return new HashSet<>(
                Files.readAllLines(USERNAMES_FILE)
        );
    }

    @Override
    public void close() throws Exception {
        clients.forEach(i -> {
            try{
                i.close();
            }
            catch(Exception _){}
        });
        server.close();
    }
}
