package com.concretolab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.concretolab.service.BitacoraService;
import com.concretolab.service.RespaldoDatos;
import com.concretolab.service.RespaldoService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class RespaldoTest {
    @TempDir
    Path temporal;

    private String propiedadAnterior;

    @BeforeEach
    void aislarCarpetaDatos() {
        propiedadAnterior = System.getProperty(RespaldoDatos.ERICK_DELGADO_PROPIEDAD_DATOS);
        System.setProperty(RespaldoDatos.ERICK_DELGADO_PROPIEDAD_DATOS, temporal.toString());
    }

    @AfterEach
    void restaurarPropiedad() {
        if (propiedadAnterior == null) System.clearProperty(RespaldoDatos.ERICK_DELGADO_PROPIEDAD_DATOS);
        else System.setProperty(RespaldoDatos.ERICK_DELGADO_PROPIEDAD_DATOS, propiedadAnterior);
    }

    private static void crearBase(Path archivo, int filas) throws SQLException, IOException {
        Files.createDirectories(archivo.getParent());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + archivo.toAbsolutePath());
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS prueba (id INTEGER PRIMARY KEY, texto TEXT)");
            for (int i = 0; i < filas; i++) s.execute("INSERT INTO prueba (texto) VALUES ('fila " + i + "')");
        }
    }

    private static int contarFilas(Path archivo) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + archivo.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM prueba")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static long contarArchivos(Path carpeta, String contiene) throws IOException {
        if (!Files.isDirectory(carpeta)) return 0;
        try (Stream<Path> s = Files.list(carpeta)) {
            return s.filter(p -> p.getFileName().toString().contains(contiene)).count();
        }
    }

    @Test
    void rotacionConservaLasUltimasDiezPorMotivo() throws Exception {
        crearBase(RespaldoDatos.erickDelgadoArchivoBaseDatos(), 1);
        for (int i = 0; i < 12; i++) {
            assertTrue(RespaldoDatos.erickDelgadoRespaldarArchivo("inicio", LocalDateTime.of(2026, 1, 1, 10, 0, i)).isPresent());
        }
        RespaldoDatos.erickDelgadoRespaldarArchivo("cierre", LocalDateTime.of(2026, 1, 1, 10, 0, 0));

        Path carpeta = RespaldoDatos.erickDelgadoCarpetaCopias();
        assertEquals(10, contarArchivos(carpeta, "-inicio.db"));
        assertEquals(1, contarArchivos(carpeta, "-cierre.db"));
        assertFalse(Files.exists(carpeta.resolve("concretolab-20260101-100000-inicio.db")));
        assertFalse(Files.exists(carpeta.resolve("concretolab-20260101-100001-inicio.db")));
        assertTrue(Files.exists(carpeta.resolve("concretolab-20260101-100011-inicio.db")));
    }

    @Test
    void copiasManualesNuncaSeBorran() throws Exception {
        crearBase(RespaldoDatos.erickDelgadoArchivoBaseDatos(), 1);
        for (int i = 0; i < 15; i++) {
            RespaldoDatos.erickDelgadoRespaldarArchivo("manual", LocalDateTime.of(2026, 1, 1, 10, 0, i));
        }
        assertEquals(15, contarArchivos(RespaldoDatos.erickDelgadoCarpetaCopias(), "-manual.db"));
    }

    @Test
    void respaldarSinBaseNoHaceNada() {
        assertTrue(RespaldoDatos.erickDelgadoRespaldarArchivo("inicio").isEmpty());
    }

    @Test
    void restauracionPendienteValidaReemplazaYCreaPrevia() throws Exception {
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        crearBase(base, 1);
        Path copia = temporal.resolve("externa").resolve("copia.db");
        crearBase(copia, 3);

        RespaldoDatos.erickDelgadoProgramarRestauracion(copia);
        assertTrue(RespaldoDatos.erickDelgadoRestauracionPendiente().isPresent());

        assertTrue(RespaldoDatos.erickDelgadoAplicarRestauracionPendiente());
        assertEquals(3, contarFilas(base));
        assertEquals(1, contarArchivos(RespaldoDatos.erickDelgadoCarpetaCopias(), "-previa-restauracion.db"));
        assertTrue(RespaldoDatos.erickDelgadoRestauracionPendiente().isEmpty());
        assertFalse(Files.exists(temporal.resolve("restaurar.pendiente")));

        try (Stream<Path> s = Files.list(RespaldoDatos.erickDelgadoCarpetaCopias())) {
            Path previa = s.filter(p -> p.getFileName().toString().endsWith("-previa-restauracion.db"))
                    .findFirst().orElseThrow();
            assertEquals(1, contarFilas(previa));
        }
    }

    @Test
    void copiaCorruptaNoReemplazaLaBaseNiLanzaExcepcion() throws Exception {
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        crearBase(base, 2);
        Path corrupta = temporal.resolve("corrupta.db");
        Files.writeString(corrupta, "esto no es sqlite", StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> RespaldoDatos.erickDelgadoProgramarRestauracion(corrupta));

        Files.writeString(temporal.resolve("restaurar.pendiente"), corrupta.toString(), StandardCharsets.UTF_8);
        assertFalse(RespaldoDatos.erickDelgadoAplicarRestauracionPendiente());
        assertEquals(2, contarFilas(base));
        assertFalse(Files.exists(temporal.resolve("restaurar.pendiente")));
        assertEquals(0, contarArchivos(RespaldoDatos.erickDelgadoCarpetaCopias(), "-previa-restauracion.db"));
    }

    @Test
    void copiaVaciaSeRechaza() throws Exception {
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        crearBase(base, 2);
        Path vacia = temporal.resolve("vacia.db");
        Files.createFile(vacia);
        Files.writeString(temporal.resolve("restaurar.pendiente"), vacia.toString(), StandardCharsets.UTF_8);

        assertFalse(RespaldoDatos.erickDelgadoAplicarRestauracionPendiente());
        assertEquals(2, contarFilas(base));
    }

    @Test
    void marcadorConRutaInexistenteSeDescarta() throws Exception {
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        crearBase(base, 2);
        Files.writeString(temporal.resolve("restaurar.pendiente"),
                temporal.resolve("no-existe.db").toString(), StandardCharsets.UTF_8);

        assertFalse(RespaldoDatos.erickDelgadoAplicarRestauracionPendiente());
        assertEquals(2, contarFilas(base));
        assertFalse(Files.exists(temporal.resolve("restaurar.pendiente")));
    }

    @Test
    void sinMarcadorNoHaceNada() {
        assertFalse(RespaldoDatos.erickDelgadoAplicarRestauracionPendiente());
    }

    @Test
    void eliminarCopiaFueraDeLaCarpetaFalla() throws Exception {
        RespaldoService servicio = new RespaldoService(new SQLiteDataSource(), mock(BitacoraService.class));
        Path ajeno = temporal.resolve("ajeno.db");
        Files.writeString(ajeno, "x");
        assertThrows(IllegalArgumentException.class, () -> servicio.erickDelgadoEliminarCopia(ajeno));
        Files.createDirectories(servicio.erickDelgadoCarpetaCopias());
        Path escape = servicio.erickDelgadoCarpetaCopias().resolve("..").resolve("ajeno.db");
        assertThrows(IllegalArgumentException.class, () -> servicio.erickDelgadoEliminarCopia(escape));
        assertTrue(Files.exists(ajeno));
    }

    @Test
    void crearCopiaConVacuumIntoContieneLasFilas() throws Exception {
        Path datos = temporal.resolve("dato's");
        System.setProperty(RespaldoDatos.ERICK_DELGADO_PROPIEDAD_DATOS, datos.toString());
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        crearBase(base, 5);

        SQLiteDataSource origen = new SQLiteDataSource();
        origen.setUrl("jdbc:sqlite:" + base.toAbsolutePath());
        BitacoraService bitacora = mock(BitacoraService.class);
        RespaldoService servicio = new RespaldoService(origen, bitacora);

        RespaldoService.Copia copia = servicio.erickDelgadoCrearCopia("manual");

        assertTrue(Files.isRegularFile(copia.archivo()));
        assertEquals("manual", copia.tipo());
        assertTrue(copia.bytes() > 0);
        assertTrue(RespaldoDatos.erickDelgadoEsSqliteValido(copia.archivo()));
        assertEquals(5, contarFilas(copia.archivo()));
        verify(bitacora).erickDelgadoRegistrar(eq("COPIA_CREADA"), anyString());

        RespaldoService.Copia otra = servicio.erickDelgadoCrearCopia(null);
        assertFalse(copia.archivo().equals(otra.archivo()));

        List<RespaldoService.Copia> lista = servicio.erickDelgadoListar();
        assertEquals(2, lista.size());
        assertTrue(lista.get(0).fecha().compareTo(lista.get(1).fecha()) >= 0);

        Files.writeString(servicio.erickDelgadoCarpetaCopias().resolve("notas.txt"), "x");
        assertEquals(2, servicio.erickDelgadoListar().size());

        servicio.erickDelgadoEliminarCopia(copia.archivo());
        assertEquals(1, servicio.erickDelgadoListar().size());
    }
}
