package com.concretolab.seeder;

import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyecto de demostración con la plantilla rectangular (4 zapatas, 4 columnas y 4 vigas), listo para probar el modelo 3D,
 * el metrado y el análisis sísmico. Es idempotente: si ya existe un proyecto con ese nombre, no hace nada.
 */
@Component
@RequiredArgsConstructor
public class ProyectoSeeder {

    public static final String NOMBRE = "DEMO - Edificación de práctica";

    private final YefriLermaProyectoService proyectos;

    @Transactional
    public int sembrar() {
        if (buscar(proyectos) != null) {
            return 0;
        }
        Proyecto p = proyectos.crearProyecto(NOMBRE, "Datos ficticios para aprendizaje");
        proyectos.crearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.5, 1.5, 0.5, 0.4, 3, 0.3, 0.5));
        return 1;
    }

    /** Devuelve el proyecto DEMO o {@code null} si todavía no existe. */
    static Proyecto buscar(YefriLermaProyectoService proyectos) {
        return proyectos.proyectos().stream().filter(p -> NOMBRE.equals(p.getNombre())).findFirst().orElse(null);
    }
}
