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
    static final Color FONDO = Color.web("#f5f7fa");
    static final Color SUELO = Color.web("#8a6d4b");
    static final Color SUELO_BORDE = Color.web("#5e4a33");
    static final Color SUELO_TEXTO = Color.web("#ffffff");
    static final Color REFERENCIA = Color.web("#8b95a1");
    static final Color LOSA = Color.web("#3b4654");
    static final Color TEXTO = Color.web("#2b3440");
    static final Color DERIVA_BAJA = Color.web("#2e9e5b");
    static final Color DERIVA_ALERTA = Color.web("#e0a100");
    static final Color DERIVA_EXCEDE = Color.web("#d64545");

    private final Canvas lienzo = new Canvas();
    private ResultadoTiempoHistoria resultado;
    private double altura = 1;
    private int direccion;
    private int paso;
    private double factorAutomatico = 1;
    private double multiplicador = 1;
    private double[] cotas = new double[0];

    public VistaTiempoHistoria() {
        getChildren().add(lienzo);
        setMinHeight(260);
        setPrefHeight(420);
        widthProperty().addListener((o, a, b) -> dibujar());
        heightProperty().addListener((o, a, b) -> dibujar());
    }

    @Override
    protected void layoutChildren() {
        lienzo.setWidth(Math.max(1, getWidth()));
        lienzo.setHeight(Math.max(1, getHeight()));
        dibujar();
    }

    /** Carga un resultado; las cotas (m) son las del modelo dinámico, una por piso. */
    public void mostrar(ResultadoTiempoHistoria r, double[] cotasPisos) {
        this.resultado = r;
        this.cotas = cotasPisos == null ? new double[0] : cotasPisos.clone();
        this.altura = cotas.length == 0 ? 1 : Math.max(1e-6, cotas[cotas.length - 1]);
        this.paso = 0;
        recalcularFactor();
        dibujar();
    }

    public void setDireccion(int dir) {
        this.direccion = dir == 1 ? 1 : 0;
        recalcularFactor();
        dibujar();
    }

    public void setPaso(int p) {
        this.paso = resultado == null ? 0 : Math.max(0, Math.min(resultado.pasos() - 1, p));
        dibujar();
    }

    /** Multiplicador sobre la escala automática (1 = automática). */
    public void setMultiplicador(double m) {
        this.multiplicador = m > 0 ? m : 1;
        dibujar();
    }

    /** Factor total de exageración ×N que se está aplicando. */
    public double factorTotal() { return factorAutomatico * multiplicador; }

    private void recalcularFactor() {
        factorAutomatico = resultado == null ? 1
                : ResumenSimulacion.factorVisual(ResumenSimulacion.desplazamientoAbsolutoMaximo(resultado, direccion), altura);
    }

    private static Color color(ResumenSimulacion.NivelDeriva n) {
        return switch (n) {
            case BAJO -> DERIVA_BAJA;
            case ALERTA -> DERIVA_ALERTA;
            case EXCEDE -> DERIVA_EXCEDE;
        };
    }

    private void dibujar() {
        double w = lienzo.getWidth(), h = lienzo.getHeight();
        GraphicsContext g = lienzo.getGraphicsContext2D();
        g.setLineDashes(null);
        g.setFill(FONDO);
        g.fillRect(0, 0, w, h);
        if (resultado == null || cotas.length == 0 || w < 50 || h < 50) return;

        double arriba = 40, grosorSuelo = 48, abajo = 18;
        double util = Math.max(40, h - arriba - grosorSuelo - abajo);
        double pxPorM = util / altura;
        double baseY = arriba + util;
        double anchoEdif = Math.max(70, Math.min(180, w * 0.22));
        double anchoSuelo = Math.min(w - 20, anchoEdif * 2.8);
        double x0 = w / 2;
        double factor = factorTotal();
        double ug = ResumenSimulacion.suelo(resultado, direccion)[paso];
        double dxSuelo = ug * factor * pxPorM;

        // Referencia punteada (posición inicial)
        g.setStroke(REFERENCIA);
        g.setLineWidth(1.2);
        g.setLineDashes(6, 4);
        g.strokeRect(x0 - anchoSuelo / 2, baseY, anchoSuelo, grosorSuelo);
        for (double cota : cotas) {
            double y = baseY - cota * pxPorM;
            g.strokeLine(x0 - anchoEdif / 2, y, x0 + anchoEdif / 2, y);
        }
        g.strokeLine(x0 - anchoEdif / 2, baseY, x0 - anchoEdif / 2, baseY - altura * pxPorM);
        g.strokeLine(x0 + anchoEdif / 2, baseY, x0 + anchoEdif / 2, baseY - altura * pxPorM);
        g.setLineDashes(null);

        // Suelo móvil
        g.setFill(SUELO);
        g.fillRect(x0 - anchoSuelo / 2 + dxSuelo, baseY, anchoSuelo, grosorSuelo);
        g.setStroke(SUELO_BORDE);
        g.setLineWidth(1.5);
        g.strokeRect(x0 - anchoSuelo / 2 + dxSuelo, baseY, anchoSuelo, grosorSuelo);
        g.setFill(SUELO_TEXTO);
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("Suelo (movimiento del terreno)", x0 + dxSuelo, baseY + grosorSuelo / 2 + 4);

        // Estructura en desplazamiento absoluto
        g.setLineCap(StrokeLineCap.BUTT);
        double xPrev = x0 + dxSuelo, yPrev = baseY;
        double semi = anchoEdif * 0.4;
        for (int i = 0; i < cotas.length; i++) {
            double xi = x0 + ResumenSimulacion.absoluto(resultado, direccion, paso, i) * factor * pxPorM;
            double yi = baseY - cotas[i] * pxPorM;
            g.setStroke(color(ResumenSimulacion.nivel(ResumenSimulacion.deriva(resultado, direccion, paso, i))));
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
        g.setFill(TEXTO);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.fillText("Estructura (desplazamiento absoluto)", 12, 20);
        g.setFont(Font.font("System", 12));
        g.fillText(textoExageracion(), 12, h - 6);
        g.setTextAlign(TextAlignment.RIGHT);
        double lx = w - 12;
        leyenda(g, lx, 14, DERIVA_BAJA, "deriva ≤ 60 % del límite");
        leyenda(g, lx, 30, DERIVA_ALERTA, "hasta el límite 0,00875");
        leyenda(g, lx, 46, DERIVA_EXCEDE, "supera el límite");
    }

    private void leyenda(GraphicsContext g, double xDerecha, double y, Color c, String texto) {
        g.setFont(Font.font("System", 11));
        g.setFill(TEXTO);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText(texto, xDerecha, y);
        g.setFill(c);
        g.fillRect(xDerecha - 130 - 14, y - 9, 10, 10);
    }

    /** Texto fijo que debe verse siempre: «Los desplazamientos están exagerados ×N». */
    public String textoExageracion() {
        return String.format(Locale.forLanguageTag("es-PE"), "Los desplazamientos están exagerados ×%,.0f", factorTotal());
    }
}
