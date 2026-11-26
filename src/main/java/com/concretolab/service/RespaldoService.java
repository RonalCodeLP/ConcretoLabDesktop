package com.concretolab.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RespaldoService {
    public record Copia(Path archivo, String nombre, LocalDateTime fecha, long bytes, String tipo) {
    }

    private final DataSource erickDelgadoDataSource;
    private final BitacoraService bitacora;

    public Copia erickDelgadoCrearCopia(String motivo) {
        String tipo = RespaldoDatos.erickDelgadoNormalizarMotivo(motivo);
        Path carpeta = erickDelgadoCarpetaCopias();
        Path destino;
        try {
            Files.createDirectories(carpeta);
            LocalDateTime marca = LocalDateTime.now().withNano(0);
            destino = carpeta.resolve(RespaldoDatos.erickDelgadoNombreCopia(marca, tipo));
            while (Files.exists(destino)) {
                marca = marca.plusSeconds(1);
                destino = carpeta.resolve(RespaldoDatos.erickDelgadoNombreCopia(marca, tipo));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo preparar la carpeta de copias", e);
        }

        String ruta = destino.toAbsolutePath().toString().replace("'", "''");
        try (Connection conexion = erickDelgadoDataSource.getConnection(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("VACUUM INTO '" + ruta + "'");
        } catch (SQLException e) {
            try {
                Files.deleteIfExists(destino);
            } catch (IOException | RuntimeException ignorada) {
            }
            throw new IllegalStateException("No se pudo crear la copia de seguridad: " + e.getMessage(), e);
        }

        Path creada = destino;
        Copia copia = erickDelgadoACopia(creada).orElseThrow(
                () -> new IllegalStateException("No se pudo leer la copia creada: " + creada.getFileName()));
        bitacora.erickDelgadoRegistrar("COPIA_CREADA", copia.nombre() + " (" + copia.bytes() + " bytes)");
        return copia;
    }

    public List<Copia> erickDelgadoListar() {
        Path carpeta = erickDelgadoCarpetaCopias();
        if (!Files.isDirectory(carpeta)) return List.of();
        List<Copia> copias = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            archivos.filter(Files::isRegularFile).forEach(p -> erickDelgadoACopia(p).ifPresent(copias::add));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la carpeta de copias", e);
        }
        copias.sort(Comparator.comparing(Copia::fecha).thenComparing(Copia::nombre).reversed());
        return copias;
    }

    public void erickDelgadoProgramarRestauracion(Path copia) {
        RespaldoDatos.erickDelgadoProgramarRestauracion(copia);
        bitacora.erickDelgadoRegistrar("RESTAURACION_PROGRAMADA", copia.getFileName().toString());
    }

    public Optional<Path> erickDelgadoRestauracionPendiente() {
        return RespaldoDatos.erickDelgadoRestauracionPendiente();
    }

    public void erickDelgadoEliminarCopia(Path copia) {
        if (copia == null) throw new IllegalArgumentException("Debe indicar la copia a eliminar");
        Path carpeta = erickDelgadoCarpetaCopias().toAbsolutePath().normalize();
        Path objetivo = copia.toAbsolutePath().normalize();
        if (!carpeta.equals(objetivo.getParent())) {
            throw new IllegalArgumentException("La copia está fuera de la carpeta de copias: " + copia);
        }
        try {
            Files.deleteIfExists(objetivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo eliminar la copia " + copia.getFileName(), e);
        }
    }

    public Path erickDelgadoCarpetaCopias() {
        return RespaldoDatos.erickDelgadoCarpetaCopias();
    }

    public Path erickDelgadoCarpetaRegistros() {
        return RespaldoDatos.erickDelgadoCarpetaRegistros();
    }

    public Path erickDelgadoArchivoBaseDatos() {
        return RespaldoDatos.erickDelgadoArchivoBaseDatos();
    }

    public long erickDelgadoTamanoBaseDatos() {
        try {
            Path base = erickDelgadoArchivoBaseDatos();
            return Files.isRegularFile(base) ? Files.size(base) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Optional<Copia> erickDelgadoACopia(Path archivo) {
        var m = RespaldoDatos.ERICK_DELGADO_PATRON_NOMBRE.matcher(archivo.getFileName().toString());
        if (!m.matches()) return Optional.empty();
        try {
            LocalDateTime fecha = LocalDateTime.parse(m.group(1), RespaldoDatos.ERICK_DELGADO_FORMATO_MARCA);
            return Optional.of(new Copia(archivo, archivo.getFileName().toString(), fecha, Files.size(archivo), m.group(2)));
        } catch (DateTimeParseException | IOException e) {
            return Optional.empty();
        }
    }
}
