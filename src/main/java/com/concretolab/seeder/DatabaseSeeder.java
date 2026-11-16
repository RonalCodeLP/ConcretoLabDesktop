package com.concretolab.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carga el proyecto de demostración «DEMO - Edificación de práctica» (materiales, tamizado, mezcla, ensayo y cercha) y tres edificios de varios pisos para el análisis sísmico.
 * Está APAGADO por defecto para no tocar datos reales: se activa con {@code app.seeder.enabled=true} en application.properties
 * (o con {@code -Dapp.seeder.enabled=true}). Cada seeder es idempotente: reiniciar no duplica datos, pero si se borra el
 * proyecto DEMO volverá a crearse en el siguiente arranque mientras la opción siga activa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {

    private final ProyectoSeeder proyectoSeeder;
    private final MaterialSeeder materialSeeder;
    private final GranulometriaSeeder granulometriaSeeder;
    private final MezclaSeeder mezclaSeeder;
    private final CerchaSeeder cerchaSeeder;
    private final EdificiosSismicosSeeder edificiosSeeder;

    @Override
    public void run(ApplicationArguments args) {
        sembrar();
    }

    /** Ejecuta los seeders en orden de dependencias y devuelve cuántos registros nuevos creó. */
    public int sembrar() {
        // Orden: el proyecto va primero; la granulometría necesita la arena; la mezcla y la cercha necesitan el proyecto.
        int proyectos = proyectoSeeder.sembrar();
        int materiales = materialSeeder.sembrar();
        int granulometrias = granulometriaSeeder.sembrar();
        int mezclas = mezclaSeeder.sembrar();
        int cerchas = cerchaSeeder.sembrar();
        int edificios = edificiosSeeder.sembrar();
        log.info("Seeder: {} proyecto(s), {} material(es), {} tamizado(s), {} registro(s) de mezcla/ensayo, {} cercha(s) y {} edificio(s) sísmicos nuevos.",
                proyectos, materiales, granulometrias, mezclas, cerchas, edificios);
        return proyectos + materiales + granulometrias + mezclas + cerchas + edificios;
    }
}
