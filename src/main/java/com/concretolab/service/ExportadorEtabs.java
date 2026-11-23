package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Exporta el modelo a un archivo de texto de ETABS (.e2k): pisos, puntos, columnas y vigas como líneas, losas y muros como
 * áreas, secciones rectangulares de concreto y apoyos empotrados en la base. Unidades kN, m.
 *
 * <p><b>Experimental.</b> No se ha podido verificar la importación en ETABS. Los pisos salen de las cotas superiores de vigas y
 * losas; las zapatas no se exportan (la base se modela empotrada). Revise el modelo importado antes de analizarlo.</p>
 */
public final class ExportadorEtabs {

    private ExportadorEtabs() { }

    public record Resultado(String texto, List<String> avisos) { }

    private static final double ERICK_DELGADO_TOL = 0.005;

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static long erickDelgadoMm(double v) {
        return Math.round(v * 1000);
    }

    public static Resultado erickDelgadoGenerar(String nombreProyecto, double fcMpa, List<Elemento> elementos) {
        if (elementos == null || elementos.isEmpty()) throw new IllegalArgumentException("El proyecto no tiene elementos para exportar.");
        if (!Double.isFinite(fcMpa) || fcMpa <= 0) throw new IllegalArgumentException("Ingrese una f'c positiva para el material.");
        var avisos = new ArrayList<String>();
        var columnas = new ArrayList<Elemento>();
        var vigas = new ArrayList<Elemento>();
        var losas = new ArrayList<Elemento>();
        var muros = new ArrayList<Elemento>();
        int zapatas = 0;
        for (Elemento e : elementos) {
            switch (e.getTipo()) {
                case "COLUMNA" -> columnas.add(e);
                case "VIGA" -> vigas.add(e);
                case "LOSA" -> losas.add(e);
                case "MURO" -> muros.add(e);
                case "ZAPATA" -> zapatas++;
                default -> avisos.add("Elemento " + e.getNombre() + " de tipo " + e.getTipo() + " no se exportó.");
            }
        }
        if (zapatas > 0) avisos.add(zapatas + " zapata(s) no se exportan: la base se modela empotrada en la cota más baja de las columnas.");
        if (columnas.isEmpty() && vigas.isEmpty()) throw new IllegalArgumentException("Se necesitan columnas o vigas para armar el modelo.");

        // pisos: cotas superiores de vigas y losas; si no hay, de columnas y muros
        var cotas = new TreeSet<Long>();
        vigas.forEach(e -> cotas.add(erickDelgadoMm(e.getZ() + e.getAltura() / 2)));
        losas.forEach(e -> cotas.add(erickDelgadoMm(e.getZ() + e.getAltura() / 2)));
        if (cotas.isEmpty()) {
            columnas.forEach(e -> cotas.add(erickDelgadoMm(e.getZ() + e.getAltura() / 2)));
        }
        double base = Math.min(columnas.stream().mapToDouble(e -> e.getZ() - e.getAltura() / 2).min().orElse(Double.MAX_VALUE),
                muros.stream().mapToDouble(e -> e.getZ() - e.getAltura() / 2).min().orElse(Double.MAX_VALUE));
        if (base == Double.MAX_VALUE) base = cotas.first() / 1000.0 - 3.0;
        var niveles = new ArrayList<Double>();
        for (long c : cotas) {
            if (c / 1000.0 > base + ERICK_DELGADO_TOL) niveles.add(c / 1000.0);
        }
        if (niveles.isEmpty()) throw new IllegalArgumentException("No se pudo determinar ningún piso sobre la base.");
        var nombrePiso = new ArrayList<String>();
        for (int i = 0; i < niveles.size(); i++) nombrePiso.add("PISO" + (i + 1));

        // puntos en planta (únicos a 1 mm)
        var puntos = new LinkedHashMap<String, double[]>();
        var nombrePunto = new LinkedHashMap<String, String>();
        java.util.function.BiFunction<Double, Double, String> punto = (x, y) -> {
            String clave = erickDelgadoMm(x) + "," + erickDelgadoMm(y);
            return nombrePunto.computeIfAbsent(clave, k -> {
                String n = String.valueOf(nombrePunto.size() + 1);
                puntos.put(n, new double[] {x, y});
                return n;
            });
        };
        var secciones = new LinkedHashMap<String, String>(); // nombre → línea
        var lineas = new ArrayList<String>();
        var asignaciones = new ArrayList<String>();
        var areas = new ArrayList<String>();
        var asignAreas = new ArrayList<String>();
        var puntosAsig = new ArrayList<String>();

        int c = 0;
        var centros = new ArrayList<double[]>();
        for (Elemento e : columnas) {
            int idx = erickDelgadoIndicePiso(niveles, e.getZ() + e.getAltura() / 2);
            if (idx < 0) { avisos.add("La columna " + e.getNombre() + " no termina en un piso reconocido y se omitió."); continue; }
            String sec = "COL" + Math.round(e.getLargo() * 100) + "X" + Math.round(e.getAncho() * 100);
            secciones.putIfAbsent(sec, "  FRAMESECTION  \"" + sec + "\"  MATERIAL \"CONCRETO\"  SHAPE \"Rectangular\"  D " + f(e.getAncho()) + "  B " + f(e.getLargo()));
            String p = punto.apply(e.getX(), e.getY());
            String nombre = "C" + (++c);
            lineas.add("  LINE  \"" + nombre + "\"  COLUMN  \"" + p + "\"  \"" + p + "\"  1");
            asignaciones.add("  LINEASSIGN  \"" + nombre + "\"  \"" + nombrePiso.get(idx) + "\"  SECTION \"" + sec + "\"");
            centros.add(new double[] {e.getX(), e.getY(), e.getLargo() / 2, e.getAncho() / 2});
            String apoyo = "  POINTASSIGN  \"" + p + "\"  \"BASE\"  RESTRAINT \"UX UY UZ RX RY RZ\"";
            if (Math.abs((e.getZ() - e.getAltura() / 2) - base) <= ERICK_DELGADO_TOL && !puntosAsig.contains(apoyo)) puntosAsig.add(apoyo);
        }
        int b = 0;
        for (Elemento e : vigas) {
            int idx = erickDelgadoIndicePiso(niveles, e.getZ() + e.getAltura() / 2);
            if (idx < 0) { avisos.add("La viga " + e.getNombre() + " no está en un piso reconocido y se omitió."); continue; }
            boolean ejeX = e.getLargo() >= e.getAncho();
            double largo = ejeX ? e.getLargo() : e.getAncho(), ancho = ejeX ? e.getAncho() : e.getLargo();
            double x1 = ejeX ? e.getX() - largo / 2 : e.getX(), y1 = ejeX ? e.getY() : e.getY() - largo / 2;
            double x2 = ejeX ? e.getX() + largo / 2 : e.getX(), y2 = ejeX ? e.getY() : e.getY() + largo / 2;
            double[] a = erickDelgadoAnclar(centros, x1, y1), z = erickDelgadoAnclar(centros, x2, y2);
            if (a == null || z == null) avisos.add("La viga " + e.getNombre() + " no termina sobre una columna: se usó el extremo de la viga.");
            String pa = punto.apply(a == null ? x1 : a[0], a == null ? y1 : a[1]);
            String pb = punto.apply(z == null ? x2 : z[0], z == null ? y2 : z[1]);
            if (pa.equals(pb)) { avisos.add("La viga " + e.getNombre() + " quedó con longitud nula y se omitió."); continue; }
            String sec = "VIG" + Math.round(ancho * 100) + "X" + Math.round(e.getAltura() * 100);
            secciones.putIfAbsent(sec, "  FRAMESECTION  \"" + sec + "\"  MATERIAL \"CONCRETO\"  SHAPE \"Rectangular\"  D " + f(e.getAltura()) + "  B " + f(ancho));
            String nombre = "B" + (++b);
            lineas.add("  LINE  \"" + nombre + "\"  BEAM  \"" + pa + "\"  \"" + pb + "\"  0");
            asignaciones.add("  LINEASSIGN  \"" + nombre + "\"  \"" + nombrePiso.get(idx) + "\"  SECTION \"" + sec + "\"");
        }
        var propsAreas = new LinkedHashMap<String, String>();
        int a = 0;
        for (Elemento e : losas) {
            int idx = erickDelgadoIndicePiso(niveles, e.getZ() + e.getAltura() / 2);
            if (idx < 0) { avisos.add("La losa " + e.getNombre() + " no está en un piso reconocido y se omitió."); continue; }
            String prop = "LOSA" + Math.round(e.getAltura() * 100);
            propsAreas.putIfAbsent(prop, "  SHELLPROP  \"" + prop + "\"  PROPTYPE \"Slab\"  MATERIAL \"CONCRETO\"  MODELINGTYPE \"Membrane\"  SLABTYPE \"Slab\"  SLABTHICKNESS " + f(e.getAltura()));
            double x0 = e.getX() - e.getLargo() / 2, x1 = e.getX() + e.getLargo() / 2, y0 = e.getY() - e.getAncho() / 2, y1 = e.getY() + e.getAncho() / 2;
            String nombre = "F" + (++a);
            areas.add("  AREA  \"" + nombre + "\"  PANEL  4  \"" + punto.apply(x0, y0) + "\"  \"" + punto.apply(x1, y0) + "\"  \"" + punto.apply(x1, y1) + "\"  \"" + punto.apply(x0, y1) + "\"  0  0  0  0");
            asignAreas.add("  AREAASSIGN  \"" + nombre + "\"  \"" + nombrePiso.get(idx) + "\"  SECTION \"" + prop + "\"");
        }
        int w = 0;
        for (Elemento e : muros) {
            int idx = erickDelgadoIndicePiso(niveles, e.getZ() + e.getAltura() / 2);
            if (idx < 0) { avisos.add("El muro " + e.getNombre() + " no termina en un piso reconocido y se omitió."); continue; }
            boolean ejeX = e.getLargo() >= e.getAncho();
            double largo = ejeX ? e.getLargo() : e.getAncho(), esp = ejeX ? e.getAncho() : e.getLargo();
            String prop = "MURO" + Math.round(esp * 100);
            propsAreas.putIfAbsent(prop, "  SHELLPROP  \"" + prop + "\"  PROPTYPE \"Wall\"  MATERIAL \"CONCRETO\"  MODELINGTYPE \"ShellThin\"  WALLTHICKNESS " + f(esp));
            String p1 = punto.apply(ejeX ? e.getX() - largo / 2 : e.getX(), ejeX ? e.getY() : e.getY() - largo / 2);
            String p2 = punto.apply(ejeX ? e.getX() + largo / 2 : e.getX(), ejeX ? e.getY() : e.getY() + largo / 2);
            String nombre = "W" + (++w);
            areas.add("  AREA  \"" + nombre + "\"  PANEL  4  \"" + p1 + "\"  \"" + p2 + "\"  \"" + p2 + "\"  \"" + p1 + "\"  0  0  0  0");
            asignAreas.add("  AREAASSIGN  \"" + nombre + "\"  \"" + nombrePiso.get(idx) + "\"  SECTION \"" + prop + "\"");
        }
        if (!muros.isEmpty()) avisos.add("Los muros se exportaron como paneles de pared con puntos repetidos: verifique su definición en ETABS.");
        avisos.add("Archivo experimental: no se ha verificado su importación en ETABS. Revise pisos, secciones y apoyos antes de analizar.");

        var sb = new StringBuilder();
        sb.append("$ File generated by ConcretoLab Desktop (experimental)\n\n");
        sb.append("$ PROGRAM INFORMATION\n  PROGRAM  \"ETABS\"  VERSION \"9.7.4\"\n\n");
        sb.append("$ CONTROLS\n  UNITS  \"KN\"  \"M\"  \"C\"\n\n");
        sb.append("$ STORIES - IN SEQUENCE FROM TOP\n");
        for (int i = niveles.size() - 1; i >= 0; i--) {
            double h = niveles.get(i) - (i == 0 ? base : niveles.get(i - 1));
            sb.append("  STORY  \"").append(nombrePiso.get(i)).append("\"  HEIGHT ").append(f(h));
            if (i == niveles.size() - 1) sb.append("  MASTERSTORY \"Yes\"");
            sb.append('\n');
        }
        sb.append("  STORY  \"BASE\"  ELEV ").append(f(base)).append("\n\n");
        double ec = 4700 * Math.sqrt(fcMpa) * 1000; // kN/m²
        sb.append("$ MATERIAL PROPERTIES\n");
        sb.append("  MATERIAL  \"CONCRETO\"  TYPE \"Concrete\"  WEIGHTPERVOLUME 23.5\n");
        sb.append("  MATERIAL  \"CONCRETO\"  SYMTYPE \"Isotropic\"  E ").append(String.format(Locale.ROOT, "%.0f", ec)).append("  U 0.2  A 0.0000099\n");
        sb.append("  MATERIAL  \"CONCRETO\"  FC ").append(String.format(Locale.ROOT, "%.0f", fcMpa * 1000)).append("\n\n");
        sb.append("$ FRAME SECTIONS\n");
        secciones.values().forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ SHELL/SLAB/DECK/WALL PROPERTIES\n");
        propsAreas.values().forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ POINT COORDINATES\n");
        puntos.forEach((n, p) -> sb.append("  POINT  \"").append(n).append("\"  ").append(f(p[0])).append(' ').append(f(p[1])).append('\n'));
        sb.append("\n$ LINE CONNECTIVITIES\n");
        lineas.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ AREA CONNECTIVITIES\n");
        areas.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ POINT ASSIGNS\n");
        puntosAsig.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ LINE ASSIGNS\n");
        asignaciones.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n$ AREA ASSIGNS\n");
        asignAreas.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n  END\n$ END OF MODEL FILE\n");
        return new Resultado(sb.toString(), List.copyOf(avisos));
    }

    private static int erickDelgadoIndicePiso(List<Double> niveles, double cota) {
        for (int i = 0; i < niveles.size(); i++) {
            if (Math.abs(niveles.get(i) - cota) <= ERICK_DELGADO_TOL) return i;
        }
        return -1;
    }

    /** Centro de la columna cuya huella (con 5 cm de holgura) contiene el punto, o null. */
    private static double[] erickDelgadoAnclar(List<double[]> centros, double x, double y) {
        double[] mejor = null;
        double dmin = Double.MAX_VALUE;
        for (double[] c : centros) {
            if (Math.abs(x - c[0]) <= c[2] + 0.05 && Math.abs(y - c[1]) <= c[3] + 0.05) {
                double d = Math.hypot(x - c[0], y - c[1]);
                if (d < dmin) { dmin = d; mejor = c; }
            }
        }
        return mejor;
    }
}
