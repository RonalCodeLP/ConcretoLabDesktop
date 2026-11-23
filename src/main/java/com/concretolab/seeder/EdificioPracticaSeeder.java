package com.concretolab.seeder;

import com.concretolab.model.Proyecto;
import com.concretolab.service.CuadriculaService;
import com.concretolab.service.CuadriculaService.Definicion;
import com.concretolab.service.CuadriculaService.Dimensiones;
import com.concretolab.service.CuadriculaService.Punto;
import com.concretolab.service.CuadriculaService.Solicitud;
import com.concretolab.service.CuadriculaService.Tramo;
import com.concretolab.service.YefriLermaProyectoService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EdificioPracticaSeeder {
    public static final String WALTER_QUISPESAYHUA_NOMBRE = "Práctica - Edificio de dos pisos incompleto";

    private final YefriLermaProyectoService proyectos;
    private final CuadriculaService walterQuispesayhuaCuadriculas;

    @Transactional
    public int walterQuispesayhuaSembrar() {
        if (proyectos.proyectos().stream().anyMatch(proyecto -> WALTER_QUISPESAYHUA_NOMBRE.equals(proyecto.getNombre()))) {
            return 0;
        }
        Proyecto proyecto = proyectos.yefriLermaCrearProyecto(WALTER_QUISPESAYHUA_NOMBRE, "Modelo incompleto con datos ficticios para práctica");
        Definicion definicion = new Definicion(0, 0, List.of(5.0, 4.0), List.of(4.0), List.of(0.0, 3.0, 6.0));
        Dimensiones dimensiones = new Dimensiones(1.5, 1.5, 0.5, 0.4, 0.3, 0.5);
        Set<Punto> intersecciones = Set.of(new Punto(0, 0), new Punto(1, 0), new Punto(2, 0),
                new Punto(0, 1), new Punto(1, 1), new Punto(2, 1));
        Set<Punto> zonaInicial = Set.of(new Punto(0, 0), new Punto(1, 0), new Punto(0, 1), new Punto(1, 1));
        Set<Tramo> tramosIniciales = Set.of(
                new Tramo(new Punto(0, 0), new Punto(1, 0)),
                new Tramo(new Punto(0, 1), new Punto(1, 1)),
                new Tramo(new Punto(0, 0), new Punto(0, 1)),
                new Tramo(new Punto(1, 0), new Punto(1, 1)));
        walterQuispesayhuaCuadriculas.walterQuispesayhuaCrear(proyecto.getId(), new Solicitud(definicion, intersecciones, Set.of(),
                true, false, false, false, dimensiones));
        walterQuispesayhuaCuadriculas.walterQuispesayhuaCrear(proyecto.getId(), new Solicitud(definicion, zonaInicial, tramosIniciales,
                false, true, true, true, dimensiones));
        return 1;
    }
}
