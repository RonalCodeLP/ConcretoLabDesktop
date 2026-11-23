package com.concretolab.ui;

import com.concretolab.service.BaseCifrada;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

/** Diálogos de la contraseña de la base de datos. Se pueden llamar desde cualquier hilo: se ejecutan en el de JavaFX. */
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

    /** Pide una contraseña nueva (con confirmación). {@code protegerExistente}: ya hay datos sin cifrar que se van a proteger. */
    public static Optional<char[]> erickDelgadoPedirNueva(boolean protegerExistente) {
        return erickDelgadoEnFx(() -> {
            var dialogo = new Dialog<char[]>();
            dialogo.setTitle("Proteger los datos");
            dialogo.setHeaderText(protegerExistente
                    ? "Sus datos aún no están cifrados. Cree una contraseña para protegerlos."
                    : "Cree una contraseña para cifrar sus datos en este equipo.");
            var clave = new PasswordField();
            clave.setPromptText("Contraseña (mínimo " + BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA + " caracteres)");
            var confirmacion = new PasswordField();
            confirmacion.setPromptText("Repita la contraseña");
            var aviso = new Label("Si olvida la contraseña los datos NO se pueden recuperar: no existe ninguna clave de rescate. "
                    + "Las copias de seguridad también se abren con ella.");
            aviso.setWrapText(true);
            aviso.setPrefWidth(380);
            var entiendo = new CheckBox("Entiendo que no se puede recuperar sin la contraseña");
            var error = new Label();
            error.setWrapText(true);
            var contenido = new VBox(8, clave, confirmacion, aviso, entiendo, error);
            contenido.setPadding(new Insets(12));
            dialogo.getDialogPane().setContent(contenido);
            var aceptar = new ButtonType("Cifrar y continuar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);
            var boton = dialogo.getDialogPane().lookupButton(aceptar);
            boton.setDisable(true);
            Runnable validar = () -> {
                String problema = null;
                if (clave.getText().length() < BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA) {
                    problema = "Mínimo " + BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA + " caracteres.";
                } else if (!clave.getText().equals(confirmacion.getText())) {
                    problema = "Las contraseñas no coinciden.";
                }
                error.setText(problema == null || (clave.getText().isEmpty() && confirmacion.getText().isEmpty()) ? "" : problema);
                boton.setDisable(problema != null || !entiendo.isSelected());
            };
            clave.textProperty().addListener((o, a, b) -> validar.run());
            confirmacion.textProperty().addListener((o, a, b) -> validar.run());
            entiendo.selectedProperty().addListener((o, a, b) -> validar.run());
            dialogo.setResultConverter(b -> b == aceptar ? clave.getText().toCharArray() : null);
            return dialogo.showAndWait();
        });
    }

    /** Pide la contraseña actual; {@code aviso} (puede ser nulo) se muestra, por ejemplo tras un intento fallido. */
    public static Optional<char[]> erickDelgadoPedirActual(String aviso) {
        return erickDelgadoEnFx(() -> {
            var dialogo = new Dialog<char[]>();
            dialogo.setTitle("ConcretoLab Desktop");
            dialogo.setHeaderText("Ingrese la contraseña para abrir sus datos");
            var clave = new PasswordField();
            clave.setPromptText("Contraseña");
            var etiqueta = new Label(aviso == null ? "" : aviso);
            etiqueta.setWrapText(true);
            var contenido = new VBox(8, clave, etiqueta);
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

    /** Pide contraseña actual y nueva (con confirmación). Devuelve {actual, nueva} o vacío si cancela. */
    public static Optional<char[][]> erickDelgadoPedirCambio() {
        return erickDelgadoEnFx(() -> {
            var dialogo = new Dialog<char[][]>();
            dialogo.setTitle("Cambiar contraseña");
            dialogo.setHeaderText("Cambiar la contraseña de la base de datos");
            var actual = new PasswordField();
            actual.setPromptText("Contraseña actual");
            var nueva = new PasswordField();
            nueva.setPromptText("Nueva contraseña (mínimo " + BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA + " caracteres)");
            var confirmacion = new PasswordField();
            confirmacion.setPromptText("Repita la nueva contraseña");
            var error = new Label();
            error.setWrapText(true);
            var aviso = new Label("Al terminar, la aplicación se cerrará para aplicar la nueva contraseña. "
                    + "Las copias de seguridad anteriores seguirán abriéndose con la contraseña que tenían.");
            aviso.setWrapText(true);
            aviso.setPrefWidth(380);
            var contenido = new VBox(8, actual, nueva, confirmacion, aviso, error);
            contenido.setPadding(new Insets(12));
            dialogo.getDialogPane().setContent(contenido);
            var aceptar = new ButtonType("Cambiar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);
            var boton = dialogo.getDialogPane().lookupButton(aceptar);
            boton.setDisable(true);
            Runnable validar = () -> {
                String problema = null;
                if (actual.getText().isEmpty()) {
                    problema = "";
                } else if (nueva.getText().length() < BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA) {
                    problema = nueva.getText().isEmpty() ? "" : "La nueva contraseña necesita al menos " + BaseCifrada.ERICK_DELGADO_LONGITUD_MINIMA + " caracteres.";
                } else if (!nueva.getText().equals(confirmacion.getText())) {
                    problema = confirmacion.getText().isEmpty() ? "" : "Las contraseñas nuevas no coinciden.";
                }
                error.setText(problema == null ? "" : problema);
                boolean listo = problema == null;
                boton.setDisable(!listo);
            };
            actual.textProperty().addListener((o, a, b) -> validar.run());
            nueva.textProperty().addListener((o, a, b) -> validar.run());
            confirmacion.textProperty().addListener((o, a, b) -> validar.run());
            dialogo.setResultConverter(b -> b == aceptar
                    ? new char[][] {actual.getText().toCharArray(), nueva.getText().toCharArray()} : null);
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

    public static boolean erickDelgadoConfirmar(String titulo, String mensaje) {
        return erickDelgadoEnFx(() -> {
            var a = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.YES, ButtonType.NO);
            a.setTitle("ConcretoLab Desktop");
            a.setHeaderText(titulo);
            a.setResizable(true);
            return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
        });
    }
}
