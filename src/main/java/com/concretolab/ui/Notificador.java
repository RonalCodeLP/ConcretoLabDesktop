package com.concretolab.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

@Component
public class Notificador {
    private ModalPane modalPanel;
    private ModalPane modalDialogo;
    private Label fernandoHuancaEstado;

    public void fernandoHuancaRegistrar(ModalPane modalPanel, ModalPane modalDialogo, Label estado) {
        this.modalPanel = modalPanel;
        this.modalDialogo = modalDialogo;
        this.fernandoHuancaEstado = estado;
    }

    public void exito(String mensaje) {
        fernandoHuancaMensaje(mensaje, Feather.CHECK, "estado-exito");
    }

    public void fernandoHuancaAdvertencia(String mensaje) {
        fernandoHuancaMensaje(mensaje, Feather.ALERT_TRIANGLE, "estado-advertencia");
    }

    public void fernandoHuancaError(String mensaje) {
        fernandoHuancaMensaje(mensaje, Feather.X_CIRCLE, "estado-error");
    }

    public void fernandoHuancaInfo(String mensaje) {
        fernandoHuancaMensaje(mensaje, Feather.INFO, "estado-info");
    }

    private void fernandoHuancaMensaje(String mensaje, Ikon icono, String estilo) {
        if (fernandoHuancaEstado == null) return;
        fernandoHuancaEstado.getStyleClass().removeAll("estado-exito", "estado-advertencia", "estado-error", "estado-info");
        fernandoHuancaEstado.getStyleClass().add(estilo);
        fernandoHuancaEstado.setGraphic(new FontIcon(icono));
        fernandoHuancaEstado.setText(mensaje);
        fernandoHuancaEstado.setTooltip(new javafx.scene.control.Tooltip(mensaje));
    }

    public String fernandoHuancaUltimoMensaje() {
        return fernandoHuancaEstado == null ? "" : fernandoHuancaEstado.getText();
    }

    public void fernandoHuancaConfirmar(String titulo, String mensaje, String textoAccion, Runnable alConfirmar) {
        dialogo(Feather.TRASH_2, "peligro", titulo, mensaje,
                "Cancelar", null, textoAccion, Styles.DANGER, alConfirmar);
    }

    public void fernandoHuancaExitoConAcciones(String titulo, String mensaje,
                                 String textoSecundario, Runnable secundaria,
                                 String textoPrincipal, Runnable principal) {
        dialogo(Feather.CHECK_CIRCLE, "exito", titulo, mensaje,
                textoSecundario, secundaria, textoPrincipal, Styles.ACCENT, principal);
    }

    private void dialogo(Ikon iconoDialogo, String claseIcono, String titulo, String mensaje,
                         String textoSecundario, Runnable secundaria,
                         String textoPrincipal, String estiloPrincipal, Runnable principal) {
        FontIcon icono = new FontIcon(iconoDialogo);
        StackPane circulo = new StackPane(icono);
        circulo.getStyleClass().addAll("dialogo-icono", claseIcono);

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("dialogo-titulo");
        Label lblMensaje = new Label(mensaje);
        lblMensaje.getStyleClass().add("dialogo-mensaje");
        lblMensaje.setWrapText(true);

        Button cancelar = new Button(textoSecundario);
        cancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> {
            modalDialogo.hide();
            if (secundaria != null) {
                secundaria.run();
            }
        });

        Button aceptar = new Button(textoPrincipal);
        aceptar.getStyleClass().add(estiloPrincipal);
        aceptar.setOnAction(e -> {
            modalDialogo.hide();
            principal.run();
        });

        Region espacio = new Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
        HBox acciones = new HBox(10, espacio, cancelar, aceptar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        VBox dialogo = new VBox(14, circulo, lblTitulo, lblMensaje, acciones);
        dialogo.getStyleClass().add("dialogo");
        dialogo.setMaxSize(440, Region.USE_PREF_SIZE);

        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(dialogo);
        aceptar.requestFocus();
    }

    public void fernandoHuancaMostrarDialogo(Node contenido) {
        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(contenido);
    }

    public void fernandoHuancaCerrarDialogo() {
        modalDialogo.hide();
    }

    public void fernandoHuancaAbrirPanel(Node contenido) {
        modalPanel.usePredefinedTransitionFactories(null);
        modalPanel.show(contenido);
    }

    public void fernandoHuancaCerrarPanel() {
        modalPanel.hide();
    }

    public boolean fernandoHuancaHayVentanaAbierta() {
        return (modalPanel != null && modalPanel.isDisplay()) || (modalDialogo != null && modalDialogo.isDisplay());
    }
}
