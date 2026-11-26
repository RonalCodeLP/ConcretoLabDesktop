package com.concretolab.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

public final class BaseCifrada {
    private BaseCifrada() { }

    public enum Estado { NO_EXISTE, SIN_CIFRAR, CIFRADA }

    private static final byte[] ERICK_DELGADO_CABECERA = "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1);
    public static final int ERICK_DELGADO_LONGITUD_MINIMA = 8;

    public static Properties erickDelgadoPropiedades(char[] clave) {
        Properties p = new Properties();
        p.setProperty("cipher", "sqlcipher");
        p.setProperty("legacy", "4");
        if (clave != null && clave.length > 0) p.setProperty("key", new String(clave));
        return p;
    }

    public static Map<String, Object> erickDelgadoPropiedadesSpring(char[] clave) {
        Properties p = erickDelgadoPropiedades(clave);
        var m = new java.util.LinkedHashMap<String, Object>();
        for (String k : p.stringPropertyNames()) m.put("spring.datasource.hikari.data-source-properties." + k, p.getProperty(k));
        return m;
    }

    public static Estado erickDelgadoInspeccionar(Path base) {
        try {
            if (base == null || !Files.isRegularFile(base) || Files.size(base) == 0) return Estado.NO_EXISTE;
            try (InputStream in = Files.newInputStream(base)) {
                byte[] leido = in.readNBytes(ERICK_DELGADO_CABECERA.length);
                return Arrays.equals(leido, ERICK_DELGADO_CABECERA) ? Estado.SIN_CIFRAR : Estado.CIFRADA;
            }
        } catch (IOException e) {
            return Estado.NO_EXISTE;
        }
    }

    private static String erickDelgadoUrl(Path base) {
        return "jdbc:sqlite:" + base.toAbsolutePath();
    }

    public static boolean erickDelgadoClaveCorrecta(Path base, char[] clave) {
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(clave));
             Statement s = c.createStatement()) {
            s.executeQuery("SELECT count(*) FROM sqlite_master").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static void erickDelgadoValidarClaveNueva(char[] clave) {
        if (clave == null || clave.length < ERICK_DELGADO_LONGITUD_MINIMA) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + ERICK_DELGADO_LONGITUD_MINIMA + " caracteres.");
        }
    }

    public static Path erickDelgadoCifrarExistente(Path base, char[] clave) throws SQLException, IOException {
        erickDelgadoValidarClaveNueva(clave);
        if (erickDelgadoInspeccionar(base) != Estado.SIN_CIFRAR) throw new IllegalStateException("La base no está sin cifrar.");
        Path copia = base.resolveSibling(base.getFileName() + ".sin-cifrar.bak");
        Files.copy(base, copia, StandardCopyOption.REPLACE_EXISTING);
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(null));
             Statement s = c.createStatement()) {
            s.execute("PRAGMA rekey='" + erickDelgadoEscapar(clave) + "'");
        } catch (SQLException e) {
            Files.copy(copia, base, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
        if (erickDelgadoInspeccionar(base) != Estado.CIFRADA || !erickDelgadoClaveCorrecta(base, clave)) {
            Files.copy(copia, base, StandardCopyOption.REPLACE_EXISTING);
            throw new SQLException("No se pudo verificar la base cifrada; se conservó la original.");
        }
        return copia;
    }

    public static void erickDelgadoCambiarClave(Path base, char[] actual, char[] nueva) throws SQLException {
        erickDelgadoValidarClaveNueva(nueva);
        if (!erickDelgadoClaveCorrecta(base, actual)) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(actual));
             Statement s = c.createStatement()) {
            s.execute("PRAGMA rekey='" + erickDelgadoEscapar(nueva) + "'");
        }
        if (!erickDelgadoClaveCorrecta(base, nueva)) throw new SQLException("No se pudo verificar la nueva contraseña.");
    }

    public static final String ERICK_DELGADO_MOTIVO_ANTES_DE_QUITAR_CLAVE = "antes-de-quitar-clave";

    public static Path erickDelgadoQuitarClave(Path base, char[] clave, Path carpetaCopias) throws SQLException, IOException {
        if (erickDelgadoInspeccionar(base) != Estado.CIFRADA) throw new IllegalStateException("La base no está protegida con contraseña.");
        if (!erickDelgadoClaveCorrecta(base, clave)) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        Map<String, Long> antes = erickDelgadoFilasPorTabla(base, clave);

        Files.createDirectories(carpetaCopias);
        java.time.LocalDateTime marca = java.time.LocalDateTime.now().withNano(0);
        Path copia = carpetaCopias.resolve(RespaldoDatos.erickDelgadoNombreCopia(marca, ERICK_DELGADO_MOTIVO_ANTES_DE_QUITAR_CLAVE));
        while (Files.exists(copia)) {
            marca = marca.plusSeconds(1);
            copia = carpetaCopias.resolve(RespaldoDatos.erickDelgadoNombreCopia(marca, ERICK_DELGADO_MOTIVO_ANTES_DE_QUITAR_CLAVE));
        }
        Files.copy(base, copia);
        if (Files.size(copia) != Files.size(base) || erickDelgadoInspeccionar(copia) != Estado.CIFRADA) {
            Files.deleteIfExists(copia);
            throw new IOException("No se pudo guardar la copia previa; la base no se modificó.");
        }

        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(clave));
             Statement s = c.createStatement()) {
            s.execute("PRAGMA rekey=''");
        } catch (SQLException e) {
            Files.copy(copia, base, StandardCopyOption.REPLACE_EXISTING);
            throw new SQLException("No se pudo quitar la contraseña; se dejó la base como estaba.", e);
        }
        String problema = null;
        try {
            if (erickDelgadoInspeccionar(base) != Estado.SIN_CIFRAR) problema = "la base sigue protegida";
            else if (!"ok".equalsIgnoreCase(erickDelgadoIntegridad(base))) problema = "la revisión de integridad falló";
            else if (!antes.equals(erickDelgadoFilasPorTabla(base, null))) problema = "la cantidad de registros cambió";
        } catch (SQLException e) {
            problema = e.getMessage();
        }
        if (problema != null) {
            Files.copy(copia, base, StandardCopyOption.REPLACE_EXISTING);
            throw new SQLException("No se pudo verificar la conversión (" + problema + "); se dejó la base como estaba.");
        }
        return copia;
    }

    static Map<String, Long> erickDelgadoFilasPorTabla(Path base, char[] clave) throws SQLException {
        var filas = new java.util.TreeMap<String, Long>();
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(clave));
             Statement s = c.createStatement()) {
            var tablas = new java.util.ArrayList<String>();
            try (var rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) tablas.add(rs.getString(1));
            }
            for (String t : tablas) {
                try (var rs = s.executeQuery("SELECT count(*) FROM \"" + t.replace("\"", "\"\"") + "\"")) {
                    rs.next();
                    filas.put(t, rs.getLong(1));
                }
            }
        }
        return filas;
    }

    private static String erickDelgadoIntegridad(Path base) throws SQLException {
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(null));
             Statement s = c.createStatement(); var rs = s.executeQuery("PRAGMA integrity_check")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static String erickDelgadoEscapar(char[] clave) {
        return new String(clave).replace("'", "''");
    }
}
