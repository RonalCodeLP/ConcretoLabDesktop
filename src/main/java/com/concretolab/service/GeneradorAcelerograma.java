package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

public final class GeneradorAcelerograma {
    private GeneradorAcelerograma() {}

    public static final double FERNANDO_HUANCA_DT = 0.01;
    public static final double FERNANDO_HUANCA_DURACION = 40.0;
    public static final int FERNANDO_HUANCA_MAX_ITERACIONES = 25;
    public static final double FERNANDO_HUANCA_FRECUENCIA_MINIMA = 0.05;
    public static final String FERNANDO_HUANCA_FUENTE = "Acelerograma sintetico espectro-compatible generado por ConcretoLab (no es un registro real)";

    private static final int FERNANDO_HUANCA_PASOS = (int) Math.round(FERNANDO_HUANCA_DURACION / FERNANDO_HUANCA_DT) + 1;
    private static final int FERNANDO_HUANCA_N_FFT = 4096;
    private static final double FERNANDO_HUANCA_TOL_MIN = 0.955, FERNANDO_HUANCA_TOL_MAX = 1.095;
    private static final double FERNANDO_HUANCA_LIM_MIN = 0.95, FERNANDO_HUANCA_LIM_MAX = 1.10;
    private static final int FERNANDO_HUANCA_PUNTOS_CONTROL = 120;
    private static final double FERNANDO_HUANCA_SESGO = 1.025;

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

    private static double[] fernandoHuancaComponente(SplittableRandom rnd, double[] control, double[] objetivo, boolean[] verifica, double[] env, double[] diag) {
        int nf = FERNANDO_HUANCA_N_FFT;
        double df = 1.0 / (nf * FERNANDO_HUANCA_DT), dOmega = 2 * Math.PI * df;
        double[] re = new double[nf], im = new double[nf];
        double ts = 15.0;
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

        double[] lnFrecControl = new double[control.length];
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
                ratio[j] = FERNANDO_HUANCA_SESGO * r;
                if (verifica[j]) { double q = 1.0 / r; mn = Math.min(mn, q); mx = Math.max(mx, q); }
            }
            double desv = Math.max(FERNANDO_HUANCA_SESGO - mn, mx - FERNANDO_HUANCA_SESGO);
            if (desv < mejorDesv) { mejorDesv = desv; mejor = a.clone(); mejorMin = mn; mejorMax = mx; }
            if ((mn >= FERNANDO_HUANCA_TOL_MIN && mx <= FERNANDO_HUANCA_TOL_MAX) || it == FERNANDO_HUANCA_MAX_ITERACIONES) break;
            double[] gr = new double[control.length];
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

    private static double fernandoHuancaInterpolarObjetivo(double[] periodos, double[] sa, double t) {
        if (t <= periodos[0]) return sa[0];
        if (t >= periodos[periodos.length - 1]) return sa[sa.length - 1] * Math.pow(periodos[periodos.length - 1] / t, 1.0);
        int lo = 0, hi = periodos.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (periodos[mid] <= t) lo = mid; else hi = mid; }
        double u = (Math.log(t) - Math.log(periodos[lo])) / (Math.log(periodos[hi]) - Math.log(periodos[lo]));
        return sa[lo] + u * (sa[hi] - sa[lo]);
    }

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
