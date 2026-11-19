package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;

import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.FernandoHuancaAnalisisSismico.CargaPiso;
import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SismoE030Test {
    private static Elemento el(String tipo, String n, double largo, double ancho, double alto, double x, double y, double z) {
        Elemento e = new Elemento(); e.setTipo(tipo); e.setNombre(n); e.setLargo(largo); e.setAncho(ancho); e.setAltura(alto); e.setX(x); e.setY(y); e.setZ(z); return e;
    }

    /** Pórtico de {@code pisos} niveles, 2x2 columnas, luces sx,sy, columnas cuadradas lc, vigas bv x hv. */
    static List<Elemento> porticos(int pisos, double sx, double sy, double hPiso, double lc, double bv, double hv) {
        List<Elemento> l = new ArrayList<>();
        for (double x : new double[]{-sx / 2, sx / 2}) for (double y : new double[]{-sy / 2, sy / 2})
            l.add(el("COLUMNA", "C", lc, lc, hPiso * pisos, x, y, hPiso * pisos / 2));
        for (int p = 1; p <= pisos; p++) {
            double zv = hPiso * p - hv / 2;
            for (double y : new double[]{-sy / 2, sy / 2}) l.add(el("VIGA", "VX", sx - lc, bv, hv, 0, y, zv));
            for (double x : new double[]{-sx / 2, sx / 2}) l.add(el("VIGA", "VY", bv, sy - lc, hv, x, 0, zv));
        }
        return l;
    }

    static Parametros norma(double ia, double ip) { return new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, ia, ip, 35); }

    static FernandoHuancaAnalisisSismico.Entrada entrada(List<Elemento> el, int pisos, Parametros p) {
        var base = FernandoHuancaAnalisisSismico.pisos(el); assertEquals(pisos, base.size());
        List<CargaPiso> cargas = new ArrayList<>(); for (var b : base) cargas.add(new CargaPiso(b.areaPorDefecto(), 1.0, 2.0));
        return new FernandoHuancaAnalisisSismico.Entrada(el, 21, 0.2, 1.0, 0.20, cargas, p);
    }

    @Test void tablasDeLaNormaCoincidenConValoresConocidos() {
        var p = norma(1, 1);
        assertEquals(0.45, p.z()); assertEquals(1.0, p.u()); assertEquals(1.10, p.s(), 1e-12); // S2, Z4, sin Vs30: mayor valor del intervalo assertEquals(0.6, p.tp()); assertEquals(2.0, p.tl());
        assertEquals(2.5, p.c(0.4), 1e-12);          // T < Tp
        assertEquals(2.5 * 0.6 / 1.0, p.c(1.0), 1e-12); // Tp < T < TL
        assertEquals(2.5 * 0.6 * 2.0 / 9.0, p.c(3.0), 1e-12); // T > TL
        assertEquals(0.45 * 1.0 * 2.5 * 1.10 / 8, p.saSobreG(0.4), 1e-12);
        assertEquals(1.0, Parametros.k(0.4)); assertEquals(1.0, Parametros.k(0.5)); assertEquals(1.25, Parametros.k(1.0)); assertEquals(2.0, Parametros.k(3.0));
        assertEquals(0.75 * 8, p.factorInelastico(), 1e-12);
        assertEquals(0.85 * 6, norma(0.75, 1).factorInelastico(), 1e-12); // irregular: 0,85·R con R = 8·0,75
        assertEquals(0.35, Zona.Z3.z); assertEquals(0.25, Zona.Z2.z); assertEquals(0.10, Zona.Z1.z);
        assertEquals(1.20, Suelo.S3.s(Zona.Z3, Double.NaN), 1e-12); assertEquals(1.60, Suelo.S3.s(Zona.Z1, Double.NaN), 1e-12); assertEquals(0.80, Suelo.S0.s(Zona.Z4, Double.NaN), 1e-12);
    }

    @Test void cortanteMinimoUsaCSobreRdeCeroComa11() {
        var p = new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 1, 1, 35);
        double t = 3.0; // C pequeño: C/R < 0,11
        assertTrue(p.c(t) / p.r() < 0.11);
        assertEquals(0.45 * 1.0 * 1.0 * 0.11, p.coeficienteCortante(t), 1e-12);
    }

    @Test void unPisoConVigasMuyRigidasTieneRigidezDeCuatroColumnasEmpotradas() {
        double h = 3.0, lc = 0.5; var el = porticos(1, 5, 5, h, lc, 1.0, 3.0); // vigas enormes, casi rígidas
        var r = FernandoHuancaAnalisisSismico.analizar(entrada(el, 1, norma(1, 1)));
        double E = FernandoHuancaAnalisisSismico.modulo(21) * 1000, I = Math.pow(lc, 4) / 12, k = 4 * 12 * E * I / Math.pow(h, 3);
        double m = r.pisos().get(0).peso() / FernandoHuancaAnalisisSismico.G, tEsperado = 2 * Math.PI * Math.sqrt(m / k);
        // Con vigas casi rígidas la altura efectiva de columna es la libre; el periodo debe estar cerca del de columnas biempotradas.
        assertEquals(tEsperado, r.modos().get(0).periodo(), 0.12 * tEsperado);
    }

    @Test void masasEfectivasSumanCienPorCientoYModosEstanOrdenados() {
        var r = FernandoHuancaAnalisisSismico.analizar(entrada(porticos(3, 5, 4, 3, 0.4, 0.3, 0.5), 3, norma(1, 1)));
        assertEquals(1.0, r.masaAcumuladaX(), 1e-6); assertEquals(1.0, r.masaAcumuladaY(), 1e-6);
        for (int i = 1; i < r.modos().size(); i++) assertTrue(r.modos().get(i).periodo() < r.modos().get(i - 1).periodo());
        assertEquals(9, r.modos().size());
    }

    @Test void fuerzasEstaticasSumanElCortanteBaseYSeAcumulanPorPiso() {
        var p = norma(1, 1); var r = FernandoHuancaAnalisisSismico.analizar(entrada(porticos(3, 5, 4, 3, 0.4, 0.3, 0.5), 3, p));
        var e = r.estaticoX(); double suma = 0; for (double f : e.fuerzas()) suma += f;
        assertEquals(e.cortanteBase(), suma, 1e-6);
        assertEquals(p.coeficienteCortante(e.periodo()) * r.pesoTotal(), e.cortanteBase(), 1e-6);
        assertEquals(e.cortanteBase(), e.cortantes()[0], 1e-9);
        for (int i = 1; i < 3; i++) assertTrue(e.fuerzas()[i] > e.fuerzas()[i - 1]); // fuerzas crecen con la altura
        assertEquals(9.0 / 35, e.periodo(), 1e-12);
    }

    @Test void espectralRespetaCortanteMinimoYEsSimetricoEnPlantaSimetrica() {
        var r = FernandoHuancaAnalisisSismico.analizar(entrada(porticos(2, 5, 5, 3, 0.4, 0.3, 0.5), 2, norma(1, 1)));
        assertTrue(r.espectralX().cortanteBase() >= 0.8 * r.estaticoX().cortanteBase() - 1e-6);
        assertEquals(r.espectralX().cortanteBase(), r.espectralY().cortanteBase(), 1e-6 * r.espectralX().cortanteBase());
        assertEquals(r.espectralX().derivasMaximas()[0], r.espectralY().derivasMaximas()[0], 1e-9);
        assertTrue(r.espectralX().derivaMaxima() > 0);
    }

    @Test void irregularExigeNoventaPorCientoYReduceR() {
        var reg = norma(1, 1); var irr = norma(0.75, 1);
        assertEquals(0.80, reg.fraccionMinimaDinamica()); assertEquals(0.90, irr.fraccionMinimaDinamica());
        assertEquals(6.0, irr.r(), 1e-12);
    }

    @Test void validaDatosYDetectaModelosIncompletos() {
        assertThrows(IllegalArgumentException.class, () -> FernandoHuancaAnalisisSismico.pisos(List.of(el("ZAPATA", "Z", 1, 1, 1, 0, 0, 0.5))));
        var el = porticos(1, 5, 5, 3, 0.4, 0.3, 0.5);
        var in = entrada(el, 1, norma(1, 1));
        assertThrows(IllegalArgumentException.class, () -> FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(el, 5, 0.2, 1, 0.2, in.cargas(), norma(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 1.2, 1, 35));
        assertThrows(IllegalArgumentException.class, () -> FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(el, 21, 0.2, 1, 0.2, List.of(), norma(1, 1))));
    }

    @Test void desplazamientosNoUsanElMinimoCSobreR() {
        // CT = 2 da T = hn/2 = 3 s para 2 pisos de 3 m: C/R < 0,11, así que el cortante de fuerzas queda en el mínimo (art. 4.5.2).
        var el = porticos(2, 5, 4, 3, 0.4, 0.3, 0.5);
        var a = FernandoHuancaAnalisisSismico.analizar(entrada(el, 2, new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 1, 1, 2)));
        var b = FernandoHuancaAnalisisSismico.analizar(entrada(el, 2, new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 10, 1, 1, 2)));
        assertTrue(new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 10, 1, 1, 2).c(3.0) / 10 < 0.11);
        assertEquals(a.estaticoX().cortanteBase(), b.estaticoX().cortanteBase(), 1e-9); // fuerzas: mínimo independiente de R
        // Art. 5.1: con el elástico sin mínimo, V ∝ 1/R y el desplazamiento inelástico (0,75R) no depende de R.
        for (int i = 0; i < 2; i++) assertEquals(a.estaticoX().derivasMaximas()[i], b.estaticoX().derivasMaximas()[i], 1e-9);
    }

    @Test void espectralIncluyeExcentricidadAccidentalYLaDerivaMaximaSuperaLaDelCentroDeMasas() {
        var r = FernandoHuancaAnalisisSismico.analizar(entrada(porticos(2, 5, 4, 3, 0.4, 0.3, 0.5), 2, norma(1, 1)));
        assertTrue(r.espectralX().derivasMaximas()[0] > r.espectralX().derivasCm()[0] * 1.0001);
        assertTrue(r.espectralX().razonTorsion()[0] >= 1.0);
    }

    @Test void tabla10AvisaIrregularidadesNoPermitidas() {
        assertFalse(new Parametros(Zona.Z4, Categoria.A, Suelo.S1, 8, 0.9, 1, 35).restricciones().isEmpty());
        assertTrue(new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 0.9, 0.75, 35).restricciones().isEmpty());
        assertFalse(new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 0.5, 1, 35).restricciones().isEmpty());
        assertTrue(new Parametros(Zona.Z1, Categoria.B, Suelo.S1, 8, 0.5, 0.6, 35).restricciones().isEmpty());
    }

    @Test void tablas2026InterpolanSyPeriodosConVs30() {
        var p = new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35, 450); // a mitad del intervalo 350-550 m/s
        assertEquals(1.05, p.s(), 1e-12); assertEquals(0.5, p.tp(), 1e-12); assertEquals(2.25, p.tl(), 1e-12);
        var extremoRigido = new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35, 550);
        assertEquals(1.00, extremoRigido.s(), 1e-12); assertEquals(0.4, extremoRigido.tp(), 1e-12); assertEquals(2.5, extremoRigido.tl(), 1e-12);
        var s3 = new Parametros(Zona.Z1, Categoria.C, Suelo.S3, 8, 1, 1, 35, 275); // mitad de 200-350 m/s
        assertEquals(1.45, s3.s(), 1e-12); assertEquals(0.75, s3.tp(), 1e-12); assertEquals(1.8, s3.tl(), 1e-12);
        assertEquals(1.2, new Parametros(Zona.Z3, Categoria.C, Suelo.S4, 8, 1, 1, 35).tp(), 1e-12);
        assertEquals(1.70, new Parametros(Zona.Z2, Categoria.C, Suelo.S4, 8, 1, 1, 35).s(), 1e-12);
        assertEquals(1.30, new Parametros(Zona.Z3, Categoria.C, Suelo.S4, 8, 1, 1, 35).s(), 1e-12);
        assertEquals(2.40, new Parametros(Zona.Z1, Categoria.C, Suelo.S4, 8, 1, 1, 35).s(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Parametros(Zona.Z4, Categoria.C, Suelo.S4, 8, 1, 1, 35)); // exige estudio de sitio
        assertThrows(IllegalArgumentException.class, () -> new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35, 600)); // fuera de 350-550
    }

    @Test void factorCTieneTramoCortoYElEstaticoUsaDosComaCinco() {
        var p = norma(1, 1); // Tp = 0,6
        assertEquals(1 + 7.5 * 0.06 / 0.6, p.c(0.06), 1e-12); // T < 0,2 Tp
        assertEquals(2.5, p.c(0.12), 1e-12);                  // T = 0,2 Tp
        assertEquals(2.5, p.cEstatico(0.06), 1e-12);          // art. 18.3: estático usa 2,5 si T < Tp
        assertEquals(p.c(1.0), p.cEstatico(1.0), 1e-12);
        assertEquals(0.45 * 1.0 * 1.10 * Math.max(2.5 / 8, 0.11), p.coeficienteCortante(0.06), 1e-12);
    }

    @Test void edificioAsimetricoEnPlantaProduceAccionCruzadaYResultadosFinitos() {
        var el = porticos(2, 5, 4, 3, 0.4, 0.3, 0.5); el.get(0).setLargo(0.9); el.get(0).setAncho(0.9); // una columna mucho más rígida
        var r = FernandoHuancaAnalisisSismico.analizar(entrada(el, 2, norma(1, 1)));
        for (var d : List.of(r.estaticoX(), r.estaticoY(), r.espectralX(), r.espectralY()))
            for (double v : d.derivasMaximas()) { assertTrue(Double.isFinite(v) && v > 0); }
        assertTrue(r.espectralX().razonTorsion()[0] > 1.0);
    }
    @Test void version2018ConservaSusTablasYNoCombinaTreintaPorCiento() {
        var p = Parametros.v2018(Zona.Z4, Categoria.C, Suelo.S2_2018, 8, 1, 1, 35);
        assertEquals(1.05, p.s(), 1e-12); assertEquals(0.6, p.tp(), 1e-12); assertEquals(2.0, p.tl(), 1e-12);
        assertEquals(1.20, Parametros.v2018(Zona.Z2, Categoria.C, Suelo.S2_2018, 8, 1, 1, 35).s(), 1e-12);
        assertEquals(2.00, Parametros.v2018(Zona.Z1, Categoria.C, Suelo.S3_2018, 8, 1, 1, 35).s(), 1e-12);
        assertEquals(1.0, Parametros.v2018(Zona.Z4, Categoria.C, Suelo.S3_2018, 8, 1, 1, 35).tp(), 1e-12);
        assertEquals(2.5, p.c(0.06), 1e-12); // 2018: sin el tramo 1 + 7,5·T/Tp
        assertEquals(0.0, p.fraccionPerpendicular()); assertEquals(0.30, norma(1, 1).fraccionPerpendicular());
        assertEquals("art. 4.6.4", p.art("minimo")); assertEquals("art. 44", norma(1, 1).art("minimo"));
        assertFalse(p.requiereVerificarPeriodoDeSitio());
        assertThrows(IllegalArgumentException.class, () -> new Parametros(Version.V2018, Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35, Double.NaN)); // suelo de otra versión
        assertThrows(IllegalArgumentException.class, () -> new Parametros(Version.V2026, Zona.Z4, Categoria.C, Suelo.S2_2018, 8, 1, 1, 35, Double.NaN));
        assertEquals(4, Suelo.de(Version.V2018).size()); assertEquals(5, Suelo.de(Version.V2026).size());
    }

    @Test void ambasVersionesAnalizanYLaCombinacionDireccionalSoloAfectaALa2026() {
        var el = porticos(2, 5, 4, 3, 0.4, 0.3, 0.5); el.get(0).setLargo(0.9); el.get(0).setAncho(0.9); // acoplamiento entre direcciones
        // Mismos S, Tp y TL en ambas: 2026 S2 sin Vs30 (1,10; 0,6; 2,0) frente a 2018 S2 con 1,05; se compara con S1, idéntico en las dos.
        var n26 = new Parametros(Zona.Z4, Categoria.C, Suelo.S1, 8, 1, 1, 35);
        var n18 = Parametros.v2018(Zona.Z4, Categoria.C, Suelo.S1_2018, 8, 1, 1, 35);
        var r26 = FernandoHuancaAnalisisSismico.analizar(entrada(el, 2, n26)); var r18 = FernandoHuancaAnalisisSismico.analizar(entrada(el, 2, n18));
        assertEquals(r18.estaticoX().cortanteBase(), r26.estaticoX().cortanteBase(), 1e-9);   // fuerzas iguales
        assertEquals(r18.modos().get(0).periodo(), r26.modos().get(0).periodo(), 1e-12);
        for (int i = 0; i < 2; i++) {
            assertTrue(r26.estaticoX().derivasMaximas()[i] >= r18.estaticoX().derivasMaximas()[i] - 1e-12); // el 30 % solo suma
            assertTrue(r26.espectralX().derivasMaximas()[i] >= r18.espectralX().derivasMaximas()[i] - 1e-12);
        }
        assertTrue(r26.estaticoX().derivasMaximas()[0] > r18.estaticoX().derivasMaximas()[0]); // con acoplamiento hay efecto cruzado
        assertTrue(r18.advertencias().stream().anyMatch(a -> a.contains("2018")));
    }
    @Test void generaReportePdfConEncabezadoValido() {
        var in = entrada(porticos(2, 5, 4, 3, 0.4, 0.3, 0.5), 2, norma(1, 1));
        var p = new com.concretolab.model.Proyecto(); p.setNombre("Prueba");
        byte[] pdf = com.concretolab.service.ReporteSismo.generar(p, in, FernandoHuancaAnalisisSismico.analizar(in));
        assertTrue(pdf.length > 1000); assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        var in18 = entrada(porticos(2, 5, 4, 3, 0.4, 0.3, 0.5), 2, Parametros.v2018(Zona.Z4, Categoria.C, Suelo.S2_2018, 8, 1, 1, 35));
        assertTrue(com.concretolab.service.ReporteSismo.generar(p, in18, FernandoHuancaAnalisisSismico.analizar(in18)).length > 1000);
    }

    @Test void moduloElasticoSigueLaE060() {
        assertEquals(21538, FernandoHuancaAnalisisSismico.modulo(21), 60); // ≈ 4700·√f'c
    }
}
