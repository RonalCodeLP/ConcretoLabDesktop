package com.concretolab.seeder;

import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProyectoSeeder {
    public static final String YEFRI_LERMA_NOMBRE = "DEMO - Edificación de práctica";

    private final YefriLermaProyectoService proyectos;

    @Transactional
    public int yefriLermaSembrar() {
        if (yefriLermaBuscar(proyectos) != null) {
            return 0;
        }
        Proyecto p = proyectos.yefriLermaCrearProyecto(YEFRI_LERMA_NOMBRE, "Datos ficticios para aprendizaje");
        proyectos.yefriLermaCrearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.5, 1.5, 0.5, 0.4, 3, 0.3, 0.5));
        return 1;
    }

    static Proyecto yefriLermaBuscar(YefriLermaProyectoService proyectos) {
        return proyectos.proyectos().stream().filter(p -> YEFRI_LERMA_NOMBRE.equals(p.getNombre())).findFirst().orElse(null);
    }
}
