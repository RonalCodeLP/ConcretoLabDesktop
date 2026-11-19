package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;
import com.concretolab.model.Elemento;
import com.concretolab.seeder.DatabaseSeeder;
import com.concretolab.seeder.EdificioPracticaSeeder;
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
class EdificioPracticaSeederTest {
    private static final Path BASE;
    static {
        try { BASE = Files.createTempDirectory("edificio-practica-").resolve("test.db"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
    }

    @Autowired YefriLermaProyectoService proyectos;
    @Autowired CuadriculaService cuadriculas;
    @Autowired DatabaseSeeder seeders;

    @Test
    void creaPracticaIncompletaPermiteTerminarlaYConservaCambios() {
        var proyecto = proyectos.proyectos().stream().filter(p -> EdificioPracticaSeeder.NOMBRE.equals(p.getNombre())).findFirst().orElseThrow();
        var iniciales = proyectos.elementos(proyecto.getId());
        assertEquals(22, iniciales.size());
        assertEquals(6, iniciales.stream().filter(e -> "ZAPATA".equals(e.getTipo())).count());
        assertEquals(8, iniciales.stream().filter(e -> "COLUMNA".equals(e.getTipo())).count());
        assertEquals(8, iniciales.stream().filter(e -> "VIGA".equals(e.getTipo())).count());
        var guardada = cuadriculas.obtener(proyecto.getId()).orElseThrow();
        assertEquals("5.0; 4.0", guardada.getSeparacionesX());
        assertEquals("4.0", guardada.getSeparacionesY());
        assertEquals("0.0; 3.0; 6.0", guardada.getNiveles());
        assertTrue(iniciales.stream().noneMatch(e -> "COLUMNA".equals(e.getTipo()) && e.getX() == 9));

        Definicion definicion = new Definicion(0, 0, List.of(5.0, 4.0), List.of(4.0), List.of(0.0, 3.0, 6.0));
        Dimensiones dimensiones = new Dimensiones(1.5, 1.5, .5, .4, .3, .5);
        Punto a = new Punto(1, 0), b = new Punto(2, 0), c = new Punto(1, 1), d = new Punto(2, 1);
        crearSinSuperposiciones(proyecto.getId(), new Solicitud(definicion, Set.of(b, d), Set.of(),
                false, true, false, false, dimensiones), 4);
        crearSinSuperposiciones(proyecto.getId(), new Solicitud(definicion, Set.of(a, b, c, d),
                Set.of(new Tramo(a, b), new Tramo(c, d)), false, false, true, false, dimensiones), 4);
        crearSinSuperposiciones(proyecto.getId(), new Solicitud(definicion, Set.of(b, d),
                Set.of(new Tramo(b, d)), false, false, false, true, dimensiones), 2);
        assertEquals(32, proyectos.elementos(proyecto.getId()).size());

        Elemento eliminado = iniciales.get(0);
        Elemento editado = iniciales.get(1);
        proyectos.eliminar(eliminado.getId());
        editado.setNombre("Ajustado por el usuario");
        proyectos.guardar(editado);
        assertEquals(0, seeders.sembrar());
        var finales = proyectos.elementos(proyecto.getId());
        assertEquals(31, finales.size());
        assertTrue(finales.stream().noneMatch(e -> e.getId().equals(eliminado.getId())));
        assertTrue(finales.stream().anyMatch(e -> e.getId().equals(editado.getId()) && "Ajustado por el usuario".equals(e.getNombre())));
        assertEquals(1, proyectos.proyectos().stream().filter(p -> EdificioPracticaSeeder.NOMBRE.equals(p.getNombre())).count());
    }

    private void crearSinSuperposiciones(Long proyectoId, Solicitud solicitud, int cantidad) {
        var vista = cuadriculas.previsualizar(proyectoId, solicitud);
        assertEquals(cantidad, vista.elementos().size());
        assertTrue(vista.superposiciones().isEmpty(), vista.superposiciones().toString());
        cuadriculas.crear(proyectoId, solicitud);
    }
}
