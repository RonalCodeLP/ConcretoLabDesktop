package com.concretolab.ui;

import com.concretolab.service.ResultadoTiempoHistoria;
import java.util.Locale;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Elevación animada del edificio sobre un suelo que también se mueve. El bloque de suelo se desplaza con el
 * desplazamiento del terreno ug(t) y cada losa se dibuja en desplazamiento ABSOLUTO (ug + u relativo). La posición inicial
 * se muestra punteada de referencia y cada entrepiso se colorea por su deriva instantánea respecto al límite 0,00875.
 *
 * <p>Los desplazamientos se exageran con un factor automático (el mayor desplazamiento absoluto ocupa cerca del 17,5 % de
 * la altura) multiplicado por la escala elegida por el usuario. Los colores salen de constantes con nombre, sin estilos en línea.</p>
 */
public final class VistaTiempoHistoria extends Pane {

    // Colores del lienzo: lienzo propio de fondo claro para que se lea igual en tema claro y oscuro.
    static final Color WALTER_QUISPESAYHUA_FONDO = Color.web("#f5f7fa");
    static final Color WALTER_QUISPESAYHUA_SUELO = Color.web("#8a6d4b");
    static final Color WALTER_QUISPESAYHUA_SUELO_BORDE = Color.web("#5e4a33");
    static final Color WALTER_QUISPESAYHUA_SUELO_TEXTO = Color.web("#ffffff");
    static final Color WALTER_QUISPESAYHUA_REFERENCIA = Color.web("#8b95a1");
    static final Color LOSA = Color.web("#3b4654");
    static final Color WALTER_QUISPESAYHUA_TEXTO = Color.web("#2b3440");
    static final Color WALTER_QUISPESAYHUA_DERIVA_BAJA = Color.web("#2e9e5b");
    static final Color WALTER_QUISPESAYHUA_DERIVA_ALERTA = Color.web("#e0a100");
    static final Color WALTER_QUISPESAYHUA_DERIVA_EXCEDE = Color.web("#d64545");

    private final Canvas walterQuispesayhuaLienzo = new Canvas();
    private ResultadoTiempoHistoria walterQuispesayhuaResultado;
    private double walterQuispesayhuaAltura = 1;
    private int walterQuispesayhuaDireccion;
    private int walterQuispesayhuaPaso;
    private double walterQuispesayhuaFactorAutomatico = 1;
    private double walterQuispesayhuaMultiplicador = 1;
    private double[] walterQuispesayhuaCotas = new double[0];

    public VistaTiempoHistoria() {
        getChildren().add(walterQuispesayhuaLienzo);
        setMinHeight(260);
        setPrefHeight(420);
        widthProperty().addListener((o, a, b) -> walterQuispesayhuaDibujar());
        heightProperty().addListener((o, a, b) -> walterQuispesayhuaDibujar());
    }

    @Override
    protected void layoutChildren() {
        walterQuispesayhuaLienzo.setWidth(Math.max(1, getWidth()));
        walterQuispesayhuaLienzo.setHeight(Math.max(1, getHeight()));
        walterQuispesayhuaDibujar();
    }

    /** Carga un resultado; las cotas (m) son las del modelo dinámico, una por piso. */
    public void walterQuispesayhuaMostrar(ResultadoTiempoHistoria r, double[] cotasPisos) {
        this.walterQuispesayhuaResultado = r;
        this.walterQuispesayhuaCotas = cotasPisos == null ? new double[0] : cotasPisos.clone();
        this.walterQuispesayhuaAltura = walterQuispesayhuaCotas.length == 0 ? 1 : Math.max(1e-6, walterQuispesayhuaCotas[walterQuispesayhuaCotas.length - 1]);
        this.walterQuispesayhuaPaso = 0;
        walterQuispesayhuaRecalcularFactor();
        walterQuispesayhuaDibujar();
    }

    public void setDireccion(int dir) {
        this.walterQuispesayhuaDireccion = dir == 1 ? 1 : 0;
        walterQuispesayhuaRecalcularFactor();
        walterQuispesayhuaDibujar();
    }

    public void setPaso(int p) {
        this.walterQuispesayhuaPaso = walterQuispesayhuaResultado == null ? 0 : Math.max(0, Math.min(walterQuispesayhuaResultado.pasos() - 1, p));
        walterQuispesayhuaDibujar();
    }

    /** Multiplicador sobre la escala automática (1 = automática). */
    public void setMultiplicador(double m) {
        this.walterQuispesayhuaMultiplicador = m > 0 ? m : 1;
        walterQuispesayhuaDibujar();
    }

    /** Factor total de exageración ×N que se está aplicando. */
    public double walterQuispesayhuaFactorTotal() { return walterQuispesayhuaFactorAutomatico * walterQuispesayhuaMultiplicador; }

    private void walterQuispesayhuaRecalcularFactor() {
        walterQuispesayhuaFactorAutomatico = walterQuispesayhuaResultado == null ? 1
                : ResumenSimulacion.walterQuispesayhuaFactorVisual(ResumenSimulacion.walterQuispesayhuaDesplazamientoAbsolutoMaximo(walterQuispesayhuaResultado, walterQuispesayhuaDireccion), walterQuispesayhuaAltura);
    }

    private static Color walterQuispesayhuaColor(ResumenSimulacion.NivelDeriva n) {
        return switch (n) {
            case BAJO -> WALTER_QUISPESAYHUA_DERIVA_BAJA;
            case ALERTA -> WALTER_QUISPESAYHUA_DERIVA_ALERTA;
            case EXCEDE -> WALTER_QUISPESAYHUA_DERIVA_EXCEDE;
        };
    }

    private void walterQuispesayhuaDibujar() {
        double w = walterQuispesayhuaLienzo.getWidth(), h = walterQuispesayhuaLienzo.getHeight();
        GraphicsContext g = walterQuispesayhuaLienzo.getGraphicsContext2D();
        g.setLineDashes(null);
        g.setFill(WALTER_QUISPESAYHUA_FONDO);
        g.fillRect(0, 0, w, h);
        if (walterQuispesayhuaResultado == null || walterQuispesayhuaCotas.length == 0 || w < 50 || h < 50) return;

        double arriba = 40, grosorSuelo = 48, abajo = 18;
        double util = Math.max(40, h - arriba - grosorSuelo - abajo);
        double pxPorM = util / walterQuispesayhuaAltura;
        double baseY = arriba + util;
        double anchoEdif = Math.max(70, Math.min(180, w * 0.22));
        double anchoSuelo = Math.min(w - 20, anchoEdif * 2.8);
        double x0 = w / 2;
        double factor = walterQuispesayhuaFactorTotal();
        double ug = ResumenSimulacion.walterQuispesayhuaSuelo(walterQuispesayhuaResultado, walterQuispesayhuaDireccion)[walterQuispesayhuaPaso];
        double dxSuelo = ug * factor * pxPorM;

        // Referencia punteada (posición inicial)
        g.setStroke(WALTER_QUISPESAYHUA_REFERENCIA);
        g.setLineWidth(1.2);
        g.setLineDashes(6, 4);
        g.strokeRect(x0 - anchoSuelo / 2, baseY, anchoSuelo, grosorSuelo);
        for (double cota : walterQuispesayhuaCotas) {
            double y = baseY - cota * pxPorM;
            g.strokeLine(x0 - anchoEdif / 2, y, x0 + anchoEdif / 2, y);
        }
        g.strokeLine(x0 - anchoEdif / 2, baseY, x0 - anchoEdif / 2, baseY - walterQuispesayhuaAltura * pxPorM);
        g.strokeLine(x0 + anchoEdif / 2, baseY, x0 + anchoEdif / 2, baseY - walterQuispesayhuaAltura * pxPorM);
        g.setLineDashes(null);

        // Suelo móvil
        g.setFill(WALTER_QUISPESAYHUA_SUELO);
        g.fillRect(x0 - anchoSuelo / 2 + dxSuelo, baseY, anchoSuelo, grosorSuelo);
        g.setStroke(WALTER_QUISPESAYHUA_SUELO_BORDE);
        g.setLineWidth(1.5);
        g.strokeRect(x0 - anchoSuelo / 2 + dxSuelo, baseY, anchoSuelo, grosorSuelo);
        g.setFill(WALTER_QUISPESAYHUA_SUELO_TEXTO);
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("Suelo (movimiento del terreno)", x0 + dxSuelo, baseY + grosorSuelo / 2 + 4);

        // Estructura en desplazamiento absoluto
        g.setLineCap(StrokeLineCap.BUTT);
        double xPrev = x0 + dxSuelo, yPrev = baseY;
        double semi = anchoEdif * 0.4;
        for (int i = 0; i < walterQuispesayhuaCotas.length; i++) {
            double xi = x0 + ResumenSimulacion.walterQuispesayhuaAbsoluto(walterQuispesayhuaResultado, walterQuispesayhuaDireccion, walterQuispesayhuaPaso, i) * factor * pxPorM;
            double yi = baseY - walterQuispesayhuaCotas[i] * pxPorM;
            g.setStroke(walterQuispesayhuaColor(ResumenSimulacion.walterQuispesayhuaNivel(ResumenSimulacion.walterQuispesayhuaDeriva(walterQuispesayhuaResultado, walterQuispesayhuaDireccion, walterQuispesayhuaPaso, i))));
            g.setLineWidth(4);
            g.strokeLine(xPrev - semi, yPrev, xi - semi, yi);
            g.strokeLine(xPrev + semi, yPrev, xi + semi, yi);
            g.setFill(LOSA);
            g.fillRect(xi - anchoEdif / 2, yi - 4, anchoEdif, 8);
            xPrev = xi;
            yPrev = yi;
        }
        g.setFill(LOSA);
        g.fillRect(x0 + dxSuelo - anchoEdif / 2, baseY - 4, anchoEdif, 8);

        // Rótulos
        g.setFill(WALTER_QUISPESAYHUA_TEXTO);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.fillText("Estructura (desplazamiento absoluto)", 12, 20);
        g.setFont(Font.font("System", 12));
        g.fillText(walterQuispesayhuaTextoExageracion(), 12, h - 6);
        g.setTextAlign(TextAlignment.RIGHT);
        double lx = w - 12;
        walterQuispesayhuaLeyenda(g, lx, 14, WALTER_QUISPESAYHUA_DERIVA_BAJA, "deriva ≤ 60 % del límite");
        walterQuispesayhuaLeyenda(g, lx, 30, WALTER_QUISPESAYHUA_DERIVA_ALERTA, "hasta el límite 0,00875");
        walterQuispesayhuaLeyenda(g, lx, 46, WALTER_QUISPESAYHUA_DERIVA_EXCEDE, "supera el límite");
    }

    private void walterQuispesayhuaLeyenda(GraphicsContext g, double xDerecha, double y, Color c, String texto) {
        g.setFont(Font.font("System", 11));
        g.setFill(WALTER_QUISPESAYHUA_TEXTO);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText(texto, xDerecha, y);
        g.setFill(c);
        g.fillRect(xDerecha - 130 - 14, y - 9, 10, 10);
    }

    /** Texto fijo que debe verse siempre: «Los desplazamientos están exagerados ×N». */
    public String walterQuispesayhuaTextoExageracion() {
        return String.format(Locale.forLanguageTag("es-PE"), "Los desplazamientos están exagerados ×%,.0f", walterQuispesayhuaFactorTotal());
    }
}
