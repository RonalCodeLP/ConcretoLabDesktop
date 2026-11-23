package com.concretolab.service;

/**
 * Espectro de pseudo-aceleraciones de un acelerograma (E.030 texto 2026, art. 47.4). Se resuelve un oscilador de 1 GDL
 * amortiguado excitado en su base con el algoritmo exacto para excitación lineal por tramos (Nair-Jennings; coeficientes
 * según Chopra, «Dynamics of Structures», Tabla 5.2.1). La pseudo-aceleración es Sa = ω²·máx|u|, con u el desplazamiento relativo.
 */
public final class EspectroRespuesta {
    private EspectroRespuesta() {}

    /** Periodo (s) por debajo del cual el oscilador es rígido y Sa se toma igual a la aceleración pico del terreno. */
    public static final double FERNANDO_HUANCA_PERIODO_RIGIDO = 0.02;

    /**
     * Pseudo-aceleración (m/s²) para cada periodo.
     *
     * @param aceleracionSuelo aceleración del terreno (cualquier unidad; Sa sale en la misma unidad)
     * @param dt paso de tiempo del registro (s)
     * @param periodos periodos (s) donde se evalúa
     * @param amortiguamiento fracción del amortiguamiento crítico (0,05 = 5 %)
     */
    public static double[] fernandoHuancaPseudoAceleracion(double[] aceleracionSuelo, double dt, double[] periodos, double amortiguamiento) {
        if (aceleracionSuelo == null || aceleracionSuelo.length < 2) throw new IllegalArgumentException("El registro necesita al menos 2 puntos.");
        if (!(dt > 0) || !Double.isFinite(dt)) throw new IllegalArgumentException("dt debe ser positivo.");
        if (!(amortiguamiento > 0 && amortiguamiento < 1)) throw new IllegalArgumentException("El amortiguamiento debe estar entre 0 y 1 (exclusivo).");
        double pga = 0;
        for (double a : aceleracionSuelo) pga = Math.max(pga, Math.abs(a));
        double[] sa = new double[periodos.length];
        for (int j = 0; j < periodos.length; j++) {
            double t = periodos[j];
            if (!(t > 0)) throw new IllegalArgumentException("Los periodos deben ser positivos.");
            sa[j] = t <= FERNANDO_HUANCA_PERIODO_RIGIDO ? pga : fernandoHuancaSaUnPeriodo(aceleracionSuelo, dt, t, amortiguamiento);
        }
        return sa;
    }

    private static double fernandoHuancaSaUnPeriodo(double[] ag, double dt, double t, double xi) {
        double w = 2 * Math.PI / t;
        double k = w * w;
        // Si el registro es grueso respecto al periodo se interpola linealmente a ~T/20 (el método es exacto para esa señal lineal por tramos).
        int sub = 1;
        if (dt > t / 10) sub = (int) Math.min(64, Math.ceil(dt / (t / 20)));
        double h = dt / sub;
        double sq = Math.sqrt(1 - xi * xi), wd = w * sq, e = Math.exp(-xi * w * h);
        double sn = Math.sin(wd * h), cs = Math.cos(wd * h);
        double xw = 2 * xi / (w * h);
        double a11 = e * (xi / sq * sn + cs);
        double a12 = e / wd * sn;
        double b1 = (xw + e * (((1 - 2 * xi * xi) / (wd * h) - xi / sq) * sn - (1 + xw) * cs)) / k;
        double c1 = (1 - xw + e * ((2 * xi * xi - 1) / (wd * h) * sn + xw * cs)) / k;
        double a21 = -e * (w / sq) * sn;
        double a22 = e * (cs - xi / sq * sn);
        double b2 = (-1 / h + e * ((w / sq + xi / (h * sq)) * sn + cs / h)) / k;
        double c2 = (1 - e * (xi / sq * sn + cs)) / (k * h);
        double u = 0, v = 0, umax = 0;
        double pPrev = -ag[0];
        for (int i = 0; i < ag.length - 1; i++) {
            double g0 = ag[i], dg = ag[i + 1] - g0;
            for (int m = 1; m <= sub; m++) {
                double pNext = -(g0 + dg * m / sub);
                double un = a11 * u + a12 * v + b1 * pPrev + c1 * pNext;
                double vn = a21 * u + a22 * v + b2 * pPrev + c2 * pNext;
                u = un; v = vn; pPrev = pNext;
                double au = Math.abs(u);
                if (au > umax) umax = au;
            }
        }
        return k * umax;
    }

    /** {@code n} periodos con espaciamiento logarítmico entre {@code tMin} y {@code tMax} (ambos incluidos). */
    public static double[] fernandoHuancaRejillaPeriodos(double tMin, double tMax, int n) {
        if (!(tMin > 0) || !(tMax > tMin) || n < 2) throw new IllegalArgumentException("La rejilla requiere 0 < tMin < tMax y al menos 2 puntos.");
        double[] p = new double[n];
        double l0 = Math.log(tMin), l1 = Math.log(tMax);
        for (int i = 0; i < n; i++) p[i] = Math.exp(l0 + (l1 - l0) * i / (n - 1));
        p[0] = tMin; p[n - 1] = tMax;
        return p;
    }
}
