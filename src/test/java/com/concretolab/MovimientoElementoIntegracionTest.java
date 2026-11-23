package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;
import com.concretolab.model.Elemento;
import com.concretolab.service.CuadriculaService;
import com.concretolab.service.MovimientoElementoService;
import com.concretolab.service.MovimientoElementoService.Posicion;
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
class MovimientoElementoIntegracionTest {
    private static final Path BASE;
    static {
        try { BASE = Files.createTempDirectory("movimiento-test-").resolve("test.db"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath());
        registro.add("app.seeder.enabled", () -> "false");
    }
    @Autowired MovimientoElementoService movimientos;
    @Autowired YefriLermaProyectoService modelos;
    @Autowired CuadriculaService cuadriculas;

    @Test
    void vistaPreviaOfreceAlineacionYAdvierteSinGuardar() {
        var proyecto = modelos.yefriLermaCrearProyecto("Vista previa", "");
        cuadriculas.walterQuispesayhuaGuardarCuadricula(proyecto.getId(), new CuadriculaService.Definicion(0, 0,
                List.of(4.0), List.of(4.0), List.of(0.0, 3.0)));
        Elemento columna = elemento(proyecto.getId(), "COLUMNA", "C1", 0, 0, 1.5, .4, .4, 3);
        elemento(proyecto.getId(), "ZAPATA", "Z1", 0, 0, -.25, 1, 1, .5);
        var original = movimientos.walterQuispesayhuaEstado(columna);
        var vista = movimientos.walterQuispesayhuaPrevisualizar(original, new Posicion(0.1, 0, 1.5));
        assertTrue(vista.sugerencias().stream().anyMatch(s -> s.posicion().x() == 0));
        assertEquals(0, modelos.elementos(proyecto.getId()).get(0).getX());
        var separada = movimientos.walterQuispesayhuaPrevisualizar(original, new Posicion(2, 0, 1.5));
        assertTrue(separada.advertencias().stream().anyMatch(s -> s.contains("Z1")));
        assertThrows(IllegalArgumentException.class, () -> movimientos.walterQuispesayhuaPrevisualizar(original, new Posicion(Double.NaN, 0, 0)));
    }

    @Test
    void guardaYDeshaceSoloSiElElementoNoCambio() {
        var proyecto = modelos.yefriLermaCrearProyecto("Movimiento", "");
        var original = movimientos.walterQuispesayhuaEstado(elemento(proyecto.getId(), "COLUMNA", "C1", 0, 0, 1.5, .4, .4, 3));
        var guardado = movimientos.walterQuispesayhuaGuardar(original, new Posicion(2, 1, 1.5));
        assertEquals(2, modelos.elementos(proyecto.getId()).get(0).getX());
        assertThrows(IllegalStateException.class, () -> movimientos.walterQuispesayhuaGuardar(original, new Posicion(3, 1, 1.5)));
        movimientos.walterQuispesayhuaGuardar(guardado, original.posicion());
        var restaurado = modelos.elementos(proyecto.getId()).get(0);
        assertEquals(0, restaurado.getX());
        assertEquals(.48, restaurado.volumen(), 1e-9);
    }

    @Test
    void vinculaYReajustaVigaConfirmadaYDeshaceTodaLaOperacion() {
        var proyecto = modelos.yefriLermaCrearProyecto("Viga vinculada", "");
        var columna = movimientos.walterQuispesayhuaEstado(elemento(proyecto.getId(), "COLUMNA", "C1", 0, 0, 1.5, .4, .4, 3));
        var viga = movimientos.walterQuispesayhuaEstado(elemento(proyecto.getId(), "VIGA", "V1", 2, 0, 2.75, 3.6, .3, .5));
        assertEquals(1, movimientos.walterQuispesayhuaCandidatos(columna).size());
        assertFalse(movimientos.walterQuispesayhuaCandidatos(columna).get(0).vinculado());
        var operacion = movimientos.walterQuispesayhuaPreparar(columna, new Posicion(.5, 0, 1.5), Set.of(viga.id()));
        assertEquals(3.1, operacion.propuestos().get(1).largo(), 1e-9);
        assertEquals(2.25, operacion.propuestos().get(1).posicion().x(), 1e-9);
        assertEquals(3.6, modelos.elementos(proyecto.getId()).get(1).getLargo(), 1e-9);
        movimientos.walterQuispesayhuaGuardar(operacion);
        assertEquals(3.1, modelos.elementos(proyecto.getId()).get(1).getLargo(), 1e-9);
        assertTrue(movimientos.walterQuispesayhuaCandidatos(operacion.propuestos().get(0)).get(0).vinculado());
        assertThrows(IllegalArgumentException.class, () -> movimientos.walterQuispesayhuaPreparar(operacion.propuestos().get(0),
                new Posicion(.5, 1, 1.5), Set.of(viga.id())));
        movimientos.walterQuispesayhuaDeshacer(operacion);
        assertEquals(3.6, modelos.elementos(proyecto.getId()).get(1).getLargo(), 1e-9);
        assertFalse(movimientos.walterQuispesayhuaCandidatos(columna).get(0).vinculado());
    }

    @Test
    void reajustaElExtremoFinalEnYYSuspendeDeshacerSiLaVigaCambio() {
        var proyecto = modelos.yefriLermaCrearProyecto("Viga en Y", "");
        var columna = movimientos.walterQuispesayhuaEstado(elemento(proyecto.getId(), "COLUMNA", "C2", 0, 4, 1.5, .4, .4, 3));
        var viga = movimientos.walterQuispesayhuaEstado(elemento(proyecto.getId(), "VIGA", "VY1", 0, 2, 2.75, .3, 3.6, .5));
        var operacion = movimientos.walterQuispesayhuaPreparar(columna, new Posicion(0, 3.5, 1.5), Set.of(viga.id()));
        assertEquals(3.1, operacion.propuestos().get(1).ancho(), 1e-9);
        assertEquals(1.75, operacion.propuestos().get(1).posicion().y(), 1e-9);
        movimientos.walterQuispesayhuaGuardar(operacion);
        Elemento editada = modelos.elementos(proyecto.getId()).get(1);
        editada.setNombre("Viga editada");
        modelos.yefriLermaGuardar(editada);
        assertThrows(IllegalStateException.class, () -> movimientos.walterQuispesayhuaDeshacer(operacion));
        assertEquals(3.5, modelos.elementos(proyecto.getId()).get(0).getY(), 1e-9);
        assertEquals("Viga editada", modelos.elementos(proyecto.getId()).get(1).getNombre());
    }

    private Elemento elemento(Long proyectoId, String tipo, String nombre, double x, double y, double z,
                              double largo, double ancho, double altura) {
        Elemento e = new Elemento();
        e.setProyectoId(proyectoId); e.setTipo(tipo); e.setNombre(nombre); e.setX(x); e.setY(y); e.setZ(z);
        e.setLargo(largo); e.setAncho(ancho); e.setAltura(altura);
        return modelos.yefriLermaGuardar(e);
    }
}
