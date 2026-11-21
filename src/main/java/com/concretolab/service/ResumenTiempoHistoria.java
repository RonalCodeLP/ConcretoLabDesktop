package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resumen de una simulación tiempo-historia ya ejecutada (lo usan la pestaña «Simulación temporal» y el reporte PDF).
 *
 * @param origenRegistros    descripción del origen de los registros (sintéticos E.030 o importados)
 * @param periodoFundamental T usado para el escalado y la rejilla 0,2T a 1,5T (s)
 * @param escalado           resultado del escalado de los registros (art. 47)
 * @param resultados         un resultado tiempo-historia por registro escalado
 * @param verificacion       verificación del art. 49 (o 4.7.3 de la versión 2018)
 */
public record ResumenTiempoHistoria(String origenRegistros, double periodoFundamental, EscaladoRegistros.ResultadoEscalado escalado,
                                    List<ResultadoTiempoHistoria> resultados, VerificacionTiempoHistoria verificacion) {

    /** Cabecera de la tabla por registro. */
    public static final String[] CABECERA = {"Registro", "PGA (g)", "Factor de escala", "Techo máx. X (cm)", "Techo máx. Y (cm)",
            "Deriva máx. X", "Deriva máx. Y", "V basal máx. X (kN)", "V basal máx. Y (kN)"};

    private static String f(double v, int d) { return String.format(Locale.ROOT, "%." + d + "f", v); }

    /** PGA (g) del registro escalado {@code i}: mayor entre las dos componentes. */
    public double pgaG(int i) {
        RegistroSismico r = escalado.escalados().get(i);
        return Math.max(r.aceleracionMaxima(0), r.aceleracionMaxima(1)) / EscaladoRegistros.G;
    }

    /**
     * Filas de la tabla: una por registro, luego «Promedio» (o «Máximo» si el criterio es el máximo) y la fila del límite.
     */
    public List<String[]> filasTabla() {
        List<String[]> filas = new ArrayList<>();
        int n = resultados.size();
        boolean promedio = "promedio".equals(verificacion.criterio());
        double[][] col = new double[8][n];
        for (int i = 0; i < n; i++) {
            ResultadoTiempoHistoria r = resultados.get(i);
            double pga = i < escalado.escalados().size() ? pgaG(i) : Double.NaN;
            double fac = i < escalado.factores().length ? escalado.factores()[i] : Double.NaN;
            double[] v = {pga, fac, r.desplazamientoTechoMaximoX() * 100, r.desplazamientoTechoMaximoY() * 100,
                    r.derivaMaximaX(), r.derivaMaximaY(), r.cortanteBaseMaximoX(), r.cortanteBaseMaximoY()};
            for (int k = 0; k < 8; k++) col[k][i] = v[k];
            filas.add(fila(r.nombreRegistro(), v));
        }
        double[] agg = new double[8];
        for (int k = 0; k < 8; k++) {
            double s = 0, m = 0;
            for (double x : col[k]) { s += x; m = Math.max(m, x); }
            agg[k] = promedio ? s / Math.max(1, n) : m;
        }
        filas.add(fila(promedio ? "Promedio" : "Máximo", agg));
        String lim = f(verificacion.limite(), 5).replace('.', ',');
        filas.add(new String[]{"Límite 1,25×0,007 = " + lim, "", "", "", "", lim, lim, "", ""});
        return filas;
    }

    private static String[] fila(String nombre, double[] v) {
        return new String[]{nombre, f(v[0], 3), f(v[1], 3), f(v[2], 2), f(v[3], 2), f(v[4], 5), f(v[5], 5), f(v[6], 1), f(v[7], 1)};
    }

    /** Veredicto textual: CUMPLE, NO CUMPLE o NO CONCLUYENTE (menos conjuntos que el mínimo). */
    public String veredicto() {
        if (!verificacion.conjuntosSuficientes()) return "NO CONCLUYENTE";
        return verificacion.cumple() ? "CUMPLE" : "NO CUMPLE";
    }

    /** Advertencia legal que debe acompañar siempre a los resultados. */
    public static final String ADVERTENCIA_LEGAL = "Verificación complementaria: no sustituye al análisis estático ni al modal espectral "
            + "(arts. 46.1 y 32.3). Modelo lineal-elástico con R = 1 (conservador) y base empotrada.";
}
