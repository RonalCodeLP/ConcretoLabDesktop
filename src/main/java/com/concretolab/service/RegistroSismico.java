package com.concretolab.service;

import java.util.Locale;

/**
 * Registro sísmico (acelerograma) de dos componentes horizontales ortogonales para el análisis tiempo-historia
 * (E.030 texto 2026, arts. 46 a 49; versión 2018, art. 4.7). Las aceleraciones están en m/s² y se muestrean con paso
 * {@code dt} constante. El registro es inmutable: los arreglos se copian al construir y al consultarlos.
 *
 * <p>Integración y corrección de línea base (usada por {@link #velocidad(int)} y {@link #desplazamiento(int)}):
 * <ol>
 *   <li>Se resta de la aceleración su valor medio (integral trapezoidal dividida entre la duración), de modo que la velocidad final es nula.</li>
 *   <li>La velocidad se obtiene por integración trapezoidal.</li>
 *   <li>Se resta de la velocidad un polinomio de grado 2 sin término independiente, {@code p(t) = α·t + β·t²}, con α y β
 *       tales que la velocidad corregida termina en cero y su integral (el desplazamiento final) también es cero.
 *       Es equivalente a restar de la aceleración una recta, que es la corrección clásica de línea base con extremos fijos.</li>
 *   <li>El desplazamiento se obtiene integrando (trapecios) la velocidad corregida.</li>
 * </ol>
 * Un registro cuyas integrales ya son nulas casi no se modifica; una deriva lineal en la aceleración se elimina por completo.
 */
public record RegistroSismico(String nombre, String fuente, double dt, double[] ax, double[] ay) {

    /** Paso de tiempo mínimo admitido (s). */
    public static final double DT_MINIMO = 0.001;
    /** Paso de tiempo máximo admitido (s). */
    public static final double DT_MAXIMO = 0.1;
    /** Número máximo de pasos por componente. */
    public static final int PASOS_MAXIMOS = 200_000;

    public RegistroSismico {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El registro sísmico debe tener un nombre.");
        if (fuente == null) fuente = "";
        if (!Double.isFinite(dt) || dt < DT_MINIMO || dt > DT_MAXIMO)
            throw new IllegalArgumentException("El paso de tiempo dt debe estar entre " + DT_MINIMO + " y " + DT_MAXIMO + " s (valor recibido: " + dt + ").");
        if (ax == null || ay == null) throw new IllegalArgumentException("Faltan las aceleraciones de alguna componente (X e Y son obligatorias).");
        if (ax.length != ay.length)
            throw new IllegalArgumentException("Las componentes X e Y deben tener el mismo número de pasos (X: " + ax.length + ", Y: " + ay.length + ").");
        if (ax.length < 2) throw new IllegalArgumentException("El registro necesita al menos 2 pasos de tiempo.");
        if (ax.length > PASOS_MAXIMOS)
            throw new IllegalArgumentException("El registro tiene " + ax.length + " pasos; el máximo admitido es " + PASOS_MAXIMOS + ".");
        for (int i = 0; i < ax.length; i++)
            if (!Double.isFinite(ax[i]) || !Double.isFinite(ay[i]))
                throw new IllegalArgumentException("La aceleración del paso " + (i + 1) + " no es un número finito.");
        ax = ax.clone();
        ay = ay.clone();
    }

    /** Copia defensiva de la componente X (m/s²). */
    @Override public double[] ax() { return ax.clone(); }

    /** Copia defensiva de la componente Y (m/s²). */
    @Override public double[] ay() { return ay.clone(); }

    /** Número de pasos de tiempo por componente. */
    public int pasos() { return ax.length; }

    /** Duración del registro: (pasos − 1)·dt, en segundos. */
    public double duracion() { return (ax.length - 1) * dt; }

    /** Aceleración pico absoluta (m/s²) de la componente 0 (X) o 1 (Y). */
    public double aceleracionMaxima(int componente) {
        double max = 0;
        for (double v : serie(componente)) max = Math.max(max, Math.abs(v));
        return max;
    }

    /** Velocidad (m/s) con línea base corregida; componente 0 = X, 1 = Y. Véase la descripción de la clase. */
    public double[] velocidad(int componente) { return integrar(serie(componente), dt)[0]; }

    /** Desplazamiento (m) con línea base corregida; componente 0 = X, 1 = Y. Véase la descripción de la clase. */
    public double[] desplazamiento(int componente) { return integrar(serie(componente), dt)[1]; }

    /** Copia con ambas componentes multiplicadas por el mismo factor (art. 47.5); el nombre indica el factor aplicado. */
    public RegistroSismico escalado(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) throw new IllegalArgumentException("El factor de escala debe ser un número positivo (recibido: " + factor + ").");
        double[] x = ax.clone(), y = ay.clone();
        for (int i = 0; i < x.length; i++) { x[i] *= factor; y[i] *= factor; }
        String f = String.format(Locale.ROOT, "%.3f", factor).replace('.', ',');
        return new RegistroSismico(nombre + " (x" + f + ")", fuente, dt, x, y);
    }

    private double[] serie(int componente) {
        if (componente != 0 && componente != 1) throw new IllegalArgumentException("La componente debe ser 0 (X) o 1 (Y).");
        return componente == 0 ? ax : ay;
    }

    /** Devuelve {velocidad corregida, desplazamiento corregido}. */
    static double[][] integrar(double[] a, double dt) {
        int n = a.length;
        double duracion = (n - 1) * dt;
        // 1) media trapezoidal
        double suma = 0;
        for (int i = 0; i < n - 1; i++) suma += 0.5 * (a[i] + a[i + 1]) * dt;
        double media = suma / duracion;
        // 2) velocidad e integral de la velocidad (desplazamiento sin corregir)
        double[] v = new double[n];
        for (int i = 1; i < n; i++) v[i] = v[i - 1] + 0.5 * ((a[i - 1] - media) + (a[i] - media)) * dt;
        double vFinal = v[n - 1];
        double dFinal = 0;
        for (int i = 1; i < n; i++) dFinal += 0.5 * (v[i - 1] + v[i]) * dt;
        // 3) p(t) = α t + β t²: p(T) = vFinal, ∫p = dFinal (integral trapezoidal discreta, coherente con el desplazamiento)
        double i1 = 0, i2 = 0;
        for (int i = 1; i < n; i++) {
            double t0 = (i - 1) * dt, t1 = i * dt;
            i1 += 0.5 * (t0 + t1) * dt;
            i2 += 0.5 * (t0 * t0 + t1 * t1) * dt;
        }
        double m11 = duracion, m12 = duracion * duracion, m21 = i1, m22 = i2;
        double det = m11 * m22 - m12 * m21;
        double alfa = (vFinal * m22 - m12 * dFinal) / det;
        double beta = (m11 * dFinal - m21 * vFinal) / det;
        double[] vc = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i * dt;
            vc[i] = v[i] - (alfa * t + beta * t * t);
        }
        vc[n - 1] = 0.0; // exacto por construcción; elimina el error de redondeo
        double[] d = new double[n];
        for (int i = 1; i < n; i++) d[i] = d[i - 1] + 0.5 * (vc[i - 1] + vc[i]) * dt;
        return new double[][]{vc, d};
    }
}
