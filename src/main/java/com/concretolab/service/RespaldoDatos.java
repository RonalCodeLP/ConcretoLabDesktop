package com.concretolab.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RespaldoDatos {
    public static final String ERICK_DELGADO_PROPIEDAD_DATOS = "concretolab.datos";

    public static final int ERICK_DELGADO_MAXIMO_AUTOMATICAS = 10;
    public static final int ERICK_DELGADO_MAXIMO_PREVIAS = 20;

    public static final String ERICK_DELGADO_MOTIVO_INICIO = "inicio";
    public static final String ERICK_DELGADO_MOTIVO_CIERRE = "cierre";
    public static final String ERICK_DELGADO_MOTIVO_MANUAL = "manual";
    public static final String ERICK_DELGADO_MOTIVO_PREVIA = "previa-restauracion";

    public static final DateTimeFormatter ERICK_DELGADO_FORMATO_MARCA = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static final Pattern ERICK_DELGADO_PATRON_NOMBRE =
            Pattern.compile("concretolab-(\\d{8}-\\d{6})-([a-z]+(?:-[a-z]+)*)\\.db");

    private static final String ERICK_DELGADO_NOMBRE_BASE = "concretolab.db";
    private static final String ERICK_DELGADO_NOMBRE_MARCADOR = "restaurar.pendiente";
    private static final byte[] ERICK_DELGADO_CABECERA_SQLITE = "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1);

    private static final System.Logger ERICK_DELGADO_LOG = System.getLogger(RespaldoDatos.class.getName());

    private RespaldoDatos() {
    }

    public static Path erickDelgadoCarpetaDatos() {
        String sobrescrita = System.getProperty(ERICK_DELGADO_PROPIEDAD_DATOS);
        if (sobrescrita != null && !sobrescrita.isBlank()) {
            return Path.of(sobrescrita);
        }
        return Path.of(System.getProperty("user.home"), ".concretolab");
    }

    public static Path erickDelgadoCarpetaCopias() {
        return erickDelgadoCarpetaDatos().resolve("backups");
    }

    public static Path erickDelgadoCarpetaRegistros() {
        return erickDelgadoCarpetaDatos().resolve("logs");
    }

    public static Path erickDelgadoArchivoBaseDatos() {
        return erickDelgadoCarpetaDatos().resolve(ERICK_DELGADO_NOMBRE_BASE);
    }

    private static Path erickDelgadoArchivoMarcador() {
        return erickDelgadoCarpetaDatos().resolve(ERICK_DELGADO_NOMBRE_MARCADOR);
    }

    public static String erickDelgadoNormalizarMotivo(String motivo) {
        if (motivo == null) return ERICK_DELGADO_MOTIVO_MANUAL;
        String texto = Normalizer.normalize(motivo.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return texto.isEmpty() ? ERICK_DELGADO_MOTIVO_MANUAL : texto;
    }

    public static String erickDelgadoNombreCopia(LocalDateTime momento, String motivo) {
        return "concretolab-" + ERICK_DELGADO_FORMATO_MARCA.format(momento) + "-" + erickDelgadoNormalizarMotivo(motivo) + ".db";
    }

    public static Optional<Path> erickDelgadoRespaldarArchivo(String motivo) {
        return erickDelgadoRespaldarArchivo(motivo, LocalDateTime.now());
    }

    public static Optional<Path> erickDelgadoRespaldarArchivo(String motivo, LocalDateTime momento) {
        try {
            Path base = erickDelgadoArchivoBaseDatos();
            if (!Files.isRegularFile(base) || Files.size(base) == 0) {
                return Optional.empty();
            }
            String motivoNormal = erickDelgadoNormalizarMotivo(motivo);
            Path carpeta = erickDelgadoCarpetaCopias();
            Files.createDirectories(carpeta);

            Path destino = carpeta.resolve(erickDelgadoNombreCopia(momento, motivoNormal));
            LocalDateTime marca = momento;
            while (Files.exists(destino)) {
                marca = marca.plusSeconds(1);
                destino = carpeta.resolve(erickDelgadoNombreCopia(marca, motivoNormal));
            }
            Path temporal = carpeta.resolve(destino.getFileName() + ".tmp");
            try {
                Files.copy(base, temporal, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporal);
            }
            erickDelgadoRotar(motivoNormal);
            return Optional.of(destino);
        } catch (IOException | RuntimeException e) {
            ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "No se pudo crear la copia de seguridad «" + motivo + "»: " + e);
            return Optional.empty();
        }
    }

    private static void erickDelgadoRotar(String motivo) {
        int limite;
        if (ERICK_DELGADO_MOTIVO_INICIO.equals(motivo) || ERICK_DELGADO_MOTIVO_CIERRE.equals(motivo)) {
            limite = ERICK_DELGADO_MAXIMO_AUTOMATICAS;
        } else if (ERICK_DELGADO_MOTIVO_PREVIA.equals(motivo)) {
            limite = ERICK_DELGADO_MAXIMO_PREVIAS;
        } else {
            return;
        }
        List<Path> copias = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(erickDelgadoCarpetaCopias())) {
            archivos.filter(Files::isRegularFile).forEach(p -> {
                var m = ERICK_DELGADO_PATRON_NOMBRE.matcher(p.getFileName().toString());
                if (m.matches() && m.group(2).equals(motivo)) copias.add(p);
            });
        } catch (IOException e) {
            ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "No se pudo aplicar la rotación de copias: " + e);
            return;
        }
        copias.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        for (int i = limite; i < copias.size(); i++) {
            try {
                Files.deleteIfExists(copias.get(i));
            } catch (IOException e) {
                ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "No se pudo borrar la copia antigua " + copias.get(i) + ": " + e);
            }
        }
    }

    public static boolean erickDelgadoEsSqliteValido(Path archivo) {
        if (archivo == null) return false;
        try {
            if (!Files.isRegularFile(archivo) || Files.size(archivo) < ERICK_DELGADO_CABECERA_SQLITE.length) return false;
            try (InputStream in = Files.newInputStream(archivo)) {
                byte[] leido = in.readNBytes(ERICK_DELGADO_CABECERA_SQLITE.length);
                if (Arrays.equals(leido, ERICK_DELGADO_CABECERA_SQLITE)) return true;
            }
            long tamano = Files.size(archivo);
            return tamano >= 4096 && tamano % 512 == 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static void erickDelgadoProgramarRestauracion(Path copia) {
        if (!erickDelgadoEsSqliteValido(copia)) {
            throw new IllegalArgumentException("La copia no existe o no es una base de datos SQLite válida: " + copia);
        }
        try {
            Files.createDirectories(erickDelgadoCarpetaDatos());
            Files.writeString(erickDelgadoArchivoMarcador(), copia.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo programar la restauración", e);
        }
    }

    public static Optional<Path> erickDelgadoRestauracionPendiente() {
        try {
            Path marcador = erickDelgadoArchivoMarcador();
            if (!Files.isRegularFile(marcador)) return Optional.empty();
            String texto = Files.readString(marcador, StandardCharsets.UTF_8).trim();
            return texto.isEmpty() ? Optional.empty() : Optional.of(Path.of(texto));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean erickDelgadoAplicarRestauracionPendiente() {
        Path marcador = erickDelgadoArchivoMarcador();
        try {
            if (!Files.exists(marcador)) return false;
            try {
                return erickDelgadoRestaurar(marcador);
            } finally {
                Files.deleteIfExists(marcador);
            }
        } catch (IOException | RuntimeException e) {
            ERICK_DELGADO_LOG.log(System.Logger.Level.ERROR, "Falló la restauración pendiente; se conserva la base actual: " + e);
            try {
                Files.deleteIfExists(marcador);
            } catch (IOException | RuntimeException ignorada) {
            }
            return false;
        }
    }

    private static boolean erickDelgadoRestaurar(Path marcador) {
        Path temporal = null;
        try {
            String texto = Files.readString(marcador, StandardCharsets.UTF_8).trim();
            if (texto.isEmpty()) {
                ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "El marcador de restauración está vacío; se ignora.");
                return false;
            }
            Path copia = Path.of(texto);
            if (!Files.isRegularFile(copia)) {
                ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "La copia a restaurar no existe: " + copia);
                return false;
            }
            if (!erickDelgadoEsSqliteValido(copia)) {
                ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "La copia a restaurar no es una base SQLite válida: " + copia);
                return false;
            }

            Path base = erickDelgadoArchivoBaseDatos();
            boolean baseConDatos = Files.isRegularFile(base) && Files.size(base) > 0;
            if (baseConDatos && erickDelgadoRespaldarArchivo(ERICK_DELGADO_MOTIVO_PREVIA).isEmpty()) {
                ERICK_DELGADO_LOG.log(System.Logger.Level.WARNING, "No se pudo respaldar la base actual; se cancela la restauración.");
                return false;
            }

            Files.createDirectories(base.getParent());
            temporal = base.resolveSibling(ERICK_DELGADO_NOMBRE_BASE + ".restaurando");
            Files.copy(copia, temporal, StandardCopyOption.REPLACE_EXISTING);
            for (String sufijo : new String[]{"-wal", "-shm", "-journal"}) {
                Files.deleteIfExists(base.resolveSibling(ERICK_DELGADO_NOMBRE_BASE + sufijo));
            }
            Files.move(temporal, base, StandardCopyOption.REPLACE_EXISTING);
            temporal = null;
            ERICK_DELGADO_LOG.log(System.Logger.Level.INFO, "Base de datos restaurada desde " + copia);
            return true;
        } catch (IOException | RuntimeException e) {
            ERICK_DELGADO_LOG.log(System.Logger.Level.ERROR, "No se pudo restaurar la copia: " + e);
            return false;
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException | RuntimeException ignorada) {
                }
            }
        }
    }
}
