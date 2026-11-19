package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;
import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.CuadriculaService;
import com.concretolab.service.CuadriculaService.Definicion;
import com.concretolab.service.CuadriculaService.Dimensiones;
import com.concretolab.service.CuadriculaService.Punto;
import com.concretolab.service.CuadriculaService.Solicitud;
import com.concretolab.service.CuadriculaService.Tramo;
import com.concretolab.service.YefriLermaProyectoService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CuadriculaIntegracionTest {
    private static final Path BASE;
    static {
        try { BASE = Files.createTempDirectory("cuadricula-test-").resolve("test.db"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath());
        registro.add("app.seeder.enabled", () -> "false");
    }
    @Autowired CuadriculaService cuadriculas;
    @Autowired YefriLermaProyectoService modelos;

    @Test
    void generaPorEjesEnProyectoExistenteYDetectaSuperposiciones() {
        Proyecto proyecto = modelos.crearProyecto("Proyecto existente", "");
        Elemento existente = new Elemento();
        existente.setProyectoId(proyecto.getId()); existente.setTipo("ZAPATA"); existente.setNombre("Existente");
        existente.setLargo(1); existente.setAncho(1); existente.setAltura(0.5); existente.setZ(-0.25);
        modelos.guardar(existente);
        Definicion d = new Definicion(0, 0, List.of(4.0, 6.0), List.of(5.0), List.of(0.0, 3.0, 6.5));
        Solicitud s = new Solicitud(d, Set.of(new Punto(0, 0), new Punto(1, 0), new Punto(2, 0)),
                Set.of(new Tramo(new Punto(0, 0), new Punto(1, 0)), new Tramo(new Punto(1, 0), new Punto(2, 0))),
                true, true, true, false, new Dimensiones(1.5, 1.5, 0.5, 0.4, 0.3, 0.5));
        var vista = cuadriculas.previsualizar(proyecto.getId(), s);
        assertEquals(13, vista.elementos().size());
        assertTrue(vista.superposiciones().stream().anyMatch(c -> c.contains("Existente")));
        Elemento viga = vista.elementos().stream().filter(e -> e.getNombre().equals("VX-1-1-1")).findFirst().orElseThrow();
        assertEquals(3.6, viga.getLargo(), 1e-9);
        assertEquals(2, viga.getX(), 1e-9);
        Elemento columna = vista.elementos().stream().filter(e -> e.getNombre().equals("C-2-3-1")).findFirst().orElseThrow();
        assertEquals(10, columna.getX(), 1e-9);
        assertEquals(4.75, columna.getZ(), 1e-9);
        Solicitud unTramo = new Solicitud(d, s.puntos(), Set.of(new Tramo(new Punto(0, 0), new Punto(1, 0))),
                false, false, true, false, s.dimensiones());
        assertEquals(2, cuadriculas.previsualizar(proyecto.getId(), unTramo).elementos().size());
        assertEquals(1, modelos.elementos(proyecto.getId()).size());
        cuadriculas.crear(proyecto.getId(), s);
        assertEquals(14, modelos.elementos(proyecto.getId()).size());
        assertEquals("4.0; 6.0", cuadriculas.obtener(proyecto.getId()).orElseThrow().getSeparacionesX());
        assertEquals(-0.25, modelos.elementos(proyecto.getId()).get(0).getZ(), 1e-9);
    }

    @Test
    void rechazaNivelesYVanosInvalidosSinGuardar() {
        Proyecto proyecto = modelos.crearProyecto("Validación", "");
        Dimensiones dimensiones = new Dimensiones(1, 1, 0.5, 0.4, 0.3, 0.5);
        Definicion nivelesInvalidos = new Definicion(0, 0, List.of(4.0), List.of(5.0), List.of(3.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> cuadriculas.guardarCuadricula(proyecto.getId(), nivelesInvalidos));
        Definicion vanoInvalido = new Definicion(0, 0, List.of(0.3), List.of(5.0), List.of(0.0, 3.0));
        Solicitud solicitud = new Solicitud(vanoInvalido, Set.of(new Punto(0, 0), new Punto(1, 0)),
                Set.of(new Tramo(new Punto(0, 0), new Punto(1, 0))),
                false, true, true, false, dimensiones);
        assertThrows(IllegalArgumentException.class, () -> cuadriculas.crear(proyecto.getId(), solicitud));
        assertTrue(modelos.elementos(proyecto.getId()).isEmpty());
        assertTrue(cuadriculas.obtener(proyecto.getId()).isEmpty());
    }
}
