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

/**
 * Cifrado en reposo de la base SQLite (AES-256, modo SQLCipher 4 del driver SQLite3MultipleCiphers). La clave se
 * deriva de la contraseña con PBKDF2 dentro del propio driver. Sin la contraseña el archivo es indescifrable: no hay
 * ninguna puerta trasera ni se guarda la clave en disco.
 */
public final class BaseCifrada {

    private BaseCifrada() { }

    public enum Estado { NO_EXISTE, SIN_CIFRAR, CIFRADA }

    private static final byte[] ERICK_DELGADO_CABECERA = "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1);
    public static final int ERICK_DELGADO_LONGITUD_MINIMA = 8;

    /** Propiedades de conexión del driver para abrir la base con esa contraseña (sin clave: base sin cifrar). */
    public static Properties erickDelgadoPropiedades(char[] clave) {
        Properties p = new Properties();
        p.setProperty("cipher", "sqlcipher");
        p.setProperty("legacy", "4");
        if (clave != null && clave.length > 0) p.setProperty("key", new String(clave));
        return p;
    }

    /** Las mismas propiedades como mapa, para pasarlas a Spring/Hikari como {@code data-source-properties}. */
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

    /** Comprueba que la contraseña abre la base: lee el catálogo, que falla con «file is not a database» si es incorrecta. */
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

    /**
     * Cifra una base sin cifrar en el sitio. Antes guarda una copia sin cifrar junto al archivo; si la verificación con
     * la contraseña falla, deja la base como estaba. Devuelve la ruta de esa copia.
     */
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

    /** Cambia la contraseña de una base cifrada. Falla sin tocar nada si la contraseña actual es incorrecta. */
    public static void erickDelgadoCambiarClave(Path base, char[] actual, char[] nueva) throws SQLException {
        erickDelgadoValidarClaveNueva(nueva);
        if (!erickDelgadoClaveCorrecta(base, actual)) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        try (Connection c = DriverManager.getConnection(erickDelgadoUrl(base), erickDelgadoPropiedades(actual));
             Statement s = c.createStatement()) {
            s.execute("PRAGMA rekey='" + erickDelgadoEscapar(nueva) + "'");
        }
        if (!erickDelgadoClaveCorrecta(base, nueva)) throw new SQLException("No se pudo verificar la nueva contraseña.");
    }

    private static String erickDelgadoEscapar(char[] clave) {
        return new String(clave).replace("'", "''");
    }
}
