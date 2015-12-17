import com.fenix.DTBot.Bot;
import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.logging.ConsoleHandler;

/**
 * Created by Jean-Luc on 7/14/2015.
 */
public class Test extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Button button = new Button("Add Text");
        ProgressBar progressBar = new ProgressBar();
        Label label = new Label("0");
        BotService myService = new BotService();

        progressBar.setMinWidth(400);
        progressBar.setPrefHeight(30);
        progressBar.progressProperty().bind(myService.progressProperty());
        myService.progressProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(newValue.doubleValue());
            label.setText(Integer.toString((int) (newValue.doubleValue() * 100)));
        });
        button.setOnAction(event -> myService.start());

        StackPane stackPane = new StackPane(progressBar, label);
        VBox layout = new VBox(stackPane, button);

        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));

        primaryStage.setScene(new Scene(layout, 400, 200));
        primaryStage.show();
    }

    private class MyTask extends Task<Void> {

        @Override
        protected Void call() throws Exception {
            int max = 1000;
            for (int i = 0; i < max; i++) {
                incrementTutProgress(i, max);
                Thread.sleep(10);
            }

            return null;
        }

        public void incrementTutProgress(int i, int max) {
            updateProgress(i, max);
        }
    }

    private class MyService extends Service {

        @Override
        protected Task createTask() {
            return new MyTask();
        }
    }

    private class BotService extends Service {

        @Override
        protected Task createTask() {
            return new Bot("jeanlucthumm@gmail.com", "fenix1234", new File("queue.txt"), new File("/D://Users///Jean-Luc///DigitalTutors///"), new ConsoleHandler());
        }
    }
}
