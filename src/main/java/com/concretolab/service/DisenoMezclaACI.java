package com.concretolab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diseño de mezcla de concreto de peso normal por el método ACI 211.1 (volumen absoluto), con la resistencia
 * promedio requerida según E.060 (art. 5) / ACI 318 y los límites de durabilidad del capítulo 4 de la E.060.
 *
 * <p>Es un cálculo de partida: el resultado debe validarse con mezclas de prueba y ajustarse en laboratorio.
 * Las tablas de ACI 211.1 corresponden a agregado grueso angular de peso normal; no cubre concreto liviano,
 * de alta resistencia (f'cr &gt; 45 MPa) ni con aditivos reductores de agua (que reducen el agua de mezclado).</p>
 */
public final class DisenoMezclaACI {

    private DisenoMezclaACI() { }

    /** Condición de exposición del concreto (E.060 cap. 4; valores derivados de ACI 318 cap. 19). */
    public enum Exposicion {
        NORMAL("Normal (sin requisitos especiales)", 0.0, 0.0, false),
        BAJA_PERMEABILIDAD("Servicio en agua: baja permeabilidad", 0.50, 28.0, false),
        CONGELAMIENTO("Congelamiento y deshielo en condición húmeda", 0.45, 31.0, true),
        SULFATOS_MODERADOS("Sulfatos moderados (SO₄ 0,10 – 0,20 %): cemento tipo II", 0.50, 28.0, false),
        SULFATOS_SEVEROS("Sulfatos severos (SO₄ 0,20 – 2,00 %): cemento tipo V", 0.45, 31.0, false),
        SULFATOS_MUY_SEVEROS("Sulfatos muy severos (SO₄ > 2,00 %): tipo V + puzolana", 0.45, 31.0, false),
        AGUA_DE_MAR("Agua salobre o de mar / cloruros (protección del acero)", 0.40, 35.0, false);

        private final String etiqueta;
        private final double acMaxima;
        private final double fcMinima;
        private final boolean exigeAire;

        Exposicion(String etiqueta, double acMaxima, double fcMinima, boolean exigeAire) {
            this.etiqueta = etiqueta;
            this.acMaxima = acMaxima;
            this.fcMinima = fcMinima;
            this.exigeAire = exigeAire;
        }

        public double acMaxima() { return acMaxima; }
        public double fcMinima() { return fcMinima; }
        public boolean exigeAire() { return exigeAire; }
        @Override public String toString() { return etiqueta; }
    }

    /** Severidad de la exposición para el contenido de aire incorporado (ACI 211.1 tabla 6.3.3). */
    public enum SeveridadAire {
        SUAVE("Suave"), MODERADA("Moderada"), SEVERA("Severa");
        private final String etiqueta;
        SeveridadAire(String e) { etiqueta = e; }
        @Override public String toString() { return etiqueta; }
    }

    public record Entrada(
            double fcMpa,
            Double desviacionMpa,
            int numeroEnsayos,
            double asentamientoMm,
            double tmaMm,
            double moduloFinura,
            double pesoUnitarioCompactadoGrueso,
            double densidadCemento,
            double densidadRelativaFino,
            double densidadRelativaGrueso,
            double absorcionGruesoPct,
            Exposicion exposicion,
            boolean aireIncorporado,
            SeveridadAire severidadAire) { }

    public record Resultado(
            double fcrMpa,
            String criterioFcr,
            double relacionAcResistencia,
            double relacionAcDurabilidad,
            double relacionAc,
            double tmaTabla,
            double agua,
            double airePorcentaje,
            double cemento,
            double bolsasCemento,
            double volumenRelativoGrueso,
            double gruesoSeco,
            double gruesoSss,
            double finoSss,
            double volumenAgua,
            double volumenCemento,
            double volumenGrueso,
            double volumenFino,
            double volumenAire,
            List<String> advertencias) {

        /** Proporción en peso respecto del cemento (SSS), p. ej. 1 : 1,8 : 2,7. */
        public String proporcionEnPeso() {
            return String.format(Locale.ROOT, "1 : %.2f : %.2f / %.1f L de agua por bolsa de 42,5 kg",
                    finoSss / cemento, gruesoSss / cemento, relacionAc * 42.5);
        }
    }

    // ---- ACI 211.1 tabla 6.3.3 (mm de asentamiento: 25-50, 75-100, 150-175; centros 37,5 / 87,5 / 162,5)
    private static final double[] TMA = {9.5, 12.5, 19.0, 25.0, 37.5, 50.0, 75.0, 150.0};
    private static final double[] ASENT = {37.5, 87.5, 162.5};
    private static final double[][] AGUA_SIN_AIRE = {
        {207, 199, 190, 179, 166, 154, 130, 113},
        {228, 216, 205, 193, 181, 169, 145, 124},
        {243, 228, 216, 202, 190, 178, 160, Double.NaN}};
    private static final double[][] AGUA_CON_AIRE = {
        {181, 175, 168, 160, 150, 142, 122, 107},
        {202, 193, 184, 175, 165, 157, 133, 119},
        {216, 205, 197, 184, 174, 166, 154, Double.NaN}};
    private static final double[] AIRE_ATRAPADO = {3.0, 2.5, 2.0, 1.5, 1.0, 0.5, 0.3, 0.2};
    private static final double[][] AIRE_INCORPORADO = { // suave, moderada, severa
        {4.5, 4.0, 3.5, 3.0, 2.5, 2.0, 1.5, 1.0},
        {6.0, 5.5, 5.0, 4.5, 4.5, 4.0, 3.5, 3.0},
        {7.5, 7.0, 6.0, 6.0, 5.5, 5.0, 4.5, 4.0}};

    // ---- ACI 211.1 tabla 6.3.4(a): f'cr a 28 días vs a/c
    private static final double[] FCR = {15, 20, 25, 30, 35, 40, 45};
    private static final double[] AC_SIN_AIRE = {0.79, 0.69, 0.61, 0.54, 0.47, 0.42, 0.38};
    private static final double[] AC_CON_AIRE = {0.70, 0.60, 0.52, 0.45, 0.39};

    // ---- ACI 211.1 tabla 6.3.6: volumen de grueso seco compactado por m³, según MF de la arena
    private static final double[] MF = {2.40, 2.60, 2.80, 3.00};
    private static final double[][] VOL_GRUESO = {
        {0.50, 0.48, 0.46, 0.44}, {0.59, 0.57, 0.55, 0.53}, {0.66, 0.64, 0.62, 0.60}, {0.71, 0.69, 0.67, 0.65},
        {0.75, 0.73, 0.71, 0.69}, {0.78, 0.76, 0.74, 0.72}, {0.82, 0.80, 0.78, 0.76}, {0.87, 0.85, 0.83, 0.81}};

    /** Factor de corrección de la desviación estándar cuando hay menos de 30 ensayos (ACI 318 / E.060). */
    static double factorMuestra(int n) {
        if (n >= 30) return 1.00;
        if (n <= 15) return 1.16; // con menos de 15 ensayos no se usa s: lo decide fcr()
        double[] ns = {15, 20, 25, 30};
        double[] fs = {1.16, 1.08, 1.03, 1.00};
        return interpolar(ns, fs, n);
    }

    /** Resistencia promedio requerida f'cr (MPa) y el criterio aplicado. */
    public static double[] fcr(double fc, Double s, int n, StringBuilder criterio) {
        if (!Double.isFinite(fc) || fc <= 0) {
            throw new IllegalArgumentException("Ingrese una f'c positiva (MPa).");
        }
        if (s == null || n < 15) {
            double suma = fc < 21 ? 7.0 : fc <= 35 ? 8.5 : 9.8;
            criterio.append(String.format(Locale.ROOT,
                    "Sin registro estadístico suficiente (menos de 15 ensayos): f'cr = f'c + %.1f MPa (E.060 tabla 5.3).", suma));
            return new double[] {fc + suma};
        }
        if (!Double.isFinite(s) || s <= 0) {
            throw new IllegalArgumentException("La desviación estándar debe ser positiva.");
        }
        double ss = s * factorMuestra(n);
        double a = fc + 1.34 * ss;
        double b = fc <= 35 ? fc + 2.33 * ss - 3.5 : 0.9 * fc + 2.33 * ss;
        criterio.append(String.format(Locale.ROOT,
                "Con desviación estándar s = %.2f MPa (%d ensayos, s corregida %.2f): f'cr = mayor de f'c + 1,34 s = %.1f y %s = %.1f MPa.",
                s, n, ss, a, fc <= 35 ? "f'c + 2,33 s − 3,5" : "0,9 f'c + 2,33 s", b));
        return new double[] {Math.max(a, b)};
    }

    public static Resultado disenar(Entrada e) {
        validar(e);
        List<String> av = new ArrayList<>();
        StringBuilder criterio = new StringBuilder();
        double fcr = fcr(e.fcMpa(), e.desviacionMpa(), e.numeroEnsayos(), criterio)[0];

        if (e.fcMpa() < 17) {
            av.add("La E.060 exige f'c ≥ 17 MPa en concreto estructural; el valor ingresado es menor.");
        }
        Exposicion ex = e.exposicion() == null ? Exposicion.NORMAL : e.exposicion();
        boolean aire = e.aireIncorporado() || ex.exigeAire();
        if (ex.exigeAire() && !e.aireIncorporado()) {
            av.add("La exposición a congelamiento exige aire incorporado: se calculó con aire incorporado.");
        }
        if (ex.fcMinima() > e.fcMpa()) {
            av.add(String.format(Locale.ROOT,
                    "La exposición elegida exige f'c ≥ %.0f MPa (E.060 cap. 4); la f'c ingresada es menor: especifique al menos ese valor.",
                    ex.fcMinima()));
        }
        if (ex.fcMinima() > 0) {
            av.add("Los límites de durabilidad (a/c máxima y f'c mínima) se tomaron de ACI 318 cap. 19 / E.060 cap. 4: "
                    + "verifíquelos contra la edición vigente de la norma.");
        }

        // a/c por resistencia
        double acRes;
        if (aire) {
            if (fcr > FCR[4]) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "Con aire incorporado la tabla ACI 211.1 llega a f'cr = 35 MPa (resulta %.1f). Reduzca f'c o use mezcla de prueba.", fcr));
            }
            acRes = interpolarRango(FCR, AC_CON_AIRE, fcr, av);
        } else {
            if (fcr > 45) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "La tabla ACI 211.1 llega a f'cr = 45 MPa (resulta %.1f): para mayor resistencia use el procedimiento de alta resistencia (ACI 211.4) y mezclas de prueba.", fcr));
            }
            acRes = interpolarRango(FCR, AC_SIN_AIRE, fcr, av);
        }
        double acDur = ex.acMaxima() > 0 ? ex.acMaxima() : Double.POSITIVE_INFINITY;
        double ac = Math.min(acRes, acDur);
        if (acDur < acRes) {
            av.add(String.format(Locale.ROOT, "Rige la durabilidad: a/c = %.2f (máxima por exposición) en lugar de %.2f por resistencia.", acDur, acRes));
        }

        // agua y aire
        int it = indiceTma(e.tmaMm(), av);
        double tma = TMA[it];
        double[] aguaCol = new double[ASENT.length];
        double[][] tablaAgua = aire ? AGUA_CON_AIRE : AGUA_SIN_AIRE;
        for (int i = 0; i < ASENT.length; i++) {
            aguaCol[i] = tablaAgua[i][it];
        }
        if (e.asentamientoMm() < 25 || e.asentamientoMm() > 175) {
            av.add("El asentamiento está fuera de 25 – 175 mm (rango de la tabla ACI 211.1); se usó el límite más cercano.");
        }
        double slump = Math.min(175, Math.max(25, e.asentamientoMm()));
        double agua = interpolarNaN(ASENT, aguaCol, slump, tma, av);
        double airePct = aire ? AIRE_INCORPORADO[(e.severidadAire() == null ? SeveridadAire.MODERADA : e.severidadAire()).ordinal()][it]
                : AIRE_ATRAPADO[it];

        double cemento = agua / ac;
        // volumen de grueso seco compactado según MF
        double mf = e.moduloFinura();
        if (mf < 2.4 || mf > 3.0) {
            av.add(String.format(Locale.ROOT, "El módulo de finura %.2f está fuera de 2,40 – 3,00 (tabla ACI 211.1); se usó el límite más cercano.", mf));
        }
        double mfAcot = Math.min(3.0, Math.max(2.4, mf));
        double b = interpolar(MF, VOL_GRUESO[it], mfAcot);
        double gruesoSeco = b * e.pesoUnitarioCompactadoGrueso();
        double gruesoSss = gruesoSeco * (1 + e.absorcionGruesoPct() / 100);

        var vol = VolumenAbsolutoService.calcular(new VolumenAbsolutoService.Datos(
                agua, ac, e.densidadCemento(), gruesoSss, e.densidadRelativaGrueso(), e.densidadRelativaFino(), airePct));

        if (cemento < 250) {
            av.add(String.format(Locale.ROOT, "El contenido de cemento (%.0f kg/m³) es bajo; verifique cohesión y requisitos de la especificación.", cemento));
        }
        if (cemento > 550) {
            av.add(String.format(Locale.ROOT, "El contenido de cemento (%.0f kg/m³) es alto: riesgo de retracción y calor de hidratación.", cemento));
        }
        av.add("Cálculo de partida (ACI 211.1): debe verificarse con mezclas de prueba y ajustarse en laboratorio. "
                + "Las tablas suponen agregado angular y sin aditivos reductores de agua.");

        return new Resultado(fcr, criterio.toString(), acRes, acDur == Double.POSITIVE_INFINITY ? 0 : acDur, ac, tma, agua,
                airePct, cemento, cemento / 42.5, b, gruesoSeco, gruesoSss, vol.finoSss(), vol.volumenAgua(),
                vol.volumenCemento(), vol.volumenGrueso(), vol.volumenFino(), vol.volumenAire(), List.copyOf(av));
    }

    // ------------------------------------------------------------------ utilidades

    private static void validar(Entrada e) {
        if (e == null) {
            throw new IllegalArgumentException("Faltan datos de entrada.");
        }
        double[] v = {e.fcMpa(), e.asentamientoMm(), e.tmaMm(), e.moduloFinura(), e.pesoUnitarioCompactadoGrueso(),
            e.densidadCemento(), e.densidadRelativaFino(), e.densidadRelativaGrueso()};
        for (double x : v) {
            if (!Double.isFinite(x) || x <= 0) {
                throw new IllegalArgumentException("Todos los datos deben ser números positivos.");
            }
        }
        if (!Double.isFinite(e.absorcionGruesoPct()) || e.absorcionGruesoPct() < 0 || e.absorcionGruesoPct() > 20) {
            throw new IllegalArgumentException("La absorción del agregado grueso debe estar entre 0 y 20 %.");
        }
        if (e.densidadRelativaFino() < 2.0 || e.densidadRelativaFino() > 3.2
                || e.densidadRelativaGrueso() < 2.0 || e.densidadRelativaGrueso() > 3.2) {
            throw new IllegalArgumentException("Las densidades relativas de los agregados deben estar entre 2,0 y 3,2.");
        }
        if (e.densidadCemento() < 2.5 || e.densidadCemento() > 3.3) {
            throw new IllegalArgumentException("La densidad del cemento debe estar entre 2,5 y 3,3 g/cm³.");
        }
        if (e.pesoUnitarioCompactadoGrueso() < 1000 || e.pesoUnitarioCompactadoGrueso() > 2000) {
            throw new IllegalArgumentException("El peso unitario compactado del grueso debe estar entre 1 000 y 2 000 kg/m³.");
        }
    }

    private static int indiceTma(double tma, List<String> av) {
        if (tma < TMA[0]) {
            throw new IllegalArgumentException("El tamaño máximo nominal mínimo de la tabla es 9,5 mm (3/8\").");
        }
        int idx = 0;
        for (int i = 0; i < TMA.length; i++) {
            if (TMA[i] <= tma + 1e-9) {
                idx = i;
            }
        }
        if (Math.abs(TMA[idx] - tma) > 0.6) {
            av.add(String.format(Locale.ROOT,
                    "El tamaño máximo nominal %.1f mm no está en la tabla ACI 211.1; se usó el inmediato inferior (%.1f mm).", tma, TMA[idx]));
        }
        return idx;
    }

    private static double interpolarRango(double[] xs, double[] ys, double x, List<String> av) {
        if (x < xs[0]) {
            av.add("f'cr menor que 15 MPa: se usó la a/c de 15 MPa (revise con mezclas de prueba).");
            return ys[0];
        }
        double[] xr = java.util.Arrays.copyOf(xs, ys.length);
        return interpolar(xr, ys, x);
    }

    private static double interpolarNaN(double[] xs, double[] ys, double x, double tma, List<String> av) {
        // la tabla no da asentamiento de 150-175 mm para TMA 150 mm
        int n = 0;
        while (n < ys.length && !Double.isNaN(ys[n])) {
            n++;
        }
        if (n < ys.length && x > xs[n - 1]) {
            av.add(String.format(Locale.ROOT, "La tabla no da agua para ese asentamiento con TMA %.0f mm; se usó el mayor asentamiento disponible.", tma));
        }
        return interpolar(java.util.Arrays.copyOf(xs, n), java.util.Arrays.copyOf(ys, n), x);
    }

    /** Interpolación lineal con valores extremos constantes fuera del rango. */
    static double interpolar(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[xs.length - 1];
    }
}
