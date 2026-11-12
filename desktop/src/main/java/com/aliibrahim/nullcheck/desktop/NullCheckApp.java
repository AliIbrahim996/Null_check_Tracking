package com.aliibrahim.nullcheck.desktop;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** The JavaFX application: one window with a {@link MainView}. */
public final class NullCheckApp extends Application {

    /** Creates the application; called by the JavaFX launcher. */
    public NullCheckApp() {
    }

    /**
     * Shows the main window and opens the file or folder named on the command line, if any.
     *
     * @param stage the primary stage
     */
    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        Scene scene = new Scene(view, 1200, 760);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("app.css"), "app.css").toExternalForm());
        stage.setTitle(MainView.APP_NAME);
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.show();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) {
            try {
                Path file = Path.of(args.get(0));
                if (Files.isDirectory(file)) {
                    view.openFolder(file);
                } else if (Files.isRegularFile(file)) {
                    view.open(file);
                }
            } catch (InvalidPathException e) {
                // Not a usable path on this system: start with no file open, as for a missing file.
            }
        }
    }
}
