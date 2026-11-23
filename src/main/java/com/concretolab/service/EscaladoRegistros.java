package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Escalado de conjuntos de registros sísmicos al espectro de diseño con R = 1 (E.030 texto 2026, arts. 46 a 49;
 * versión 2018, art. 4.7). Criterio implementado:
 * <ol>
 *   <li>Para cada conjunto (par de componentes ortogonales) se calculan Sa_x y Sa_y con 5 % de amortiguamiento y su
 *       SRSS = √(Sa_x² + Sa_y²) (art. 47.4), en una rejilla logarítmica de periodos entre 0,2·T y 1,5·T (art. 47.5).</li>
 *   <li>Factor individual f_i: hace que el promedio en la rejilla de SRSS_i / Sa_diseño(R = 1) sea igual a 1.</li>
 *   <li>Se aplica el mismo f_i a las dos componentes del par (art. 47.5).</li>
 *   <li>Factor común adicional k ≥ 1 para todos los conjuntos, tal que el promedio de los SRSS escalados sea al menos el 100 %
 *       del espectro de diseño con R = 1 en todos los periodos de la rejilla (arts. 47.5 y 47.6). El factor devuelto es f_i·k.</li>
 *   <li>{@code razonMinima} = mínimo en la rejilla de (promedio SRSS escalado) / (espectro de diseño).</li>
 *   <li>Se verifica además el art. 47.6 (en cada registro y dirección, Sa escalado ≥ 90 % del espectro de diseño); si no se cumple
 *       —lo normal con registros que no son espectro-compatibles— se informa en {@code avisos} sin bloquear el resultado.</li>
 *   <li>Se avisa si hay menos conjuntos que el mínimo de la versión (7 en el texto 2026, art. 47.1; 3 en la versión 2018).</li>
 * </ol>
 * Con {@code espectroCompatible = true} (registros sintéticos cuyas componentes ya se ajustaron al espectro de diseño con R = 1; art. 47.6) el paso (2) es otro:
 * f_i = máx(1, factor mínimo que deja AMBAS componentes del par en al menos 90 % del espectro de diseño en toda la rejilla 0,2T–1,5T), el mismo f_i para las dos
 * componentes; luego el factor común k ≥ 1 del paso (4) sobre el promedio SRSS (≥ 100 %). Solo hay aviso si tras escalar alguna componente queda bajo 90 % o el promedio bajo 100 %,
 * y {@code cumple} exige ambas condiciones (y el mínimo de conjuntos).
 * El espectro de diseño es Sa(T) = Z·U·C(T)·S·g/R con R = 1.
 */
public final class EscaladoRegistros {
    private EscaladoRegistros() {}

    /** Aceleración de la gravedad (m/s²). */
    public static final double FERNANDO_HUANCA_G = 9.80665;
    /** Puntos de la rejilla de periodos del escalado. */
    public static final int FERNANDO_HUANCA_PUNTOS_REJILLA = 60;
    /** Amortiguamiento del espectro de respuesta (art. 47.4). */
    public static final double FERNANDO_HUANCA_AMORTIGUAMIENTO = 0.05;

    public record ResultadoEscalado(List<RegistroSismico> escalados, double[] factores, double[] periodos, double[] espectroDiseno,
                                    double[] espectroPromedioSrss, double razonMinima, boolean cumple, List<String> avisos) {}

    /** Número mínimo de conjuntos de registros: 7 (texto 2026, art. 47.1) o 3 (versión 2018, art. 4.7). */
    public static int fernandoHuancaMinimoConjuntos(NormaE030.Version version) { return version == NormaE030.Version.V2026 ? 7 : 3; }

    /** Parámetros iguales a los dados pero con R0 = Ia = Ip = 1 (espectro elástico). */
    public static NormaE030.Parametros fernandoHuancaConR1(NormaE030.Parametros p) {
        return new NormaE030.Parametros(p.version(), p.zona(), p.categoria(), p.suelo(), 1.0, 1.0, 1.0, p.ct(), p.vs30());
    }

    /** Espectro de diseño del art. 41 con R = 1, en m/s², para los periodos dados. */
    public static double[] fernandoHuancaEspectroDiseno(NormaE030.Parametros norma, double[] periodos) {
        NormaE030.Parametros p = fernandoHuancaConR1(norma);
        double[] s = new double[periodos.length];
        for (int i = 0; i < s.length; i++) s[i] = p.saSobreG(periodos[i]) * FERNANDO_HUANCA_G;
        return s;
    }

    public static ResultadoEscalado fernandoHuancaEscalar(List<RegistroSismico> conjuntos, NormaE030.Parametros norma, double periodoFundamental) {
        return fernandoHuancaEscalar(conjuntos, norma, periodoFundamental, false);
    }

    public static ResultadoEscalado fernandoHuancaEscalar(List<RegistroSismico> conjuntos, NormaE030.Parametros norma, double periodoFundamental, boolean espectroCompatible) {
        if (conjuntos == null || conjuntos.isEmpty()) throw new IllegalArgumentException("No hay registros sísmicos para escalar.");
        if (norma == null) throw new IllegalArgumentException("Faltan los parámetros de la norma E.030.");
        if (!(periodoFundamental > 0) || !Double.isFinite(periodoFundamental)) throw new IllegalArgumentException("El periodo fundamental debe ser positivo.");
        int n = conjuntos.size();
        double[] periodos = EspectroRespuesta.fernandoHuancaRejillaPeriodos(0.2 * periodoFundamental, 1.5 * periodoFundamental, FERNANDO_HUANCA_PUNTOS_REJILLA);
        double[] diseno = fernandoHuancaEspectroDiseno(norma, periodos);
        int m = periodos.length;
        double[][] sax = new double[n][], say = new double[n][], srss = new double[n][];
        double[] f = new double[n];
        for (int i = 0; i < n; i++) {
            RegistroSismico r = conjuntos.get(i);
            sax[i] = EspectroRespuesta.fernandoHuancaPseudoAceleracion(r.ax(), r.dt(), periodos, FERNANDO_HUANCA_AMORTIGUAMIENTO);
            say[i] = EspectroRespuesta.fernandoHuancaPseudoAceleracion(r.ay(), r.dt(), periodos, FERNANDO_HUANCA_AMORTIGUAMIENTO);
            srss[i] = new double[m];
            double prom = 0;
            for (int j = 0; j < m; j++) {
                srss[i][j] = Math.hypot(sax[i][j], say[i][j]);
                prom += srss[i][j] / diseno[j];
            }
            prom /= m;
            if (!(prom > 0)) throw new IllegalArgumentException("El registro «" + r.nombre() + "» no tiene respuesta espectral en el rango de periodos 0,2T–1,5T.");
            if (espectroCompatible) {
                double mn = Double.POSITIVE_INFINITY;
                for (int j = 0; j < m; j++) mn = Math.min(mn, Math.min(sax[i][j], say[i][j]) / diseno[j]);
                f[i] = Math.max(1.0, 0.90 / mn * (1 + 1e-9));
            } else f[i] = 1.0 / prom;
        }
        // factor común k >= 1 para que el promedio SRSS escalado no baje del 100 % del diseño en ningún periodo
        double[] prom = new double[m];
        double minRazon = Double.POSITIVE_INFINITY;
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) prom[j] += f[i] * srss[i][j];
            prom[j] /= n;
            minRazon = Math.min(minRazon, prom[j] / diseno[j]);
        }
        double k = Math.max(1.0, (1.0 / minRazon) * (1 + 1e-9));
        List<String> avisos = new ArrayList<>();
        double[] factores = new double[n];
        List<RegistroSismico> escalados = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            factores[i] = f[i] * k;
            escalados.add(conjuntos.get(i).escalado(factores[i]));
        }
        double razonMinima = Double.POSITIVE_INFINITY;
        for (int j = 0; j < m; j++) { prom[j] *= k; razonMinima = Math.min(razonMinima, prom[j] / diseno[j]); }
        if (!espectroCompatible && k > 1.0 + 1e-6)
            avisos.add("Se aplicó un factor común adicional k = " + fernandoHuancaFmt(k) + " para que el promedio SRSS no sea menor que el espectro de diseño con R = 1 en todo el intervalo 0,2T–1,5T (arts. 47.5 y 47.6).");
        // art. 47.6: cada registro, en cada dirección, >= 90 % del diseño
        int incumplen = 0;
        double peor = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            for (double[] sa : new double[][]{sax[i], say[i]}) {
                double mn = Double.POSITIVE_INFINITY;
                for (int j = 0; j < m; j++) mn = Math.min(mn, factores[i] * sa[j] / diseno[j]);
                peor = Math.min(peor, mn);
                if (mn < 0.90) incumplen++;
            }
        }
        if (incumplen > 0)
            avisos.add("Art. 47.6: " + incumplen + " de " + (2 * n) + " componentes escaladas quedan por debajo del 90 % del espectro de diseño con R = 1 en algún periodo de 0,2T–1,5T (mínimo " + fernandoHuancaFmt(peor * 100) + " %). "
                    + "Es habitual si los registros no son espectro-compatibles; verifique el criterio con el proyectista.");
        int minimo = fernandoHuancaMinimoConjuntos(norma.version());
        if (n < minimo)
            avisos.add("La norma (" + norma.version().etiqueta + ") exige al menos " + minimo + " conjuntos de registros con dos componentes ortogonales; se tienen " + n + ".");
        for (int i = 0; i < n; i++)
            if (factores[i] > 4 || factores[i] < 0.25)
                avisos.add("El factor de escala del registro «" + conjuntos.get(i).nombre() + "» es " + fernandoHuancaFmt(factores[i]) + ": un escalado tan grande o tan pequeño resta representatividad al registro.");
        boolean cumple = razonMinima >= 0.999 && n >= minimo && (!espectroCompatible || incumplen == 0);
        return new ResultadoEscalado(List.copyOf(escalados), factores, periodos, diseno, prom, razonMinima, cumple, List.copyOf(avisos));
    }

    private static String fernandoHuancaFmt(double v) { return String.format(Locale.ROOT, "%.2f", v).replace('.', ','); }
}
