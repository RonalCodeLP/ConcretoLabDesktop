package com.concretolab;

import com.concretolab.service.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportacionDxfTest {
    @Test void leeSeccionesDistintasDirectamenteDeLosContornos() throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(Path.of("src/test/resources/planta-contornos.dxf"));
        var elementos = lector.fernandoHuancaConvertir(plano,new FernandoHuancaPlanoDxf.Datos(
                "COLUMNAS","VIGAS","",0.001,2,3,Double.NaN,0.25,0.5,Double.NaN,1));
        assertEquals(16,elementos.size());
        assertEquals(0.4,elementos.get(0).getLargo(),1e-9);
        assertEquals(0.6,elementos.get(1).getLargo(),1e-9);
        assertEquals(0.3,elementos.get(1).getAncho(),1e-9);
        assertEquals(4.5,elementos.get(4).getLargo(),1e-9);
        assertEquals(2.45,elementos.get(4).getX(),1e-9);
        var pisos = FernandoHuancaAnalisisSismico.pisos(elementos);
        var cargas = pisos.stream().map(p->new FernandoHuancaAnalisisSismico.CargaPiso(p.areaPorDefecto(),1,2)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4,NormaE030.Categoria.C,NormaE030.Suelo.S1,8,1,1,35);
        var resultado = FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(
                elementos,21,0.2,1,0.2,cargas,norma));
        assertEquals(2,resultado.pisos().size());
        assertTrue(resultado.espectralX().cortanteBase()>0);
    }

    @Test void modeloCompletoConservaGeometriaAlExportarYReimportar(@org.junit.jupiter.api.io.TempDir Path temporal) throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var originales = lector.fernandoHuancaLeer(Path.of("doc/portico-completo-3d.dxf")).modelo3d();
        assertEquals(45,originales.size());
        Path archivo = temporal.resolve("exportado.dxf");
        lector.fernandoHuancaExportar(archivo,originales);
        var recuperados = lector.fernandoHuancaLeer(archivo).modelo3d();
        assertEquals(originales.size(),recuperados.size());
        for(int i=0;i<originales.size();i++){
            var a=originales.get(i); var b=recuperados.get(i);
            assertEquals(a.getTipo(),b.getTipo());
            assertArrayEquals(new double[]{a.getX(),a.getY(),a.getZ(),a.getLargo(),a.getAncho(),a.getAltura()},
                    new double[]{b.getX(),b.getY(),b.getZ(),b.getLargo(),b.getAncho(),b.getAltura()},1e-12);
        }
        var pisos = FernandoHuancaAnalisisSismico.pisos(recuperados);
        assertEquals(3,pisos.size());
        var cargas = pisos.stream().map(p -> new FernandoHuancaAnalisisSismico.CargaPiso(p.areaPorDefecto(),1,2)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4,NormaE030.Categoria.C,NormaE030.Suelo.S1,8,1,1,35);
        var resultado = FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(
                recuperados,21,0.2,1,0.2,cargas,norma));
        assertTrue(resultado.espectralX().cortanteBase() > 0);
    }

    @Test void porticoDeDosVanosSeImportaYSeAnaliza() throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(Path.of("doc/portico-dos-vanos.dxf"));
        assertEquals("6", plano.unidad());
        var elementos = lector.fernandoHuancaConvertir(plano, new FernandoHuancaPlanoDxf.Datos(
                "COLUMNAS","VIGAS","ZAPATAS",1,2,3,0.4,0.3,0.5,1.5,0.5));
        assertEquals(32, elementos.size());
        var pisos = FernandoHuancaAnalisisSismico.pisos(elementos);
        assertEquals(2, pisos.size());
        var cargas = pisos.stream().map(p -> new FernandoHuancaAnalisisSismico.CargaPiso(p.areaPorDefecto(),1,2)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4,NormaE030.Categoria.C,NormaE030.Suelo.S1,8,1,1,35);
        var resultado = FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(
                elementos,21,0.2,1,0.2,cargas,norma));
        assertTrue(resultado.espectralX().cortanteBase() > 0);
        assertFalse(resultado.modos().get(0).forma().isEmpty());
    }

    @Test void plantaEnMilimetrosProduceUnModeloQueElMotorPuedeAnalizar() throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(Path.of("doc/planta-estructural-ejemplo.dxf"));
        assertEquals("4", plano.unidad());
        var elementos = lector.fernandoHuancaConvertir(plano, new FernandoHuancaPlanoDxf.Datos(
                "COLUMNAS","VIGAS","ZAPATAS",0.001,3,3,0.4,0.3,0.5,1.5,0.5));
        assertEquals(28, elementos.size());
        assertEquals(4.6, elementos.get(8).getLargo(), 1e-9);
        var pisos = FernandoHuancaAnalisisSismico.pisos(elementos);
        assertEquals(3, pisos.size());
        var cargas = pisos.stream().map(p -> new FernandoHuancaAnalisisSismico.CargaPiso(p.areaPorDefecto(),1,2)).toList();
        var norma = new NormaE030.Parametros(NormaE030.Zona.Z4,NormaE030.Categoria.C,NormaE030.Suelo.S1,8,1,1,35);
        var resultado = FernandoHuancaAnalisisSismico.analizar(new FernandoHuancaAnalisisSismico.Entrada(
                elementos,21,0.2,1,0.2,cargas,norma));
        assertTrue(resultado.espectralX().cortanteBase() > 0);
        assertFalse(resultado.modos().get(0).forma().isEmpty());
    }
}
