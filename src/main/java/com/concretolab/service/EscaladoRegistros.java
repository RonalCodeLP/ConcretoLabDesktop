package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EscaladoRegistros {
    private EscaladoRegistros() {}

    public static final double FERNANDO_HUANCA_G = 9.80665;
    public static final int FERNANDO_HUANCA_PUNTOS_REJILLA = 60;
    public static final double FERNANDO_HUANCA_AMORTIGUAMIENTO = 0.05;

    public record ResultadoEscalado(List<RegistroSismico> escalados, double[] factores, double[] periodos, double[] espectroDiseno,
                                    double[] espectroPromedioSrss, double razonMinima, boolean cumple, List<String> avisos) {}

    public static int fernandoHuancaMinimoConjuntos(NormaE030.Version version) { return version == NormaE030.Version.V2026 ? 7 : 3; }

    public static NormaE030.Parametros fernandoHuancaConR1(NormaE030.Parametros p) {
        return new NormaE030.Parametros(p.version(), p.zona(), p.categoria(), p.suelo(), 1.0, 1.0, 1.0, p.ct(), p.vs30());
    }

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
