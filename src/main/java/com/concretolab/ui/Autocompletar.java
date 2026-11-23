package com.concretolab.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Autocompletado para un {@link TextField}: mientras el usuario escribe (con una pausa
 * de 180 ms) consulta {@code buscador} y muestra las sugerencias en una lista flotante.
 * <ul>
 *   <li>Flechas arriba/abajo para moverse, Enter para elegir, Esc para cerrar.</li>
 *   <li>El texto buscado se resalta en negrita dentro de cada sugerencia.</li>
 * </ul>
 */
public class Autocompletar<T> {

    private static final Duration YEFRI_LERMA_PAUSA = Duration.millis(180);
    private static final int YEFRI_LERMA_FILAS_VISIBLES = 7;
    private static final double YEFRI_LERMA_ALTO_FILA = 54;

    private final TextField campo;
    private final Function<String, List<T>> yefriLermaBuscador;
    private final Consumer<T> yefriLermaAlSeleccionar;

    private Function<T, String> yefriLermaTitulo = String::valueOf;
    private Function<T, String> yefriLermaDetalle = t -> null;
    private Function<T, String> yefriLermaExtra = t -> null;
    private Function<T, Node> yefriLermaIcono = t -> new FontIcon(Feather.SEARCH);
    private int yefriLermaMinimoCaracteres = 1;

    private final Popup yefriLermaPopup = new Popup();
    private final ListView<T> yefriLermaLista = new ListView<>();
    private final PauseTransition yefriLermaEspera = new PauseTransition(YEFRI_LERMA_PAUSA);
    private boolean yefriLermaSuspendido;
    private String yefriLermaConsulta = "";

    public Autocompletar(TextField campo, Function<String, List<T>> buscador, Consumer<T> alSeleccionar) {
        this.campo = campo;
        this.yefriLermaBuscador = buscador;
        this.yefriLermaAlSeleccionar = alSeleccionar;
        yefriLermaConstruirPopup();
        yefriLermaInstalarEventos();
    }

    // ------------------------------------------------------- configuracion

    /** Textos de cada sugerencia: titulo (resaltado), detalle gris y valor a la derecha. */
    public Autocompletar<T> yefriLermaPresentacion(Function<T, String> titulo, Function<T, String> detalle,
                                         Function<T, String> extra) {
        this.yefriLermaTitulo = titulo;
        this.yefriLermaDetalle = detalle;
        this.yefriLermaExtra = extra;
        return this;
    }

    public Autocompletar<T> yefriLermaIcono(Function<T, Node> icono) {
        this.yefriLermaIcono = icono;
        return this;
    }

    public Autocompletar<T> yefriLermaMinimoCaracteres(int minimo) {
        this.yefriLermaMinimoCaracteres = minimo;
        return this;
    }

    /** Cambia el texto del campo sin disparar una busqueda. */
    public void setTexto(String texto) {
        yefriLermaSuspendido = true;
        campo.setText(texto);
        campo.positionCaret(campo.getLength());
        yefriLermaSuspendido = false;
        yefriLermaOcultar();
    }

    public void yefriLermaOcultar() {
        yefriLermaEspera.stop();
        yefriLermaPopup.hide();
    }

    // -------------------------------------------------------------- interno

    private void yefriLermaConstruirPopup() {
        yefriLermaLista.getStyleClass().add("autocompletar-lista");
        yefriLermaLista.setCellFactory(lv -> new Celda());
        yefriLermaLista.setFocusTraversable(false);
        Label sinResultados = new Label("Sin coincidencias");
        sinResultados.getStyleClass().add("autocompletar-vacio");
        yefriLermaLista.setPlaceholder(sinResultados);
        yefriLermaLista.setOnMouseClicked(e -> yefriLermaElegir(yefriLermaLista.getSelectionModel().getSelectedItem()));

        VBox contenedor = new VBox(yefriLermaLista);
        contenedor.getStyleClass().add("autocompletar");
        // El popup es otra ventana: necesita la hoja de estilos de la aplicacion.
        contenedor.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        yefriLermaPopup.getContent().add(contenedor);
        yefriLermaPopup.setAutoHide(true);
        yefriLermaPopup.setAutoFix(true);
    }

    private void yefriLermaInstalarEventos() {
        yefriLermaEspera.setOnFinished(e -> yefriLermaBuscar());
        campo.textProperty().addListener((obs, a, b) -> {
            if (!yefriLermaSuspendido) {
                yefriLermaEspera.playFromStart();
            }
        });
        campo.focusedProperty().addListener((obs, a, enfocado) -> {
            if (!enfocado) {
                yefriLermaOcultar();
            }
        });
        campo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case DOWN -> {
                    if (!yefriLermaPopup.isShowing()) {
                        yefriLermaBuscar();
                    } else {
                        yefriLermaMover(1);
                    }
                    e.consume();
                }
                case UP -> {
                    yefriLermaMover(-1);
                    e.consume();
                }
                case ENTER -> {
                    if (yefriLermaPopup.isShowing() && yefriLermaLista.getSelectionModel().getSelectedItem() != null) {
                        yefriLermaElegir(yefriLermaLista.getSelectionModel().getSelectedItem());
                        e.consume();
                    }
                }
                case ESCAPE -> {
                    if (yefriLermaPopup.isShowing()) {
                        yefriLermaOcultar();
                        e.consume();
                    }
                }
                case TAB -> yefriLermaOcultar();
                default -> { }
            }
        });
    }

    private void yefriLermaBuscar() {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.length() < yefriLermaMinimoCaracteres || campo.getScene() == null) {
            yefriLermaPopup.hide();
            return;
        }
        yefriLermaConsulta = texto;
        List<T> resultados = yefriLermaBuscador.apply(texto);
        yefriLermaLista.getItems().setAll(resultados);
        yefriLermaLista.getSelectionModel().clearSelection();
        if (!resultados.isEmpty()) {
            yefriLermaLista.getSelectionModel().selectFirst();
        }
        yefriLermaLista.setPrefHeight(resultados.isEmpty()
                ? YEFRI_LERMA_ALTO_FILA
                : Math.min(resultados.size(), YEFRI_LERMA_FILAS_VISIBLES) * YEFRI_LERMA_ALTO_FILA + 6);
        yefriLermaMostrar();
    }

    private void yefriLermaMostrar() {
        Bounds b = campo.localToScreen(campo.getBoundsInLocal());
        if (b == null) {
            return;
        }
        yefriLermaLista.setPrefWidth(Math.max(campo.getWidth(), 360));
        if (!yefriLermaPopup.isShowing()) {
            yefriLermaPopup.show(campo, b.getMinX(), b.getMaxY() + 4);
        } else {
            yefriLermaPopup.setX(b.getMinX());
            yefriLermaPopup.setY(b.getMaxY() + 4);
        }
    }

    private void yefriLermaMover(int delta) {
        if (!yefriLermaPopup.isShowing() || yefriLermaLista.getItems().isEmpty()) {
            return;
        }
        int actual = yefriLermaLista.getSelectionModel().getSelectedIndex();
        int nuevo = Math.max(0, Math.min(yefriLermaLista.getItems().size() - 1, actual + delta));
        yefriLermaLista.getSelectionModel().select(nuevo);
        yefriLermaLista.scrollTo(Math.max(0, nuevo - YEFRI_LERMA_FILAS_VISIBLES + 1));
    }

    private void yefriLermaElegir(T item) {
        if (item == null) {
            return;
        }
        yefriLermaOcultar();
        yefriLermaAlSeleccionar.accept(item);
    }

    /** Parte {@code texto} en tramos normales y resaltados segun las palabras buscadas. */
    private TextFlow yefriLermaResaltar(String texto) {
        String minusculas = texto.toLowerCase(Locale.ROOT);
        boolean[] marcado = new boolean[texto.length()];
        for (String palabra : yefriLermaConsulta.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (palabra.isEmpty()) {
                continue;
            }
            int i = minusculas.indexOf(palabra);
            while (i >= 0) {
                for (int k = i; k < i + palabra.length(); k++) {
                    marcado[k] = true;
                }
                i = minusculas.indexOf(palabra, i + palabra.length());
            }
        }

        List<Text> tramos = new ArrayList<>();
        int inicio = 0;
        for (int i = 1; i <= texto.length(); i++) {
            if (i == texto.length() || marcado[i] != marcado[inicio]) {
                Text t = new Text(texto.substring(inicio, i));
                t.getStyleClass().add(marcado[inicio] ? "autocompletar-coincidencia" : "autocompletar-texto");
                tramos.add(t);
                inicio = i;
            }
        }
        TextFlow flujo = new TextFlow(tramos.toArray(Text[]::new));
        flujo.getStyleClass().add("autocompletar-titulo");
        return flujo;
    }

    private class Celda extends ListCell<T> {
        @Override
        protected void updateItem(T item, boolean vacio) {
            super.updateItem(item, vacio);
            if (vacio || item == null) {
                setGraphic(null);
                return;
            }
            StackPane caja = new StackPane(yefriLermaIcono.apply(item));
            caja.getStyleClass().add("autocompletar-icono");

            VBox textos = new VBox(2, yefriLermaResaltar(yefriLermaTitulo.apply(item)));
            String d = yefriLermaDetalle.apply(item);
            if (d != null && !d.isBlank()) {
                TextFlow detalleFlujo = yefriLermaResaltar(d);
                detalleFlujo.getStyleClass().setAll("autocompletar-detalle");
                textos.getChildren().add(detalleFlujo);
            }
            HBox.setHgrow(textos, Priority.ALWAYS);

            HBox fila = new HBox(12, caja, textos);
            fila.setAlignment(Pos.CENTER_LEFT);
            String valor = yefriLermaExtra.apply(item);
            if (valor != null) {
                Label lblExtra = new Label(valor);
                lblExtra.getStyleClass().add("autocompletar-extra");
                lblExtra.setMinWidth(Label.USE_PREF_SIZE);
                fila.getChildren().add(lblExtra);
            }
            setGraphic(fila);
        }
    }
}
