package com.fenix.DTBot;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.prefs.Preferences;

public class Controller implements Initializable {


    @FXML
    private StackPane leftPane;

    @FXML
    private ListView<ConsoleMessage> console;

    @FXML
    private PasswordField passField;

    @FXML
    private Button outputButton;

    @FXML
    private Button startButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressBarLabel;

    @FXML
    private TextField userField;

    @FXML
    private Button queueButton;

    private Stage primaryStage;
    public File outputDir;
    public File queue;
    private Preferences prefs;
    DirectoryChooser directoryChooser;
    FileChooser fileChooser;
    private Handler handler;
    private BotService botService;


    public void setStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public String getUser() {
        return userField.getText();
    }

    public String getPass() {
        return passField.getText();
    }

    @FXML
    void buttonPressed(ActionEvent event) {
        if (event.getSource() == startButton) {
            // raise a new alert box if the user has forgotten to complete something
            String title = "Missing Data";
            if (getUser().length() < 1) {
                AlertBox.display(title, "Please enter a username.");
                event.consume();
                return;
            } else if (getPass().length() < 1) {
                AlertBox.display(title, "Please enter a password.");
                event.consume();
                return;
            } else if (outputDir == null) {
                AlertBox.display(title, "Please select an output directory.");
                event.consume();
                return;
            } else if (queue == null) {
                AlertBox.display(title, "Please select a queue file.");
                event.consume();
                return;
            }

            // store the login info in preferences
            prefs.put("user", getUser());
            prefs.put("pass", getPass());


            // start a new bot thread and bind related progress bar properties
            progressBar.progressProperty().bind(botService.progressProperty());
            progressBarLabel.textProperty().bind(botService.titleProperty()); // ghetto but it works

            if (botService.getState() == Worker.State.READY) {
                botService.start();
            } else if (botService.getState() == Worker.State.SUCCEEDED) {
                botService.restart();
            }

        } else if (event.getSource() == outputButton) {
            // create new directory chooser
            directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Download Location");

            // check preference for default directory
            String temp = prefs.get("outputDir", "default");
            if (!temp.equals("default")) {
                directoryChooser.setInitialDirectory(new File(temp));
            }

            // get download location and check for errors
            File outputDirTemp;
            try {
                outputDirTemp = directoryChooser.showDialog(primaryStage);
            } catch (IllegalArgumentException e) {
                directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
                outputDirTemp = directoryChooser.showDialog(primaryStage);
            }
            if (outputDirTemp != null && !outputDirTemp.getAbsolutePath().equals(temp)) {
                // user has entered a new directory that needs to be stored in preferences
                prefs.put("outputDir", outputDirTemp.getAbsolutePath());
                outputDir = outputDirTemp; // store new file permanently
            }

        } else if (event.getSource() == queueButton) {
            // create new file chooser and obtain queue location
            fileChooser = new FileChooser();
            fileChooser.setTitle("Select Queue File");
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

            // check preference for default directory
            String temp = prefs.get("queuePath", "default");
            if (!temp.equals("default")) {
                fileChooser.setInitialDirectory(new File(temp));
            }

            // get download location and check for errors
            File tempQueue = null;
            try {
                tempQueue = fileChooser.showOpenDialog(primaryStage);
            } catch (IllegalArgumentException e) {
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            }
            if (tempQueue != null && !tempQueue.getAbsolutePath().equals(temp)) {
                // user has entered a new path that needs to be stored in preferences
                prefs.put("queuePath", tempQueue.getParentFile().getAbsolutePath());
                prefs.put("queue", tempQueue.getAbsolutePath());
                queue = tempQueue; // store new file permanently
            }
        }

        if (console.getItems().size() > 200) {
            console.getItems().remove(0);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Component initialization
        Tooltip queueTooltip = new Tooltip();
        queueTooltip.setText("Contains list of URLs to DT tutorials");
        queueButton.setTooltip(queueTooltip);
        Tooltip outputTooltip = new Tooltip();
        outputTooltip.setText("Directory to which files will be downloaded\n" +
                "Tutorial subdirectories will be automatically created");
        outputButton.setTooltip(outputTooltip);
        startButton.requestFocus();
        // set custom cell factory so we can change font fill
        console.setCellFactory(new ConsoleCellFactory());
        progressBar.prefWidthProperty().bind(leftPane.widthProperty());
        botService = new BotService();
//        progressBar.progressProperty().bind(botService.progressProperty());
        botService.progressProperty().addListener((observable, oldValue, newValue) -> {
        });


        // Preference initialization
        prefs = Preferences.userRoot().node(this.getClass().getName());
        // set the text for the user field
        String temp = prefs.get("user", "default");
        if (!temp.equals("default")) {
            userField.setText(temp);
        }
        // set the text for the pass field. note that this is not secure at all
        temp = prefs.get("pass", "default");
        if (!temp.equals("default")) {
            passField.setText(temp);
        }
        // set the output directory
        temp = prefs.get("outputDir", "default");
        if (!temp.equals("default")) {

            outputDir = new File(temp);
        }
        // set the queue directory
        temp = prefs.get("queue", "default");
        if (!temp.equals("default")) {
            queue = new File(temp);
        }

        // Bot initialization
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // add message to the console with proper log level
                console.getItems().add(new ConsoleMessage(record.getMessage(), record.getLevel()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };


    }

    private class ConsoleMessage {
        String message;
        Level logLevel;

        public ConsoleMessage(String message, Level logLevel) {
            this.message = message;
            this.logLevel = logLevel;
        }
    }

    private class ConsoleCellFactory implements Callback<ListView<ConsoleMessage>, ListCell<ConsoleMessage>> {

        @Override
        public ListCell<ConsoleMessage> call(ListView<ConsoleMessage> param) {
            return new ListCell<ConsoleMessage>() {

                @Override
                protected void updateItem(ConsoleMessage item, boolean empty) {
                    Platform.runLater(() -> {
                        super.updateItem(item, empty);

                        // if this is an empty cell then add no text
                        if (item == null | empty) {
                            setText(null);
                        } else {
                            // set the text to the console message
                            setText(item.message);

                            // determine font color based on log level
                            if (item.logLevel.equals(Level.FINER)) {
                                setTextFill(Color.BLACK);
                            } else if (item.logLevel.equals(Level.INFO)) {
                                setTextFill(Color.BLUE);
                            } else if (item.logLevel.equals(Level.WARNING)) {
                                setTextFill(Color.RED);
                            } else if (item.logLevel.equals(Level.SEVERE)) {
                                setTextFill(Color.RED);
                            } else {
                                setTextFill(Color.BLACK);
                            }
                        }

                    });
                }
            };
        }
    }

    private class BotService extends Service {

        @Override
        protected Task createTask() {
            return new Bot(getUser(), getPass(), outputDir, queue, handler);
        }
    }


}
