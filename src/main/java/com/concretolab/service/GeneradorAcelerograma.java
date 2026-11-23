package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Generador de acelerogramas sintéticos espectro-compatibles con el espectro de diseño de la E.030 con R = 1
 * (texto 2026, art. 47.3 y 47.6; versión 2018, art. 4.7).
 *
 * <p>Método por componente:
 * <ol>
 *   <li>Densidad espectral objetivo unilateral G(ω) = 4ζ/(πω)·(Sa(ω)/η)², con ζ = 5 % y η el factor de pico de Vanmarcke
 *       (relación entre pseudo-aceleración y desviación estándar de la respuesta de un oscilador excitado por ruido blanco).</li>
 *   <li>Proceso gaussiano estacionario por representación espectral: suma de armónicos en las frecuencias de la FFT,
 *       con amplitudes √(2·G·Δω) y fases uniformes aleatorias (se evalúa con FFT inversa).</li>
 *   <li>Envolvente de intensidad tipo Jennings–Housner: subida cuadrática (0–3 s), meseta (hasta 15 s) y decaimiento exponencial hasta 40 s.</li>
 *   <li>Corrección iterativa en el dominio de la frecuencia (máximo {@value #FERNANDO_HUANCA_MAX_ITERACIONES} iteraciones): el espectro de Fourier se multiplica
 *       por el cociente objetivo/espectro de respuesta al 5 % interpolado en frecuencia, hasta que el espectro de cada componente quede
 *       en 95,5–109,5 % del objetivo en 0,2T–1,5T (tolerancia exigida 95–110 %; se apunta al 102,5 % porque el máximo del oscilador tiende a quedar por debajo del objetivo). Las frecuencias menores de {@value #FERNANDO_HUANCA_FRECUENCIA_MINIMA} Hz se eliminan
 *       (línea base); además {@link RegistroSismico} aplica corrección de línea base al integrar.</li>
 * </ol>
 * Los componentes X e Y de cada conjunto usan fases independientes. El resultado es reproducible para la misma semilla.
 * Si alguna componente no converge, la razón mínima alcanzada se anota en {@code fuente}.
 */
public final class GeneradorAcelerograma {
    private GeneradorAcelerograma() {}

    public static final double FERNANDO_HUANCA_DT = 0.01;
    public static final double FERNANDO_HUANCA_DURACION = 40.0;
    public static final int FERNANDO_HUANCA_MAX_ITERACIONES = 25;
    public static final double FERNANDO_HUANCA_FRECUENCIA_MINIMA = 0.05;
    public static final String FERNANDO_HUANCA_FUENTE = "Acelerograma sintetico espectro-compatible generado por ConcretoLab (no es un registro real)";

    private static final int FERNANDO_HUANCA_PASOS = (int) Math.round(FERNANDO_HUANCA_DURACION / FERNANDO_HUANCA_DT) + 1; // 4001
    private static final int FERNANDO_HUANCA_N_FFT = 4096;
    private static final double FERNANDO_HUANCA_TOL_MIN = 0.955, FERNANDO_HUANCA_TOL_MAX = 1.095;    // criterio de parada interno (ligeramente dentro de la tolerancia declarada)
    private static final double FERNANDO_HUANCA_LIM_MIN = 0.95, FERNANDO_HUANCA_LIM_MAX = 1.10;      // tolerancia declarada
    private static final int FERNANDO_HUANCA_PUNTOS_CONTROL = 120;
    private static final double FERNANDO_HUANCA_SESGO = 1.025;

    /** Genera {@code cantidad} conjuntos (cada uno con componentes X e Y independientes) compatibles con el espectro de diseño con R = 1. */
    public static List<RegistroSismico> fernandoHuancaGenerarConjuntos(NormaE030.Parametros norma, int cantidad, double periodoFundamental, long semilla) {
        if (norma == null) throw new IllegalArgumentException("Faltan los parámetros de la norma E.030.");
        if (cantidad < 1 || cantidad > 50) throw new IllegalArgumentException("La cantidad de conjuntos debe estar entre 1 y 50.");
        if (!(periodoFundamental > 0) || !Double.isFinite(periodoFundamental)) throw new IllegalArgumentException("El periodo fundamental debe ser positivo.");
        NormaE030.Parametros r1 = EscaladoRegistros.fernandoHuancaConR1(norma);
        double lo = 0.2 * periodoFundamental, hi = 1.5 * periodoFundamental;
        double[] control = EspectroRespuesta.fernandoHuancaRejillaPeriodos(Math.max(0.025, lo / 1.6), Math.max(hi * 1.6, 0.05), FERNANDO_HUANCA_PUNTOS_CONTROL);
        double[] objetivo = EscaladoRegistros.fernandoHuancaEspectroDiseno(norma, control);
        boolean[] verifica = new boolean[control.length];
        for (int j = 0; j < control.length; j++) verifica[j] = control[j] >= lo * (1 - 1e-9) && control[j] <= hi * (1 + 1e-9);
        double[] envolvente = fernandoHuancaEnvolvente();
        String suelo = r1.suelo().name().replace("_2018", "");
        List<RegistroSismico> res = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            double[][] comp = new double[2][];
            double razonMin = Double.POSITIVE_INFINITY;
            boolean convergio = true;
            for (int c = 0; c < 2; c++) {
                SplittableRandom rnd = new SplittableRandom(semilla ^ (0x9E3779B97F4A7C15L * (2L * i + c + 1)));
                double[] diag = new double[2];
                comp[c] = fernandoHuancaComponente(rnd, control, objetivo, verifica, envolvente, diag);
                razonMin = Math.min(razonMin, diag[0]);
                if (diag[0] < FERNANDO_HUANCA_LIM_MIN || diag[1] > FERNANDO_HUANCA_LIM_MAX) convergio = false;
            }
            String nombre = "Sintetico " + (i + 1) + " (E.030 Z=" + String.format(Locale.ROOT, "%.2f", r1.z()).replace('.', ',') + " " + suelo + ")";
            String fuente = FERNANDO_HUANCA_FUENTE;
            if (!convergio)
                fuente += ". Aviso: no convergió al 95–110 % del espectro objetivo en " + FERNANDO_HUANCA_MAX_ITERACIONES + " iteraciones (razón mínima "
                        + String.format(Locale.ROOT, "%.2f", razonMin).replace('.', ',') + ").";
            res.add(new RegistroSismico(nombre, fuente, FERNANDO_HUANCA_DT, comp[0], comp[1]));
        }
        return res;
    }

    private static double[] fernandoHuancaEnvolvente() {
        double[] w = new double[FERNANDO_HUANCA_PASOS];
        double t1 = 3.0, t2 = 15.0, c = 0.2;
        for (int n = 0; n < FERNANDO_HUANCA_PASOS; n++) {
            double t = n * FERNANDO_HUANCA_DT;
            w[n] = t < t1 ? (t / t1) * (t / t1) : t <= t2 ? 1.0 : Math.exp(-c * (t - t2));
        }
        return w;
    }

    /** Genera una componente; {@code diag} = {razón mínima, razón máxima} del espectro final frente al objetivo en 0,2T–1,5T. */
    private static double[] fernandoHuancaComponente(SplittableRandom rnd, double[] control, double[] objetivo, boolean[] verifica, double[] env, double[] diag) {
        int nf = FERNANDO_HUANCA_N_FFT;
        double df = 1.0 / (nf * FERNANDO_HUANCA_DT), dOmega = 2 * Math.PI * df;
        double[] re = new double[nf], im = new double[nf];
        double ts = 15.0; // duración de la fase intensa para el factor de pico
        for (int k = 1; k < nf / 2; k++) {
            double f = k * df;
            if (f < FERNANDO_HUANCA_FRECUENCIA_MINIMA) continue;
            double w = 2 * Math.PI * f;
            double sa = fernandoHuancaInterpolarObjetivo(control, objetivo, 1.0 / f);
            double eta = Math.max(2.0, Math.sqrt(2 * Math.log(Math.max(2 * f * ts, 3.0))));
            double g = 4 * 0.05 / (Math.PI * w) * (sa / eta) * (sa / eta);
            double amp = Math.sqrt(2 * g * dOmega);
            double fase = 2 * Math.PI * rnd.nextDouble();
            double cr = nf * amp / 2 * Math.cos(fase), ci = nf * amp / 2 * Math.sin(fase);
            re[k] = cr; im[k] = ci; re[nf - k] = cr; im[nf - k] = -ci;
        }
        fernandoHuancaFft(re, im, true);
        double[] x = new double[nf];
        for (int n = 0; n < FERNANDO_HUANCA_PASOS; n++) x[n] = re[n] * env[n];

        double[] lnFrecControl = new double[control.length]; // ascendente
        for (int j = 0; j < control.length; j++) lnFrecControl[j] = -Math.log(control[control.length - 1 - j]);
        double[] mejor = null;
        double mejorDesv = Double.POSITIVE_INFINITY, mejorMin = 0, mejorMax = 0;
        double[] a = new double[FERNANDO_HUANCA_PASOS];
        for (int it = 0; it <= FERNANDO_HUANCA_MAX_ITERACIONES; it++) {
            System.arraycopy(x, 0, a, 0, FERNANDO_HUANCA_PASOS);
            double[] sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(a, FERNANDO_HUANCA_DT, control, EscaladoRegistros.FERNANDO_HUANCA_AMORTIGUAMIENTO);
            double mn = Double.POSITIVE_INFINITY, mx = 0;
            double[] ratio = new double[control.length];
            for (int j = 0; j < control.length; j++) {
                double r = objetivo[j] / Math.max(sa[j], 1e-12);
                ratio[j] = FERNANDO_HUANCA_SESGO * r; // se apunta al centro de la banda 95-110 %
                if (verifica[j]) { double q = 1.0 / r; mn = Math.min(mn, q); mx = Math.max(mx, q); }
            }
            double desv = Math.max(FERNANDO_HUANCA_SESGO - mn, mx - FERNANDO_HUANCA_SESGO);
            if (desv < mejorDesv) { mejorDesv = desv; mejor = a.clone(); mejorMin = mn; mejorMax = mx; }
            if ((mn >= FERNANDO_HUANCA_TOL_MIN && mx <= FERNANDO_HUANCA_TOL_MAX) || it == FERNANDO_HUANCA_MAX_ITERACIONES) break;
            // corrección en frecuencia: la razón objetivo/respuesta se aplica a las amplitudes de Fourier
            double[] gr = new double[control.length]; // ordenado por frecuencia ascendente
            for (int j = 0; j < control.length; j++) gr[j] = Math.pow(Math.min(5.0, Math.max(0.2, ratio[control.length - 1 - j])), 1.0);
            double[] xr = new double[nf], xi = new double[nf];
            System.arraycopy(x, 0, xr, 0, nf);
            fernandoHuancaFft(xr, xi, false);
            xr[0] = 0; xi[0] = 0;
            for (int k = 1; k <= nf / 2; k++) {
                double f = k * df;
                double g = f < FERNANDO_HUANCA_FRECUENCIA_MINIMA ? 0.0 : fernandoHuancaInterpolarGanancia(lnFrecControl, gr, Math.log(f));
                xr[k] *= g; xi[k] *= g;
                if (k < nf / 2) { xr[nf - k] *= g; xi[nf - k] *= g; }
            }
            fernandoHuancaFft(xr, xi, true);
            double media = 0;
            for (int n = 0; n < FERNANDO_HUANCA_PASOS; n++) media += xr[n];
            media /= FERNANDO_HUANCA_PASOS;
            for (int n = 0; n < nf; n++) x[n] = n < FERNANDO_HUANCA_PASOS ? xr[n] - media : 0.0;
        }
        diag[0] = mejorMin; diag[1] = mejorMax;
        return mejor;
    }

    private static double fernandoHuancaInterpolarGanancia(double[] lnF, double[] g, double lnf) {
        if (lnf <= lnF[0] || lnf >= lnF[lnF.length - 1]) return 1.0;
        int lo = 0, hi = lnF.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (lnF[mid] <= lnf) lo = mid; else hi = mid; }
        double t = (lnf - lnF[lo]) / (lnF[hi] - lnF[lo]);
        return g[lo] + t * (g[hi] - g[lo]);
    }

    /** Espectro objetivo (m/s²) para cualquier periodo, interpolado logarítmicamente de la rejilla de control (constante fuera de ella). */
    private static double fernandoHuancaInterpolarObjetivo(double[] periodos, double[] sa, double t) {
        if (t <= periodos[0]) return sa[0];
        if (t >= periodos[periodos.length - 1]) return sa[sa.length - 1] * Math.pow(periodos[periodos.length - 1] / t, 1.0); // cola ∝ 1/T
        int lo = 0, hi = periodos.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (periodos[mid] <= t) lo = mid; else hi = mid; }
        double u = (Math.log(t) - Math.log(periodos[lo])) / (Math.log(periodos[hi]) - Math.log(periodos[lo]));
        return sa[lo] + u * (sa[hi] - sa[lo]);
    }

    /** FFT radix-2 in situ; la inversa incluye el factor 1/N. */
    static void fernandoHuancaFft(double[] re, double[] im, boolean inversa) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inversa ? 1 : -1);
            double wlr = Math.cos(ang), wli = Math.sin(ang);
            int mitad = len >> 1;
            for (int i = 0; i < n; i += len) {
                double wr = 1, wi = 0;
                for (int j = 0; j < mitad; j++) {
                    int p = i + j, q = p + mitad;
                    double vr = re[q] * wr - im[q] * wi, vi = re[q] * wi + im[q] * wr;
                    re[q] = re[p] - vr; im[q] = im[p] - vi;
                    re[p] += vr; im[p] += vi;
                    double nwr = wr * wlr - wi * wli;
                    wi = wr * wli + wi * wlr; wr = nwr;
                }
            }
        }
        if (inversa) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}
