package app.app;

import Server.Message.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Controller {
    @FXML
    private ListView<Message> messageList;
    @FXML
    private TextArea inputArea;
    @FXML
    private Button fileButton;
    @FXML
    private Button sendButton;

    private Model model;
    private StringProperty error;

    @FXML
    public void initialize() {
        fileButton.setOnAction(ignored -> sendFile());
        sendButton.setOnAction(ignored -> sendMessage());
        messageList.setCellFactory(ignored -> new MessageCell());
    }

    public void setModel(Model model){
        this.model = model;
        inputArea.textProperty().bindBidirectional(
                model.inputTextProperty()
        );
        messageList.setItems(model.messagesList());
        model.messagesList().addListener((ListChangeListener<Message>) change -> {
            if (!model.messagesList().isEmpty())
                Platform.runLater(() ->
                        messageList.scrollTo(model.messagesList().size() - 1)
                );
        });
        error = new SimpleStringProperty("");
        error.bindBidirectional(
                model.errorProperty()
        );
        error.addListener((obs, oldVal, newVal) ->{
            if (newVal != null && !newVal.isEmpty() && !newVal.equals(oldVal))
                showError(newVal);
        });

    }

    private void sendFile(){
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите файл");
        File file = chooser.showOpenDialog(
                fileButton.getScene().getWindow()
        );
        if (file != null)
            model.sendFile(Path.of(file.getAbsolutePath()));
    }

    private void sendMessage(){
        model.sendMessage();
    }

    private class MessageCell extends ListCell<Message> {

        @Override
        protected void updateItem(Message message, boolean empty) {

            super.updateItem(message, empty);

            if (empty || message == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            switch(message){
                case TextMessage textMessage -> {
                    Label label = new Label(
                            textMessage.username() + "\n\n" + textMessage.text()
                    );
                    label.setWrapText(true);
                    VBox box = new VBox(label, new Separator());
                    box.setPadding(new Insets(5));
                    setGraphic(box);
                    setText(null);
                }
                case FileMessage fileMessage -> {
                    Label label = new Label(
                            fileMessage.username() + " отправил файл " + fileMessage.fileName()
                    );
                    Button download = new Button("Скачать");
                    download.setOnAction(ignored -> saveFile(
                            fileMessage.fileName(), fileMessage.data()
                    ));
                    HBox box = new HBox(10, label, download);
                    VBox wrapper = new VBox(box, new Separator());
                    wrapper.setPadding(new Insets(5));
                    setGraphic(wrapper);
                    setText(null);
                }
            }
        }
    }

    private void saveFile(String fileName, byte[] data){
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(fileName);
        File saveFile = chooser.showSaveDialog(
                messageList.getScene().getWindow()
        );
        if (saveFile == null)
            return;
        new Thread(() -> {
            try {
                Files.write(saveFile.toPath(), data);
            }
            catch (Exception e) {
                Platform.runLater(() -> error.set(e.getMessage()));
            }
        }).start();
    }

    public static void showError(String text){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Ошибка");
        alert.setContentText(text);
        alert.showAndWait();
    }
}
/*
поставить сервер на виртуалку и подключаться к серверу через локалхост
 */