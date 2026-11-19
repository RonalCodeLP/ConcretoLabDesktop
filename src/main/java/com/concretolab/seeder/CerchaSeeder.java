package com.concretolab.seeder;

import com.concretolab.model.ModeloCercha;
import com.concretolab.model.Proyecto;
import com.concretolab.service.ModeloCerchaService;
import com.concretolab.service.YefriLermaProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cercha triangular de ejemplo asociada al proyecto DEMO. Requiere {@link ProyectoSeeder}. Es idempotente. */
@Component
@RequiredArgsConstructor
public class CerchaSeeder {

    public static final String NOMBRE = "DEMO - Cercha triangular";

    private final YefriLermaProyectoService proyectos;
    private final ModeloCerchaService cerchas;

    @Transactional
    public int sembrar() {
        Proyecto p = ProyectoSeeder.buscar(proyectos);
        if (p == null || cerchas.listar(p.getId()).stream().anyMatch(c -> NOMBRE.equals(c.getNombre()))) {
            return 0;
        }
        ModeloCercha c = new ModeloCercha();
        c.setProyectoId(p.getId());
        c.setNombre(NOMBRE);
        c.setNudos("A; 0; 0; SI; SI; 0; 0\nB; 4; 0; NO; SI; 0; 0\nC; 2; 3; NO; NO; 0; -30");
        c.setBarras("AB; A; B; 200000; 20\nAC; A; C; 200000; 20\nBC; B; C; 200000; 20");
        cerchas.guardar(c);
        return 1;
    }
}
