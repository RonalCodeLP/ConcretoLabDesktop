package com.concretolab;

import javafx.application.Application;

/** Punto de entrada: evita el error "JavaFX runtime components missing" al ejecutar desde el jar. */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
