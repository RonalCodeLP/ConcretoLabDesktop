package com.concretolab.service;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnalisisTiempoHistoria {
    private AnalisisTiempoHistoria() {}

    private static final double FERNANDO_HUANCA_DERIVA_CONCRETO = 0.007;
    private static final double FERNANDO_HUANCA_FACTOR_LIMITE = 1.25;

    public static ResultadoTiempoHistoria fernandoHuancaResolver(FernandoHuancaAnalisisSismico.ModeloDinamico modelo,
                                                   RegistroSismico registro, double amortiguamiento) {
        if (modelo == null || registro == null) throw new IllegalArgumentException("Falta el modelo o el registro sísmico.");
        double dt = registro.dt();
        double[] ax = registro.ax(), ay = registro.ay();
        if (!(dt > 0 && dt <= 0.1)) throw new IllegalArgumentException("El paso de tiempo dt debe estar en (0; 0,1] s.");
        if (ax == null || ay == null || ax.length != ay.length) throw new IllegalArgumentException("Las componentes X e Y del registro deben tener igual longitud.");
        if (ax.length < 2) throw new IllegalArgumentException("El registro debe tener al menos dos muestras.");
        for (int i = 0; i < ax.length; i++)
            if (!Double.isFinite(ax[i]) || !Double.isFinite(ay[i])) throw new IllegalArgumentException("El registro contiene valores no finitos (muestra " + i + ").");
        if (!(amortiguamiento >= 0 && amortiguamiento < 0.2)) throw new IllegalArgumentException("El amortiguamiento debe estar en [0; 0,2).");

        final int S = modelo.pisos(), n = modelo.grados(), N = ax.length;
        if (S < 1 || modelo.masas().length != n || modelo.rigidez().length != n) throw new IllegalArgumentException("Modelo dinámico inconsistente.");
        double[] m = modelo.masas();
        for (double mi : m) if (!(mi > 0)) throw new IllegalArgumentException("Las masas del modelo deben ser positivas.");
        double[][] K = modelo.rigidez();
        double[] per = modelo.periodos();
        if (per == null || per.length == 0) throw new IllegalArgumentException("El modelo no tiene periodos.");

        List<String> avisos = new ArrayList<>();
        double t1 = per[0];
        double tMin = Double.MAX_VALUE;
        for (double p : per) if (p > 0) tMin = Math.min(tMin, p);
        if (!(t1 > 0) || tMin == Double.MAX_VALUE) throw new IllegalArgumentException("Periodos del modelo inválidos.");
        if (dt > tMin / 10.0)
            avisos.add(String.format(Locale.ROOT, "dt = %.4f s es mayor que T_menor/10 = %.4f s: Newmark es estable pero se pierde precisión en los modos altos.", dt, tMin / 10.0));

        double t2 = 0.2 * t1;
        if (t2 < tMin) {
            t2 = tMin;
            avisos.add(String.format(Locale.ROOT, "0,2·T1 = %.4f s queda por debajo del menor periodo del modelo; el segundo punto de Rayleigh se toma en T = %.4f s.", 0.2 * t1, tMin));
        }
        double w1 = 2 * Math.PI / t1, w2 = 2 * Math.PI / t2;
        double alfa, beta;
        if (Math.abs(w2 - w1) < 1e-9 * w1) {
            alfa = 2 * amortiguamiento * w1;
            beta = 0;
        } else {
            alfa = 2 * amortiguamiento * w1 * w2 / (w1 + w2);
            beta = 2 * amortiguamiento / (w1 + w2);
        }

        double[][] C = new double[n][n];
        double[][] Kef = new double[n][n];
        double c1 = 2.0 / dt, c2 = 4.0 / (dt * dt);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = beta * K[i][j] + (i == j ? alfa * m[i] : 0);
                Kef[i][j] = K[i][j] + c1 * C[i][j] + (i == j ? c2 * m[i] : 0);
            }
        }
        DecompositionSolver solver = new LUDecomposition(new Array2DRowRealMatrix(Kef, false)).getSolver();

        double[][] U = new double[N][n];
        double[] u = new double[n], v = new double[n], a = new double[n];
        double[] cbx = new double[N], cby = new double[N];
        for (int s = 0; s < S; s++) { a[3 * s] = -ax[0]; a[3 * s + 1] = -ay[0]; }
        fernandoHuancaRegistrarCortante(m, S, a, ax[0], ay[0], cbx, cby, 0);

        double[] rhs = new double[n], w1v = new double[n], w2v = new double[n];
        for (int i = 1; i < N; i++) {
            for (int j = 0; j < n; j++) {
                w1v[j] = c2 * u[j] + 2 * c1 * v[j] + a[j];
                w2v[j] = c1 * u[j] + v[j];
            }
            for (int j = 0; j < n; j++) {
                double carga = 0;
                if (j % 3 == 0) carga = -m[j] * ax[i];
                else if (j % 3 == 1) carga = -m[j] * ay[i];
                double s = carga + m[j] * w1v[j];
                for (int k = 0; k < n; k++) s += C[j][k] * w2v[k];
                rhs[j] = s;
            }
            double[] un = solver.solve(new ArrayRealVector(rhs, false)).toArray();
            for (int j = 0; j < n; j++) {
                double du = un[j] - u[j];
                double vn = c1 * du - v[j];
                double an = c2 * du - 2 * c1 * v[j] - a[j];
                u[j] = un[j]; v[j] = vn; a[j] = an;
            }
            System.arraycopy(u, 0, U[i], 0, n);
            fernandoHuancaRegistrarCortante(m, S, a, ax[i], ay[i], cbx, cby, i);
        }

        double[] tiempo = new double[N];
        double[][] ux = new double[N][S], uy = new double[N][S], rz = new double[N][S];
        double[][] dx = new double[N][S], dy = new double[N][S];
        for (int i = 0; i < N; i++) {
            tiempo[i] = i * dt;
            for (int s = 0; s < S; s++) {
                ux[i][s] = U[i][3 * s]; uy[i][s] = U[i][3 * s + 1]; rz[i][s] = U[i][3 * s + 2];
                double h = modelo.alturas()[s];
                double uxPrev = s == 0 ? 0 : U[i][3 * (s - 1)];
                double uyPrev = s == 0 ? 0 : U[i][3 * (s - 1) + 1];
                dx[i][s] = (ux[i][s] - uxPrev) / h;
                dy[i][s] = (uy[i][s] - uyPrev) / h;
            }
        }
        double[] maxX = new double[S], maxY = new double[S];
        for (int s = 0; s < S; s++) {
            List<double[]> pts = modelo.puntos() == null || s >= modelo.puntos().size() ? List.of() : modelo.puntos().get(s);
            if (pts.isEmpty()) pts = List.of(new double[]{modelo.xcm()[s], modelo.ycm()[s]});
            double h = modelo.alturas()[s];
            for (int i = 0; i < N; i++) {
                for (double[] p : pts) {
                    maxX[s] = Math.max(maxX[s], Math.abs(fernandoHuancaDerivaPunto(U[i], 0, s, p[0], p[1], modelo, h)));
                    maxY[s] = Math.max(maxY[s], Math.abs(fernandoHuancaDerivaPunto(U[i], 1, s, p[0], p[1], modelo, h)));
                }
            }
        }
        double mx = 0, my = 0, tx = 0, ty = 0, vx = 0, vy = 0;
        for (int s = 0; s < S; s++) { mx = Math.max(mx, maxX[s]); my = Math.max(my, maxY[s]); }
        for (int i = 0; i < N; i++) {
            tx = Math.max(tx, Math.abs(ux[i][S - 1])); ty = Math.max(ty, Math.abs(uy[i][S - 1]));
            vx = Math.max(vx, Math.abs(cbx[i])); vy = Math.max(vy, Math.abs(cby[i]));
        }
        avisos.add(String.format(Locale.ROOT, "Newmark de aceleración media; Rayleigh ξ = %.3f en T1 = %.3f s y T2 = %.3f s. Derivas elásticas (sin 0,75·R).", amortiguamiento, t1, t2));

        return new ResultadoTiempoHistoria(registro.nombre(), dt, N, S, tiempo, ux, uy, rz,
                registro.desplazamiento(0).clone(), registro.desplazamiento(1).clone(), ax.clone(), ay.clone(), dx, dy,
                maxX, maxY, mx, my, tx, ty, cbx, cby, vx, vy, List.copyOf(avisos));
    }

    private static void fernandoHuancaRegistrarCortante(double[] m, int S, double[] a, double agx, double agy, double[] cbx, double[] cby, int i) {
        double sx = 0, sy = 0;
        for (int s = 0; s < S; s++) {
            sx -= m[3 * s] * (a[3 * s] + agx);
            sy -= m[3 * s + 1] * (a[3 * s + 1] + agy);
        }
        cbx[i] = sx; cby[i] = sy;
    }

    private static double fernandoHuancaDerivaPunto(double[] u, int dir, int s, double x, double y,
                                      FernandoHuancaAnalisisSismico.ModeloDinamico mo, double h) {
        return (fernandoHuancaDesplazamientoPunto(u, dir, s, x, y, mo) - fernandoHuancaDesplazamientoPunto(u, dir, s - 1, x, y, mo)) / h;
    }

    private static double fernandoHuancaDesplazamientoPunto(double[] u, int dir, int s, double x, double y,
                                              FernandoHuancaAnalisisSismico.ModeloDinamico mo) {
        if (s < 0) return 0;
        return dir == 0 ? u[3 * s] - (y - mo.ycm()[s]) * u[3 * s + 2] : u[3 * s + 1] + (x - mo.xcm()[s]) * u[3 * s + 2];
    }

    public static VerificacionTiempoHistoria fernandoHuancaVerificar(List<ResultadoTiempoHistoria> resultados, NormaE030.Parametros norma) {
        if (resultados == null || resultados.isEmpty()) throw new IllegalArgumentException("Se requiere al menos un resultado tiempo-historia.");
        if (norma == null) throw new IllegalArgumentException("Faltan los parámetros de la norma.");
        int S = resultados.get(0).pisos();
        for (var r : resultados) if (r.pisos() != S) throw new IllegalArgumentException("Todos los resultados deben tener el mismo número de pisos.");
        int n = resultados.size();
        boolean v2026 = norma.version() == NormaE030.Version.V2026;
        int minimo = v2026 ? 7 : 3;
        boolean suficientes = n >= minimo;
        boolean promedio = v2026 || n >= 7;
        double limite = FERNANDO_HUANCA_FACTOR_LIMITE * FERNANDO_HUANCA_DERIVA_CONCRETO;

        List<VerificacionTiempoHistoria.Piso> px = new ArrayList<>(), py = new ArrayList<>();
        boolean cumple = suficientes;
        for (int s = 0; s < S; s++) {
            double dx = 0, dy = 0;
            for (var r : resultados) {
                if (promedio) { dx += r.derivaMaximaPisoX()[s]; dy += r.derivaMaximaPisoY()[s]; }
                else { dx = Math.max(dx, r.derivaMaximaPisoX()[s]); dy = Math.max(dy, r.derivaMaximaPisoY()[s]); }
            }
            if (promedio) { dx /= n; dy /= n; }
            boolean cx = dx <= limite, cy = dy <= limite;
            px.add(new VerificacionTiempoHistoria.Piso(s, dx, limite, cx));
            py.add(new VerificacionTiempoHistoria.Piso(s, dy, limite, cy));
            cumple &= cx && cy;
        }

        List<String> avisos = new ArrayList<>();
        if (!suficientes)
            avisos.add(v2026 ? "E.030 2026 art. 47.1: se requieren al menos siete conjuntos de registros; hay " + n + "."
                    : "E.030 2018 art. 4.7.3: se requieren al menos tres conjuntos de registros; hay " + n + ".");
        if (!v2026 && n < 7 && n >= 3) avisos.add("E.030 2018 art. 4.7.3: con menos de siete conjuntos el diseño toma el MÁXIMO de las respuestas, no el promedio.");
        avisos.add("El análisis tiempo-historia es una VERIFICACIÓN complementaria y no sustituye al análisis estático o espectral (arts. 46.1 y 32.3 del texto 2026).");
        avisos.add("Modelo lineal-elástico con el registro escalado a R = 1: es conservador porque no hay reducción por ductilidad ni se multiplica por 0,75·R (art. 48.4 permite propiedades lineales solo si el análisis demuestra que los elementos permanecen elásticos).");
        avisos.add("Base empotrada, sin interacción suelo-estructura (art. 48.6 lo permite).");
        avisos.add(String.format(Locale.ROOT, "Límite de distorsión: 1,25 × %.3f = %.5f (concreto armado, Tabla 14).", FERNANDO_HUANCA_DERIVA_CONCRETO, limite));

        return new VerificacionTiempoHistoria(norma.version(), promedio ? "promedio" : "máximo", n, minimo, suficientes, limite,
                List.copyOf(px), List.copyOf(py), cumple, List.copyOf(avisos));
    }
}
