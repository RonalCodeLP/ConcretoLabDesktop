package com.concretolab.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa acelerogramas desde archivos de texto (CISMID, IGP, CSMIP, PEER-NGA y similares).
 *
 * <p>Formatos admitidos: una columna (solo aceleración; dt del usuario o del encabezado: «DT=0.005», «dt: 0.005», «DELTA T 0.01»,
 * «INTERVALO 0.005»), dos columnas (tiempo, aceleración) o tres columnas (tiempo, ax, ay). Separadores: espacio, tabulación, coma
 * o punto y coma; se admite coma decimal. Las líneas de texto previas a los datos (encabezado) se omiten. Si hay más de tres columnas
 * y se conoce dt, todos los valores se leen como una sola serie (formato PEER-NGA, varias aceleraciones por línea).
 * El tamaño máximo del archivo es de 10 MB.
 */
public final class ImportadorRegistro {
    private ImportadorRegistro() {}

    /** Tamaño máximo del archivo (bytes). */
    public static final long FERNANDO_HUANCA_TAMANO_MAXIMO = 10L * 1024 * 1024;

    /** Unidad de la aceleración del archivo, con su factor de conversión a m/s². */
    public enum UnidadAceleracion {
        M_S2("m/s²", 1.0), CM_S2("cm/s² (gal)", 0.01), G("g", 9.80665);
        public final String etiqueta;
        /** Factor que convierte a m/s². */
        public final double factor;
        UnidadAceleracion(String e, double f) { etiqueta = e; factor = f; }
        @Override public String toString() { return etiqueta; }
    }

    /** Una componente de aceleración (m/s²) con su paso de tiempo. */
    public record Componente(double dt, double[] a) {}

    private record Leido(String nombre, int columnas, double[][] cols, double dtArchivo, UnidadAceleracion sugerida) {}

    private static final Pattern FERNANDO_HUANCA_NUMERICA = Pattern.compile("^[\\s0-9eE+\\-.,;]+$");
    private static final Pattern FERNANDO_HUANCA_DIGITO = Pattern.compile("[0-9]");
    private static final Pattern FERNANDO_HUANCA_DT = Pattern.compile(
            "(?i)(?<![A-Za-z])(?:DT|DELTA\\s*T|DELTAT|INTERVALO(?:\\s+DE\\s+MUESTREO)?)\\s*[=:]?\\s*([0-9]*[.,]?[0-9]+(?:[eE][+-]?[0-9]+)?)");

    /** Importa un registro de dos componentes desde un archivo de tres columnas (tiempo, ax, ay). */
    public static RegistroSismico fernandoHuancaImportar(Path archivo, UnidadAceleracion unidad, Double dtOpcional) {
        Leido l = fernandoHuancaLeer(archivo, unidad, dtOpcional);
        if (l.columnas != 3)
            throw new IllegalArgumentException("El archivo «" + fernandoHuancaNombreArchivo(archivo) + "» contiene una sola componente de aceleración. El análisis tiempo-historia necesita dos "
                    + "componentes horizontales ortogonales: importe el segundo archivo y use la importación de pares, o use un archivo de tres columnas (tiempo, ax, ay)." + fernandoHuancaSugerencia(l, unidad));
        double dt = fernandoHuancaDtDeTiempo(l.cols[0], dtOpcional, archivo);
        double[] ax = fernandoHuancaConvertir(l.cols[1], unidad), ay = fernandoHuancaConvertir(l.cols[2], unidad);
        return new RegistroSismico(l.nombre, "Archivo importado: " + fernandoHuancaNombreArchivo(archivo) + " (" + unidad.etiqueta + ")", dt, ax, ay);
    }

    /** Importa un registro con una componente por archivo (X e Y). */
    public static RegistroSismico fernandoHuancaImportarPar(Path componenteX, Path componenteY, UnidadAceleracion unidad, Double dtOpcional) {
        Componente cx = fernandoHuancaImportarUnaComponente(componenteX, unidad, dtOpcional);
        Componente cy = fernandoHuancaImportarUnaComponente(componenteY, unidad, dtOpcional);
        if (Math.abs(cx.dt - cy.dt) > 0.01 * cx.dt)
            throw new IllegalArgumentException("Los dos archivos tienen distinto paso de tiempo (X: " + cx.dt + " s, Y: " + cy.dt + " s). Las componentes deben estar muestreadas igual.");
        int nx = cx.a.length, ny = cy.a.length;
        int n = Math.min(nx, ny);
        if (Math.abs(nx - ny) > Math.max(1, n / 100))
            throw new IllegalArgumentException("Los dos archivos tienen distinto número de datos (X: " + nx + ", Y: " + ny + "). Las componentes deben tener la misma duración.");
        String nombre = fernandoHuancaNombreArchivo(componenteX).replaceFirst("\\.[^.]*$", "") + " + " + fernandoHuancaNombreArchivo(componenteY).replaceFirst("\\.[^.]*$", "");
        return new RegistroSismico(nombre, "Archivos importados: " + fernandoHuancaNombreArchivo(componenteX) + " (X), " + fernandoHuancaNombreArchivo(componenteY) + " (Y) (" + unidad.etiqueta + ")",
                cx.dt, Arrays.copyOf(cx.a, n), Arrays.copyOf(cy.a, n));
    }

    /** Importa un archivo con una sola componente (1 columna, o tiempo y aceleración); la aceleración sale en m/s². */
    public static Componente fernandoHuancaImportarUnaComponente(Path archivo, UnidadAceleracion unidad, Double dtOpcional) {
        Leido l = fernandoHuancaLeer(archivo, unidad, dtOpcional);
        if (l.columnas == 3)
            throw new IllegalArgumentException("El archivo «" + fernandoHuancaNombreArchivo(archivo) + "» tiene tres columnas (tiempo, ax, ay): importe el registro completo con importar(...) en lugar de una sola componente.");
        double dt;
        if (l.columnas == 2) dt = fernandoHuancaDtDeTiempo(l.cols[0], dtOpcional, archivo);
        else dt = dtOpcional != null ? dtOpcional : l.dtArchivo;
        if (!(dt > 0) || !Double.isFinite(dt))
            throw new IllegalArgumentException("No se pudo determinar el paso de tiempo dt del archivo «" + fernandoHuancaNombreArchivo(archivo) + "»: indíquelo (por ejemplo 0,005 s) o agregue «DT=0.005» en el encabezado.");
        double[] a = fernandoHuancaConvertir(l.cols[l.columnas == 2 ? 1 : 0], unidad);
        return new Componente(dt, a);
    }

    // ---------------------------------------------------------------- lectura

    private static Leido fernandoHuancaLeer(Path archivo, UnidadAceleracion unidad, Double dtOpcional) {
        if (archivo == null) throw new IllegalArgumentException("Seleccione un archivo de registro sísmico.");
        if (unidad == null) throw new IllegalArgumentException("Indique la unidad de la aceleración (m/s², cm/s² o g).");
        if (dtOpcional != null && !(dtOpcional > 0 && Double.isFinite(dtOpcional))) throw new IllegalArgumentException("El paso de tiempo dt indicado debe ser un número positivo.");
        String nombre = fernandoHuancaNombreArchivo(archivo);
        byte[] bytes;
        try {
            if (!Files.isRegularFile(archivo)) throw new IllegalArgumentException("No se encontró el archivo «" + nombre + "».");
            if (Files.size(archivo) > FERNANDO_HUANCA_TAMANO_MAXIMO) throw new IllegalArgumentException("El archivo «" + nombre + "» supera el límite de 10 MB.");
            bytes = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo «" + nombre + "»: " + e.getMessage());
        }
        String[] lineas = new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n|\\r");

        boolean hayPunto = false, hayComa = false;
        for (String s : lineas) {
            if (fernandoHuancaEsNumerica(s)) { if (s.indexOf('.') >= 0) hayPunto = true; if (s.indexOf(',') >= 0) hayComa = true; }
        }
        boolean comaDecimal = !hayPunto && hayComa;

        StringBuilder encabezado = new StringBuilder();
        List<double[]> filas = new ArrayList<>();
        List<Integer> numeroLinea = new ArrayList<>();
        for (int i = 0; i < lineas.length; i++) {
            String s = lineas[i].trim();
            if (s.isEmpty()) continue;
            double[] fila = fernandoHuancaEsNumerica(s) ? fernandoHuancaParsear(s, comaDecimal) : null;
            if (fila == null) {
                if (!filas.isEmpty())
                    throw new IllegalArgumentException("Línea " + (i + 1) + " de «" + nombre + "» no es numérica («" + fernandoHuancaRecortar(s) + "»): el archivo mezcla datos con texto." + fernandoHuancaSugerencia(encabezado.toString(), unidad));
                encabezado.append(s).append('\n');
            } else { filas.add(fila); numeroLinea.add(i + 1); }
        }
        String enc = encabezado.toString();
        UnidadAceleracion sugerida = fernandoHuancaDetectarUnidad(enc);
        if (filas.isEmpty())
            throw new IllegalArgumentException("El archivo «" + nombre + "» no contiene datos numéricos de aceleración." + fernandoHuancaSugerencia(enc, unidad));
        Double dtEnc = fernandoHuancaDetectarDt(enc);
        int columnas = filas.get(0).length;
        if (columnas > 3) {
            if (dtOpcional == null && dtEnc == null)
                throw new IllegalArgumentException("El archivo «" + nombre + "» tiene " + columnas + " columnas por línea; se leerá como una sola serie, pero falta dt (indíquelo o agregue «DT=0.005» en el encabezado).");
            double[] serie = filas.stream().flatMapToDouble(Arrays::stream).toArray();
            return fernandoHuancaValidar(new Leido(nombre, 1, new double[][]{serie}, dtEnc == null ? Double.NaN : dtEnc, sugerida), unidad);
        }
        double[][] cols = new double[columnas][filas.size()];
        for (int r = 0; r < filas.size(); r++) {
            double[] f = filas.get(r);
            if (f.length != columnas)
                throw new IllegalArgumentException("Línea " + numeroLinea.get(r) + " de «" + nombre + "» tiene " + f.length + " columnas y se esperaban " + columnas + "." + fernandoHuancaSugerencia(enc, unidad));
            for (int c = 0; c < columnas; c++) cols[c][r] = f[c];
        }
        return fernandoHuancaValidar(new Leido(nombre, columnas, cols, dtEnc == null ? Double.NaN : dtEnc, sugerida), unidad);
    }

    private static Leido fernandoHuancaValidar(Leido l, UnidadAceleracion unidad) {
        int n = l.cols[0].length;
        if (n < 2) throw new IllegalArgumentException("El archivo «" + l.nombre + "» tiene menos de 2 datos de aceleración.");
        if (n > RegistroSismico.PASOS_MAXIMOS)
            throw new IllegalArgumentException("El archivo «" + l.nombre + "» tiene " + n + " datos; el máximo admitido es " + RegistroSismico.PASOS_MAXIMOS + ".");
        return l;
    }

    private static boolean fernandoHuancaEsNumerica(String s) { return FERNANDO_HUANCA_NUMERICA.matcher(s).matches() && FERNANDO_HUANCA_DIGITO.matcher(s).find(); }

    private static double[] fernandoHuancaParsear(String linea, boolean comaDecimal) {
        String[] tk = comaDecimal ? linea.trim().split("[\\s;]+") : linea.trim().split("[\\s;,]+");
        List<Double> v = new ArrayList<>();
        for (String t : tk) {
            if (t.isEmpty()) continue;
            try { v.add(Double.parseDouble(comaDecimal ? t.replace(',', '.') : t)); }
            catch (NumberFormatException e) { return null; }
        }
        if (v.isEmpty()) return null;
        double[] r = new double[v.size()];
        for (int i = 0; i < r.length; i++) {
            if (!Double.isFinite(v.get(i))) return null;
            r[i] = v.get(i);
        }
        return r;
    }

    private static Double fernandoHuancaDetectarDt(String encabezado) {
        Matcher m = FERNANDO_HUANCA_DT.matcher(encabezado);
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1).replace(',', '.'));
                if (v > 0) return v;
            } catch (NumberFormatException ignorada) { /* sigue buscando */ }
        }
        return null;
    }

    private static UnidadAceleracion fernandoHuancaDetectarUnidad(String encabezado) {
        String s = encabezado.toLowerCase(Locale.ROOT);
        if (Pattern.compile("cm\\s*/\\s*s(\\^?2|²|eg)|\\bgals?\\b").matcher(s).find()) return UnidadAceleracion.CM_S2;
        if (Pattern.compile("m\\s*/\\s*s(\\^?2|²|eg)").matcher(s).find()) return UnidadAceleracion.M_S2;
        if (Pattern.compile("\\(g\\)|\\bin\\s+g\\b|units?\\s+of\\s+g\\b|unidades?\\s*[:=]?\\s*g\\b|\\bg\\b").matcher(s).find()) return UnidadAceleracion.G;
        return null;
    }

    private static String fernandoHuancaSugerencia(Leido l, UnidadAceleracion elegida) {
        return l.sugerida == null || l.sugerida == elegida ? "" : " El encabezado sugiere unidades de " + l.sugerida.etiqueta + " (se eligió " + elegida.etiqueta + ").";
    }

    private static String fernandoHuancaSugerencia(String encabezado, UnidadAceleracion elegida) {
        UnidadAceleracion s = fernandoHuancaDetectarUnidad(encabezado);
        return s == null || s == elegida ? "" : " El encabezado sugiere unidades de " + s.etiqueta + " (se eligió " + elegida.etiqueta + ").";
    }

    // ---------------------------------------------------------------- utilidades

    /** dt de la columna de tiempo: mediana de los incrementos, con uniformidad ±1 %. */
    private static double fernandoHuancaDtDeTiempo(double[] t, Double dtOpcional, Path archivo) {
        int n = t.length;
        double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) d[i] = t[i + 1] - t[i];
        double[] ord = d.clone();
        Arrays.sort(ord);
        double dt = ord[ord.length / 2];
        if (!(dt > 0)) throw new IllegalArgumentException("La columna de tiempo de «" + fernandoHuancaNombreArchivo(archivo) + "» no es creciente.");
        for (int i = 0; i < d.length; i++) {
            if (Math.abs(d[i] - dt) > 0.01 * dt)
                throw new IllegalArgumentException("El paso de tiempo de «" + fernandoHuancaNombreArchivo(archivo) + "» no es uniforme: entre t = " + t[i] + " y t = " + t[i + 1]
                        + " s hay " + d[i] + " s y lo habitual es " + dt + " s (tolerancia 1 %). Remuestree el registro a paso constante.");
        }
        if (dtOpcional != null && Math.abs(dtOpcional - dt) > 0.01 * dt)
            throw new IllegalArgumentException("El dt indicado (" + dtOpcional + " s) no coincide con el de la columna de tiempo de «" + fernandoHuancaNombreArchivo(archivo) + "» (" + dt + " s).");
        return dt;
    }

    private static double[] fernandoHuancaConvertir(double[] a, UnidadAceleracion u) {
        double[] r = new double[a.length];
        for (int i = 0; i < r.length; i++) r[i] = a[i] * u.factor;
        return r;
    }

    private static String fernandoHuancaNombreArchivo(Path p) { return p.getFileName() == null ? p.toString() : p.getFileName().toString(); }

    private static String fernandoHuancaRecortar(String s) { return s.length() > 40 ? s.substring(0, 40) + "…" : s; }
}
