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

public final class ResumenSimulacion {
    private ResumenSimulacion() {}

    public static final double WALTER_QUISPESAYHUA_LIMITE_DERIVA = 1.25 * 0.007;
    public static final double WALTER_QUISPESAYHUA_AMORTIGUAMIENTO = 0.05;
    public static final int WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS = 2000;
    public static final double WALTER_QUISPESAYHUA_FRACCION_VISUAL = 0.175;
    public static final double WALTER_QUISPESAYHUA_UMBRAL_VERDE = 0.6;

    public enum NivelDeriva { BAJO, ALERTA, EXCEDE }

    public static double walterQuispesayhuaPeriodoFundamental(double periodoX, double periodoY) {
        if (!(periodoX > 0) || !(periodoY > 0) || !Double.isFinite(periodoX) || !Double.isFinite(periodoY))
            throw new IllegalArgumentException("Los periodos fundamentales deben ser positivos.");
        return Math.max(periodoX, periodoY);
    }

    public static NivelDeriva walterQuispesayhuaNivel(double deriva) {
        double c = Math.abs(deriva) / WALTER_QUISPESAYHUA_LIMITE_DERIVA;
        if (c <= WALTER_QUISPESAYHUA_UMBRAL_VERDE) return NivelDeriva.BAJO;
        return c <= 1.0 ? NivelDeriva.ALERTA : NivelDeriva.EXCEDE;
    }

    public static double walterQuispesayhuaFactorVisual(double desplazamientoMaximo, double alturaEdificio) {
        if (!(desplazamientoMaximo > 1e-12) || !(alturaEdificio > 0)) return 1.0;
        return WALTER_QUISPESAYHUA_FRACCION_VISUAL * alturaEdificio / desplazamientoMaximo;
    }

    public static double[][] walterQuispesayhuaDecimar(double[] x, double[] y, int maximo) {
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

    public static double walterQuispesayhuaDesplazamientoAbsolutoMaximo(ResultadoTiempoHistoria r, int dir) {
        double m = 0;
        double[] suelo = walterQuispesayhuaSuelo(r, dir);
        for (int p = 0; p < r.pasos(); p++) {
            m = Math.max(m, Math.abs(suelo[p]));
            for (int s = 0; s < r.pisos(); s++) m = Math.max(m, Math.abs(walterQuispesayhuaAbsoluto(r, dir, p, s)));
        }
        return m;
    }

    public static double[] walterQuispesayhuaSuelo(ResultadoTiempoHistoria r, int dir) { return dir == 0 ? r.sueloX() : r.sueloY(); }

    public static double[] walterQuispesayhuaAceleracionSuelo(ResultadoTiempoHistoria r, int dir) { return dir == 0 ? r.aceleracionSueloX() : r.aceleracionSueloY(); }

    public static double walterQuispesayhuaRelativo(ResultadoTiempoHistoria r, int dir, int paso, int piso) { return dir == 0 ? r.ux()[paso][piso] : r.uy()[paso][piso]; }

    public static double walterQuispesayhuaAbsoluto(ResultadoTiempoHistoria r, int dir, int paso, int piso) {
        return dir == 0 ? r.desplazamientoAbsolutoX(paso, piso) : r.desplazamientoAbsolutoY(paso, piso);
    }

    public static double walterQuispesayhuaDeriva(ResultadoTiempoHistoria r, int dir, int paso, int piso) {
        return Math.abs(dir == 0 ? r.derivaX()[paso][piso] : r.derivaY()[paso][piso]);
    }

    public static double walterQuispesayhuaDerivaMaximaInstante(ResultadoTiempoHistoria r, int dir, int paso) {
        double m = 0;
        for (int s = 0; s < r.pisos(); s++) m = Math.max(m, walterQuispesayhuaDeriva(r, dir, paso, s));
        return m;
    }

    public static int walterQuispesayhuaPisoCritico(ResultadoTiempoHistoria r, int dir) {
        double[] d = dir == 0 ? r.derivaMaximaPisoX() : r.derivaMaximaPisoY();
        int k = 0;
        for (int i = 1; i < d.length; i++) if (d[i] > d[k]) k = i;
        return k;
    }

    public static double[] walterQuispesayhuaTechoRelativo(ResultadoTiempoHistoria r, int dir) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = walterQuispesayhuaRelativo(r, dir, p, r.pisos() - 1);
        return s;
    }

    public static double[] walterQuispesayhuaTechoAbsoluto(ResultadoTiempoHistoria r, int dir) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = walterQuispesayhuaAbsoluto(r, dir, p, r.pisos() - 1);
        return s;
    }

    public static double[] walterQuispesayhuaDerivaPiso(ResultadoTiempoHistoria r, int dir, int piso) {
        double[] s = new double[r.pasos()];
        for (int p = 0; p < s.length; p++) s[p] = walterQuispesayhuaDeriva(r, dir, p, piso);
        return s;
    }

    public static ResumenTiempoHistoria walterQuispesayhuaSimular(ModeloDinamico modelo, Parametros norma, List<RegistroSismico> registros,
                                                boolean espectroCompatible, double periodoFundamental, String origen,
                                                DoubleConsumer progreso) {
        if (registros == null || registros.isEmpty()) throw new IllegalArgumentException("No hay registros para simular.");
        DoubleConsumer p = progreso == null ? x -> { } : progreso;
        int n = registros.size();
        p.accept(0.0);
        var esc = EscaladoRegistros.fernandoHuancaEscalar(registros, norma, periodoFundamental, espectroCompatible);
        p.accept(1.0 / (n + 2));
        List<ResultadoTiempoHistoria> res = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            res.add(AnalisisTiempoHistoria.fernandoHuancaResolver(modelo, esc.escalados().get(i), WALTER_QUISPESAYHUA_AMORTIGUAMIENTO));
            p.accept((i + 2.0) / (n + 2));
        }
        var ver = AnalisisTiempoHistoria.fernandoHuancaVerificar(res, norma);
        p.accept(1.0);
        return new ResumenTiempoHistoria(origen, periodoFundamental, esc, List.copyOf(res), ver);
    }
}
