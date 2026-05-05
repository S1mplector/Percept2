package com.musicplayer;

/**
 * Launcher class to work around JavaFX runtime issues in packaged applications.
 * This class serves as the main entry point and delegates to the actual Main class.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}