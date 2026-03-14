package app.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("connect.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("Connect");
        stage.setScene(scene);
        stage.show();
    }
}
