package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Módulo de finura y verificación de husos granulométricos según NTP 400.037 / ASTM C33
 * (agregado fino y agregados gruesos tamaños Nº 57, 67 y 7).
 */
public final class HusosGranulometricos {

    private HusosGranulometricos() { }

    public enum Huso {
        FINO("Agregado fino (NTP 400.037 / ASTM C33)", new double[][] {
            {9.5, 100, 100}, {4.75, 95, 100}, {2.36, 80, 100}, {1.18, 50, 85}, {0.60, 25, 60}, {0.30, 5, 30}, {0.15, 0, 10}}),
        GRUESO_57("Grueso Nº 57 (25 a 4,75 mm)", new double[][] {
            {37.5, 100, 100}, {25, 95, 100}, {12.5, 25, 60}, {4.75, 0, 10}, {2.36, 0, 5}}),
        GRUESO_67("Grueso Nº 67 (19 a 4,75 mm)", new double[][] {
            {25, 100, 100}, {19, 90, 100}, {9.5, 20, 55}, {4.75, 0, 10}, {2.36, 0, 5}}),
        GRUESO_7("Grueso Nº 7 (12,5 a 4,75 mm)", new double[][] {
            {19, 100, 100}, {12.5, 90, 100}, {9.5, 40, 70}, {4.75, 0, 15}, {2.36, 0, 5}});

        private final String etiqueta;
        private final double[][] limites; // abertura mm, mínimo % pasa, máximo % pasa

        Huso(String etiqueta, double[][] limites) {
            this.etiqueta = etiqueta;
            this.limites = limites;
        }

        public double[][] limites() { return limites; }
        @Override public String toString() { return etiqueta; }
    }

    public record Chequeo(double aberturaMm, double pasa, double minimo, double maximo, boolean dentro) { }

    public record Resultado(Huso huso, List<Chequeo> chequeos, boolean cumple, Double moduloFinura, String mensaje) { }

    private static final double[] SERIE_MF = {0.15, 0.30, 0.60, 1.18, 2.36, 4.75, 9.5, 19.0, 37.5, 75.0, 150.0};

    /** Módulo de finura: suma de % retenido acumulado en la serie estándar dividido entre 100. */
    public static Double moduloFinura(List<GranulometriaService.Fila> filas) {
        double suma = 0;
        int encontrados = 0;
        for (double a : SERIE_MF) {
            for (var f : filas) {
                if (Math.abs(f.aberturaMm() - a) <= 0.02 * a + 0.005) {
                    suma += f.retenidoAcumulado();
                    encontrados++;
                    break;
                }
            }
        }
        // para agregado fino se exigen los tamices 0,15 a 4,75 mm
        return encontrados >= 6 ? suma / 100.0 : null;
    }

    public static Resultado verificar(Huso huso, List<GranulometriaService.Fila> filas) {
        List<Chequeo> cs = new ArrayList<>();
        boolean cumple = true;
        int faltantes = 0;
        for (double[] l : huso.limites()) {
            Double pasa = null;
            for (var f : filas) {
                if (Math.abs(f.aberturaMm() - l[0]) <= 0.02 * l[0] + 0.005) {
                    pasa = f.pasaPorcentaje();
                    break;
                }
            }
            if (pasa == null) {
                faltantes++;
                continue;
            }
            boolean dentro = pasa >= l[1] - 1e-9 && pasa <= l[2] + 1e-9;
            cumple &= dentro;
            cs.add(new Chequeo(l[0], pasa, l[1], l[2], dentro));
        }
        Double mf = huso == Huso.FINO ? moduloFinura(filas) : null;
        StringBuilder sb = new StringBuilder();
        if (cs.isEmpty()) {
            return new Resultado(huso, cs, false, mf, "Los tamices ingresados no coinciden con la serie del huso elegido.");
        }
        sb.append(cumple ? "Dentro del huso " : "FUERA del huso ").append(huso).append('.');
        if (faltantes > 0) {
            sb.append(" Faltan ").append(faltantes).append(" tamiz(ces) de la serie: la verificación es parcial.");
        }
        if (mf != null) {
            sb.append(String.format(Locale.ROOT, " Módulo de finura %.2f", mf));
            sb.append(mf >= 2.3 && mf <= 3.1 ? " (dentro de 2,3 – 3,1)." : " (fuera de 2,3 – 3,1 exigido por la NTP/ASTM C33).");
        }
        return new Resultado(huso, List.copyOf(cs), cumple && faltantes == 0, mf, sb.toString());
    }
}
