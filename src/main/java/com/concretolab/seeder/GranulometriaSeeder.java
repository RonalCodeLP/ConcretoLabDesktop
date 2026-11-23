package com.concretolab.seeder;

import com.concretolab.model.Material;
import com.concretolab.model.RegistroGranulometria;
import com.concretolab.service.MaterialService;
import com.concretolab.service.RegistroGranulometriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tamizado de ejemplo de la arena DEMO. Requiere que {@link MaterialSeeder} ya haya corrido. Es idempotente. */
@Component
@RequiredArgsConstructor
public class GranulometriaSeeder {

    public static final String YEFRI_LERMA_IDENTIFICACION = "DEMO - Tamizado de arena";

    private final MaterialService materiales;
    private final RegistroGranulometriaService granulometrias;

    @Transactional
    public int yefriLermaSembrar() {
        Material arena = materiales.yefriLermaListar().stream().filter(m -> MaterialSeeder.YEFRI_LERMA_ARENA.equals(m.getNombre())).findFirst().orElse(null);
        if (arena == null || granulometrias.yefriLermaListar(arena.getId()).stream().anyMatch(r -> YEFRI_LERMA_IDENTIFICACION.equals(r.getIdentificacion()))) {
            return 0;
        }
        RegistroGranulometria g = new RegistroGranulometria();
        g.setMaterialId(arena.getId());
        g.setIdentificacion(YEFRI_LERMA_IDENTIFICACION);
        g.setFecha("2026-11-01");
        g.setMasaInicial(1000);
        g.setFondo(50);
        g.setMediciones("4.75; 20\n2.36; 80\n1.18; 200\n0.60; 300\n0.30; 250\n0.15; 100");
        granulometrias.yefriLermaGuardar(g);
        return 1;
    }
}
