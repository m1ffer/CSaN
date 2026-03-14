package app.app;

import Server.Client;
import Server.Message.*;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class Model {
    private String username;
    private Client client;

    private final StringProperty error;
    private final StringProperty input;
    private final ObservableList<Message> messagesList;

    public Model(){
        input = new SimpleStringProperty("");
        messagesList = FXCollections.observableArrayList();
        error = new SimpleStringProperty("");
    }

    public void init(String host, int port, String username){
        try{
            if (username.isBlank())
                throw new IllegalArgumentException("Никнейм не может быть пустым");
            this.username = username;
            client = new Client(new Socket(host, port));
        }
        catch(Exception e){
            Platform.runLater(() -> error.set(e.getMessage()));
        }
    }

    public void start(){
        Thread reader = new Thread(() -> {
            try{
                while(true){
                    Message message = client.read();
                    Platform.runLater(() ->
                            messagesList.add(message)
                    );
                }
            } catch (Exception ignored){
                Platform.runLater(() -> error.set("Соединение с сервером разорвано"));
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    public StringProperty inputTextProperty(){
        return input;
    }

    public StringProperty errorProperty(){
        return error;
    }

    public ObservableList<Message> messagesList(){
        return messagesList;
    }

    public void sendFile(Path file){
        new Thread(() -> {
            try {
                client.write(
                        new FileMessage(username,
                                file.getFileName().toString(),
                                Files.readAllBytes(file))
                );
            } catch (Exception e) {
                Platform.runLater(() -> error.set(e.getMessage()));
            }
        }).start();
    }

    public void sendMessage(){
        String text = input.get().trim();
        if (text.isBlank())
            return;
        new Thread(() -> {
            try {
                System.out.println("Пытаемся отправить сообщение");
                client.write(new TextMessage(username, text));
                System.out.println("Сообщение отправлено");
            } catch (IOException e) {
                Platform.runLater(() -> error.set(e.getMessage()));
            }
        }).start();
        input.set("");
    }

    public void shutdown(){
        try{
            client.close();
        }
        catch(Exception ignored){}
    }

}
