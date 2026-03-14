package app.app;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class ConnectController {

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private TextField usernameField;

    @FXML
    private Button connectButton;

    @FXML
    public void initialize() {

        connectButton.setOnAction(e -> connect());

    }

    private void connect() {

        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();

        int port;

        try {
            port = Integer.parseInt(portField.getText().trim());
        }
        catch (Exception e) {
            showError("Неверный порт");
            return;
        }

        try {

            Model model = new Model();

            model.init(host, port, username);
            model.start();

            openChatWindow(model);

        }
        catch (Exception e) {
            showError(e.getMessage());
        }

    }

    private void openChatWindow(Model model) {

        try {

            FXMLLoader loader =
                    new FXMLLoader(getClass().getResource("view.fxml"));

            Scene scene = new Scene(loader.load());

            Controller controller = loader.getController();

            controller.setModel(model);

            Stage stage = new Stage();
            stage.setTitle("Chat");
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(event -> {
                model.shutdown();
            });
            connectButton.getScene().getWindow().hide();

        }
        catch (Exception e) {
            showError(e.getMessage());
        }

    }

    private void showError(String text) {
        Controller.showError(text);
    }

}