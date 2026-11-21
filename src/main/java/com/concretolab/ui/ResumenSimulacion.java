package com.concretolab.ui;

import com.concretolab.service.AnalisisTiempoHistoria;
import com.concretolab.service.EscaladoRegistros;
import com.concretolab.service.FernandoHuancaAnalisisSismico.ModeloDinamico;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.RegistroSismico;
import com.concretolab.service.ResultadoTiempoHistoria;
import com.concretolab.service.ResumenTiempoHistoria;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Lógica pura (sin JavaFX) de la pestaña «Simulación temporal»: ejecución de la simulación, decimación de series,
 * escala visual automática, nivel de deriva y selección del periodo fundamental.
 */
public final class ResumenSimulacion {
    private ResumenSimulacion() {}

    /** Distorsión límite del tiempo-historia: 1,25 × 0,007 (art. 49.2). */
    public static final double LIMITE_DERIVA = 1.25 * 0.007;
    /** Amortiguamiento fijo (art. 48.5). */
    public static final double AMORTIGUAMIENTO = 0.05;
    /** Máximo de puntos por serie en las gráficas. */
    public static final int PUNTOS_MAXIMOS = 2000;
    /** Fracción de la altura del edificio que ocupa el mayor desplazamiento absoluto con la escala automática. */
    public static final double FRACCION_VISUAL = 0.175;
    /** Hasta este cociente deriva/límite el entrepiso se pinta verde; hasta 1,0 ámbar; más, rojo. */
    public static final double UMBRAL_VERDE = 0.6;

    /** Nivel de deriva de un entrepiso respecto al límite. */
    public enum NivelDeriva { BAJO, ALERTA, EXCEDE }

    /** Periodo fundamental para escalado y rejilla: el mayor entre los de X e Y (la rejilla 0,2T a 1,5T cubre ambas direcciones). */
    public static double periodoFundamental(double periodoX, double periodoY) {
        if (!(periodoX > 0) || !(periodoY > 0) || !Double.isFinite(periodoX) || !Double.isFinite(periodoY))
            throw new IllegalArgumentException("Los periodos fundamentales deben ser positivos.");
        return Math.max(periodoX, periodoY);
    }

    /** Nivel de una deriva (valor absoluto) respecto a {@link #LIMITE_DERIVA}. */
    public static NivelDeriva nivel(double deriva) {
        double c = Math.abs(deriva) / LIMITE_DERIVA;
        if (c <= UMBRAL_VERDE) return NivelDeriva.BAJO;
        return c <= 1.0 ? NivelDeriva.ALERTA : NivelDeriva.EXCEDE;
    }

    /**
     * Factor de exageración visual: el mayor desplazamiento absoluto ocupa {@link #FRACCION_VISUAL} de la altura.
     * Si no hay movimiento se devuelve 1.
     */
    public static double factorVisual(double desplazamientoMaximo, double alturaEdificio) {
        if (!(desplazamientoMaximo > 1e-12) || !(alturaEdificio > 0)) return 1.0;
        return FRACCION_VISUAL * alturaEdificio / desplazamientoMaximo;
    }

    /**
     * Reduce la serie a como máximo {@code maximo} puntos conservando siempre el primero, el último y el de mayor valor
     * absoluto de cada tramo (así el máximo absoluto global nunca se pierde).
     *
     * @return {x, y} decimados
     */
    public static double[][] decimar(double[] x, double[] y, int maximo) {
        if (x.length != y.length) throw new IllegalArgumentException("Las series x e y deben tener igual longitud.");
        if (maximo < 3) throw new IllegalArgumentException("El máximo de puntos debe ser al menos 3.");
        int n = x.length;
        if (n <= maximo) return new double[][]{x.clone(), y.clone()};
        int tramos = maximo - 2;
        List<Integer> idx = new ArrayList<>();
        idx.add(0);
        for (int b = 0; b < tramos; b++) {
            int lo = (int) ((long) b * n / tramos), hi = (int) ((long) (b + 1) * n / tramos);
            int mejor = lo;
            for (int i = lo; i < hi; i++) if (Math.abs(y[i]) > Math.abs(y[mejor])) mejor = i;
            if (mejor != idx.get(idx.size() - 1)) idx.add(mejor);
        }
        if (idx.get(idx.size() - 1) != n - 1) idx.add(n - 1);
        double[][] r = new double[2][idx.size()];
        for (int k = 0; k < idx.size(); k++) { r[0][k] = x[idx.get(k)]; r[1][k] = y[idx.get(k)]; }
        return r;
    }

    /** Mayor |desplazamiento absoluto| (suelo + relativo) de cualquier piso o del suelo, en la dirección (0 = X, 1 = Y), en m. */
    public static double desplazamientoAbsolutoMaximo(ResultadoTiempoHistoria r, int dir) {
        double m = 0;
        double[] suelo = suelo(r, dir);
        for (int p = 0; p < r.pasos(); p++) {
            m = Math.max(m, Math.abs(suelo[p]));
            for (int s = 0; s < r.pisos(); s++) m = Math.max(m, Math.abs(absoluto(r, dir, p, s)));
        }
        return m;
    }

    public static double[] suelo(ResultadoTiempoHistoria r, int dir) { return dir == 0 ? r.sueloX() : r.sueloY(); }

    public static double[] aceleracionSuelo(ResultadoTiempoHistoria r, int dir) { return dir == 0 ? r.aceleracionSueloX() : r.aceleracionSueloY(); }

    public static double relativo(ResultadoTiempoHistoria r, int dir, int paso, int piso) { return dir == 0 ? r.ux()[paso][piso] : r.uy()[paso][piso]; }

    public static double absoluto(ResultadoTiempoHistoria r, int dir, int paso, int piso) {
        return dir == 0 ? r.desplazamientoAbsolutoX(paso, piso) : r.desplazamientoAbsolutoY(paso, piso);
    }

    public static double deriva(ResultadoTiempoHistoria r, int dir, int paso, int piso) {
        return Math.abs(dir == 0 ? r.derivaX()[paso][piso] : r.derivaY()[paso][piso]);
    }

    /** Mayor deriva de entrepiso (valor absoluto) en un instante. */
    public static double derivaMaximaInstante(ResultadoTiempoHistoria r, int dir, int paso) {
        double m = 0;
        for (int s = 0; s < r.pisos(); s++) m = Math.max(m, deriva(r, dir, paso, s));
        return m;
    }

    /** Piso (0 = primero) con la mayor deriva máxima en la dirección. */
    public static int pisoCritico(ResultadoTiempoHistoria r, int dir) {
        double[] d = dir == 0 ? r.derivaMaximaPisoX() : r.derivaMaximaPisoY();
        int k = 0;
        for (int i = 1; i < d.length; i++) if (d[i] > d[k]) k = i;
        return k;
    }

    /** Serie del desplazamiento relativo (m) del último piso. */
    public static double[] techoRelativo(ResultadoTiempoHistoria r, int dir) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = relativo(r, dir, p, r.pisos() - 1);
        return s;
    }

    /** Serie del desplazamiento absoluto (m) del último piso. */
    public static double[] techoAbsoluto(ResultadoTiempoHistoria r, int dir) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = absoluto(r, dir, p, r.pisos() - 1);
        return s;
    }

    /** Serie de la deriva (valor absoluto) de un entrepiso. */
    public static double[] derivaPiso(ResultadoTiempoHistoria r, int dir, int piso) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = deriva(r, dir, p, piso);
        return s;
    }

    /**
     * Escala los registros, los simula todos sobre el modelo y verifica el art. 49. Es el cálculo que corre en segundo plano.
     *
     * @param espectroCompatible {@code true} para sintéticos compatibles, {@code false} para registros reales importados
     * @param progreso           recibe la fracción completada (0 a 1); puede ser {@code null}
     */
    public static ResumenTiempoHistoria simular(ModeloDinamico modelo, Parametros norma, List<RegistroSismico> registros,
                                                boolean espectroCompatible, double periodoFundamental, String origen,
                                                DoubleConsumer progreso) {
        if (registros == null || registros.isEmpty()) throw new IllegalArgumentException("No hay registros para simular.");
        DoubleConsumer p = progreso == null ? x -> { } : progreso;
        int n = registros.size();
        p.accept(0.0);
        var esc = EscaladoRegistros.escalar(registros, norma, periodoFundamental, espectroCompatible);
        p.accept(1.0 / (n + 2));
        List<ResultadoTiempoHistoria> res = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            res.add(AnalisisTiempoHistoria.resolver(modelo, esc.escalados().get(i), AMORTIGUAMIENTO));
            p.accept((i + 2.0) / (n + 2));
        }
        var ver = AnalisisTiempoHistoria.verificar(res, norma);
        p.accept(1.0);
        return new ResumenTiempoHistoria(origen, periodoFundamental, esc, List.copyOf(res), ver);
    }
}
