package Server;

import Server.Message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Objects;

public class Client implements AutoCloseable{
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final Socket socket;

    public Client(Socket clientSocket) throws IOException {
        socket = Objects.requireNonNull(clientSocket,
                "Сокет не может быть null");
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized void write(Message message) throws IOException {
        Objects.requireNonNull(message,
                "Сообщение не может быть null");
        IO.println("Метод вызван");
        out.writeObject(message);
        IO.println("Сообщение записано");
        out.flush();
        IO.println("Буфер смыт");
    }

    public Message read() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
