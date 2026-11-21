package com.concretolab.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Contraseña de la base cifrada en uso. El cambio se hace por la misma conexión que la aplicación tiene abierta (con
 * otra conexión, la clave en memoria quedaría desfasada): por eso la aplicación debe cerrarse tras cambiarla.
 */
@Service
public class ClaveBaseService {

    private final DataSource dataSource;
    private final BitacoraService bitacora;
    private final Path base;

    public ClaveBaseService(DataSource dataSource, BitacoraService bitacora, @Value("${spring.datasource.url}") String url) {
        this.dataSource = dataSource;
        this.bitacora = bitacora;
        this.base = archivoDe(url);
    }

    /** Archivo de la base según la URL JDBC (jdbc:sqlite:ruta o jdbc:sqlite:file:ruta?opciones). */
    static Path archivoDe(String url) {
        String ruta = url.replaceFirst("^jdbc:sqlite:", "").replaceFirst("^file:", "");
        int q = ruta.indexOf('?');
        return Path.of(q >= 0 ? ruta.substring(0, q) : ruta);
    }

    /** Indica si el archivo de la base en uso está cifrado. */
    public boolean estaCifrada() {
        return BaseCifrada.inspeccionar(base) == BaseCifrada.Estado.CIFRADA;
    }

    /**
     * Cambia la contraseña.
     *
     * @throws IllegalArgumentException si la actual es incorrecta, la nueva es corta o la base no está cifrada
     */
    public void cambiar(char[] actual, char[] nueva) throws SQLException {
        if (!estaCifrada()) throw new IllegalArgumentException("La base de datos no está cifrada.");
        BaseCifrada.validarClaveNueva(nueva);
        if (!BaseCifrada.claveCorrecta(base, actual)) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA rekey='" + new String(nueva).replace("'", "''") + "'");
        }
        if (!BaseCifrada.claveCorrecta(base, nueva)) throw new SQLException("No se pudo verificar la nueva contraseña.");
        bitacora.registrar("Contraseña de la base", "Se cambió la contraseña de cifrado de la base de datos.");
    }
}
