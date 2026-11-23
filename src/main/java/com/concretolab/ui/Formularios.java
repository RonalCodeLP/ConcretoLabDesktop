package com.concretolab.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilidades para los formularios del panel lateral.
 * <p>
 * Cada campo vive en un contenedor con la clase {@code campo} que incluye un
 * {@code Label.campo-error}; los errores se muestran ahi, bajo el propio campo.
 */
public final class Formularios {

    private static final Pattern FERNANDO_HUANCA_DECIMAL = Pattern.compile("\\d{0,8}([.,]\\d{0,2})?");

    private Formularios() {
    }

    /** Marca el campo en rojo, muestra el mensaje debajo y lo sacude suavemente. */
    public static void fernandoHuancaMarcarError(Control campo, String mensaje) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, true);
        Label error = fernandoHuancaEtiquetaError(campo);
        if (error != null) {
            error.setText(mensaje);
            if (!error.isVisible()) {
                error.setVisible(true);
                error.setManaged(true);
                Animations.fadeInDown(error, Duration.millis(220)).play();
            }
        }
        Animations.shakeX(campo, 4).play();
    }

    public static void fernandoHuancaLimpiarError(Control campo) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, false);
        Label error = fernandoHuancaEtiquetaError(campo);
        if (error != null) {
            error.setVisible(false);
            error.setManaged(false);
        }
    }

    public static void fernandoHuancaLimpiarErrores(Control... campos) {
        for (Control campo : campos) {
            fernandoHuancaLimpiarError(campo);
        }
    }

    /** Quita el estado de error en cuanto el usuario vuelve a escribir. */
    public static void fernandoHuancaLimpiarAlEditar(TextInputControl... campos) {
        for (TextInputControl campo : campos) {
            campo.textProperty().addListener((obs, a, b) -> fernandoHuancaLimpiarError(campo));
        }
    }

    /**
     * Pinta cada violacion de Bean Validation en su campo. Devuelve los mensajes que
     * no pudieron asociarse a ningun campo del formulario.
     */
    public static List<String> fernandoHuancaMostrarViolaciones(ConstraintViolationException e,
                                                  Map<String, ? extends Control> campos) {
        List<String> sinCampo = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            Control campo = campos.get(fernandoHuancaUltimoNodo(v.getPropertyPath()));
            if (campo != null) {
                fernandoHuancaMarcarError(campo, v.getMessage());
            } else {
                sinCampo.add(v.getMessage());
            }
        }
        return sinCampo;
    }

    /** Solo acepta digitos, hasta {@code maximo} caracteres. */
    public static void fernandoHuancaSoloDigitos(TextInputControl campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("\\d{0," + maximo + "}") ? cambio : null));
    }

    /** Importe con hasta 8 enteros y 2 decimales (acepta punto o coma). */
    public static void fernandoHuancaSoloDecimal(TextInputControl campo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                FERNANDO_HUANCA_DECIMAL.matcher(cambio.getControlNewText()).matches() ? cambio : null));
    }

    private static Label fernandoHuancaEtiquetaError(Control campo) {
        Parent contenedor = campo.getParent();
        while (contenedor != null && !contenedor.getStyleClass().contains("campo")) {
            contenedor = contenedor.getParent();
        }
        if (contenedor == null) {
            return null;
        }
        Node nodo = contenedor.lookup(".campo-error");
        return nodo instanceof Label l ? l : null;
    }

    private static String fernandoHuancaUltimoNodo(Path ruta) {
        String nombre = null;
        for (Path.Node nodo : ruta) {
            nombre = nodo.getName();
        }
        return nombre;
    }
}
