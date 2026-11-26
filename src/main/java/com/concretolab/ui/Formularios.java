package com.concretolab.ui;

import atlantafx.base.theme.Styles;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Formularios {
    private static final Pattern FERNANDO_HUANCA_DECIMAL = Pattern.compile("\\d{0,8}([.,]\\d{0,2})?");

    private Formularios() {
    }

    public static void fernandoHuancaMarcarError(Control campo, String mensaje) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, true);
        Label error = fernandoHuancaEtiquetaError(campo);
        if (error != null) {
            error.setText(mensaje);
            if (!error.isVisible()) {
                error.setVisible(true);
                error.setManaged(true);
            }
        }
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

    public static void fernandoHuancaLimpiarAlEditar(TextInputControl... campos) {
        for (TextInputControl campo : campos) {
            campo.textProperty().addListener((obs, a, b) -> fernandoHuancaLimpiarError(campo));
        }
    }

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

    public static void fernandoHuancaSoloDigitos(TextInputControl campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("\\d{0," + maximo + "}") ? cambio : null));
    }

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
