package com.concretolab;

import com.concretolab.service.EscaladoRegistros;
import com.concretolab.service.EscaladoRegistros.ResultadoEscalado;
import com.concretolab.service.EspectroRespuesta;
import com.concretolab.service.GeneradorAcelerograma;
import com.concretolab.service.ImportadorRegistro;
import com.concretolab.service.ImportadorRegistro.Componente;
import com.concretolab.service.ImportadorRegistro.UnidadAceleracion;
import com.concretolab.service.NormaE030;
import com.concretolab.service.RegistroSismico;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RegistrosSismicosTest {
    static final NormaE030.Parametros NORMA = new NormaE030.Parametros(NormaE030.Zona.Z4, NormaE030.Categoria.C, NormaE030.Suelo.S2, 8, 1, 1, 35);
    static final double T = 0.6;
    static List<RegistroSismico> conjuntos;

    @TempDir Path tmp;

    @BeforeAll
    static void generar() { conjuntos = GeneradorAcelerograma.fernandoHuancaGenerarConjuntos(NORMA, 7, T, 2026L); }

    @Test
    void validacionesDelRegistro() {
        double[] a = {0, 1, 2};
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.0005, a, a)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dt");
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.5, a, a)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.01, a, new double[]{1, 2})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mismo número");
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.01, new double[]{0, Double.NaN}, new double[]{0, 1})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finito");
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.01, new double[]{1}, new double[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.01, new double[200_001], new double[200_001])).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("máximo");
        assertThatThrownBy(() -> new RegistroSismico(" ", "f", 0.01, a, a)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroSismico("x", "f", 0.01, null, a)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiasDefensivasDuracionYEscalado() {
        double[] x = {0, 1, -3, 2}, y = {0, 0.5, 1, -1};
        RegistroSismico r = new RegistroSismico("r", "f", 0.01, x, y);
        x[1] = 99;
        r.ax()[2] = 99;
        assertThat(r.ax()).containsExactly(0, 1, -3, 2);
        assertThat(r.pasos()).isEqualTo(4);
        assertThat(r.duracion()).isCloseTo(0.03, within(1e-12));
        assertThat(r.aceleracionMaxima(0)).isEqualTo(3.0);
        assertThat(r.aceleracionMaxima(1)).isEqualTo(1.0);
        RegistroSismico e = r.escalado(2.0);
        assertThat(e.ax()).containsExactly(0, 2, -6, 4);
        assertThat(e.ay()).containsExactly(0, 1, 2, -2);
        assertThat(e.nombre()).contains("2,000");
        assertThat(r.ax()).containsExactly(0, 1, -3, 2);
        assertThatThrownBy(() -> r.escalado(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.velocidad(2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void integracionRecuperaVelocidadYDesplazamientoConocidos() {
        double L = 4, dt = 0.002, D = 0.1;
        int n = (int) Math.round(L / dt) + 1;
        double[] a = new double[n], vEsp = new double[n], dEsp = new double[n];
        for (int i = 0; i < n; i++) {
            double th = Math.PI * i * dt / L, s = Math.sin(th), c = Math.cos(th), k = Math.PI / L;
            a[i] = 4 * D * k * k * (3 * s * s * c * c - Math.pow(s, 4));
            vEsp[i] = 4 * D * k * Math.pow(s, 3) * c;
            dEsp[i] = D * Math.pow(s, 4);
        }
        RegistroSismico r = new RegistroSismico("seno", "prueba", dt, a, a);
        assertThat(maxDif(r.velocidad(0), vEsp)).isLessThan(0.01 * maxAbs(vEsp));
        assertThat(maxDif(r.desplazamiento(1), dEsp)).isLessThan(0.01 * maxAbs(dEsp));
        assertThat(r.velocidad(0)[n - 1]).isCloseTo(0, within(1e-9));
        assertThat(r.desplazamiento(0)[n - 1]).isCloseTo(0, within(1e-9));
    }

    @Test
    void correccionDeLineaBaseAnulaDerivaYDesviosFinales() {
        double L = 4, dt = 0.002, D = 0.1;
        int n = (int) Math.round(L / dt) + 1;
        double[] a = new double[n], limpia = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i * dt, th = Math.PI * t / L, s = Math.sin(th), c = Math.cos(th), k = Math.PI / L;
            limpia[i] = 4 * D * k * k * (3 * s * s * c * c - Math.pow(s, 4));
            a[i] = limpia[i] + 0.05 + 0.03 * (t - L / 2);
        }
        RegistroSismico sucio = new RegistroSismico("sucio", "prueba", dt, a, a);
        RegistroSismico bueno = new RegistroSismico("limpio", "prueba", dt, limpia, limpia);
        assertThat(sucio.velocidad(0)[n - 1]).isCloseTo(0, within(1e-9));
        assertThat(sucio.desplazamiento(0)[n - 1]).isCloseTo(0, within(1e-9));
        assertThat(maxDif(sucio.desplazamiento(0), bueno.desplazamiento(0))).isLessThan(0.03 * maxAbs(bueno.desplazamiento(0)));
    }

    @Test
    void aceleracionConstanteEsUnDesfaseQueSeElimina() {
        double[] a = new double[1001];
        Arrays.fill(a, 1.0);
        RegistroSismico r = new RegistroSismico("cte", "prueba", 0.01, a, a);
        assertThat(maxAbs(r.velocidad(0))).isLessThan(1e-9);
        assertThat(maxAbs(r.desplazamiento(0))).isLessThan(1e-9);
        assertThat(r.aceleracionMaxima(0)).isEqualTo(1.0);
    }

    @Test
    void espectroDeSenoEnResonanciaCoincideConLaSolucionAnalitica() {
        double dt = 0.005, periodo = 1.0, w = 2 * Math.PI / periodo, xi = 0.05, amp = 1.0;
        double[] a = new double[(int) (80 / dt) + 1];
        for (int i = 0; i < a.length; i++) a[i] = amp * Math.sin(w * i * dt);
        double sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(a, dt, new double[]{periodo}, xi)[0];
        assertThat(sa).isCloseTo(amp / (2 * xi), within(0.01 * amp / (2 * xi)));
        double r = 0.5;
        double[] b = new double[a.length];
        for (int i = 0; i < b.length; i++) b[i] = Math.sin(r * w * i * dt);
        double sb = EspectroRespuesta.fernandoHuancaPseudoAceleracion(b, dt, new double[]{periodo}, xi)[0];
        double est = 1 / Math.sqrt(Math.pow(1 - r * r, 2) + Math.pow(2 * xi * r, 2));
        assertThat(sb).isBetween(est * 0.99, est * 1.5);
    }

    @Test
    void espectroTiendeALaAceleracionPicoParaPeriodoMuyPequeno() {
        double dt = 0.01;
        double[] a = new double[2001];
        for (int i = 0; i < a.length; i++) a[i] = 2.5 * Math.sin(2 * Math.PI * 1.0 * i * dt) + 0.7 * Math.sin(2 * Math.PI * 2.3 * i * dt);
        double pga = Arrays.stream(a).map(Math::abs).max().orElseThrow();
        double[] sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(a, dt, new double[]{0.005, 0.02, 0.03}, 0.05);
        assertThat(sa[0]).isEqualTo(pga);
        assertThat(sa[1]).isEqualTo(pga);
        assertThat(sa[2]).isCloseTo(pga, within(0.03 * pga));
    }

    @Test
    void espectroDeUnImpulsoCoincideConLaSolucionAnalitica() {
        double dt = 0.001, a0 = 1.0, xi = 0.05, periodo = 1.0;
        double[] a = new double[2001];
        a[10] = a0;
        double dv = a0 * dt, w = 2 * Math.PI / periodo, wd = w * Math.sqrt(1 - xi * xi);
        double tPico = Math.atan2(wd, xi * w) / wd;
        double umax = dv / wd * Math.exp(-xi * w * tPico) * Math.sin(wd * tPico);
        double sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(a, dt, new double[]{periodo}, xi)[0];
        assertThat(sa).isCloseTo(w * w * umax, within(0.01 * w * w * umax));
        double[] grueso = EspectroRespuesta.fernandoHuancaPseudoAceleracion(new double[]{0, 1, 0, -1, 0, 1, 0}, 0.1, new double[]{0.05, 0.1}, 0.05);
        assertThat(grueso[0]).isPositive().isFinite();
    }

    @Test
    void rejillaDePeriodosEsLogaritmica() {
        double[] p = EspectroRespuesta.fernandoHuancaRejillaPeriodos(0.1, 10, 5);
        assertThat(p).hasSize(5);
        assertThat(p[0]).isEqualTo(0.1);
        assertThat(p[4]).isEqualTo(10);
        assertThat(p[2]).isCloseTo(1.0, within(1e-12));
        assertThatThrownBy(() -> EspectroRespuesta.fernandoHuancaRejillaPeriodos(1, 0.5, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    private Path archivo(String nombre, String contenido) throws IOException {
        Path p = tmp.resolve(nombre);
        Files.writeString(p, contenido);
        return p;
    }

    @Test
    void importaUnaColumnaConDtDelEncabezadoYConvierteGal() throws IOException {
        Path p = archivo("a.txt", "REGISTRO CISMID\nUNIDADES: cm/s2\nDT = 0.005 s\n10\n-20\n30\n40\n");
        Componente c = ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.CM_S2, null);
        assertThat(c.dt()).isEqualTo(0.005);
        assertThat(c.a()).containsExactly(0.10, -0.20, 0.30, 0.40);
        assertThat(ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.M_S2, 0.01).dt()).isEqualTo(0.01);
        assertThat(ImportadorRegistro.fernandoHuancaImportarUnaComponente(archivo("b.txt", "Estacion X\ndt: 0.02\n1\n2\n3\n"), UnidadAceleracion.M_S2, null).dt()).isEqualTo(0.02);
        assertThat(ImportadorRegistro.fernandoHuancaImportarUnaComponente(archivo("c.txt", "DELTA T 0.01\n1\n2\n3\n"), UnidadAceleracion.M_S2, null).dt()).isEqualTo(0.01);
        assertThat(ImportadorRegistro.fernandoHuancaImportarUnaComponente(archivo("d.txt", "INTERVALO 0.005\n1\n2\n3\n"), UnidadAceleracion.M_S2, null).dt()).isEqualTo(0.005);
    }

    @Test
    void importaDosColumnasTiempoAceleracionConComaDecimalYTabulador() throws IOException {
        Path p = archivo("e.txt", "TIEMPO\tACEL (g)\n0,00\t0,10\n0,01\t0,20\n0,02\t-0,30\n0,03\t0,05\n");
        Componente c = ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.G, null);
        assertThat(c.dt()).isCloseTo(0.01, within(1e-12));
        assertThat(c.a()[0]).isCloseTo(0.98, within(0.01));
        assertThat(c.a()[2]).isCloseTo(-0.30 * 9.80665, within(1e-9));
    }

    @Test
    void importaTresColumnasConComaOPuntoYComaYDevuelveRegistro() throws IOException {
        Path coma = archivo("f.csv", "t,ax,ay\n0.0,1.0,-1.0\n0.01,2.0,-2.0\n0.02,3.0,-3.0\n");
        RegistroSismico r = ImportadorRegistro.fernandoHuancaImportar(coma, UnidadAceleracion.M_S2, null);
        assertThat(r.dt()).isCloseTo(0.01, within(1e-12));
        assertThat(r.ax()).containsExactly(1.0, 2.0, 3.0);
        assertThat(r.ay()).containsExactly(-1.0, -2.0, -3.0);
        assertThat(r.fuente()).contains("f.csv");
        Path pc = archivo("g.txt", "0,00;1,5;-0,5\n0,01;2,5;-1,5\n0,02;0,5;0,5\n");
        RegistroSismico r2 = ImportadorRegistro.fernandoHuancaImportar(pc, UnidadAceleracion.CM_S2, null);
        assertThat(r2.ax()[0]).isCloseTo(0.015, within(1e-12));
        assertThat(r2.ay()[1]).isCloseTo(-0.015, within(1e-12));
    }

    @Test
    void importaFormatoPeerConVariasAceleracionesPorLinea() throws IOException {
        Path p = archivo("h.AT2", "PEER NGA STRONG MOTION DATABASE RECORD\nLima 1974, Station X\nACCELERATION TIME SERIES IN UNITS OF G\nNPTS= 10, DT= .0100 SEC\n"
                + "0.0010 0.0020 0.0030 0.0040 0.0050\n0.0060 0.0070 0.0080 0.0090 0.0100\n");
        Componente c = ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.G, null);
        assertThat(c.dt()).isEqualTo(0.01);
        assertThat(c.a()).hasSize(10);
        assertThat(c.a()[9]).isCloseTo(0.01 * 9.80665, within(1e-12));
    }

    @Test
    void unaSolaComponenteConImportarPideElSegundoArchivoYSugiereUnidades() throws IOException {
        Path p = archivo("i.txt", "Registro EW  (cm/s2)\nDT=0.01\n1\n2\n3\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportar(p, UnidadAceleracion.M_S2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dos componentes")
                .hasMessageContaining("cm/s²");
    }

    @Test
    void importaParDeArchivosYValidaCoherencia() throws IOException {
        Path x = archivo("ew.txt", "DT=0.01\n1\n2\n3\n4\n");
        Path y = archivo("ns.txt", "DT=0.01\n-1\n-2\n-3\n-4\n");
        RegistroSismico r = ImportadorRegistro.fernandoHuancaImportarPar(x, y, UnidadAceleracion.M_S2, null);
        assertThat(r.ax()).containsExactly(1, 2, 3, 4);
        assertThat(r.ay()).containsExactly(-1, -2, -3, -4);
        Path z = archivo("otro.txt", "DT=0.02\n1\n2\n3\n4\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarPar(x, z, UnidadAceleracion.M_S2, null)).hasMessageContaining("distinto paso de tiempo");
        Path largo = archivo("largo.txt", "DT=0.01\n" + "1\n".repeat(50));
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarPar(x, largo, UnidadAceleracion.M_S2, null)).hasMessageContaining("distinto número");
    }

    @Test
    void archivoConBasuraDaErroresClaros() throws IOException {
        Path texto = archivo("j.txt", "esto no es un acelerograma\nsolo texto\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(texto, UnidadAceleracion.M_S2, 0.01))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no contiene datos numéricos");
        Path mezcla = archivo("k.txt", "1.0\n2.0\nabc\n3.0\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(mezcla, UnidadAceleracion.M_S2, 0.01))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Línea 3");
        Path columnas = archivo("l.txt", "0.0 1.0\n0.01 2.0 3.0\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(columnas, UnidadAceleracion.M_S2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("columnas");
        Path sinDt = archivo("m.txt", "1\n2\n3\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(sinDt, UnidadAceleracion.M_S2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dt");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(tmp.resolve("no-existe.txt"), UnidadAceleracion.M_S2, 0.01))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No se encontró");
    }

    @Test
    void dtNoUniformeSeRechaza() throws IOException {
        Path p = archivo("n.txt", "0.00 1.0\n0.01 2.0\n0.02 3.0\n0.05 4.0\n0.06 5.0\n");
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.M_S2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es uniforme");
    }

    @Test
    void archivoMayorDe10MbSeRechaza() throws IOException {
        Path p = tmp.resolve("grande.txt");
        Files.write(p, new byte[10 * 1024 * 1024 + 10]);
        assertThatThrownBy(() -> ImportadorRegistro.fernandoHuancaImportarUnaComponente(p, UnidadAceleracion.M_S2, 0.01)).hasMessageContaining("10 MB");
    }

    @Test
    void generadorEsReproducibleYLaSemillaCambiaElResultado() {
        List<RegistroSismico> igual = GeneradorAcelerograma.fernandoHuancaGenerarConjuntos(NORMA, 2, T, 2026L);
        List<RegistroSismico> otra = GeneradorAcelerograma.fernandoHuancaGenerarConjuntos(NORMA, 2, T, 7L);
        assertThat(igual.get(0).ax()).isEqualTo(conjuntos.get(0).ax());
        assertThat(igual.get(1).ay()).isEqualTo(conjuntos.get(1).ay());
        assertThat(otra.get(0).ax()).isNotEqualTo(conjuntos.get(0).ax());
        assertThat(conjuntos.get(0).ax()).isNotEqualTo(conjuntos.get(0).ay());
        assertThat(conjuntos.get(0).ax()).isNotEqualTo(conjuntos.get(1).ax());
    }

    @Test
    void generadorProduceMetadatosPgaYDuracionRazonables() {
        assertThat(conjuntos).hasSize(7);
        assertThat(conjuntos.get(0).nombre()).isEqualTo("Sintetico 1 (E.030 Z=0,45 S2)");
        assertThat(conjuntos.get(6).nombre()).startsWith("Sintetico 7");
        for (RegistroSismico r : conjuntos) {
            assertThat(r.fuente()).startsWith("Acelerograma sintetico espectro-compatible generado por ConcretoLab (no es un registro real)");
            assertThat(r.dt()).isEqualTo(0.01);
            assertThat(r.duracion()).isCloseTo(40.0, within(0.011));
            for (int c = 0; c < 2; c++) {
                double pga = r.aceleracionMaxima(c) / EscaladoRegistros.FERNANDO_HUANCA_G;
                assertThat(pga).as(r.nombre() + " comp " + c).isBetween(0.1, 1.5);
                assertThat(maxAbs(r.velocidad(c))).isLessThan(2.0);
                assertThat(maxAbs(r.desplazamiento(c))).isLessThan(2.0);
            }
        }
    }

    @Test
    void espectroGeneradoSigueAlObjetivoEnElIntervaloDeInteres() {
        double[] per = EspectroRespuesta.fernandoHuancaRejillaPeriodos(0.2 * T, 1.5 * T, 60);
        double[] objetivo = EscaladoRegistros.fernandoHuancaEspectroDiseno(NORMA, per);
        for (RegistroSismico r : conjuntos) {
            for (double[] comp : new double[][]{r.ax(), r.ay()}) {
                double[] sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(comp, r.dt(), per, 0.05);
                for (int j = 0; j < per.length; j++)
                    assertThat(sa[j] / objetivo[j]).as(r.nombre() + " T=" + per[j]).isBetween(0.85, 1.25);
            }
        }
    }

    @Test
    void escaladoCumpleElPromedioSrssYUsaElMismoFactorEnAmbasComponentes() {
        ResultadoEscalado res = EscaladoRegistros.fernandoHuancaEscalar(conjuntos, NORMA, T);
        assertThat(res.periodos().length).isGreaterThanOrEqualTo(40);
        assertThat(res.periodos()[0]).isCloseTo(0.2 * T, within(1e-12));
        assertThat(res.periodos()[res.periodos().length - 1]).isCloseTo(1.5 * T, within(1e-12));
        assertThat(res.razonMinima()).isGreaterThanOrEqualTo(0.999);
        assertThat(res.cumple()).isTrue();
        assertThat(res.escalados()).hasSize(7);
        assertThat(res.factores()).hasSize(7);
        for (int j = 0; j < res.periodos().length; j++)
            assertThat(res.espectroPromedioSrss()[j]).isGreaterThanOrEqualTo(0.999 * res.espectroDiseno()[j]);
        double[] prom = new double[res.periodos().length];
        for (int i = 0; i < 7; i++) {
            RegistroSismico orig = conjuntos.get(i), esc = res.escalados().get(i);
            double f = res.factores()[i];
            for (int k = 0; k < orig.pasos(); k += 97) {
                assertThat(esc.ax()[k]).isCloseTo(orig.ax()[k] * f, within(1e-9));
                assertThat(esc.ay()[k]).isCloseTo(orig.ay()[k] * f, within(1e-9));
            }
            double[] sx = EspectroRespuesta.fernandoHuancaPseudoAceleracion(esc.ax(), esc.dt(), res.periodos(), 0.05);
            double[] sy = EspectroRespuesta.fernandoHuancaPseudoAceleracion(esc.ay(), esc.dt(), res.periodos(), 0.05);
            for (int j = 0; j < prom.length; j++) prom[j] += Math.hypot(sx[j], sy[j]) / 7;
        }
        for (int j = 0; j < prom.length; j++) {
            assertThat(prom[j]).isCloseTo(res.espectroPromedioSrss()[j], within(1e-6 * prom[j]));
            assertThat(prom[j]).isGreaterThanOrEqualTo(0.999 * res.espectroDiseno()[j]);
        }
    }

    @Test
    void escaladoAvisaSiHayMenosConjuntosQueElMinimoDeLaVersion() {
        ResultadoEscalado res = EscaladoRegistros.fernandoHuancaEscalar(conjuntos.subList(0, 3), NORMA, T);
        assertThat(res.razonMinima()).isGreaterThanOrEqualTo(0.999);
        assertThat(res.cumple()).isFalse();
        assertThat(res.avisos()).anyMatch(s -> s.contains("al menos 7 conjuntos"));
        assertThat(EscaladoRegistros.fernandoHuancaMinimoConjuntos(NormaE030.Version.V2018)).isEqualTo(3);
        assertThat(EscaladoRegistros.fernandoHuancaMinimoConjuntos(NormaE030.Version.V2026)).isEqualTo(7);
        assertThatThrownBy(() -> EscaladoRegistros.fernandoHuancaEscalar(List.of(), NORMA, T)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escaladoDeRegistrosNoCompatiblesAjustaFactorYReportaAvisos() {
        double dt = 0.01;
        double[] a = new double[3001], b = new double[3001];
        for (int i = 0; i < a.length; i++) { a[i] = Math.sin(2 * Math.PI * 1.2 * i * dt); b[i] = 0.5 * Math.sin(2 * Math.PI * 0.8 * i * dt); }
        RegistroSismico r = new RegistroSismico("seno", "prueba", dt, a, b);
        ResultadoEscalado res = EscaladoRegistros.fernandoHuancaEscalar(List.of(r, r, r), NormaE030.Parametros.v2018(NormaE030.Zona.Z3, NormaE030.Categoria.C, NormaE030.Suelo.S2_2018, 8, 1, 1, 35), T);
        assertThat(res.razonMinima()).isGreaterThanOrEqualTo(0.999);
        assertThat(res.cumple()).isTrue();
        assertThat(res.avisos()).anyMatch(s -> s.contains("47.6"));
    }

    @Test
    void escaladoEspectroCompatibleNoReduceYCumpleElArt476() {
        ResultadoEscalado res = EscaladoRegistros.fernandoHuancaEscalar(conjuntos, NORMA, T, true);
        assertThat(res.cumple()).isTrue();
        assertThat(res.razonMinima()).isGreaterThanOrEqualTo(0.999);
        assertThat(res.avisos()).noneMatch(s -> s.contains("47.6"));
        assertThat(Arrays.stream(res.factores()).min().orElseThrow()).isGreaterThanOrEqualTo(1.0);
        double minimo = Double.POSITIVE_INFINITY;
        for (RegistroSismico r : res.escalados())
            for (double[] c : new double[][]{r.ax(), r.ay()}) {
                double[] sa = EspectroRespuesta.fernandoHuancaPseudoAceleracion(c, r.dt(), res.periodos(), 0.05);
                for (int j = 0; j < sa.length; j++) minimo = Math.min(minimo, sa[j] / res.espectroDiseno()[j]);
            }
        assertThat(minimo).isGreaterThanOrEqualTo(0.9 - 1e-6);
        System.out.println("COMPAT razonMinima=" + res.razonMinima() + " factores=" + Arrays.toString(res.factores()) + " minComp=" + minimo);
    }

    private static double maxAbs(double[] v) { return Arrays.stream(v).map(Math::abs).max().orElse(0); }

    private static double maxDif(double[] a, double[] b) {
        double m = 0;
        for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] - b[i]));
        return m;
    }
}
