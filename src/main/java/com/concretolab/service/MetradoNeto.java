package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Metrado de concreto sin doble conteo. Los elementos son prismas alineados a los ejes; donde dos se cruzan (columna con
 * viga, viga con losa, muro con losa…) el volumen común se asigna una sola vez al elemento de mayor prioridad:
 * zapata, columna, viga, muro y por último losa. A igual tipo gana el que está antes en la lista.
 *
 * <p>También estima el encofrado (superficie de contacto con el molde, sin descontar contactos entre elementos) y el
 * acero con cuantías referenciales editables. Son estimaciones de anteproyecto, no un metrado de obra.</p>
 */
public final class MetradoNeto {

    private MetradoNeto() { }

    private static final List<String> YEFRI_LERMA_PRIORIDAD = List.of("ZAPATA", "COLUMNA", "VIGA", "MURO", "LOSA");

    /** Cuantías referenciales de acero en kg por m³ de concreto, por tipo. */
    public static Map<String, Double> yefriLermaCuantiasReferenciales() {
        var m = new LinkedHashMap<String, Double>();
        m.put("ZAPATA", 60.0);
        m.put("COLUMNA", 150.0);
        m.put("VIGA", 120.0);
        m.put("MURO", 70.0);
        m.put("LOSA", 80.0);
        return m;
    }

    public record Fila(String tipo, int cantidad, double volumenBruto, double volumenNeto, double encofradoM2, double aceroKg) {
        public double descontado() { return volumenBruto - volumenNeto; }
    }

    public record Resultado(List<Fila> filas, double volumenBruto, double volumenNeto, double encofradoM2, double aceroKg,
                            Map<Long, Double> netoPorElemento) {
        public double descontado() { return volumenBruto - volumenNeto; }
    }

    private record Caja(double x0, double x1, double y0, double y1, double z0, double z1) {
        static Caja de(Elemento e) {
            return new Caja(e.getX() - e.getLargo() / 2, e.getX() + e.getLargo() / 2, e.getY() - e.getAncho() / 2,
                    e.getY() + e.getAncho() / 2, e.getZ() - e.getAltura() / 2, e.getZ() + e.getAltura() / 2);
        }

        double volumen() { return (x1 - x0) * (y1 - y0) * (z1 - z0); }

        Caja interseccion(Caja o) {
            double a = Math.max(x0, o.x0), b = Math.min(x1, o.x1), c = Math.max(y0, o.y0), d = Math.min(y1, o.y1),
                    e = Math.max(z0, o.z0), f = Math.min(z1, o.z1);
            return a < b && c < d && e < f ? new Caja(a, b, c, d, e, f) : null;
        }
    }

    public static Resultado yefriLermaCalcular(List<Elemento> elementos) {
        return yefriLermaCalcular(elementos, yefriLermaCuantiasReferenciales());
    }

    public static Resultado yefriLermaCalcular(List<Elemento> elementos, Map<String, Double> cuantias) {
        var orden = new ArrayList<Elemento>(elementos);
        orden.sort(Comparator.comparingInt((Elemento e) -> {
            int i = YEFRI_LERMA_PRIORIDAD.indexOf(e.getTipo());
            return i < 0 ? YEFRI_LERMA_PRIORIDAD.size() : i;
        }));
        var previas = new ArrayList<Caja>();
        var neto = new LinkedHashMap<Long, Double>();
        var bruto = new LinkedHashMap<String, Double>();
        var netos = new LinkedHashMap<String, Double>();
        var cantidad = new LinkedHashMap<String, Integer>();
        var encofrado = new LinkedHashMap<String, Double>();
        int indice = 0;
        for (Elemento e : orden) {
            Caja c = Caja.de(e);
            var solapes = new ArrayList<Caja>();
            for (Caja p : previas) {
                Caja i = c.interseccion(p);
                if (i != null) solapes.add(i);
            }
            double propio = Math.max(0, c.volumen() - yefriLermaVolumenUnion(solapes));
            previas.add(c);
            neto.put(e.getId() != null ? e.getId() : (long) (-(++indice)), propio);
            bruto.merge(e.getTipo(), c.volumen(), Double::sum);
            netos.merge(e.getTipo(), propio, Double::sum);
            cantidad.merge(e.getTipo(), 1, Integer::sum);
            encofrado.merge(e.getTipo(), yefriLermaSuperficieEncofrado(e), Double::sum);
        }
        var filas = new ArrayList<Fila>();
        double vb = 0, vn = 0, enc = 0, ac = 0;
        for (String tipo : bruto.keySet().stream().sorted(Comparator.comparingInt((String t) -> {
            int i = YEFRI_LERMA_PRIORIDAD.indexOf(t);
            return i < 0 ? YEFRI_LERMA_PRIORIDAD.size() : i;
        })).toList()) {
            double acero = netos.get(tipo) * cuantias.getOrDefault(tipo, 0.0);
            filas.add(new Fila(tipo, cantidad.get(tipo), bruto.get(tipo), netos.get(tipo), encofrado.get(tipo), acero));
            vb += bruto.get(tipo);
            vn += netos.get(tipo);
            enc += encofrado.get(tipo);
            ac += acero;
        }
        return new Resultado(List.copyOf(filas), vb, vn, enc, ac, neto);
    }

    /** Superficie de molde: columnas y zapatas, perímetro por altura; vigas, fondo y dos caras; muros, dos caras; losas, el fondo. */
    static double yefriLermaSuperficieEncofrado(Elemento e) {
        double l = e.getLargo(), a = e.getAncho(), h = e.getAltura();
        return switch (e.getTipo()) {
            case "COLUMNA", "ZAPATA" -> 2 * (l + a) * h;
            case "VIGA" -> Math.max(l, a) * (Math.min(l, a) + 2 * h);
            case "MURO" -> 2 * Math.max(l, a) * h;
            case "LOSA" -> l * a;
            default -> 0;
        };
    }

    /** Volumen de la unión de cajas por compresión de coordenadas (exacto). */
    static double yefriLermaVolumenUnion(List<Caja> cajas) {
        if (cajas.isEmpty()) return 0;
        if (cajas.size() == 1) return cajas.get(0).volumen();
        var xs = new TreeSet<Double>();
        var ys = new TreeSet<Double>();
        var zs = new TreeSet<Double>();
        for (Caja c : cajas) {
            xs.add(c.x0); xs.add(c.x1); ys.add(c.y0); ys.add(c.y1); zs.add(c.z0); zs.add(c.z1);
        }
        var X = xs.toArray(new Double[0]);
        var Y = ys.toArray(new Double[0]);
        var Z = zs.toArray(new Double[0]);
        double total = 0;
        for (int i = 0; i + 1 < X.length; i++) {
            for (int j = 0; j + 1 < Y.length; j++) {
                for (int k = 0; k + 1 < Z.length; k++) {
                    double cx = (X[i] + X[i + 1]) / 2, cy = (Y[j] + Y[j + 1]) / 2, cz = (Z[k] + Z[k + 1]) / 2;
                    for (Caja c : cajas) {
                        if (cx > c.x0 && cx < c.x1 && cy > c.y0 && cy < c.y1 && cz > c.z0 && cz < c.z1) {
                            total += (X[i + 1] - X[i]) * (Y[j + 1] - Y[j]) * (Z[k + 1] - Z[k]);
                            break;
                        }
                    }
                }
            }
        }
        return total;
    }
}
