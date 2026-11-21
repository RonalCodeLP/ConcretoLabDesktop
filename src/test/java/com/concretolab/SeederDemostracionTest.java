package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.model.Proyecto;
import com.concretolab.seeder.DatabaseSeeder;
import com.concretolab.seeder.EdificiosSismicosSeeder;
import com.concretolab.seeder.MaterialSeeder;
import com.concretolab.seeder.ProyectoSeeder;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.MaterialService;
import com.concretolab.service.MezclaService;
import com.concretolab.service.NormaE030;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.RegistroGranulometriaService;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Con app.seeder.enabled=true se crea el proyecto DEMO completo, sin duplicarse al volver a sembrar. */
@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeederDemostracionTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("seeder-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
    }

    @Autowired YefriLermaProyectoService proyectos;
    @Autowired MaterialService materiales;
    @Autowired RegistroGranulometriaService granulometrias;
    @Autowired MezclaService mezclas;
    @Autowired DatabaseSeeder seeder;

    private Proyecto demo() {
        return proyectos.proyectos().stream().filter(p -> ProyectoSeeder.NOMBRE.equals(p.getNombre())).findFirst().orElseThrow();
    }

    @Test
    void creaElProyectoDemoCompletoAlIniciar() {
        Proyecto p = demo();
        assertThat(proyectos.elementos(p.getId())).hasSize(12); // 4 zapatas, 4 columnas y 4 vigas
        assertThat(materiales.listar()).extracting("nombre").contains(MaterialSeeder.ARENA, MaterialSeeder.PIEDRA, MaterialSeeder.CEMENTO);
        var arena = materiales.listar().stream().filter(m -> MaterialSeeder.ARENA.equals(m.getNombre())).findFirst().orElseThrow();
        assertThat(granulometrias.listar(arena.getId())).hasSize(1);
        var mezcla = mezclas.listar(p.getId());
        assertThat(mezcla).hasSize(1);
        assertThat(mezclas.ensayos(mezcla.get(0).getId())).hasSize(1);
    }

    @Test
    void volverASembrarNoDuplicaNada() {
        assertThat(seeder.sembrar()).isZero();
        assertThat(proyectos.proyectos().stream().filter(p -> ProyectoSeeder.NOMBRE.equals(p.getNombre()))).hasSize(1);
        assertThat(proyectos.elementos(demo().getId())).hasSize(12);
        assertThat(materiales.listar().stream().filter(m -> m.getNombre().startsWith("DEMO - "))).hasSize(3);
    }

    @Test
    void elProyectoDemoSePuedeAnalizarSismicamente() {
        var elementos = proyectos.elementos(demo().getId());
        var cargas = FernandoHuancaAnalisisSismico.pisos(elementos).stream().map(b -> new FernandoHuancaAnalisisSismico.CargaPiso(b.areaPorDefecto(), 1.0, 2.0)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4, NormaE030.Categoria.C, NormaE030.Suelo.S1, 8, 1, 1, 35);
        var r = FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(elementos, 21, 0.2, 1.0, 0.20, List.copyOf(cargas), norma));
        assertThat(r.pisos()).hasSize(1);
        assertThat(r.estaticoX().cortanteBase()).isPositive();
    }

    private FernandoHuancaAnalisisSismico.Resultado analizar(String nombre) {
        Proyecto p = proyectos.proyectos().stream().filter(x -> nombre.equals(x.getNombre())).findFirst().orElseThrow();
        var elementos = proyectos.elementos(p.getId());
        var cargas = FernandoHuancaAnalisisSismico.pisos(elementos).stream().map(b -> new FernandoHuancaAnalisisSismico.CargaPiso(b.areaPorDefecto(), 1.0, 2.0)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4, NormaE030.Categoria.C, NormaE030.Suelo.S2, 8, 1, 1, 35);
        return FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(elementos, 21, 0.2, 1.0, 0.20, List.copyOf(cargas), norma));
    }

    @Test
    void losTresEdificiosSismicosDanEscenariosDistintos() {
        var regular = analizar(EdificiosSismicosSeeder.REGULAR);
        assertThat(regular.pisos()).hasSize(3);
        assertThat(regular.espectralX().cumple()).isTrue();      // cumple la deriva de 0,007
        assertThat(regular.estaticoX().cumple()).isTrue();

        var asimetrico = analizar(EdificiosSismicosSeeder.ASIMETRICO);
        assertThat(asimetrico.pisos()).hasSize(4);
        assertThat(asimetrico.espectralY().cumple()).isFalse();
        assertThat(asimetrico.estaticoY().razonTorsion()[0]).isGreaterThan(1.3); // torsión por la columna rígida de una esquina

        var torre = analizar(EdificiosSismicosSeeder.TORRE);
        assertThat(torre.pisos()).hasSize(6);
        assertThat(torre.espectralX().cumple()).isFalse();
        assertThat(torre.espectralX().factorEscala()).isGreaterThan(1.0); // el cortante mínimo se activa
    }

    @Test
    void cadaEdificioSismicoSugiereSusParametrosEnLaUbicacion() {
        assertThat(proyectos.proyectos()).filteredOn(p -> p.getNombre().startsWith("DEMO - ")).hasSize(4);
        assertThat(proyectos.proyectos()).filteredOn(p -> EdificiosSismicosSeeder.TORRE.equals(p.getNombre()))
                .allSatisfy(p -> assertThat(p.getUbicacion()).contains("zona 4"));
    }
}
