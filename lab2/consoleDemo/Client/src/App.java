import Message.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;

public class App implements AutoCloseable {
    private final Client client;
    private final String username;
    public App(String host, int port, String username) throws IOException {
        client = new Client(new Socket(host, port));
        this.username = username;
    }

    public void start(){
        clearConsole();
        IO.print("Введите 1, если хотите отправить файл; 2 - если текстовое сообщение: ");
        int choice = Integer.parseInt(IO.readln());
        clearUpper();
        switch(choice){
            case 1 -> {
                IO.print("Введите путь к файлу: ");
                Path path = Path.of(IO.readln());
                sendMessage(path);
            }
            case 2 -> {
                IO.print("Введите текст: ");
                String text = IO.readln();
                sendMessage(text);
            }
        }
    }

    static class ReaderThread extends Thread {

        private final Client client;

        public ReaderThread(Client client) {
            this.client = client;
        }

        @Override
        public void run() {

            try {

                while (true) {

                    Message message = client.read();

                    switch(message){
                        case TextMessage m -> IO.println(m.text());
                        case FileMessage _ -> IO.print("");
                    }

                }

            } catch (Exception e) {

                System.out.println("Соединение закрыто");

            }
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void clearUpper() {
        System.out.print("\033[1A"); // подняться на 1 строку вверх
        System.out.print("\033[2K"); // очистить строку
        System.out.flush();
    }
}
