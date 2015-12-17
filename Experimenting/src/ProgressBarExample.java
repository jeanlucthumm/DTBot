import javafx.application.Application;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Created by Jean-Luc on 7/14/2015.
 */
public class ProgressBarExample extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Button button = new Button("Add Text");
        ProgressBar progressBar = new ProgressBar();
        Label label = new Label("0");
        BotService botService = new BotService();


        progressBar.setMinWidth(400);
        progressBar.setPrefHeight(30);
        progressBar.progressProperty().bind(botService.progressProperty());
        button.setOnAction(event -> {
            if (botService.getState() == Worker.State.READY) {
                botService.start();
            } else if (botService.getState() == Worker.State.SUCCEEDED) {
                botService.restart();
            }
        });
        botService.progressProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(newValue);
            double percentage = newValue.doubleValue() * 100;
            label.setText(Integer.toString((int) Math.round(percentage)));
        });


        StackPane stackPane = new StackPane(progressBar, label);
        VBox layout = new VBox(stackPane, button);

        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));

        primaryStage.setScene(new Scene(layout, 400, 200));
        primaryStage.show();
    }

    private class BotService extends Service {

        @Override
        protected Task createTask() {
            return new Task() {
                @Override
                protected Void call() throws Exception {
                    int max = 1000;
                    for (int i = 0; i < max; i++) {
                        updateProgress(i, max);
                        Thread.sleep(10);
                    }
                    return null;
                }
            };
        }
    }
}
