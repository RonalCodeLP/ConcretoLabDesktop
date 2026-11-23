package com.concretolab.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carga el proyecto de demostración «DEMO - Edificación de práctica» (materiales, tamizado, mezcla, y ensayo) y tres edificios de varios pisos para el análisis sísmico.
 * Está APAGADO por defecto para no tocar datos reales: se activa con {@code app.seeder.enabled=true} en application.properties
 * (o con {@code -Dapp.seeder.enabled=true}). Cada seeder es idempotente: reiniciar no duplica datos, pero si se borra el
 * proyecto DEMO volverá a crearse en el siguiente arranque mientras la opción siga activa.
 */
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

    /** Ejecuta los seeders en orden de dependencias y devuelve cuántos registros nuevos creó. */
    public int fernandoHuancaSembrar() {
        // Orden: el proyecto va primero; la granulometría necesita la arena; la mezcla necesita el proyecto.
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
