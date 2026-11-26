package com.concretolab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.ConcretoLabApplication;
import com.concretolab.model.Elemento;
import com.concretolab.model.Material;
import com.concretolab.model.Proyecto;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.FernandoHuancaAnalisisSismico.CargaPiso;
import com.concretolab.service.GranulometriaService;
import com.concretolab.service.HusosGranulometricos;
import com.concretolab.service.MaterialService;
import com.concretolab.service.MezclaService;
import com.concretolab.service.ProyectoEjemploService;
import com.concretolab.service.RegistroGranulometriaService;
import com.concretolab.service.UsoEdificacion;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Suelo;
import com.concretolab.service.NormaE030.Zona;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProyectoEjemploTest {
    private static final Path BASE;
    static {
        try {
            BASE = Files.createTempDirectory("concretolab-ejemplo-").resolve("test.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath());
    }

    @Autowired ProyectoEjemploService ejemplo;
    @Autowired YefriLermaProyectoService proyectos;
    @Autowired MaterialService materiales;
    @Autowired RegistroGranulometriaService tamizados;
    @Autowired MezclaService mezclas;

    @Test
    void seAgregaSinTocarOtrosProyectosYSinDuplicarMateriales() {
        Proyecto propio = proyectos.yefriLermaCrearProyecto("Proyecto propio", "");
        proyectos.yefriLermaCrearPlantilla(propio.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.5, 1.5, .5, .4, 3, .3, .5));
        List<String> antes = proyectos.elementos(propio.getId()).stream().map(e -> e.getNombre() + e.getX() + e.getLargo()).toList();

        Proyecto primero = ejemplo.fernandoHuancaAgregar();
        Proyecto segundo = ejemplo.fernandoHuancaAgregar();

        assertThat(primero.getNombre()).isEqualTo(ProyectoEjemploService.FERNANDO_HUANCA_NOMBRE);
        assertThat(segundo.getNombre()).isEqualTo(ProyectoEjemploService.FERNANDO_HUANCA_NOMBRE + " (2)");
        assertThat(proyectos.elementos(propio.getId()).stream().map(e -> e.getNombre() + e.getX() + e.getLargo()).toList())
                .isEqualTo(antes);

        Map<String, Long> porTipo = proyectos.elementos(primero.getId()).stream()
                .collect(Collectors.groupingBy(Elemento::getTipo, Collectors.counting()));
        assertThat(porTipo).containsEntry("ZAPATA", 9L).containsEntry("COLUMNA", 27L).containsEntry("VIGA", 36L).containsEntry("LOSA", 12L);

        for (String nombre : List.of(ProyectoEjemploService.FERNANDO_HUANCA_ARENA, ProyectoEjemploService.FERNANDO_HUANCA_PIEDRA,
                ProyectoEjemploService.FERNANDO_HUANCA_CEMENTO)) {
            assertThat(materiales.yefriLermaListar().stream().filter(m -> m.getNombre().equals(nombre))).hasSize(1);
        }
        Material arena = materiales.yefriLermaListar().stream()
                .filter(m -> m.getNombre().equals(ProyectoEjemploService.FERNANDO_HUANCA_ARENA)).findFirst().orElseThrow();
        var registros = tamizados.yefriLermaListar(arena.getId());
        assertThat(registros).hasSize(1);
        var registro = registros.get(0);
        var filas = GranulometriaService.yefriLermaCalcular(registro.getMasaInicial(),
                RegistroGranulometriaService.yefriLermaLeer(registro.getMediciones()), registro.getFondo()).filas();
        var huso = HusosGranulometricos.yefriLermaVerificar(HusosGranulometricos.Huso.FINO, filas);
        assertThat(huso.cumple()).isTrue();
        assertThat(huso.moduloFinura()).isCloseTo(2.67, org.assertj.core.data.Offset.offset(0.005));

        for (Proyecto p : List.of(primero, segundo)) {
            var lista = mezclas.yefriLermaListar(p.getId());
            assertThat(lista).hasSize(1);
            assertThat(mezclas.ensayos(lista.get(0).getId())).hasSize(5);
        }
    }

    @Test
    void elEdificioDeEjemploEstaConectadoYCumpleConLosValoresSugeridosParaVivienda() {
        Proyecto p = ejemplo.fernandoHuancaAgregar();
        List<Elemento> elementos = proyectos.elementos(p.getId());
        var base = FernandoHuancaAnalisisSismico.fernandoHuancaPisos(elementos);
        assertThat(base).hasSize(3);
        UsoEdificacion uso = UsoEdificacion.VIVIENDA;
        List<CargaPiso> cargas = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            boolean techo = i == base.size() - 1;
            cargas.add(new CargaPiso(base.get(i).areaPorDefecto(), uso.cargaMuertaAdicionalSugerida, techo ? uso.cvTecho : uso.cvPisos));
        }
        var r = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(new FernandoHuancaAnalisisSismico.Entrada(elementos, 21, 0.2, 1.0,
                0.20, cargas, new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35)));

        assertThat(r.conexion().sueltas()).isEmpty();
        assertThat(r.conexion().formaPorticos()).isTrue();
        assertThat(GuiaSismica.walterQuispesayhuaModeloValido(r)).isTrue();
        assertThat(WalterQuispesayhuaSismoController.walterQuispesayhuaVeredicto(r)).isEqualTo("CUMPLE (preliminar)");
    }
}
