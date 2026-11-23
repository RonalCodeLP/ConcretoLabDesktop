package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.util.*;
import org.apache.commons.math3.linear.*;

/**
 * Análisis sísmico educativo de un pórtico de concreto armado según la E.030.
 * Modelo: columnas y vigas como barras 3D de Euler-Bernoulli (ejes a nudos), base empotrada,
 * un diafragma rígido por nivel (3 GDL por piso en el centro de masas) y condensación estática de los demás GDL.
 * No modela muros, losas deformables, zonas rígidas, interacción suelo-estructura ni diseño de elementos.
 */
public final class FernandoHuancaAnalisisSismico {
    private FernandoHuancaAnalisisSismico() {}

    public static final double FERNANDO_HUANCA_G = 9.80665;        // m/s²
    public static final double FERNANDO_HUANCA_PESO_CONCRETO = 24; // kN/m³ (E.020: 2400 kgf/m³)
    private static final double FERNANDO_HUANCA_TOL = 0.05;        // m, tolerancia para agrupar cotas y ubicar nudos

    /** Piso detectado: cota de entrepiso (cara superior de vigas) y área envolvente por defecto. */
    public record PisoBase(double cota, double areaPorDefecto) {}
    /** Cargas de losa de un piso: área en m²; carga muerta adicional y viva en kN/m². */
    public record CargaPiso(double area, double cmAdicional, double cv) {}
    public record Entrada(List<Elemento> elementos, double fcMpa, double poisson, double fraccionRigidez,
                          double espesorLosa, List<CargaPiso> cargas, NormaE030.Parametros norma) {}

    public record Piso(int numero, double cota, double altura, double area, double peso, double xcm, double ycm, double inercia, double largoX, double largoY) {}
    public record Modo(int numero, double periodo, double masaEfectivaX, double masaEfectivaY, List<Double> forma) {}
    /** Resultado de una dirección. Desplazamientos inelásticos en m; derivas adimensionales; fuerzas en kN. */
    public record Direccion(String nombre, double periodo, double cortanteBase, double[] fuerzas, double[] cortantes,
                            double[] desplazamientosCm, double[] derivasCm, double[] derivasMaximas, double factorEscala, double[] razonTorsion) {
        public double derivaMaxima() { return Arrays.stream(derivasMaximas).max().orElse(0); }
        public boolean cumple() { return derivaMaxima() <= NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO; }
    }
    /** Separación sísmica: mínimo por norma según la altura y 2/3 del desplazamiento máximo propio (a sumar con el del vecino). */
    public record Junta(double alturaM, double minimoNormaM, double dosTerciosPropioM) {}
    /**
     * Modelo dinámico del edificio con diafragma rígido: 3 grados de libertad por piso (ux, uy, rz en el centro de masas),
     * ordenados piso a piso. Masas en kN·s²/m (el giro, en kN·s²·m), rigidez condensada en kN/m, kN y kN·m. Sirve para el
     * análisis tiempo-historia: M·ü + C·u̇ + K·u = −M·r·ag(t).
     */
    public record ModeloDinamico(int pisos, double[] masas, double[][] rigidez, double[] cotas, double[] alturas,
                                 double[] xcm, double[] ycm, List<List<double[]>> puntos, double[] periodos) {
        /** Vector de influencia del movimiento del suelo: 1 en los GDL de traslación de la dirección (0 = X, 1 = Y). */
        public double[] influencia(int direccion) {
            double[] r = new double[3 * pisos];
            for (int s = 0; s < pisos; s++) r[3 * s + direccion] = 1.0;
            return r;
        }
        public int grados() { return 3 * pisos; }
    }    public record Resultado(List<Piso> pisos, List<Modo> modos, double pesoTotal, double ecMpa,
                            Direccion estaticoX, Direccion estaticoY, Direccion espectralX, Direccion espectralY, List<String> advertencias, Junta junta, ModeloDinamico modelo,
                            ConexionVigas conexion) {
        public double masaAcumuladaX() { return modos.stream().mapToDouble(Modo::masaEfectivaX).sum(); }
        public double masaAcumuladaY() { return modos.stream().mapToDouble(Modo::masaEfectivaY).sum(); }
    }

    /** E.060: Ec = 15 000·√f'c con f'c en kgf/cm²; se devuelve en MPa. */
    public static double fernandoHuancaModulo(double fcMpa) { return 15000 * Math.sqrt(fcMpa / 0.0980665) * 0.0980665; }

    // ---------- Geometría ----------

    private static List<Elemento> fernandoHuancaDe(List<Elemento> l, String tipo) { return l.stream().filter(e -> tipo.equals(e.getTipo())).toList(); }

    private static double[] fernandoHuancaNiveles(List<Elemento> vigas) {
        double[] tops = vigas.stream().mapToDouble(v -> v.getZ() + v.getAltura() / 2).sorted().toArray();
        List<Double> res = new ArrayList<>(); List<Double> grupo = new ArrayList<>();
        for (double t : tops) {
            if (!grupo.isEmpty() && t - grupo.get(0) > FERNANDO_HUANCA_TOL) { res.add(grupo.stream().mapToDouble(d -> d).average().orElse(0)); grupo.clear(); }
            grupo.add(t);
        }
        if (!grupo.isEmpty()) res.add(grupo.stream().mapToDouble(d -> d).average().orElse(0));
        return res.stream().mapToDouble(d -> d).toArray();
    }

    private static double fernandoHuancaBaseColumnas(List<Elemento> cols) { return cols.stream().mapToDouble(c -> c.getZ() - c.getAltura() / 2).min().orElse(0); }

    /** Pisos del modelo, para que la pantalla ofrezca la tabla de cargas. */
    public static List<PisoBase> fernandoHuancaPisos(List<Elemento> elementos) {
        var cols = fernandoHuancaDe(elementos, "COLUMNA"); var vigas = fernandoHuancaDe(elementos, "VIGA");
        if (cols.isEmpty() || vigas.isEmpty()) throw new IllegalArgumentException("El análisis sísmico necesita al menos una columna y una viga en el proyecto.");
        double base = fernandoHuancaBaseColumnas(cols); List<PisoBase> res = new ArrayList<>();
        for (double n : fernandoHuancaNiveles(vigas)) {
            if (n <= base + FERNANDO_HUANCA_TOL) continue;
            double[] r = fernandoHuancaEnvolvente(cols, vigas, n);
            if (r[0] > r[1] || r[2] > r[3]) continue;
            res.add(new PisoBase(n, (r[1] - r[0]) * (r[3] - r[2])));
        }
        if (res.isEmpty()) throw new IllegalArgumentException("Las vigas no están por encima de la base de las columnas.");
        return res;
    }

    /** {xmin, xmax, ymin, ymax} de las columnas que llegan al nivel y de las vigas de ese nivel. */
    private static double[] fernandoHuancaEnvolvente(List<Elemento> cols, List<Elemento> vigas, double nivel) {
        double[] r = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Elemento c : cols) if (c.getZ() - c.getAltura() / 2 < nivel - FERNANDO_HUANCA_TOL && c.getZ() + c.getAltura() / 2 >= nivel - FERNANDO_HUANCA_TOL) fernandoHuancaAmpliar(r, c);
        for (Elemento v : vigas) if (Math.abs(v.getZ() + v.getAltura() / 2 - nivel) <= FERNANDO_HUANCA_TOL) fernandoHuancaAmpliar(r, v);
        return r;
    }
    private static void fernandoHuancaAmpliar(double[] r, Elemento e) {
        r[0] = Math.min(r[0], e.getX() - e.getLargo() / 2); r[1] = Math.max(r[1], e.getX() + e.getLargo() / 2);
        r[2] = Math.min(r[2], e.getY() - e.getAncho() / 2); r[3] = Math.max(r[3], e.getY() + e.getAncho() / 2);
    }

    private static final class Nudo {
        final double x, y, fernandoHuancaZ, fernandoHuancaHx, fernandoHuancaHy; final int fernandoHuancaPiso; // piso -1 = base empotrada
        int[][] fernandoHuancaGdl; double[][] fernandoHuancaCoef;
        Nudo(double x, double y, double z, int piso, double hx, double hy) { this.x = x; this.y = y; this.fernandoHuancaZ = z; this.fernandoHuancaPiso = piso; this.fernandoHuancaHx = hx; this.fernandoHuancaHy = hy; }
    }
    private record Barra(Nudo a, Nudo b, double l2, double l3, double[] e1, double[] e2, double[] e3) {}
    /** Masa puntual de un piso: peso (kN), posición, inercia propia (kN·m²). */
    private record Peso(double w, double x, double y, double inercia) {}

    private static Nudo fernandoHuancaNudo(Map<String, Nudo> mapa, double x, double y, double z, int piso, double hx, double hy) {
        return mapa.computeIfAbsent(Math.round(x * 1000) + "|" + Math.round(y * 1000) + "|" + Math.round(z * 1000), k -> new Nudo(x, y, z, piso, hx, hy));
    }

    /** Una viga (dirección, centro y media longitud) toca la columna de centro (nx, ny) y sección hx × hy. */
    private static boolean fernandoHuancaEnLinea(boolean xDir, double xc, double yc, double mitad, double nx, double ny, double hx, double hy) {
        return xDir ? Math.abs(ny - yc) <= hy / 2 + FERNANDO_HUANCA_TOL && nx >= xc - mitad - hx / 2 - FERNANDO_HUANCA_TOL && nx <= xc + mitad + hx / 2 + FERNANDO_HUANCA_TOL
                : Math.abs(nx - xc) <= hx / 2 + FERNANDO_HUANCA_TOL && ny >= yc - mitad - hy / 2 - FERNANDO_HUANCA_TOL && ny <= yc + mitad + hy / 2 + FERNANDO_HUANCA_TOL;
    }

    /**
     * Cómo están unidas las vigas a las columnas. {@code sueltas}: vigas que no unen al menos dos columnas (no aportan
     * rigidez); {@code pisosSinPortico}: pisos (1 = el primero) donde ninguna viga une dos columnas, es decir, sin pórtico.
     */
    public record ConexionVigas(int vigas, List<Long> idsSueltas, List<String> sueltas, List<Integer> pisosSinPortico) {
        /** Hay al menos un pórtico en cada piso: el análisis representa un edificio de pórticos. */
        public boolean formaPorticos() { return pisosSinPortico.isEmpty() && sueltas.size() < vigas; }
        public static ConexionVigas fernandoHuancaVacia() { return new ConexionVigas(0, List.of(), List.of(), List.of()); }
    }

    /**
     * Revisa, con el mismo criterio que el análisis, qué vigas unen dos columnas. Sirve también antes de guardar una edición
     * para avisar si una viga deja de llegar a las columnas.
     *
     * @throws IllegalArgumentException si el proyecto no tiene columnas y vigas o no se reconocen pisos
     */
    public static ConexionVigas fernandoHuancaConexionVigas(List<Elemento> elementos) {
        var cols = fernandoHuancaDe(elementos, "COLUMNA"); var vigas = fernandoHuancaDe(elementos, "VIGA");
        double[] cotas = fernandoHuancaPisos(elementos).stream().mapToDouble(PisoBase::cota).toArray();
        boolean[] conPortico = new boolean[cotas.length];
        List<Long> ids = new ArrayList<>(); List<String> nombres = new ArrayList<>();
        for (Elemento v : vigas) {
            int s = fernandoHuancaPisoEnCota(cotas, v.getZ() + v.getAltura() / 2);
            int tocadas = 0;
            if (s >= 0) {
                boolean xDir = v.getLargo() >= v.getAncho();
                double mitad = (xDir ? v.getLargo() : v.getAncho()) / 2;
                for (Elemento c : cols) {
                    double bot = c.getZ() - c.getAltura() / 2, top = c.getZ() + c.getAltura() / 2;
                    boolean llega = cotas[s] > bot + FERNANDO_HUANCA_TOL && cotas[s] <= top + FERNANDO_HUANCA_TOL;
                    if (llega && fernandoHuancaEnLinea(xDir, v.getX(), v.getY(), mitad, c.getX(), c.getY(), c.getLargo(), c.getAncho())) tocadas++;
                }
            }
            if (tocadas >= 2) conPortico[s] = true;
            else { ids.add(v.getId()); nombres.add(v.getNombre()); }
        }
        List<Integer> sinPortico = new ArrayList<>();
        for (int s = 0; s < cotas.length; s++) if (!conPortico[s]) sinPortico.add(s + 1);
        return new ConexionVigas(vigas.size(), Collections.unmodifiableList(ids), List.copyOf(nombres), List.copyOf(sinPortico));
    }

    private static int fernandoHuancaPisoEnCota(double[] cotas, double z) {
        for (int s = 0; s < cotas.length; s++) if (Math.abs(cotas[s] - z) <= FERNANDO_HUANCA_TOL) return s;
        return -2;
    }

    // ---------- Rigidez de barra ----------

    private static double[][] fernandoHuancaRigidezLocal(double L, double E, double Gm, double A, double iy, double iz, double J) {
        double[][] k = new double[12][12];
        double ea = E * A / L, gj = Gm * J / L;
        double a1 = 12 * E * iz / (L * L * L), a2 = 6 * E * iz / (L * L), a3 = 4 * E * iz / L, a4 = 2 * E * iz / L;
        double b1 = 12 * E * iy / (L * L * L), b2 = 6 * E * iy / (L * L), b3 = 4 * E * iy / L, b4 = 2 * E * iy / L;
        k[0][0] = ea; k[0][6] = -ea; k[6][6] = ea;
        k[3][3] = gj; k[3][9] = -gj; k[9][9] = gj;
        k[1][1] = a1; k[1][5] = a2; k[1][7] = -a1; k[1][11] = a2;
        k[5][5] = a3; k[5][7] = -a2; k[5][11] = a4;
        k[7][7] = a1; k[7][11] = -a2; k[11][11] = a3;
        k[2][2] = b1; k[2][4] = -b2; k[2][8] = -b1; k[2][10] = -b2;
        k[4][4] = b3; k[4][8] = b2; k[4][10] = b4;
        k[8][8] = b1; k[8][10] = b2; k[10][10] = b3;
        for (int i = 0; i < 12; i++) for (int j = 0; j < i; j++) k[i][j] = k[j][i];
        return k;
    }

    private static double fernandoHuancaTorsion(double l2, double l3) {
        double a = Math.max(l2, l3), b = Math.min(l2, l3);
        return a * Math.pow(b, 3) * (1.0 / 3 - 0.21 * (b / a) * (1 - Math.pow(b, 4) / (12 * Math.pow(a, 4))));
    }

    // ---------- Análisis ----------

    public static Resultado fernandoHuancaAnalizar(Entrada in) {
        var norma = in.norma();
        if (norma == null) throw new IllegalArgumentException("Seleccione los parámetros de la E.030.");
        if (!(in.fcMpa() >= 10 && in.fcMpa() <= 100)) throw new IllegalArgumentException("f'c debe estar entre 10 y 100 MPa.");
        if (!(in.fraccionRigidez() > 0 && in.fraccionRigidez() <= 1)) throw new IllegalArgumentException("La fracción de rigidez debe estar en (0, 1].");
        if (!(in.poisson() >= 0 && in.poisson() < 0.5)) throw new IllegalArgumentException("El módulo de Poisson debe estar entre 0 y 0,5.");
        if (!(in.espesorLosa() >= 0)) throw new IllegalArgumentException("El espesor de losa no puede ser negativo.");
        var cols = fernandoHuancaDe(in.elementos(), "COLUMNA"); var vigas = fernandoHuancaDe(in.elementos(), "VIGA");
        List<PisoBase> base = fernandoHuancaPisos(in.elementos());
        final int S = base.size();
        if (in.cargas() == null || in.cargas().size() != S) throw new IllegalArgumentException("Defina las cargas de los " + S + " pisos.");
        for (CargaPiso c : in.cargas()) if (!(c.area() > 0) || !(c.cmAdicional() >= 0) || !(c.cv() >= 0)) throw new IllegalArgumentException("Use área de losa positiva y cargas no negativas en todos los pisos.");
        List<String> avisos = new ArrayList<>();
        double zBase = fernandoHuancaBaseColumnas(cols);
        double[] cotas = base.stream().mapToDouble(PisoBase::cota).toArray();
        double[] alturas = new double[S];
        for (int s = 0; s < S; s++) alturas[s] = cotas[s] - (s == 0 ? zBase : cotas[s - 1]);
        double hn = cotas[S - 1] - zBase;

        double ecMpa = fernandoHuancaModulo(in.fcMpa()), E = ecMpa * 1000, Gm = E / (2 * (1 + in.poisson())); // kN/m²

        // Nudos y barras
        Map<String, Nudo> mapa = new LinkedHashMap<>(); List<Barra> barras = new ArrayList<>();
        List<List<Peso>> pesos = new ArrayList<>(); for (int s = 0; s < S; s++) pesos.add(new ArrayList<>());
        for (Elemento c : cols) {
            double bot = c.getZ() - c.getAltura() / 2, top = c.getZ() + c.getAltura() / 2;
            List<Integer> niv = new ArrayList<>();
            for (int s = 0; s < S; s++) if (cotas[s] > bot + FERNANDO_HUANCA_TOL && cotas[s] <= top + FERNANDO_HUANCA_TOL) niv.add(s);
            if (niv.isEmpty()) { avisos.add("La columna " + c.getNombre() + " no llega a ningún nivel de vigas y se ignoró."); continue; }
            int pb = Math.abs(bot - zBase) <= FERNANDO_HUANCA_TOL ? -1 : fernandoHuancaPisoEnCota(cotas, bot);
            if (pb == -2) { avisos.add("La columna " + c.getNombre() + " inicia en una cota no reconocida; se consideró empotrada."); pb = -1; }
            Nudo previo = fernandoHuancaNudo(mapa, c.getX(), c.getY(), pb < 0 ? bot : cotas[pb], pb, c.getLargo(), c.getAncho());
            for (int s : niv) {
                Nudo sig = fernandoHuancaNudo(mapa, c.getX(), c.getY(), cotas[s], s, c.getLargo(), c.getAncho());
                double L = sig.fernandoHuancaZ - previo.fernandoHuancaZ;
                if (!(L > 1e-6)) continue;
                barras.add(new Barra(previo, sig, c.getLargo(), c.getAncho(), new double[]{0, 0, 1}, new double[]{1, 0, 0}, new double[]{0, 1, 0}));
                double w = c.getLargo() * c.getAncho() * L * FERNANDO_HUANCA_PESO_CONCRETO;
                if (previo.fernandoHuancaPiso >= 0) pesos.get(previo.fernandoHuancaPiso).add(new Peso(w / 2, c.getX(), c.getY(), 0));
                pesos.get(s).add(new Peso(w / 2, c.getX(), c.getY(), 0));
                previo = sig;
            }
        }
        if (barras.isEmpty()) throw new IllegalArgumentException("No se pudo formar ninguna columna entre niveles.");
        for (Elemento v : vigas) {
            int s = fernandoHuancaPisoEnCota(cotas, v.getZ() + v.getAltura() / 2);
            if (s < 0) { avisos.add("La viga " + v.getNombre() + " no está en un piso reconocido y se ignoró."); continue; }
            boolean xDir = v.getLargo() >= v.getAncho();
            double xc = v.getX(), yc = v.getY(), mitad = (xDir ? v.getLargo() : v.getAncho()) / 2;
            List<Nudo> cand = new ArrayList<>();
            for (Nudo n : mapa.values()) {
                if (n.fernandoHuancaPiso != s) continue;
                if (fernandoHuancaEnLinea(xDir, xc, yc, mitad, n.x, n.y, n.fernandoHuancaHx, n.fernandoHuancaHy)) cand.add(n);
            }
            cand.sort(Comparator.comparingDouble(n -> xDir ? n.x : n.y));
            if (cand.size() < 2) { avisos.add("La viga " + v.getNombre() + " no une dos columnas y no aporta rigidez."); }
            for (int i = 0; i + 1 < cand.size(); i++)
                barras.add(new Barra(cand.get(i), cand.get(i + 1), xDir ? v.getAncho() : v.getLargo(), v.getAltura(),
                        xDir ? new double[]{1, 0, 0} : new double[]{0, 1, 0}, xDir ? new double[]{0, 1, 0} : new double[]{-1, 0, 0}, new double[]{0, 0, 1}));
            double w = v.getLargo() * v.getAncho() * v.getAltura() * FERNANDO_HUANCA_PESO_CONCRETO;
            pesos.get(s).add(new Peso(w, xc, yc, w * (v.getLargo() * v.getLargo() + v.getAncho() * v.getAncho()) / 12));
        }

        // Pisos: peso, centro de masas e inercia polar
        List<Piso> pisosRes = new ArrayList<>(); double[] W = new double[S], xcm = new double[S], ycm = new double[S], I = new double[S];
        for (int s = 0; s < S; s++) {
            double[] r = fernandoHuancaEnvolvente(cols, vigas, cotas[s]);
            double lx = r[1] - r[0], ly = r[3] - r[2], areaBox = lx * ly;
            CargaPiso cp = in.cargas().get(s);
            double pct = s == S - 1 ? 0.25 : norma.categoria().porcentajeCv; // art. 31: azoteas y techos, 25 % de la carga viva
            double ws = cp.area() * (in.espesorLosa() * FERNANDO_HUANCA_PESO_CONCRETO + cp.cmAdicional() + cp.cv() * pct), esc = Math.sqrt(cp.area() / areaBox);
            pesos.get(s).add(new Peso(ws, (r[0] + r[1]) / 2, (r[2] + r[3]) / 2, ws * (lx * lx + ly * ly) * esc * esc / 12));
            double w = 0, mx = 0, my = 0;
            for (Peso p : pesos.get(s)) { w += p.w(); mx += p.w() * p.x(); my += p.w() * p.y(); }
            W[s] = w; xcm[s] = mx / w; ycm[s] = my / w;
            double inercia = 0; for (Peso p : pesos.get(s)) inercia += p.inercia() + p.w() * (Math.pow(p.x() - xcm[s], 2) + Math.pow(p.y() - ycm[s], 2));
            I[s] = inercia;
            pisosRes.add(new Piso(s + 1, cotas[s], alturas[s], cp.area(), w, xcm[s], ycm[s], inercia, lx, ly));
        }
        double P = Arrays.stream(W).sum();

        // Grados de libertad: 3 maestros por piso (diafragma rígido) + 3 libres (uz, rx, ry) por nudo de piso
        int nm = 3 * S, sig = nm;
        Map<Integer, List<Nudo>> porPiso = new HashMap<>();
        for (Nudo n : mapa.values()) {
            n.fernandoHuancaGdl = new int[6][]; n.fernandoHuancaCoef = new double[6][];
            if (n.fernandoHuancaPiso < 0) { for (int c = 0; c < 6; c++) { n.fernandoHuancaGdl[c] = new int[0]; n.fernandoHuancaCoef[c] = new double[0]; } continue; }
            int s = n.fernandoHuancaPiso; double dx = n.x - xcm[s], dy = n.y - ycm[s];
            n.fernandoHuancaGdl[0] = new int[]{3 * s, 3 * s + 2}; n.fernandoHuancaCoef[0] = new double[]{1, -dy};
            n.fernandoHuancaGdl[1] = new int[]{3 * s + 1, 3 * s + 2}; n.fernandoHuancaCoef[1] = new double[]{1, dx};
            n.fernandoHuancaGdl[2] = new int[]{sig++}; n.fernandoHuancaCoef[2] = new double[]{1};
            n.fernandoHuancaGdl[3] = new int[]{sig++}; n.fernandoHuancaCoef[3] = new double[]{1};
            n.fernandoHuancaGdl[4] = new int[]{sig++}; n.fernandoHuancaCoef[4] = new double[]{1};
            n.fernandoHuancaGdl[5] = new int[]{3 * s + 2}; n.fernandoHuancaCoef[5] = new double[]{1};
            porPiso.computeIfAbsent(s, k -> new ArrayList<>()).add(n);
        }
        int N = sig; double[][] K = new double[N][N];
        for (Barra b : barras) {
            double dx = b.b().x - b.a().x, dy = b.b().y - b.a().y, dz = b.b().fernandoHuancaZ - b.a().fernandoHuancaZ, L = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double A = b.l2() * b.l3(), iz = b.l3() * Math.pow(b.l2(), 3) / 12, iy = b.l2() * Math.pow(b.l3(), 3) / 12, f = in.fraccionRigidez();
            double[][] kl = fernandoHuancaRigidezLocal(L, E, Gm, A, iy * f, iz * f, fernandoHuancaTorsion(b.l2(), b.l3()));
            double[][] R = {b.e1(), b.e2(), b.e3()}, T = new double[12][12];
            for (int blk = 0; blk < 4; blk++) for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) T[3 * blk + i][3 * blk + j] = R[i][j];
            double[][] kg = new Array2DRowRealMatrix(T, false).transpose().multiply(new Array2DRowRealMatrix(kl, false)).multiply(new Array2DRowRealMatrix(T, false)).getData();
            for (int d1 = 0; d1 < 12; d1++) {
                Nudo n1 = d1 < 6 ? b.a() : b.b();
                for (int d2 = 0; d2 < 12; d2++) {
                    Nudo n2 = d2 < 6 ? b.a() : b.b(); double v = kg[d1][d2];
                    if (v == 0) continue;
                    int[] g1 = n1.fernandoHuancaGdl[d1 % 6], g2 = n2.fernandoHuancaGdl[d2 % 6]; double[] c1 = n1.fernandoHuancaCoef[d1 % 6], c2 = n2.fernandoHuancaCoef[d2 % 6];
                    for (int p = 0; p < g1.length; p++) for (int q = 0; q < g2.length; q++) K[g1[p]][g2[q]] += v * c1[p] * c2[q];
                }
            }
        }
        RealMatrix Kr = new Array2DRowRealMatrix(K, false);
        RealMatrix Kmm = Kr.getSubMatrix(0, nm - 1, 0, nm - 1), Kc;
        try {
            RealMatrix Kms = Kr.getSubMatrix(0, nm - 1, nm, N - 1), Ksm = Kr.getSubMatrix(nm, N - 1, 0, nm - 1), Kss = Kr.getSubMatrix(nm, N - 1, nm, N - 1);
            DecompositionSolver sol = new LUDecomposition(Kss).getSolver();
            Kc = Kmm.subtract(Kms.multiply(sol.solve(Ksm)));
        } catch (SingularMatrixException | NonSquareMatrixException e) {
            throw new IllegalArgumentException("El modelo es inestable o tiene piezas desconectadas. Revise que cada columna llegue a vigas y a la base.");
        }
        Kc = Kc.add(Kc.transpose()).scalarMultiply(0.5);
        DecompositionSolver solKc;
        try { solKc = new LUDecomposition(Kc).getSolver(); if (!solKc.isNonSingular()) throw new SingularMatrixException(); }
        catch (SingularMatrixException e) { throw new IllegalArgumentException("La rigidez lateral del modelo es nula: faltan columnas empotradas o vigas que unan los pórticos."); }

        // Modos de vibración: K φ = ω² M φ con M diagonal
        double[] m = new double[nm];
        for (int s = 0; s < S; s++) { m[3 * s] = W[s] / FERNANDO_HUANCA_G; m[3 * s + 1] = W[s] / FERNANDO_HUANCA_G; m[3 * s + 2] = I[s] / FERNANDO_HUANCA_G; }
        double[][] Ad = new double[nm][nm];
        for (int i = 0; i < nm; i++) for (int j = 0; j < nm; j++) Ad[i][j] = Kc.getEntry(i, j) / Math.sqrt(m[i] * m[j]);
        EigenDecomposition ed = new EigenDecomposition(new Array2DRowRealMatrix(Ad, false));
        Integer[] orden = new Integer[nm]; for (int i = 0; i < nm; i++) orden[i] = i;
        Arrays.sort(orden, Comparator.comparingDouble(ed::getRealEigenvalue));
        double[] omega = new double[nm], per = new double[nm], gx = new double[nm], gy = new double[nm]; double[][] phi = new double[nm][nm];
        double masaTotal = P / FERNANDO_HUANCA_G; List<Modo> modos = new ArrayList<>();
        for (int n = 0; n < nm; n++) {
            double lam = ed.getRealEigenvalue(orden[n]);
            if (!(lam > 1e-9)) throw new IllegalArgumentException("El modelo tiene modos de cuerpo rígido: revise los apoyos.");
            omega[n] = Math.sqrt(lam); per[n] = 2 * Math.PI / omega[n];
            double[] psi = ed.getEigenvector(orden[n]).toArray();
            for (int i = 0; i < nm; i++) phi[n][i] = psi[i] / Math.sqrt(m[i]);
            for (int s = 0; s < S; s++) { gx[n] += m[3 * s] * phi[n][3 * s]; gy[n] += m[3 * s + 1] * phi[n][3 * s + 1]; }
            modos.add(new Modo(n + 1, per[n], gx[n] * gx[n] / masaTotal, gy[n] * gy[n] / masaTotal, Arrays.stream(phi[n]).boxed().toList()));
        }

        // Puntos de control por piso: el centro de masas y todos los nudos del piso
        List<List<double[]>> puntos = new ArrayList<>();
        for (int s = 0; s < S; s++) {
            List<double[]> p = new ArrayList<>(); p.add(new double[]{xcm[s], ycm[s]});
            for (Nudo n : porPiso.getOrDefault(s, List.of())) p.add(new double[]{n.x, n.y});
            puntos.add(p);
        }
        double factorIne = norma.factorInelastico();
        int modoX = 0, modoY = 0; for (int n = 1; n < nm; n++) { if (modos.get(n).masaEfectivaX() > modos.get(modoX).masaEfectivaX()) modoX = n; if (modos.get(n).masaEfectivaY() > modos.get(modoY).masaEfectivaY()) modoY = n; }

        Direccion[] est = estatico(hn, norma, W, cotas, alturas, pisosRes, solKc, puntos, xcm, ycm, factorIne);
        Direccion[] esp = espectral(new double[]{per[modoX], per[modoY]}, new double[]{est[0].cortanteBase(), est[1].cortanteBase()}, norma, m, phi, omega, per,
                new double[][]{gx, gy}, S, alturas, pisosRes, solKc, puntos, xcm, ycm, factorIne);
        Direccion estX = est[0], estY = est[1], espX = esp[0], espY = esp[1];

        // Art. 33.2: el método estático vale para estructuras regulares de hasta 30 m y para cualquier estructura en zona 1.
        if (norma.regular() ? hn > 30 && norma.zona() != NormaE030.Zona.Z1 : norma.zona() != NormaE030.Zona.Z1)
            avisos.add("El método estático de la E.030 (" + norma.art("aplicabilidad") + ") no es aplicable a este caso (" + (norma.regular() ? "regular de más de 30 m" : "estructura irregular fuera de la zona 1") + "); use el resultado espectral.");
        if (espX.factorEscala() > 1.0001 || espY.factorEscala() > 1.0001) avisos.add("El cortante dinámico fue menor que el mínimo de la E.030 (" + norma.art("minimo") + ") y se escalaron fuerzas, no desplazamientos.");
        avisos.addAll(norma.restricciones());
        long muros = in.elementos().stream().filter(e -> "MURO".equals(e.getTipo())).count();
        if (muros > 0) avisos.add("El modelo tiene " + muros + " muro(s): este módulo analiza solo pórticos y NO incluye su rigidez ni su aporte al cortante. "
                + "Con muros estructurales (placas) el sistema y el factor R cambian (E.030 Tabla 9/7); modele la estructura completa en un programa de análisis.");
        if (in.elementos().stream().anyMatch(e -> "LOSA".equals(e.getTipo())))
            avisos.add("Las losas del modelo se usan solo para el metrado; el peso sísmico se toma de las cargas por m² indicadas en el formulario.");
        if (norma.requiereVerificarPeriodoDeSitio()) avisos.add("Art. 14.8: en categorías A y B de la zona 4 con suelos S1 a S4 debe verificarse que el periodo del sitio Ts (cociente H/V) sea menor que 0,65·Tp; este módulo no lo calcula.");
        if (norma.suelo().interpola() && Double.isNaN(norma.vs30())) avisos.add("Sin Vs30 se usaron los valores más desfavorables del intervalo para S, Tp y TL (art. 17, nota *): S = " + String.format(Locale.ROOT, "%.2f", norma.s()) + ", Tp = " + norma.tp() + " s, TL = " + norma.tl() + " s.");
        avisos.add(norma.fraccionPerpendicular() > 0
                ? "Combinación direccional de los arts. 28.1, 33.3 y 43: 100 % en la dirección analizada y 30 % en la perpendicular (suma de absolutos en el estático, raíz de la suma de cuadrados en el espectral)."
                : "Versión 2018: cada dirección se analiza por separado con el 100 % de la acción; esa versión no pide combinar un 30 % en la dirección perpendicular.");
        for (Direccion d : List.of(estX, estY, espX, espY)) {
            double lim = NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO; double rmax = 1; boolean aplica = false;
            for (int s = 0; s < S; s++) if (d.derivasMaximas()[s] > 0.5 * lim) { aplica = true; rmax = Math.max(rmax, d.razonTorsion()[s]); }
            if (aplica && rmax > 1.3 && norma.ip() > (rmax > 1.5 ? 0.60 : 0.75) + 1e-9)
                avisos.add("Δmáx/Δprom = " + String.format(Locale.ROOT, "%.2f", rmax) + " en " + (d == estX || d == estY ? "estático " : "espectral ") + d.nombre() + ": posible irregularidad torsional (" + (norma.version() == NormaE030.Version.V2026 ? "Tabla 12" : "Tabla 9") + ": Ip = " + (rmax > 1.5 ? "0,60 extrema" : "0,75") + "). Revise Ip.");
        }
        double mx = modos.stream().mapToDouble(Modo::masaEfectivaX).sum(), my = modos.stream().mapToDouble(Modo::masaEfectivaY).sum();
        if (mx < 0.9 || my < 0.9) avisos.add("La masa efectiva acumulada es menor que 90 % en alguna dirección.");
        if (Math.abs(estX.periodo() - per[modoX]) / per[modoX] > 0.5) avisos.add("El periodo aproximado hn/CT difiere más de 50 % del periodo modal en X; revise el modelo.");
        if (in.fraccionRigidez() < 1.0) avisos.add("Rigidez reducida a " + String.format(Locale.ROOT, "%.2f", in.fraccionRigidez()) + " de EI: la norma (" + norma.art("inerciaBruta") + ") pide secciones brutas sin fisurar para el análisis; con menos rigidez los periodos y desplazamientos salen mayores que los exigidos.");
        double desplMax = 0;
        for (var dir : List.of(estX, estY, espX, espY)) desplMax = Math.max(desplMax, dir.desplazamientosCm()[S - 1]);
        var junta = new Junta(hn, norma.juntaMinima(hn), 2.0 / 3.0 * desplMax);
        avisos.add("Junta sísmica (" + norma.art("junta") + "): separa el edificio de sus vecinos al menos " + String.format(Locale.ROOT, "%.1f cm", Math.max(junta.minimoNormaM(), 0) * 100) + " por norma y no menos de 2/3 de la suma de los desplazamientos máximos de ambos edificios (el propio aporta " + String.format(Locale.ROOT, "%.1f cm", junta.dosTerciosPropioM() * 100) + ").");
        avisos.add("Base empotrada: las zapatas no se modelan. Sin zonas rígidas en nudos ni muros; resultados educativos y preliminares.");
        var modeloDinamico = new ModeloDinamico(S, m.clone(), Kc.getData(), cotas.clone(), alturas.clone(), xcm.clone(), ycm.clone(), List.copyOf(puntos), per.clone());
        return new Resultado(pisosRes, modos, P, ecMpa, estX, estY, espX, espY, avisos, junta, modeloDinamico,
                fernandoHuancaConexionVigas(in.elementos()));
    }

    private static double fernandoHuancaDesplazamiento(double[] u, int dir, int s, double x, double y, double[] xcm, double[] ycm) {
        if (s < 0) return 0;
        return dir == 0 ? u[3 * s] - (y - ycm[s]) * u[3 * s + 2] : u[3 * s + 1] + (x - xcm[s]) * u[3 * s + 2];
    }

    private static double fernandoHuancaDeriva(double[] u, int dir, int s, double x, double y, double[] xcm, double[] ycm, double h) {
        return (fernandoHuancaDesplazamiento(u, dir, s, x, y, xcm, ycm) - fernandoHuancaDesplazamiento(u, dir, s - 1, x, y, xcm, ycm)) / h;
    }

    // ---------- Método estático (E.030 2026, arts. 33 a 37) ----------

    /** Razón Δmáx/Δprom entre los nudos extremos en planta (perpendiculares a la dirección de análisis); 1 si no hay dos extremos. */
    private static double fernandoHuancaRazonTorsion(List<double[]> pts, double[] derivaAbs, int dir) {
        int a = -1, b = -1; double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 1; i < pts.size(); i++) {
            double c = dir == 0 ? pts.get(i)[1] : pts.get(i)[0];
            if (c < min) { min = c; a = i; } if (c > max) { max = c; b = i; }
        }
        if (a < 0 || b < 0 || max - min < 1e-6) return 1.0;
        double prom = (derivaAbs[a] + derivaAbs[b]) / 2;
        return prom > 1e-12 ? Math.max(derivaAbs[a], derivaAbs[b]) / prom : 1.0;
    }

    /**
     * Resultados estáticos para las direcciones X (índice 0) e Y (índice 1). Cada dirección se analiza con el 100 % de la acción
     * en esa dirección (con excentricidad accidental, art. 37) más el 30 % de la acción en la perpendicular sin excentricidad
     * accidental (arts. 28.3 y 33.3), sumando valores absolutos.
     */
    private static Direccion[] estatico(double hn, NormaE030.Parametros norma, double[] W, double[] cotas, double[] alturas,
                                        List<Piso> pisos, DecompositionSolver solKc, List<List<double[]>> puntos, double[] xcm, double[] ycm, double factorIne) {
        int S = W.length; double zBase = cotas[S - 1] - hn;
        double T = hn / norma.ct(), k = NormaE030.Parametros.k(T), P = Arrays.stream(W).sum();
        double V = norma.coeficienteCortante(T) * P, den = 0;
        // Art. 50.3: para los desplazamientos no se considera el mínimo C/R de 0,11.
        double reduccion = norma.coeficienteCortanteSinMinimo(T) * P / V;
        double[] h = new double[S], F = new double[S], Q = new double[S];
        for (int s = 0; s < S; s++) { h[s] = cotas[s] - zBase; den += W[s] * Math.pow(h[s], k); }
        for (int s = 0; s < S; s++) F[s] = V * W[s] * Math.pow(h[s], k) / den;
        for (int s = S - 1; s >= 0; s--) Q[s] = F[s] + (s == S - 1 ? 0 : Q[s + 1]);

        double[][] sinTorsion = new double[2][]; double[][][] conTorsion = new double[2][2][];
        for (int L = 0; L < 2; L++) {
            double[] f = new double[3 * S];
            for (int s = 0; s < S; s++) f[3 * s + L] = F[s] * reduccion;
            sinTorsion[L] = solKc.solve(new ArrayRealVector(f)).toArray();
            for (int g = 0; g < 2; g++) {
                int signo = g == 0 ? -1 : 1; double[] ft = f.clone();
                for (int s = 0; s < S; s++) ft[3 * s + 2] = signo * F[s] * reduccion * 0.05 * (L == 0 ? pisos.get(s).largoY() : pisos.get(s).largoX());
                conTorsion[L][g] = solKc.solve(new ArrayRealVector(ft)).toArray();
            }
        }
        Direccion[] res = new Direccion[2];
        for (int d = 0; d < 2; d++) {
            int cruzada = 1 - d; double[] dCm = new double[S], derCm = new double[S], derMax = new double[S], razon = new double[S];
            Arrays.fill(razon, 1.0);
            for (int s = 0; s < S; s++) {
                List<double[]> pts = puntos.get(s); double[] total = new double[pts.size()]; double desp = 0;
                for (int g = 0; g < 2; g++) {
                    double[] u = conTorsion[d][g], principal = new double[pts.size()];
                    for (int p = 0; p < pts.size(); p++) {
                        principal[p] = Math.abs(fernandoHuancaDeriva(u, d, s, pts.get(p)[0], pts.get(p)[1], xcm, ycm, alturas[s])) * factorIne;
                        double cruce = Math.abs(fernandoHuancaDeriva(sinTorsion[cruzada], d, s, pts.get(p)[0], pts.get(p)[1], xcm, ycm, alturas[s])) * factorIne;
                        total[p] = Math.max(total[p], principal[p] + norma.fraccionPerpendicular() * cruce);
                    }
                    razon[s] = Math.max(razon[s], fernandoHuancaRazonTorsion(pts, principal, d));
                    desp = Math.max(desp, Math.abs(u[3 * s + d]) * factorIne + norma.fraccionPerpendicular() * Math.abs(sinTorsion[cruzada][3 * s + d]) * factorIne);
                }
                dCm[s] = desp; derCm[s] = total[0]; for (double v : total) derMax[s] = Math.max(derMax[s], v);
            }
            res[d] = new Direccion(d == 0 ? "X" : "Y", T, V, F, Q, dCm, derCm, derMax, 1.0, razon);
        }
        return res;
    }

    // ---------- Análisis modal espectral con CQC (E.030 2026, arts. 39 a 45) ----------

    /**
     * Resultados espectrales para X (índice 0) e Y (índice 1). Respuesta modal por CQC (art. 42), excentricidad accidental
     * sobre la dirección al 100 % (art. 45, sumada en valor absoluto) y combinación direccional 100 % + 30 % por raíz de la
     * suma de cuadrados (art. 43). El cortante mínimo (art. 44) escala fuerzas y cortantes, no desplazamientos.
     */
    private static Direccion[] espectral(double[] periodoUsado, double[] cortanteEstatico, NormaE030.Parametros norma, double[] m,
                                         double[][] phi, double[] omega, double[] per, double[][] gamma, int S, double[] alturas, List<Piso> pisos,
                                         DecompositionSolver solKc, List<List<double[]>> puntos, double[] xcm, double[] ycm, double factorIne) {
        int nm = phi.length; double xi = 0.05;
        double[][] rho = new double[nm][nm];
        for (int i = 0; i < nm; i++) for (int j = 0; j < nm; j++) {
            double r = omega[j] / omega[i];
            rho[i][j] = 8 * xi * xi * (1 + r) * Math.pow(r, 1.5) / (Math.pow(1 - r * r, 2) + 4 * xi * xi * r * Math.pow(1 + r, 2));
        }
        // Para cada espectro L (dirección del sismo): desplazamientos modales, fuerzas y cortantes combinados, y respuestas CQC
        // de las derivas en cada componente d.
        double[][] fuerza = new double[2][S], cort = new double[2][S];
        double[][][] dCmCqc = new double[2][2][S]; double[][][][] derCqc = new double[2][2][S][];
        double[][][] torsion = new double[2][2][]; // [L][d][s] se llena luego
        double[][] utorsion = new double[2][];
        for (int L = 0; L < 2; L++) {
            double[][] u = new double[nm][3 * S];
            for (int n = 0; n < nm; n++) {
                double sa = norma.saSobreG(per[n]) * FERNANDO_HUANCA_G, amp = gamma[L][n] * sa / (omega[n] * omega[n]);
                for (int i = 0; i < 3 * S; i++) u[n][i] = amp * phi[n][i];
            }
            for (int s = 0; s < S; s++) {
                double[] rf = new double[nm], rq = new double[nm];
                for (int n = 0; n < nm; n++) {
                    double sa = norma.saSobreG(per[n]) * FERNANDO_HUANCA_G;
                    rf[n] = m[3 * s] * phi[n][3 * s + L] * gamma[L][n] * sa;
                    for (int j = s; j < S; j++) rq[n] += m[3 * j] * phi[n][3 * j + L] * gamma[L][n] * sa;
                }
                fuerza[L][s] = cqc(rho, rf); cort[L][s] = cqc(rho, rq);
                for (int d = 0; d < 2; d++) {
                    double[] rd = new double[nm]; for (int n = 0; n < nm; n++) rd[n] = u[n][3 * s + d];
                    dCmCqc[L][d][s] = cqc(rho, rd) * factorIne;
                    List<double[]> pts = puntos.get(s); derCqc[L][d][s] = new double[pts.size()];
                    for (int p = 0; p < pts.size(); p++) {
                        double[] rder = new double[nm];
                        for (int n = 0; n < nm; n++) rder[n] = fernandoHuancaDeriva(u[n], d, s, pts.get(p)[0], pts.get(p)[1], xcm, ycm, alturas[s]);
                        derCqc[L][d][s][p] = cqc(rho, rder) * factorIne;
                    }
                }
            }
            // Art. 45: momento accidental ±F·e con las fuerzas combinadas sin escalar (mismo signo en todos los pisos)
            double[] mt = new double[3 * S];
            for (int s = 0; s < S; s++) mt[3 * s + 2] = fuerza[L][s] * 0.05 * (L == 0 ? pisos.get(s).largoY() : pisos.get(s).largoX());
            utorsion[L] = solKc.solve(new ArrayRealVector(mt)).toArray();
        }
        Direccion[] res = new Direccion[2];
        for (int d = 0; d < 2; d++) {
            int cruzada = 1 - d; double[] dCm = new double[S], derCm = new double[S], derMax = new double[S], razon = new double[S];
            double escala = Math.max(1.0, norma.fraccionMinimaDinamica() * cortanteEstatico[d] / cort[d][0]);
            double[] f = new double[S], q = new double[S];
            for (int s = 0; s < S; s++) {
                f[s] = fuerza[d][s] * escala; q[s] = cort[d][s] * escala;
                List<double[]> pts = puntos.get(s); double[] principal = new double[pts.size()];
                for (int p = 0; p < pts.size(); p++) {
                    principal[p] = derCqc[d][d][s][p] + Math.abs(fernandoHuancaDeriva(utorsion[d], d, s, pts.get(p)[0], pts.get(p)[1], xcm, ycm, alturas[s])) * factorIne;
                    double v = Math.hypot(principal[p], norma.fraccionPerpendicular() * derCqc[cruzada][d][s][p]);
                    if (p == 0) derCm[s] = v;
                    derMax[s] = Math.max(derMax[s], v);
                }
                razon[s] = fernandoHuancaRazonTorsion(pts, principal, d);
                dCm[s] = Math.hypot(dCmCqc[d][d][s] + Math.abs(utorsion[d][3 * s + d]) * factorIne, norma.fraccionPerpendicular() * dCmCqc[cruzada][d][s]);
            }
            res[d] = new Direccion(d == 0 ? "X" : "Y", periodoUsado[d], q[0], f, q, dCm, derCm, derMax, escala, razon);
        }
        return res;
    }

    private static double cqc(double[][] rho, double[] r) {
        double s = 0;
        for (int i = 0; i < r.length; i++) for (int j = 0; j < r.length; j++) s += rho[i][j] * r[i] * r[j];
        return Math.sqrt(Math.max(0, s));
    }
}
