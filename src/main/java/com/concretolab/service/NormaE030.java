package com.concretolab.service;

/**
 * Parámetros y espectro de la Norma Técnica E.030 «Diseño Sismorresistente» (RNE, Perú) en dos versiones:
 * texto 2026 (R.M. N° 183-2026-VIVIENDA) y versión 2018 (R.M. N° 355-2018-VIVIENDA). Ambas se contrastaron con el texto oficial
 * (el 2026 en doc/8081915-nt-e-030-diseno-sismorresistente-2026.pdf). Los proyectos con trámite iniciado pueden seguir sujetos a
 * la versión anterior (disposición transitoria de la R.M. 183-2026-VIVIENDA y sus modificaciones).
 */
public final class NormaE030 {
    private NormaE030() {}

    /** Versión de la norma. */
    public enum Version {
        V2026("E.030 texto 2026 (R.M. 183-2026-VIVIENDA)"), V2018("E.030 versión 2018 (R.M. 355-2018-VIVIENDA)");
        public final String etiqueta;
        Version(String e) { etiqueta = e; }
        @Override public String toString() { return etiqueta; }
    }

    /** Tabla 1: factor de zona Z. */
    public enum Zona {
        Z4("Zona 4", 0.45), Z3("Zona 3", 0.35), Z2("Zona 2", 0.25), Z1("Zona 1", 0.10);
        public final String etiqueta; public final double z;
        Zona(String e, double z) { etiqueta = e; this.z = z; }
        @Override public String toString() { return etiqueta + " (Z = " + z + ")"; }
    }

    /** Tabla 7: categoría de la edificación, factor U y porcentaje de carga viva para el peso sísmico (art. 31). */
    public enum Categoria {
        A("A · Esenciales (A2)", 1.5, 0.50), B("B · Importantes", 1.3, 0.50), C("C · Comunes", 1.0, 0.25);
        public final String etiqueta; public final double u, porcentajeCv;
        Categoria(String e, double u, double p) { etiqueta = e; this.u = u; porcentajeCv = p; }
        @Override public String toString() { return etiqueta + " (U = " + u + ")"; }
    }

    /**
     * Tablas 2 a 5: perfiles de suelo S0 a S4 con S, Tp y TL. En S2 y S3 cada valor es un intervalo {a, b} que se interpola
     * linealmente con Vs30 (a para el extremo de mayor velocidad, b para el de menor). Sin Vs30 se toma el valor b (art. 17, nota *).
     */
    public enum Suelo {
        S0(Version.V2026, "S0 · Roca (Vs30 ≥ 800 m/s)", new double[][]{{.8, .8}, {.8, .8}, {.8, .8}, {.8, .8}}, new double[]{.3, .3}, new double[]{3.0, 3.0}, Double.NaN, Double.NaN),
        S1(Version.V2026, "S1 · Suelos muy rígidos (550–800 m/s)", new double[][]{{1, 1}, {1, 1}, {1, 1}, {1, 1}}, new double[]{.4, .4}, new double[]{2.5, 2.5}, Double.NaN, Double.NaN),
        S2(Version.V2026, "S2 · Suelos rígidos (350–550 m/s)", new double[][]{{1.00, 1.10}, {1.00, 1.15}, {1.00, 1.30}, {1.00, 1.30}}, new double[]{.4, .6}, new double[]{2.5, 2.0}, 550, 350),
        S3(Version.V2026, "S3 · Suelos intermedios (200–350 m/s)", new double[][]{{1.10, 1.20}, {1.15, 1.20}, {1.30, 1.40}, {1.30, 1.60}}, new double[]{.6, .9}, new double[]{2.0, 1.6}, 350, 200),
        S4(Version.V2026, "S4 · Suelos blandos (< 200 m/s)", new double[][]{{Double.NaN, Double.NaN}, {1.30, 1.30}, {1.70, 1.70}, {2.40, 2.40}}, new double[]{1.2, 1.2}, new double[]{1.6, 1.6}, Double.NaN, Double.NaN),
        // Versión 2018 (Tablas 3 y 4): valores fijos por perfil y zona
        S0_2018(Version.V2018, "S0 · Roca dura", new double[][]{{.8, .8}, {.8, .8}, {.8, .8}, {.8, .8}}, new double[]{.3, .3}, new double[]{3.0, 3.0}, Double.NaN, Double.NaN),
        S1_2018(Version.V2018, "S1 · Roca o suelos muy rígidos", new double[][]{{1, 1}, {1, 1}, {1, 1}, {1, 1}}, new double[]{.4, .4}, new double[]{2.5, 2.5}, Double.NaN, Double.NaN),
        S2_2018(Version.V2018, "S2 · Suelos intermedios", new double[][]{{1.05, 1.05}, {1.15, 1.15}, {1.20, 1.20}, {1.60, 1.60}}, new double[]{.6, .6}, new double[]{2.0, 2.0}, Double.NaN, Double.NaN),
        S3_2018(Version.V2018, "S3 · Suelos blandos", new double[][]{{1.10, 1.10}, {1.20, 1.20}, {1.40, 1.40}, {2.00, 2.00}}, new double[]{1.0, 1.0}, new double[]{1.6, 1.6}, Double.NaN, Double.NaN);
        public final Version version; public final String etiqueta; private final double[][] s; private final double[] tp, tl; private final double vsAlto, vsBajo; // s por zona: Z4, Z3, Z2, Z1
        Suelo(Version v, String e, double[][] s, double[] tp, double[] tl, double vsAlto, double vsBajo) { version = v; etiqueta = e; this.s = s; this.tp = tp; this.tl = tl; this.vsAlto = vsAlto; this.vsBajo = vsBajo; }
        public static java.util.List<Suelo> de(Version v) { return java.util.Arrays.stream(values()).filter(x -> x.version == v).toList(); }
        public boolean interpola() { return !Double.isNaN(vsAlto); }
        public double vsMinimo() { return vsBajo; }
        public double vsMaximo() { return vsAlto; }
        private double interpolar(double[] ab, double vs30) {
            if (ab[0] == ab[1] || Double.isNaN(vs30) || !interpola()) return ab[1];
            double t = Math.max(0, Math.min(1, (vsAlto - vs30) / (vsAlto - vsBajo)));
            return ab[0] + t * (ab[1] - ab[0]);
        }
        public double s(Zona z, double vs30) { return interpolar(s[z.ordinal()], vs30); }
        public double tp(double vs30) { return interpolar(tp, vs30); }
        public double tl(double vs30) { return interpolar(tl, vs30); }
        @Override public String toString() { return etiqueta; }
    }

    /**
     * Datos elegidos por el usuario. {@code r0} es el coeficiente básico; ia e ip, los factores de irregularidad;
     * {@code vs30} es la velocidad de ondas de corte en m/s (NaN si se desconoce; solo se usa en la versión 2026).
     */
    public record Parametros(Version version, Zona zona, Categoria categoria, Suelo suelo, double r0, double ia, double ip, double ct, double vs30) {
        public Parametros {
            if (version == null || zona == null || categoria == null || suelo == null) throw new IllegalArgumentException("Seleccione versión, zona, categoría y suelo.");
            if (suelo.version != version) throw new IllegalArgumentException("El suelo elegido no pertenece a la versión " + version.etiqueta + ".");
            if (!(r0 > 0) || !(ia > 0 && ia <= 1) || !(ip > 0 && ip <= 1) || !(ct > 0)) throw new IllegalArgumentException("R0, Ia, Ip y CT deben ser positivos (Ia e Ip no mayores que 1).");
            if (suelo == Suelo.S4 && zona == Zona.Z4) throw new IllegalArgumentException("Suelo S4 en zona 4: la Tabla 4 de la E.030 exige un análisis de respuesta de sitio; no hay factor S tabulado.");
            if (suelo.interpola() && !Double.isNaN(vs30) && (vs30 < suelo.vsMinimo() || vs30 > suelo.vsMaximo()))
                throw new IllegalArgumentException("Vs30 para " + suelo.name() + " debe estar entre " + (int) suelo.vsMinimo() + " y " + (int) suelo.vsMaximo() + " m/s (Tabla 3).");
        }
        /** Texto 2026 con Vs30. */
        public Parametros(Zona zona, Categoria categoria, Suelo suelo, double r0, double ia, double ip, double ct, double vs30) { this(Version.V2026, zona, categoria, suelo, r0, ia, ip, ct, vs30); }
        /** Texto 2026 sin Vs30: se aplican los valores más desfavorables del intervalo (art. 17, nota *). */
        public Parametros(Zona zona, Categoria categoria, Suelo suelo, double r0, double ia, double ip, double ct) { this(Version.V2026, zona, categoria, suelo, r0, ia, ip, ct, Double.NaN); }
        /** Versión 2018 (suelos S0_2018 a S3_2018). */
        public static Parametros v2018(Zona zona, Categoria categoria, Suelo suelo, double r0, double ia, double ip, double ct) { return new Parametros(Version.V2018, zona, categoria, suelo, r0, ia, ip, ct, Double.NaN); }

        private boolean v2026() { return version == Version.V2026; }
        public double z() { return zona.z; }
        public double u() { return categoria.u; }
        public double s() { return suelo.s(zona, vs30); }
        public double tp() { return suelo.tp(vs30); }
        public double tl() { return suelo.tl(vs30); }
        public double r() { return r0 * ia * ip; }
        public boolean regular() { return ia == 1.0 && ip == 1.0; }
        /** Factor de amplificación sísmica C (2026: art. 18, Tabla 6, con tramo T < 0,2·Tp; 2018: sección 2.5, C = 2,5 si T < Tp). */
        public double c(double t) {
            if (v2026() && t < 0.2 * tp()) return 1 + 7.5 * t / tp();
            if (t <= tp()) return 2.5;
            if (t < tl()) return 2.5 * tp() / t;
            return 2.5 * tp() * tl() / (t * t);
        }
        /** C para el cortante basal estático: 2,5 si T < Tp (2026: arts. 18.3 y 34.1; 2018 ya lo da la Tabla). */
        public double cEstatico(double t) { return t < tp() ? 2.5 : c(t); }
        /** Pseudoaceleración espectral dividida por g: Sa/g = Z·U·C·S/R. */
        public double saSobreG(double t) { return z() * u() * c(t) * s() / r(); }
        /** Cociente V/P del método estático: Z·U·C·S/R con C/R ≥ 0,11. */
        public double coeficienteCortante(double t) { return z() * u() * s() * Math.max(cEstatico(t) / r(), 0.11); }
        /** Cociente V/P sin el mínimo C/R, para desplazamientos. */
        public double coeficienteCortanteSinMinimo(double t) { return z() * u() * s() * cEstatico(t) / r(); }
        /** Exponente k de la distribución de fuerzas. */
        public static double k(double t) { return t <= 0.5 ? 1.0 : Math.min(2.0, 0.75 + 0.5 * t); }
        /** Fracción de la acción sísmica en la dirección perpendicular: 0,30 en el texto 2026 (arts. 28.1, 33.3 y 43); 0 en 2018. */
        public double fraccionPerpendicular() { return v2026() ? 0.30 : 0.0; }
        /** Cita del artículo o tabla correspondiente en la versión elegida. */
        public String art(String tema) {
            boolean n = v2026();
            return switch (tema) {
                case "aplicabilidad" -> n ? "art. 33.2" : "art. 4.5.1";
                case "minimo" -> n ? "art. 44" : "art. 4.6.4";
                case "desplazamientos" -> n ? "art. 50.3" : "art. 5.1";
                case "excentricidad" -> n ? "art. 37" : "art. 4.5.5";
                case "excentricidadEspectral" -> n ? "art. 45" : "art. 4.6.5";
                case "peso" -> n ? "art. 31" : "art. 4.3";
                case "estatico" -> n ? "arts. 33 a 37" : "art. 4.5";
                case "espectral" -> n ? "arts. 39 a 45" : "art. 4.6";
                case "cqc" -> n ? "art. 42" : "art. 4.6.3";
                case "tablaIrregularidad" -> n ? "Tabla 13" : "Tabla 10";
                default -> tema;
            };
        }
        /** Restricciones a las irregularidades según categoría y zona. Lista vacía si no hay conflicto. */
        public java.util.List<String> restricciones() {
            java.util.List<String> av = new java.util.ArrayList<>();
            String t = art("tablaIrregularidad") + " de la E.030: ";
            boolean alguna = ia < 1.0 || ip < 1.0, extrema = ia <= 0.60 + 1e-9 || ip <= 0.60 + 1e-9, zona234 = zona != Zona.Z1;
            if (categoria == Categoria.A && zona234 && alguna) av.add(t + "en categoría A y zonas 2 a 4 no se permiten irregularidades.");
            else if (categoria == Categoria.A && extrema) av.add(t + "en categoría A no se permiten irregularidades extremas.");
            if (categoria == Categoria.B && zona234 && extrema) av.add(t + "en categoría B y zonas 2 a 4 no se permiten irregularidades extremas.");
            if (categoria == Categoria.C && (zona == Zona.Z4 || zona == Zona.Z3) && extrema) av.add(t + "en categoría C y zonas 3 y 4 no se permiten irregularidades extremas.");
            if (categoria == Categoria.C && zona == Zona.Z2 && extrema) av.add(t + "en categoría C y zona 2 las irregularidades extremas solo se admiten hasta 2 pisos u 8 m de altura.");
            return av;
        }
        /** Solo texto 2026, art. 14.8: categorías A y B en zona 4 con suelos S1 a S4 requieren verificar el periodo del sitio Ts < 0,65·Tp. */
        public boolean requiereVerificarPeriodoDeSitio() {
            return v2026() && zona == Zona.Z4 && categoria != Categoria.C && suelo != Suelo.S0;
        }
        /** Factor que convierte desplazamientos elásticos en inelásticos: 0,75R regular, 0,85R irregular. */
        public double factorInelastico() { return (regular() ? 0.75 : 0.85) * r(); }
        /** Fracción mínima del cortante estático que debe alcanzar el dinámico: 80 % regular, 90 % irregular. */
        public double fraccionMinimaDinamica() { return regular() ? 0.80 : 0.90; }
    }

    /** Distorsión máxima de entrepiso para concreto armado (2026: Tabla 14; 2018: Tabla 11). */
    public static final double DERIVA_LIMITE_CONCRETO = 0.007;
}
