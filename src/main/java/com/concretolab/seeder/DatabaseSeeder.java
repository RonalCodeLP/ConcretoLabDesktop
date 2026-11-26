package com.concretolab.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {
    private final ProyectoSeeder fernandoHuancaProyectoSeeder;
    private final MaterialSeeder fernandoHuancaMaterialSeeder;
    private final GranulometriaSeeder fernandoHuancaGranulometriaSeeder;
    private final MezclaSeeder fernandoHuancaMezclaSeeder;
    private final EdificiosSismicosSeeder fernandoHuancaEdificiosSeeder;
    private final EdificioPracticaSeeder fernandoHuancaEdificioPracticaSeeder;

    @Override
    public void run(ApplicationArguments args) {
        fernandoHuancaSembrar();
    }

    public int fernandoHuancaSembrar() {
        int proyectos = fernandoHuancaProyectoSeeder.yefriLermaSembrar();
        int materiales = fernandoHuancaMaterialSeeder.yefriLermaSembrar();
        int granulometrias = fernandoHuancaGranulometriaSeeder.yefriLermaSembrar();
        int mezclas = fernandoHuancaMezclaSeeder.yefriLermaSembrar();
        int edificios = fernandoHuancaEdificiosSeeder.yefriLermaSembrar();
        int practicas = fernandoHuancaEdificioPracticaSeeder.walterQuispesayhuaSembrar();
        log.info("Seeder: {} proyecto(s), {} material(es), {} tamizado(s), {} registro(s) de mezcla/ensayo, {} edificio(s) sísmicos y {} proyecto(s) de práctica nuevos.",
                proyectos, materiales, granulometrias, mezclas, edificios, practicas);
        return proyectos + materiales + granulometrias + mezclas + edificios + practicas;
    }
}
