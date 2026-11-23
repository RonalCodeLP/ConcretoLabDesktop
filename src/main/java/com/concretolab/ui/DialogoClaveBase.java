package com.concretolab.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

/**
 * Diálogos del arranque. ConcretoLab abre sin contraseña; solo se pide la contraseña de una base creada con una versión
 * anterior que la tenía, para convertirla una única vez. Se pueden llamar desde cualquier hilo: se ejecutan en el de JavaFX.
 */
public final class DialogoClaveBase {

    private DialogoClaveBase() { }

    static <T> T erickDelgadoEnFx(Supplier<T> tarea) {
        if (Platform.isFxApplicationThread()) return tarea.get();
        var futuro = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                futuro.complete(tarea.get());
            } catch (Throwable t) {
                futuro.completeExceptionally(t);
            }
        });
        return futuro.join();
    }

    /** Pide la contraseña de una base antigua protegida; {@code aviso} explica el motivo o el intento fallido. */
    public static Optional<char[]> erickDelgadoPedirActual(String aviso) {
        return erickDelgadoEnFx(() -> {
            var dialogo = new Dialog<char[]>();
            dialogo.setTitle("ConcretoLab Desktop");
            dialogo.setHeaderText("Abrir datos de una versión anterior");
            var clave = new PasswordField();
            clave.setPromptText("Contraseña anterior");
            var etiqueta = new Label(aviso == null ? "" : aviso);
            etiqueta.setWrapText(true);
            etiqueta.setPrefWidth(420);
            etiqueta.setMinHeight(Label.USE_PREF_SIZE);
            var contenido = new VBox(8, etiqueta, clave);
            contenido.setPadding(new Insets(12));
            dialogo.getDialogPane().setContent(contenido);
            var abrir = new ButtonType("Abrir", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialogo.getDialogPane().getButtonTypes().addAll(abrir, new ButtonType("Salir", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
            dialogo.getDialogPane().lookupButton(abrir).disableProperty().bind(clave.textProperty().isEmpty());
            dialogo.setResultConverter(b -> b == abrir ? clave.getText().toCharArray() : null);
            Platform.runLater(clave::requestFocus);
            return dialogo.showAndWait();
        });
    }

    public static void erickDelgadoMostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        erickDelgadoEnFx(() -> {
            var a = new Alert(tipo, mensaje);
            a.setTitle("ConcretoLab Desktop");
            a.setHeaderText(titulo);
            a.setResizable(true);
            a.showAndWait();
            return null;
        });
    }
}
