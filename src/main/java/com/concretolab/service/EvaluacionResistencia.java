package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluación de la resistencia a compresión con los criterios de aceptación de la E.060 (art. 5.6) / ACI 318:
 * (a) el promedio de cualquier grupo de 3 ensayos consecutivos es ≥ f'c, y
 * (b) ningún ensayo individual es menor que f'c − 3,5 MPa (f'c ≤ 35 MPa) o que 0,90 f'c (f'c &gt; 35 MPa).
 *
 * <p>Cada «ensayo» de la norma es el promedio de al menos dos probetas de la misma muestra, a la edad de
 * diseño (28 días). Si se registran probetas sueltas, el resultado es orientativo.</p>
 */
public final class EvaluacionResistencia {

    private EvaluacionResistencia() { }

    public record Resultado(int cantidad, double promedio, double minimo, double menorPromedioDeTres,
                            double limiteIndividual, Double desviacion, boolean cumplePromedios,
                            boolean cumpleIndividuales, boolean completo, String mensaje) {
        public boolean conforme() {
            return completo && cumplePromedios && cumpleIndividuales;
        }
    }

    public static Resultado yefriLermaEvaluar(double fc, List<Double> resultadosMpa) {
        if (!Double.isFinite(fc) || fc <= 0) {
            throw new IllegalArgumentException("f'c debe ser positiva.");
        }
        List<Double> r = new ArrayList<>();
        if (resultadosMpa != null) {
            for (Double v : resultadosMpa) {
                if (v != null && Double.isFinite(v) && v > 0) {
                    r.add(v);
                }
            }
        }
        int n = r.size();
        double limite = fc <= 35 ? fc - 3.5 : 0.9 * fc;
        if (n == 0) {
            return new Resultado(0, 0, 0, 0, limite, null, false, false, false, "Sin ensayos a 28 días para evaluar.");
        }
        double suma = 0;
        double min = Double.MAX_VALUE;
        for (double v : r) {
            suma += v;
            min = Math.min(min, v);
        }
        double prom = suma / n;
        double menorTres = Double.NaN;
        boolean okProm = true;
        for (int i = 0; i + 2 < n; i++) {
            double m = (r.get(i) + r.get(i + 1) + r.get(i + 2)) / 3.0;
            menorTres = Double.isNaN(menorTres) ? m : Math.min(menorTres, m);
            if (m < fc) {
                okProm = false;
            }
        }
        boolean okInd = min >= limite;
        Double s = null;
        if (n >= 2) {
            double q = 0;
            for (double v : r) {
                q += (v - prom) * (v - prom);
            }
            s = Math.sqrt(q / (n - 1));
        }
        boolean completo = n >= 3;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%d ensayo(s) a 28 días · promedio %.1f MPa · mínimo %.1f MPa (límite individual %.1f MPa)",
                n, prom, min, limite));
        if (completo) {
            sb.append(String.format(Locale.ROOT, " · menor promedio de 3 consecutivos %.1f MPa (debe ser ≥ %.1f).", menorTres, fc));
            sb.append(okProm && okInd ? " CUMPLE los criterios de la E.060." : " NO CUMPLE: revise las causas y la investigación de la E.060 (art. 5.6).");
        } else {
            sb.append(". Se necesitan al menos 3 ensayos para aplicar el criterio de promedios.");
            if (!okInd) {
                sb.append(" Algún ensayo ya está por debajo del límite individual.");
            }
        }
        return new Resultado(n, prom, min, completo ? menorTres : Double.NaN, limite, s, okProm, okInd, completo, sb.toString());
    }
}
