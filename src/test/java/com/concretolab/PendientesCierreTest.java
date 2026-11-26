package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.service.CuantiasAcero;
import com.concretolab.service.DisenoMezclaACI;
import com.concretolab.service.DisenoMezclaACI.Exposicion;
import com.concretolab.service.MaterialService;
import com.concretolab.service.MetradoNeto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendientesCierreTest {
    @Test
    void cuantiasSinArchivoSonLasReferencialesYSeGuardanYLeen(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("sub").resolve("cuantias-acero.properties");
        assertThat(CuantiasAcero.yefriLermaLeer(archivo)).isEqualTo(MetradoNeto.yefriLermaCuantiasReferenciales());

        var propias = new LinkedHashMap<>(MetradoNeto.yefriLermaCuantiasReferenciales());
        propias.put("COLUMNA", 180.0);
        propias.put("LOSA", 95.5);
        CuantiasAcero.yefriLermaGuardar(archivo, propias);
        assertThat(CuantiasAcero.yefriLermaLeer(archivo)).isEqualTo(propias);
    }

    @Test
    void cuantiasInvalidasNoSeGuardanYLasDanadasUsanElReferencial(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("cuantias-acero.properties");
        var malas = new LinkedHashMap<>(MetradoNeto.yefriLermaCuantiasReferenciales());
        malas.put("VIGA", -5.0);
        assertThatThrownBy(() -> CuantiasAcero.yefriLermaGuardar(archivo, malas)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("viga");
        assertThat(archivo).doesNotExist();

        Files.writeString(archivo, "COLUMNA=abc\nVIGA=9999\nZAPATA=70\n");
        var leidas = CuantiasAcero.yefriLermaLeer(archivo);
        assertThat(leidas.get("COLUMNA")).isEqualTo(150.0);
        assertThat(leidas.get("VIGA")).isEqualTo(120.0);
        assertThat(leidas.get("ZAPATA")).isEqualTo(70.0);
    }

    @Test
    void elCatalogoIncluyeCementosNtpYAditivosSinPerderLosTiposAnteriores() {
        assertThat(MaterialService.YEFRI_LERMA_TIPOS).contains("CEMENTO", "AGREGADO FINO", "AGREGADO GRUESO",
                "CEMENTO TIPO I", "CEMENTO TIPO IP", "CEMENTO TIPO V", "CEMENTO TIPO HS", "ADITIVO");
    }

    @Test
    void avisaCuandoElCementoNoCorrespondeALaExposicionASulfatos() {
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.NORMAL, "CEMENTO TIPO I")).isEmpty();
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_MODERADOS, "CEMENTO TIPO II")).isEmpty();
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_MODERADOS, "CEMENTO TIPO MS")).isEmpty();
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_MODERADOS, "CEMENTO TIPO I"))
                .get().asString().contains("tipo II o MS");
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_SEVEROS, "CEMENTO TIPO V")).isEmpty();
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_SEVEROS, "CEMENTO TIPO IP"))
                .get().asString().contains("tipo V o HS");
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_SEVEROS, "CEMENTO"))
                .get().asString().contains("sin tipo indicado");
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_MUY_SEVEROS, "CEMENTO TIPO V"))
                .get().asString().contains("puzolana");
        assertThat(DisenoMezclaACI.yefriLermaAvisoCemento(Exposicion.SULFATOS_SEVEROS, null)).isEmpty();
    }
}
