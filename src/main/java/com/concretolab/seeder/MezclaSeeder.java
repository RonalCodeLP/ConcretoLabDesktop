package com.concretolab.seeder;

import com.concretolab.model.Ensayo;
import com.concretolab.model.Mezcla;
import com.concretolab.model.Proyecto;
import com.concretolab.service.MezclaService;
import com.concretolab.service.YefriLermaProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dosificación ficticia de 21 MPa con un ensayo de probeta a 28 días, asociadas al proyecto DEMO.
 * Requiere que {@link ProyectoSeeder} ya haya corrido. Es idempotente.
 */
@Component
@RequiredArgsConstructor
public class MezclaSeeder {

    public static final String NOMBRE = "DEMO - Mezcla de práctica";
    public static final String PROBETA = "DEMO - Probeta 28 días";

    private final YefriLermaProyectoService proyectos;
    private final MezclaService mezclas;

    @Transactional
    public int sembrar() {
        Proyecto p = ProyectoSeeder.buscar(proyectos);
        if (p == null) {
            return 0;
        }
        int nuevos = 0;
        Mezcla mezcla = mezclas.listar(p.getId()).stream().filter(x -> NOMBRE.equals(x.getNombre())).findFirst().orElse(null);
        if (mezcla == null) {
            mezcla = mezclas.guardar(nuevaMezcla(p.getId()));
            nuevos++;
        }
        if (mezclas.ensayos(mezcla.getId()).stream().noneMatch(e -> PROBETA.equals(e.getIdentificacion()))) {
            mezclas.guardarEnsayo(nuevoEnsayo(mezcla.getId()));
            nuevos++;
        }
        return nuevos;
    }

    private static Mezcla nuevaMezcla(Long proyectoId) {
        Mezcla m = new Mezcla();
        m.setProyectoId(proyectoId);
        m.setNombre(NOMBRE);
        m.setReferencia("Datos ficticios; no utilizar en obra");
        m.setResistencia(21);
        m.setCemento(350);
        m.setAgua(180);
        m.setFinoSss(808);
        m.setGruesoSss(1010);
        m.setAbsorcionFino(1);
        m.setHumedadFino(3);
        m.setAbsorcionGrueso(1);
        m.setHumedadGrueso(0.5);
        m.setMaterialFino(MaterialSeeder.ARENA + " | informe DEMO-LAB-01");
        m.setMaterialGrueso(MaterialSeeder.PIEDRA + " | informe DEMO-LAB-02");
        return m;
    }

    private static Ensayo nuevoEnsayo(Long mezclaId) {
        Ensayo e = new Ensayo();
        e.setMezclaId(mezclaId);
        e.setIdentificacion(PROBETA);
        e.setFecha("2026-11-15");
        e.setEdadDias(28);
        e.setResistenciaMpa(24);
        e.setAsentamientoMm(75);
        e.setObservaciones("Datos ficticios, no representan ensayos reales.");
        return e;
    }
}
