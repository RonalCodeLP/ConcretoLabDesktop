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

/**
 * Utilidad SIN Spring para las copias de seguridad del archivo de la base de datos.
 * Se usa antes de que arranque Spring (en {@code JavaFxApplication.init()} y {@code stop()}),
 * por eso solo depende del JDK y registra con {@link System.Logger}.
 *
 * <p>Regla de oro: ninguna operación de arranque lanza excepciones; una copia que falla no impide abrir la app.
 */
public final class RespaldoDatos {

    /** Propiedad de sistema para cambiar la carpeta de datos (pruebas). */
    public static final String PROPIEDAD_DATOS = "concretolab.datos";

    /** Motivos automáticos: se conservan solo las últimas {@link #MAXIMO_AUTOMATICAS} de cada uno. */
    public static final int MAXIMO_AUTOMATICAS = 10;
    /** Máximo de copias «previa-restauracion» conservadas. */
    public static final int MAXIMO_PREVIAS = 20;

    public static final String MOTIVO_INICIO = "inicio";
    public static final String MOTIVO_CIERRE = "cierre";
    public static final String MOTIVO_MANUAL = "manual";
    public static final String MOTIVO_PREVIA = "previa-restauracion";

    /** Formato de la marca de tiempo del nombre de la copia. */
    public static final DateTimeFormatter FORMATO_MARCA = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Patrón del nombre: grupo 1 = marca de tiempo, grupo 2 = motivo. */
    public static final Pattern PATRON_NOMBRE =
            Pattern.compile("concretolab-(\\d{8}-\\d{6})-([a-z]+(?:-[a-z]+)*)\\.db");

    private static final String NOMBRE_BASE = "concretolab.db";
    private static final String NOMBRE_MARCADOR = "restaurar.pendiente";
    private static final byte[] CABECERA_SQLITE = "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1);

    private static final System.Logger LOG = System.getLogger(RespaldoDatos.class.getName());

    private RespaldoDatos() {
    }

    // ---------------------------------------------------------------- Rutas

    /** Carpeta de datos: {@code ~/.concretolab}, o la indicada en la propiedad {@code concretolab.datos}. */
    public static Path carpetaDatos() {
        String sobrescrita = System.getProperty(PROPIEDAD_DATOS);
        if (sobrescrita != null && !sobrescrita.isBlank()) {
            return Path.of(sobrescrita);
        }
        return Path.of(System.getProperty("user.home"), ".concretolab");
    }

    public static Path carpetaCopias() {
        return carpetaDatos().resolve("backups");
    }

    public static Path carpetaRegistros() {
        return carpetaDatos().resolve("logs");
    }

    public static Path archivoBaseDatos() {
        return carpetaDatos().resolve(NOMBRE_BASE);
    }

    private static Path archivoMarcador() {
        return carpetaDatos().resolve(NOMBRE_MARCADOR);
    }

    // ---------------------------------------------------------------- Nombres

    /** Normaliza el motivo: minúsculas, sin acentos ni espacios (solo a-z y guiones). Vacío = «manual». */
    public static String normalizarMotivo(String motivo) {
        if (motivo == null) return MOTIVO_MANUAL;
        String texto = Normalizer.normalize(motivo.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return texto.isEmpty() ? MOTIVO_MANUAL : texto;
    }

    /** Nombre de archivo de una copia: {@code concretolab-AAAAMMdd-HHmmss-<motivo>.db}. */
    public static String nombreCopia(LocalDateTime momento, String motivo) {
        return "concretolab-" + FORMATO_MARCA.format(momento) + "-" + normalizarMotivo(motivo) + ".db";
    }

    // ---------------------------------------------------------------- Copias

    /** Copia el archivo de la base con el momento actual. Devuelve vacío si no hay nada que copiar o falla. */
    public static Optional<Path> respaldarArchivo(String motivo) {
        return respaldarArchivo(motivo, LocalDateTime.now());
    }

    /**
     * Copia el archivo .db (si existe y no está vacío) a {@code backups/} con la marca indicada y aplica la rotación.
     * Si ya existe una copia con esa marca, avanza un segundo hasta encontrar un nombre libre.
     * Nunca lanza excepciones: ante un error lo registra y devuelve vacío.
     */
    public static Optional<Path> respaldarArchivo(String motivo, LocalDateTime momento) {
        try {
            Path base = archivoBaseDatos();
            if (!Files.isRegularFile(base) || Files.size(base) == 0) {
                return Optional.empty();
            }
            String motivoNormal = normalizarMotivo(motivo);
            Path carpeta = carpetaCopias();
            Files.createDirectories(carpeta);

            Path destino = carpeta.resolve(nombreCopia(momento, motivoNormal));
            LocalDateTime marca = momento;
            while (Files.exists(destino)) {
                marca = marca.plusSeconds(1);
                destino = carpeta.resolve(nombreCopia(marca, motivoNormal));
            }
            // Se copia a un temporal y luego se renombra: nunca queda una copia a medias con nombre válido.
            Path temporal = carpeta.resolve(destino.getFileName() + ".tmp");
            try {
                Files.copy(base, temporal, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporal);
            }
            rotar(motivoNormal);
            return Optional.of(destino);
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo crear la copia de seguridad «" + motivo + "»: " + e);
            return Optional.empty();
        }
    }

    /** Borra las copias más antiguas del motivo según el límite (las manuales nunca se borran). */
    private static void rotar(String motivo) {
        int limite;
        if (MOTIVO_INICIO.equals(motivo) || MOTIVO_CIERRE.equals(motivo)) {
            limite = MAXIMO_AUTOMATICAS;
        } else if (MOTIVO_PREVIA.equals(motivo)) {
            limite = MAXIMO_PREVIAS;
        } else {
            return;
        }
        List<Path> copias = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(carpetaCopias())) {
            archivos.filter(Files::isRegularFile).forEach(p -> {
                var m = PATRON_NOMBRE.matcher(p.getFileName().toString());
                if (m.matches() && m.group(2).equals(motivo)) copias.add(p);
            });
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo aplicar la rotación de copias: " + e);
            return;
        }
        // La marca de tiempo está en el nombre: el orden alfabético inverso es del más reciente al más antiguo.
        copias.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        for (int i = limite; i < copias.size(); i++) {
            try {
                Files.deleteIfExists(copias.get(i));
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo borrar la copia antigua " + copias.get(i) + ": " + e);
            }
        }
    }

    // ---------------------------------------------------------------- Restauración

    /** Indica si el archivo existe, no está vacío y empieza con la cabecera «SQLite format 3\0». */
    public static boolean esSqliteValido(Path archivo) {
        if (archivo == null) return false;
        try {
            if (!Files.isRegularFile(archivo) || Files.size(archivo) < CABECERA_SQLITE.length) return false;
            try (InputStream in = Files.newInputStream(archivo)) {
                byte[] leido = in.readNBytes(CABECERA_SQLITE.length);
                if (Arrays.equals(leido, CABECERA_SQLITE)) return true;
            }
            // Base cifrada: no tiene cabecera legible; se admite un archivo con páginas completas (múltiplo de 512, mínimo 4 KB).
            long tamano = Files.size(archivo);
            return tamano >= 4096 && tamano % 512 == 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Deja programada una restauración para el próximo arranque (escribe el marcador con la ruta de la copia).
     *
     * @throws IllegalArgumentException si la copia no existe o no es un archivo SQLite válido
     * @throws UncheckedIOException     si no se puede escribir el marcador
     */
    public static void programarRestauracion(Path copia) {
        if (!esSqliteValido(copia)) {
            throw new IllegalArgumentException("La copia no existe o no es una base de datos SQLite válida: " + copia);
        }
        try {
            Files.createDirectories(carpetaDatos());
            Files.writeString(archivoMarcador(), copia.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo programar la restauración", e);
        }
    }

    /** Ruta de la copia pendiente de restaurar, si hay marcador legible. */
    public static Optional<Path> restauracionPendiente() {
        try {
            Path marcador = archivoMarcador();
            if (!Files.isRegularFile(marcador)) return Optional.empty();
            String texto = Files.readString(marcador, StandardCharsets.UTF_8).trim();
            return texto.isEmpty() ? Optional.empty() : Optional.of(Path.of(texto));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Aplica la restauración pendiente (llamar ANTES de abrir la base). Valida la copia, respalda la base actual
     * como «previa-restauracion», la reemplaza y borra el marcador. Si algo falla no toca la base actual,
     * borra el marcador y devuelve false. Nunca lanza excepciones.
     *
     * @return true si la base fue reemplazada
     */
    public static boolean aplicarRestauracionPendiente() {
        Path marcador = archivoMarcador();
        try {
            if (!Files.exists(marcador)) return false;
            try {
                return restaurar(marcador);
            } finally {
                Files.deleteIfExists(marcador);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Falló la restauración pendiente; se conserva la base actual: " + e);
            try {
                Files.deleteIfExists(marcador);
            } catch (IOException | RuntimeException ignorada) {
                // sin más opciones: se reintentará borrar en el próximo arranque
            }
            return false;
        }
    }

    private static boolean restaurar(Path marcador) {
        Path temporal = null;
        try {
            String texto = Files.readString(marcador, StandardCharsets.UTF_8).trim();
            if (texto.isEmpty()) {
                LOG.log(System.Logger.Level.WARNING, "El marcador de restauración está vacío; se ignora.");
                return false;
            }
            Path copia = Path.of(texto);
            if (!Files.isRegularFile(copia)) {
                LOG.log(System.Logger.Level.WARNING, "La copia a restaurar no existe: " + copia);
                return false;
            }
            if (!esSqliteValido(copia)) {
                LOG.log(System.Logger.Level.WARNING, "La copia a restaurar no es una base SQLite válida: " + copia);
                return false;
            }

            Path base = archivoBaseDatos();
            boolean baseConDatos = Files.isRegularFile(base) && Files.size(base) > 0;
            if (baseConDatos && respaldarArchivo(MOTIVO_PREVIA).isEmpty()) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo respaldar la base actual; se cancela la restauración.");
                return false;
            }

            // Se copia primero a un temporal junto a la base y luego se mueve: el reemplazo es casi atómico.
            Files.createDirectories(base.getParent());
            temporal = base.resolveSibling(NOMBRE_BASE + ".restaurando");
            Files.copy(copia, temporal, StandardCopyOption.REPLACE_EXISTING);
            for (String sufijo : new String[]{"-wal", "-shm", "-journal"}) {
                Files.deleteIfExists(base.resolveSibling(NOMBRE_BASE + sufijo));
            }
            Files.move(temporal, base, StandardCopyOption.REPLACE_EXISTING);
            temporal = null;
            LOG.log(System.Logger.Level.INFO, "Base de datos restaurada desde " + copia);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo restaurar la copia: " + e);
            return false;
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException | RuntimeException ignorada) {
                    // limpieza best-effort
                }
            }
        }
    }
}
