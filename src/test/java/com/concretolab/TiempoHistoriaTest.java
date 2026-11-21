package com.concretolab;

import com.concretolab.service.AnalisisTiempoHistoria;
import com.concretolab.service.FernandoHuancaAnalisisSismico.ModeloDinamico;
import com.concretolab.service.NormaE030;
import com.concretolab.service.RegistroSismico;
import com.concretolab.service.ResultadoTiempoHistoria;
import com.concretolab.service.VerificacionTiempoHistoria;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Pruebas físicas del motor tiempo-historia (sin Spring): oscilador exacto, periodo, simetría, convergencia y verificación E.030. */
class TiempoHistoriaTest {

    private static final double M1 = 2.0, KX = 1000.0, KY = 4000.0;

    private static List<List<double[]>> puntos(int pisos) {
        List<List<double[]>> l = new ArrayList<>();
        for (int s = 0; s < pisos; s++)
            l.add(List.of(new double[]{0, 0}, new double[]{-5, -4}, new double[]{5, -4}, new double[]{5, 4}, new double[]{-5, 4}));
        return l;
    }

    /** Edificio de 1 piso: ux (kx), uy (ky) y rz desacoplados; periodos {Tx, Ty, Tθ} con Tx el fundamental. */
    private static ModeloDinamico unPiso() {
        double ix = M1 * 8, kt = 6000;
        double[][] k = {{KX, 0, 0}, {0, KY, 0}, {0, 0, kt}};
        double[] per = {2 * Math.PI / Math.sqrt(KX / M1), 2 * Math.PI / Math.sqrt(KY / M1), 2 * Math.PI / Math.sqrt(kt / ix)};
        return new ModeloDinamico(1, new double[]{M1, M1, ix}, k, new double[]{3}, new double[]{3},
                new double[]{0}, new double[]{0}, puntos(1), per);
    }

    /** Edificio de 2 pisos simétrico (bloques X, Y y giro desacoplados) con periodos reales de los autovalores. */
    private static ModeloDinamico dosPisos() {
        double m = 2, ip = 16;
        double[] masas = {m, m, ip, m, m, ip};
        double[][] k = new double[6][6];
        double[] kd = {1000, 1500, 5000};
        for (int d = 0; d < 3; d++) {
            k[d][d] += 2 * kd[d]; k[d][d + 3] -= kd[d]; k[d + 3][d] -= kd[d]; k[d + 3][d + 3] += kd[d];
        }
        // Autovalores de M^-1/2 K M^-1/2
        RealMatrix a = new Array2DRowRealMatrix(6, 6);
        for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) a.setEntry(i, j, k[i][j] / Math.sqrt(masas[i] * masas[j]));
        double[] ev = new EigenDecomposition(a).getRealEigenvalues();
        double[] per = Arrays.stream(ev).map(l -> 2 * Math.PI / Math.sqrt(l)).boxed().sorted((p, q) -> Double.compare(q, p)).mapToDouble(Double::doubleValue).toArray();
        return new ModeloDinamico(2, masas, k, new double[]{3, 6}, new double[]{3, 3}, new double[]{0, 0}, new double[]{0, 0}, puntos(2), per);
    }

    private static RegistroSismico registro(double dt, double duracion, DoubleUnaryOperator fx, DoubleUnaryOperator fy) {
        int n = (int) Math.round(duracion / dt) + 1;
        double[] ax = new double[n], ay = new double[n];
        for (int i = 0; i < n; i++) { ax[i] = fx.applyAsDouble(i * dt); ay[i] = fy.applyAsDouble(i * dt); }
        return new RegistroSismico("sintetico", "prueba", dt, ax, ay);
    }

    private static double maximoAbs(double[] x, int desde) {
        double m = 0;
        for (int i = desde; i < x.length; i++) m = Math.max(m, Math.abs(x[i]));
        return m;
    }

    private static double[] columna(double[][] x, int piso) {
        double[] c = new double[x.length];
        for (int i = 0; i < x.length; i++) c[i] = x[i][piso];
        return c;
    }

    @Test
    void osciladorArmonicoCoincideConRegimenPermanente() {
        var modelo = unPiso();
        double t0 = modelo.periodos()[0], w0 = 2 * Math.PI / t0, xi = 0.05, r = 0.5, A = 1.5, w = r * w0;
        double dt = 0.002;
        var res = AnalisisTiempoHistoria.resolver(modelo, registro(dt, 60 * t0, t -> A * Math.sin(w * t), t -> 0), xi);
        double esperado = A / (w0 * w0) / Math.sqrt(Math.pow(1 - r * r, 2) + Math.pow(2 * xi * r, 2));
        int desde = (int) (res.pasos() * 0.8);
        assertThat(maximoAbs(columna(res.ux(), 0), desde)).isCloseTo(esperado, within(0.015 * esperado));
        // Cortante basal = fuerza del resorte + amortiguador: amplitud k·û·√(1+(2ξr)²)
        double vEsperado = KX * esperado * Math.sqrt(1 + Math.pow(2 * xi * r, 2));
        assertThat(maximoAbs(res.cortanteBaseX(), desde)).isCloseTo(vEsperado, within(0.02 * vEsperado));
        assertThat(res.cortanteBaseMaximoX()).isGreaterThan(0);
    }

    @Test
    void sinCargaNadaSeMueve() {
        var res = AnalisisTiempoHistoria.resolver(dosPisos(), registro(0.01, 5, t -> 0, t -> 0), 0.0);
        assertThat(res.derivaMaximaX()).isZero();
        assertThat(res.derivaMaximaY()).isZero();
        assertThat(res.desplazamientoTechoMaximoX()).isZero();
        assertThat(res.cortanteBaseMaximoX()).isZero();
        assertThat(res.cortanteBaseMaximoY()).isZero();
        for (double[] fila : res.ux()) assertThat(maximoAbs(fila, 0)).isZero();
    }

    @Test
    void periodoDeVibracionLibreEsT1() {
        var modelo = dosPisos();
        double t1 = modelo.periodos()[0];
        double dt = 0.001;
        // Impulso corto en el primer paso; luego vibración libre sin amortiguamiento
        var res = AnalisisTiempoHistoria.resolver(modelo, registro(dt, 25, t -> t < 0.0015 ? 20.0 : 0.0, t -> 0), 0.0);
        // Coordenada modal del modo fundamental: q = v1ᵀ M^½ u, con v1 el autovector del menor autovalor
        double[] m = modelo.masas();
        RealMatrix a = new Array2DRowRealMatrix(6, 6);
        for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) a.setEntry(i, j, modelo.rigidez()[i][j] / Math.sqrt(m[i] * m[j]));
        var eig = new EigenDecomposition(a);
        double[] ev = eig.getRealEigenvalues();
        // El impulso es en X: solo excita los modos con participación en X. El modo fundamental del modelo puede ser Y o
        // torsional (el edificio no es simétrico en ambas direcciones), así que se busca el primer modo con participación
        // en X proyectando la excitación (vector √M·r) sobre cada subespacio propio (válido aun con autovalores repetidos).
        double[] excitacion = new double[6];
        for (int g = 0; g < 6; g++) excitacion[g] = (g % 3 == 0) ? Math.sqrt(m[g]) : 0.0;
        double[] v1 = null;
        double lambdaX = Double.NaN;
        double[] orden = ev.clone();
        java.util.Arrays.sort(orden);
        for (double lambda : orden) {
            double[] candidato = new double[6];
            for (int i = 0; i < 6; i++) {
                if (Math.abs(ev[i] - lambda) > 1e-6 * Math.abs(lambda)) continue;
                double[] vi = eig.getEigenvector(i).toArray();
                double c = 0;
                for (int g = 0; g < 6; g++) c += vi[g] * excitacion[g];
                for (int g = 0; g < 6; g++) candidato[g] += c * vi[g];
            }
            double norma = 0;
            for (double x : candidato) norma += x * x;
            norma = Math.sqrt(norma);
            if (norma > 1e-6) {
                for (int g = 0; g < 6; g++) candidato[g] /= norma;
                v1 = candidato;
                lambdaX = lambda;
                break;
            }
        }
        assertThat(v1).as("debe existir un modo con participación en X").isNotNull();
        double periodoEsperado = 2 * Math.PI / Math.sqrt(lambdaX);
        double[] q = new double[res.pasos()];
        for (int p = 0; p < q.length; p++) {
            double[] u = {res.ux()[p][0], res.uy()[p][0], res.rz()[p][0], res.ux()[p][1], res.uy()[p][1], res.rz()[p][1]};
            for (int g = 0; g < 6; g++) q[p] += v1[g] * Math.sqrt(m[g]) * u[g];
        }
        // Cruces ascendentes por cero (interpolados), tras el impulso
        List<Double> cruces = new ArrayList<>();
        for (int p = 10; p < q.length - 1; p++)
            if (q[p] < 0 && q[p + 1] >= 0) cruces.add((p + q[p] / (q[p] - q[p + 1])) * dt);
        assertThat(cruces.size()).isGreaterThan(10);
        double periodo = (cruces.get(cruces.size() - 1) - cruces.get(0)) / (cruces.size() - 1);
        assertThat(periodo).isCloseTo(periodoEsperado, within(0.01 * periodoEsperado));
    }

    @Test
    void simetriaSinMovimientoEnYNiGiro() {
        var res = AnalisisTiempoHistoria.resolver(dosPisos(),
                registro(0.01, 10, t -> 3 * Math.sin(2 * Math.PI * 1.3 * t) + Math.sin(2 * Math.PI * 4 * t), t -> 0), 0.05);
        assertThat(res.desplazamientoTechoMaximoX()).isGreaterThan(1e-4);
        assertThat(res.desplazamientoTechoMaximoY()).isLessThan(1e-12);
        for (int p = 0; p < res.pasos(); p++)
            for (int s = 0; s < 2; s++) {
                assertThat(Math.abs(res.uy()[p][s])).isLessThan(1e-12);
                assertThat(Math.abs(res.rz()[p][s])).isLessThan(1e-12);
            }
        assertThat(res.cortanteBaseMaximoY()).isLessThan(1e-9);
    }

    @Test
    void convergenciaConDtYMitad() {
        DoubleUnaryOperator fx = t -> 2 * Math.sin(2 * Math.PI * 1.25 * t) + Math.sin(2 * Math.PI * 3 * t);
        DoubleUnaryOperator fy = t -> 1.5 * Math.sin(2 * Math.PI * 0.9 * t);
        var r1 = AnalisisTiempoHistoria.resolver(dosPisos(), registro(0.01, 10, fx, fy), 0.05);
        var r2 = AnalisisTiempoHistoria.resolver(dosPisos(), registro(0.005, 10, fx, fy), 0.05);
        assertThat(r1.desplazamientoTechoMaximoX()).isCloseTo(r2.desplazamientoTechoMaximoX(), within(0.01 * r2.desplazamientoTechoMaximoX()));
        assertThat(r1.desplazamientoTechoMaximoY()).isCloseTo(r2.desplazamientoTechoMaximoY(), within(0.01 * r2.desplazamientoTechoMaximoY()));
        assertThat(r1.derivaMaximaX()).isCloseTo(r2.derivaMaximaX(), within(0.01 * r2.derivaMaximaX()));
        assertThat(r1.cortanteBaseMaximoX()).isCloseTo(r2.cortanteBaseMaximoX(), within(0.01 * r2.cortanteBaseMaximoX()));
    }

    @Test
    void desplazamientoAbsolutoEsRelativoMasSuelo() {
        var reg = registro(0.01, 8, t -> 2 * Math.sin(2 * Math.PI * 1.1 * t), t -> Math.cos(2 * Math.PI * 0.7 * t));
        var res = AnalisisTiempoHistoria.resolver(dosPisos(), reg, 0.05);
        assertThat(res.sueloX()).containsExactly(reg.desplazamiento(0));
        assertThat(res.sueloY()).containsExactly(reg.desplazamiento(1));
        for (int p = 0; p < res.pasos(); p++) {
            assertThat(res.desplazamientoAbsolutoX(p, 1)).isEqualTo(res.ux()[p][1] + res.sueloX()[p]);
            assertThat(res.desplazamientoAbsolutoY(p, 1)).isEqualTo(res.uy()[p][1] + res.sueloY()[p]);
        }
    }

    @Test
    void validaEntradas() {
        var modelo = dosPisos();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AnalisisTiempoHistoria.resolver(modelo, registro(0.01, 2, t -> 0, t -> 0), 0.25));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AnalisisTiempoHistoria.resolver(modelo, registro(0.2, 2, t -> 0, t -> 0), 0.05));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AnalisisTiempoHistoria.resolver(modelo, new RegistroSismico("x", "y", 0.01, new double[10], new double[9]), 0.05));
    }

    // ---------- verificación E.030 ----------

    private static ResultadoTiempoHistoria sintetico(double d0, double d1, double e0, double e1) {
        double[] z = new double[1];
        return new ResultadoTiempoHistoria("s", 0.01, 1, 2, z, new double[1][2], new double[1][2], new double[1][2], z, z, z, z,
                new double[1][2], new double[1][2], new double[]{d0, d1}, new double[]{e0, e1}, Math.max(d0, d1), Math.max(e0, e1),
                0, 0, z, z, 0, 0, List.of());
    }

    private static List<ResultadoTiempoHistoria> conjunto(int n) {
        double[] base = {0.004, 0.005, 0.006, 0.007, 0.008, 0.009, 0.010};
        List<ResultadoTiempoHistoria> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(sintetico(base[i], base[i] * 0.8, base[i] * 0.5, base[i] * 0.4));
        return l;
    }

    @Test
    void verificacionPromedioMaximoYConjuntos() {
        var n2026 = new NormaE030.Parametros(NormaE030.Zona.Z4, NormaE030.Categoria.C, NormaE030.Suelo.S1, 8, 1, 1, 45);
        var n2018 = NormaE030.Parametros.v2018(NormaE030.Zona.Z4, NormaE030.Categoria.C, NormaE030.Suelo.S1_2018, 8, 1, 1, 35);

        // Siete conjuntos: promedio = 0,007 (el máximo 0,010 incumpliría el límite 0,00875)
        var v7 = AnalisisTiempoHistoria.verificar(conjunto(7), n2026);
        assertThat(v7.limite()).isCloseTo(0.00875, within(1e-12));
        assertThat(v7.criterio()).isEqualTo("promedio");
        assertThat(v7.conjuntosSuficientes()).isTrue();
        assertThat(v7.pisosX().get(0).derivaDiseno()).isCloseTo(0.007, within(1e-12));
        assertThat(v7.pisosX().get(0).cumple()).isTrue();
        assertThat(v7.cumple()).isTrue();
        assertThat(v7.avisos()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(String.join(" ", v7.avisos())).contains("VERIFICACIÓN complementaria").contains("R = 1").contains("empotrada");

        // 2018 con siete conjuntos también promedia
        assertThat(AnalisisTiempoHistoria.verificar(conjunto(7), n2018).pisosX().get(0).derivaDiseno()).isCloseTo(0.007, within(1e-12));

        // 2018 con tres conjuntos {0,004; 0,005; 0,006}: máximo 0,006 cumple; suficientes
        var v3 = AnalisisTiempoHistoria.verificar(conjunto(3), n2018);
        assertThat(v3.criterio()).isEqualTo("máximo");
        assertThat(v3.conjuntosSuficientes()).isTrue();
        assertThat(v3.pisosX().get(0).derivaDiseno()).isCloseTo(0.006, within(1e-12));

        // 2018 con menos de siete toma el máximo aunque el promedio cumpliría
        var lista = new ArrayList<>(conjunto(3));
        lista.set(2, sintetico(0.010, 0.002, 0.001, 0.001));
        var vMax = AnalisisTiempoHistoria.verificar(lista, n2018);
        assertThat(vMax.pisosX().get(0).derivaDiseno()).isCloseTo(0.010, within(1e-12));
        assertThat(vMax.pisosX().get(0).cumple()).isFalse();
        assertThat(vMax.cumple()).isFalse();

        // 2026 con tres conjuntos: insuficientes
        var v3_2026 = AnalisisTiempoHistoria.verificar(conjunto(3), n2026);
        assertThat(v3_2026.conjuntosSuficientes()).isFalse();
        assertThat(v3_2026.cumple()).isFalse();
        assertThat(v3_2026.conjuntos()).isEqualTo(3);

        // 2018 con dos conjuntos: insuficientes
        assertThat(AnalisisTiempoHistoria.verificar(conjunto(2), n2018).conjuntosSuficientes()).isFalse();
    }
}