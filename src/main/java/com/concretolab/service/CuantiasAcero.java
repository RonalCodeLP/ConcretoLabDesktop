package com.concretolab.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class CuantiasAcero {
    private CuantiasAcero() { }

    static final String YEFRI_LERMA_ARCHIVO = "cuantias-acero.properties";
    public static final double YEFRI_LERMA_MAXIMO = 400;

    public static Path yefriLermaArchivo() {
        return RespaldoDatos.erickDelgadoCarpetaDatos().resolve(YEFRI_LERMA_ARCHIVO);
    }

    public static Map<String, Double> yefriLermaLeer(Path archivo) {
        var cuantias = new LinkedHashMap<>(MetradoNeto.yefriLermaCuantiasReferenciales());
        if (archivo == null || !Files.isRegularFile(archivo)) return cuantias;
        var p = new Properties();
        try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return cuantias;
        }
        for (String tipo : cuantias.keySet()) {
            String v = p.getProperty(tipo);
            if (v == null) continue;
            try {
                double d = Double.parseDouble(v.trim().replace(',', '.'));
                if (Double.isFinite(d) && d >= 0 && d <= YEFRI_LERMA_MAXIMO) cuantias.put(tipo, d);
            } catch (NumberFormatException ignorada) {
            }
        }
        return cuantias;
    }

    public static Map<String, Double> yefriLermaLeer() {
        return yefriLermaLeer(yefriLermaArchivo());
    }

    public static void yefriLermaGuardar(Path archivo, Map<String, Double> cuantias) throws IOException {
        var p = new Properties();
        for (var e : cuantias.entrySet()) {
            double d = e.getValue() == null ? Double.NaN : e.getValue();
            if (!Double.isFinite(d) || d < 0 || d > YEFRI_LERMA_MAXIMO) {
                throw new IllegalArgumentException("La cuantía de " + e.getKey().toLowerCase(Locale.ROOT)
                        + " debe estar entre 0 y " + (int) YEFRI_LERMA_MAXIMO + " kg/m³.");
            }
            p.setProperty(e.getKey(), Double.toString(d));
        }
        Files.createDirectories(archivo.getParent());
        try (Writer w = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
            p.store(w, "Cuantias de acero (kg/m3) del metrado referencial de ConcretoLab");
        }
    }

    public static void yefriLermaGuardar(Map<String, Double> cuantias) throws IOException {
        yefriLermaGuardar(yefriLermaArchivo(), cuantias);
    }
}
