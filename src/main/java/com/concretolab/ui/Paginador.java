package com.concretolab.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.data.domain.Page;

public class Paginador extends HBox {
    private static final int YEFRI_LERMA_BOTONES_VISIBLES = 5;

    private final Label lblResumen = new Label();
    private final ComboBox<Integer> yefriLermaCmbTamanio =
            new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 25, 50));
    private final HBox yefriLermaBotones = new HBox(4);

    private int yefriLermaPagina = 0;
    private int yefriLermaTotalPaginas = 1;
    private Runnable yefriLermaOnCambio = () -> { };

    public Paginador() {
        getStyleClass().add("paginador");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        lblResumen.getStyleClass().add("paginador-resumen");

        Label lblFilas = new Label("Filas");
        lblFilas.getStyleClass().add("paginador-resumen");
        yefriLermaCmbTamanio.setValue(10);
        yefriLermaCmbTamanio.getStyleClass().add("paginador-tamanio");
        yefriLermaCmbTamanio.valueProperty().addListener((obs, a, b) -> yefriLermaIrAPrimera());

        yefriLermaBotones.setAlignment(Pos.CENTER);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        getChildren().addAll(lblResumen, espacio, lblFilas, yefriLermaCmbTamanio, yefriLermaBotones);
    }

    public void setOnCambio(Runnable onCambio) {
        this.yefriLermaOnCambio = onCambio;
    }

    public int getPagina() {
        return yefriLermaPagina;
    }

    public int getTamanio() {
        return yefriLermaCmbTamanio.getValue();
    }

    public void yefriLermaIrAPrimera() {
        yefriLermaIrA(0);
    }

    public void yefriLermaActualizar(Page<?> resultado) {
        yefriLermaTotalPaginas = Math.max(resultado.getTotalPages(), 1);
        if (yefriLermaPagina >= yefriLermaTotalPaginas) {
            yefriLermaIrA(yefriLermaTotalPaginas - 1);
            return;
        }

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) yefriLermaPagina * getTamanio() + 1;
        long hasta = (long) yefriLermaPagina * getTamanio() + resultado.getNumberOfElements();
        lblResumen.setText(total == 0
                ? "Sin registros"
                : "Mostrando " + desde + "–" + hasta + " de " + total);

        yefriLermaConstruirBotones();
    }

    private void yefriLermaIrA(int nuevaPagina) {
        yefriLermaPagina = Math.max(0, Math.min(nuevaPagina, yefriLermaTotalPaginas - 1));
        yefriLermaOnCambio.run();
    }

    private void yefriLermaConstruirBotones() {
        yefriLermaBotones.getChildren().clear();
        yefriLermaBotones.getChildren().add(yefriLermaBotonIcono(Feather.CHEVRONS_LEFT, 0, yefriLermaPagina == 0));
        yefriLermaBotones.getChildren().add(yefriLermaBotonIcono(Feather.CHEVRON_LEFT, yefriLermaPagina - 1, yefriLermaPagina == 0));

        int inicio = Math.max(0, Math.min(yefriLermaPagina - YEFRI_LERMA_BOTONES_VISIBLES / 2, yefriLermaTotalPaginas - YEFRI_LERMA_BOTONES_VISIBLES));
        int fin = Math.min(yefriLermaTotalPaginas, inicio + YEFRI_LERMA_BOTONES_VISIBLES);

        if (inicio > 0) {
            yefriLermaBotones.getChildren().add(yefriLermaBotonNumero(0));
            if (inicio > 1) {
                yefriLermaBotones.getChildren().add(yefriLermaElipsis());
            }
        }
        for (int i = inicio; i < fin; i++) {
            yefriLermaBotones.getChildren().add(yefriLermaBotonNumero(i));
        }
        if (fin < yefriLermaTotalPaginas) {
            if (fin < yefriLermaTotalPaginas - 1) {
                yefriLermaBotones.getChildren().add(yefriLermaElipsis());
            }
            yefriLermaBotones.getChildren().add(yefriLermaBotonNumero(yefriLermaTotalPaginas - 1));
        }

        boolean esUltima = yefriLermaPagina >= yefriLermaTotalPaginas - 1;
        yefriLermaBotones.getChildren().add(yefriLermaBotonIcono(Feather.CHEVRON_RIGHT, yefriLermaPagina + 1, esUltima));
        yefriLermaBotones.getChildren().add(yefriLermaBotonIcono(Feather.CHEVRONS_RIGHT, yefriLermaTotalPaginas - 1, esUltima));
    }

    private Button yefriLermaBotonNumero(int indice) {
        Button b = new Button(String.valueOf(indice + 1));
        b.getStyleClass().add("pagina-btn");
        if (indice == yefriLermaPagina) {
            b.getStyleClass().add("activa");
        }
        b.setOnAction(e -> {
            if (indice != yefriLermaPagina) {
                yefriLermaIrA(indice);
            }
        });
        return b;
    }

    private Button yefriLermaBotonIcono(Feather icono, int destino, boolean deshabilitado) {
        Button b = new Button(null, new FontIcon(icono));
        b.getStyleClass().add("pagina-btn");
        b.setDisable(deshabilitado);
        b.setOnAction(e -> yefriLermaIrA(destino));
        return b;
    }

    private Label yefriLermaElipsis() {
        Label l = new Label("…");
        l.getStyleClass().add("paginador-resumen");
        return l;
    }
}
