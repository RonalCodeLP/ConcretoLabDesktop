package com.concretolab.service;

import com.concretolab.service.FernandoHuancaPlanoDxf.Entidad;
import com.concretolab.service.FernandoHuancaPlanoDxf.Plano;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reconoce el rol de las capas de un plano CAD por su nombre (convenciones habituales en el Perú y en inglés) y arma un
 * diagnóstico legible de lo que se usará y de lo que se ignora. Solo sugiere: la persona usuaria confirma el mapeo.
 */
public final class DeteccionCapasDxf {

    private DeteccionCapasDxf() { }

    public enum Rol {
        COLUMNAS("columnas", List.of("COLUMNA", "COLUMNAS", "COL", "COLS", "COLUMN", "COLUMNS", "PILAR", "PILARES")),
        VIGAS("vigas", List.of("VIGA", "VIGAS", "VG", "BEAM", "BEAMS", "VIG")),
        ZAPATAS("zapatas", List.of("ZAPATA", "ZAPATAS", "ZAP", "CIMENTACION", "CIMIENTO", "CIMIENTOS", "FOOTING", "FOOTINGS", "FOUNDATION")),
        LOSAS("losas", List.of("LOSA", "LOSAS", "ALIGERADO", "ALIGERADOS", "SLAB", "SLABS", "TECHO")),
        MUROS("muros", List.of("MURO", "MUROS", "PLACA", "PLACAS", "WALL", "WALLS", "MUR", "ALBANILERIA")),
        EJES("ejes", List.of("EJE", "EJES", "GRID", "GRIDS", "AXIS", "AXES", "TRAZO"));

        private final String etiqueta;
        private final List<String> palabras;

        Rol(String etiqueta, List<String> palabras) {
            this.etiqueta = etiqueta;
            this.palabras = palabras;
        }

        public String etiqueta() { return etiqueta; }
    }

    /** Capa sugerida por rol; los roles sin capa reconocida no aparecen. */
    public record Mapeo(Map<Rol, String> capas) {
        public String de(Rol rol) { return capas.get(rol); }
    }

    public record Unidad(double metrosPorUnidad, String descripcion, boolean declarada) { }

    public record Diagnostico(List<String> lineas, List<String> avisos) {
        public String texto() {
            var sb = new StringBuilder();
            lineas.forEach(l -> sb.append(l).append('\n'));
            if (!avisos.isEmpty()) {
                sb.append("\nAvisos:\n");
                avisos.forEach(a -> sb.append("• ").append(a).append('\n'));
            }
            return sb.toString().stripTrailing();
        }
    }

    private static final Set<String> ERICK_DELGADO_TIPOS_PUNTUALES = Set.of("POINT", "BLOQUE", "RECTÁNGULO", "CÍRCULO");

    /** Quita tildes, pasa a mayúsculas y separa en palabras (por guiones, puntos, espacios, $, _ y |). */
    static List<String> erickDelgadoPalabras(String capa) {
        String limpio = Normalizer.normalize(capa, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return Arrays.stream(limpio.split("[^A-Z0-9]+")).filter(s -> !s.isEmpty()).toList();
    }

    /** Puntaje del nombre de capa para un rol: palabra exacta 3, prefijo de palabra 2, contenido 1, nada 0. */
    public static int erickDelgadoPuntaje(String capa, Rol rol) {
        int mejor = 0;
        for (String p : erickDelgadoPalabras(capa)) {
            for (String clave : rol.palabras) {
                if (p.equals(clave)) mejor = Math.max(mejor, 3);
                else if (clave.length() >= 4 && (p.startsWith(clave) || clave.startsWith(p) && p.length() >= 4)) mejor = Math.max(mejor, 2);
                else if (clave.length() >= 5 && p.contains(clave)) mejor = Math.max(mejor, 1);
            }
        }
        return mejor;
    }

    private static Map<String, List<Entidad>> erickDelgadoPorCapa(Plano plano) {
        return plano.entidades().stream().collect(Collectors.groupingBy(Entidad::capa, TreeMap::new, Collectors.toList()));
    }

    private static boolean erickDelgadoCompatible(List<Entidad> es, Set<String> tipos) {
        return !es.isEmpty() && es.size() <= 200 && es.stream().allMatch(e -> tipos.contains(e.tipo()));
    }

    /** Sugiere una capa por rol entre las capas compatibles con el uso previsto (puntos/rectángulos o líneas). */
    public static Mapeo erickDelgadoSugerir(Plano plano) {
        var mapa = new LinkedHashMap<Rol, String>();
        var capas = erickDelgadoPorCapa(plano);
        for (Rol rol : Rol.values()) {
            Set<String> tipos = switch (rol) {
                case COLUMNAS, ZAPATAS -> ERICK_DELGADO_TIPOS_PUNTUALES;
                case VIGAS, EJES -> Set.of("LINE");
                case LOSAS -> Set.of("RECTÁNGULO");
                case MUROS -> Set.of("LINE");
            };
            String elegida = null;
            int mejorPuntaje = 0;
            for (var e : capas.entrySet()) {
                if (!tipos.isEmpty() && !erickDelgadoCompatible(e.getValue(), tipos)) continue;
                int p = erickDelgadoPuntaje(e.getKey(), rol);
                if (p > mejorPuntaje) {
                    mejorPuntaje = p;
                    elegida = e.getKey();
                }
            }
            if (elegida != null && !(rol == Rol.ZAPATAS && mapa.containsValue(elegida))) mapa.put(rol, elegida);
        }
        return new Mapeo(mapa);
    }

    /** Metros por unidad: la declarada en $INSUNITS o, si falta, una estimación por el tamaño del dibujo. */
    public static Unidad erickDelgadoUnidad(Plano plano) {
        double declarado = FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad(plano.unidad());
        if (!Double.isNaN(declarado)) {
            return new Unidad(declarado, FernandoHuancaPlanoDxf.fernandoHuancaNombreUnidad(plano.unidad()), true);
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean hay = false;
        for (Entidad e : plano.entidades()) {
            if (e.inicio() == null || !(ERICK_DELGADO_TIPOS_PUNTUALES.contains(e.tipo()) || "LINE".equals(e.tipo()))) continue;
            for (var p : new FernandoHuancaPlanoDxf.Punto[] {e.inicio(), e.fin()}) {
                if (p == null) continue;
                hay = true;
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
        }
        if (!hay) return new Unidad(Double.NaN, "no se pudo estimar", false);
        double extension = Math.max(maxX - minX, maxY - minY);
        if (extension > 2000) return new Unidad(0.001, "milímetros (estimado por el tamaño del dibujo)", false);
        if (extension > 200) return new Unidad(0.01, "centímetros (estimado por el tamaño del dibujo)", false);
        return new Unidad(1.0, "metros (estimado por el tamaño del dibujo)", false);
    }

    public static Diagnostico erickDelgadoDiagnosticar(Plano plano) {
        var lineas = new ArrayList<String>();
        var avisos = new ArrayList<String>();
        var capas = erickDelgadoPorCapa(plano);
        var mapeo = erickDelgadoSugerir(plano);
        for (var e : capas.entrySet()) {
            var conteo = e.getValue().stream().collect(Collectors.groupingBy(Entidad::tipo, TreeMap::new, Collectors.counting()));
            String rol = Arrays.stream(Rol.values()).filter(r -> e.getKey().equals(mapeo.de(r))).map(Rol::etiqueta).findFirst()
                    .orElseGet(() -> Arrays.stream(Rol.values()).filter(r -> erickDelgadoPuntaje(e.getKey(), r) >= 2).map(Rol::etiqueta).findFirst().orElse(null));
            lineas.add(e.getKey() + " · " + conteo + (rol == null ? "" : "  → " + rol));
        }
        var tiposIgnorados = plano.entidades().stream().map(Entidad::tipo)
                .filter(t -> !ERICK_DELGADO_TIPOS_PUNTUALES.contains(t) && !"LINE".equals(t)).collect(Collectors.groupingBy(t -> t, TreeMap::new, Collectors.counting()));
        if (!tiposIgnorados.isEmpty()) {
            avisos.add("Se ignoran entidades que no definen elementos estructurales: " + tiposIgnorados + ".");
        }
        for (Rol rol : List.of(Rol.LOSAS, Rol.MUROS)) {
            if (mapeo.de(rol) == null) {
                capas.keySet().stream().filter(c -> erickDelgadoPuntaje(c, rol) >= 2).findFirst().ifPresent(c -> avisos.add(
                        "La capa " + c + " parece de " + rol.etiqueta() + " pero no se puede importar: "
                        + (rol == Rol.LOSAS ? "las losas deben ser rectángulos cerrados" : "los muros deben ser líneas o polilíneas de 2 puntos")
                        + " (≤ 200 entidades) sin otros tipos en la capa."));
            }
        }
        capas.forEach((capa, es) -> {
            if (es.size() > 200) avisos.add("La capa " + capa + " tiene " + es.size() + " entidades (el límite es 200 por capa estructural).");
        });
        if (plano.entidades().stream().anyMatch(e -> e.tipo().endsWith("inválida"))) {
            avisos.add("Hay entidades inválidas (coordenadas faltantes o fuera de rango): revise el dibujo.");
        }
        if (plano.entidades().stream().anyMatch(e -> "CÍRCULO".equals(e.tipo()))) {
            avisos.add("Los círculos se importan como columnas cuadradas de lado igual al diámetro; ajuste la sección si es circular.");
        }
        if (plano.entidades().stream().anyMatch(e -> "BLOQUE".equals(e.tipo()))) {
            avisos.add("Los bloques se usan solo como punto de inserción (centro); indique el lado de la sección.");
        }
        if (plano.entidades().stream().anyMatch(e -> "PRESENTACIÓN".equals(e.tipo()))) {
            avisos.add("Se ignoran las entidades del espacio papel (presentaciones).");
        }
        var unidad = erickDelgadoUnidad(plano);
        if (!unidad.declarada()) {
            avisos.add("El archivo no declara unidades: se estima " + unidad.descripcion() + ". Verifique la escala antes de guardar.");
        }
        lineas.sort(Comparator.naturalOrder());
        return new Diagnostico(List.copyOf(lineas), List.copyOf(avisos));
    }
}
