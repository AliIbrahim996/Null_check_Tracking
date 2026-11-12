package com.aliibrahim.nullcheck.desktop;

import javafx.application.Application;

/**
 * Starts the desktop app.
 *
 * <p>This class does not extend {@link Application}, so the app also starts from a plain class path
 * (for example from the distribution scripts) without the JavaFX launcher's module checks.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Starts the app.
     *
     * @param args an optional path to a source file or folder to open on start
     */
    public static void main(String[] args) {
        Application.launch(NullCheckApp.class, args);
    }
}
