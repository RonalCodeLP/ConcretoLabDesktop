package com.concretolab.seeder;

import com.concretolab.model.Material;
import com.concretolab.service.MaterialService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de materiales ficticios: una arena, una piedra y un cemento. Omite los que ya existen por nombre.
 * Los valores no provienen de ensayos reales.
 */
@Component
@RequiredArgsConstructor
public class MaterialSeeder {

    public static final String YEFRI_LERMA_ARENA = "DEMO - Arena de ensayo";
    public static final String YEFRI_LERMA_PIEDRA = "DEMO - Piedra de ensayo";
    public static final String CEMENTO = "DEMO - Cemento de ensayo";

    private final MaterialService materiales;

    @Transactional
    public int yefriLermaSembrar() {
        Set<String> existentes = materiales.yefriLermaListar().stream().map(Material::getNombre).collect(Collectors.toSet());
        int nuevos = 0;
        for (Material m : yefriLermaPlantillas()) {
            if (existentes.add(m.getNombre())) {
                materiales.yefriLermaGuardar(m);
                nuevos++;
            }
        }
        return nuevos;
    }

    private static List<Material> yefriLermaPlantillas() {
        Material arena = new Material();
        arena.setTipo("AGREGADO FINO");
        arena.setNombre(YEFRI_LERMA_ARENA);
        arena.setProcedencia("Cantera ficticia");
        arena.setReferenciaEnsayo("DEMO-LAB-01");
        arena.setFechaEnsayo("2026-11-01");
        arena.setDensidadRelativa(2.65);
        arena.setAbsorcion(1.0);
        arena.setHumedad(3.0);
        arena.setModuloFinura(2.7);

        Material piedra = new Material();
        piedra.setTipo("AGREGADO GRUESO");
        piedra.setNombre(YEFRI_LERMA_PIEDRA);
        piedra.setProcedencia("Cantera ficticia");
        piedra.setReferenciaEnsayo("DEMO-LAB-02");
        piedra.setFechaEnsayo("2026-11-01");
        piedra.setDensidadRelativa(2.65);
        piedra.setAbsorcion(1.0);
        piedra.setHumedad(0.5);
        piedra.setTamanoMaximoMm(19.0);
        piedra.setPesoUnitarioCompactado(1600.0);

        Material cemento = new Material();
        cemento.setTipo("CEMENTO");
        cemento.setNombre(CEMENTO);
        cemento.setDensidadRelativa(3.15);
        cemento.setReferenciaEnsayo("Datos ficticios");

        return List.of(arena, piedra, cemento);
    }
}
