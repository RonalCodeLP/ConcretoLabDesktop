package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.service.DeteccionCapasDxf;
import com.concretolab.service.DeteccionCapasDxf.Rol;
import com.concretolab.service.FernandoHuancaPlanoDxf;
import com.concretolab.service.FernandoHuancaPlanoDxf.Datos;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DxfDeteccionTest {

    private static void g(StringBuilder sb, Object codigo, Object valor) {
        sb.append(codigo).append('\n').append(valor).append('\n');
    }

    private static void poli(StringBuilder sb, String capa, int flag, double... xy) {
        g(sb, 0, "LWPOLYLINE"); g(sb, 8, capa); g(sb, 100, "AcDbPolyline"); g(sb, 90, xy.length / 2); g(sb, 70, flag);
        for (int i = 0; i < xy.length; i += 2) { g(sb, 10, xy[i]); g(sb, 20, xy[i + 1]); }
    }

    private static void linea(StringBuilder sb, String capa, double x1, double y1, double x2, double y2) {
        g(sb, 0, "LINE"); g(sb, 8, capa); g(sb, 10, x1); g(sb, 20, y1); g(sb, 30, 0); g(sb, 11, x2); g(sb, 21, y2); g(sb, 31, 0);
    }

    /** Planta en milímetros, sin $INSUNITS, con columnas de 4 tipos de entidad y vigas de LINE y polilínea. */
    private Path plano(Path dir, double desvioViga) throws Exception { return plano(dir, desvioViga, false); }

    private Path plano(Path dir, double desvioViga, boolean extras) throws Exception {
        var sb = new StringBuilder();
        g(sb, 0, "SECTION"); g(sb, 2, "ENTITIES");
        poli(sb, "S-COLUMNAS", 0, -200, -200, 200, -200, 200, 200, -200, 200, -200, -200); // rectángulo con vértice repetido
        g(sb, 0, "INSERT"); g(sb, 8, "S-COLUMNAS"); g(sb, 2, "COL-40"); g(sb, 10, 5000); g(sb, 20, 0); g(sb, 30, 0);
        g(sb, 0, "CIRCLE"); g(sb, 8, "S-COLUMNAS"); g(sb, 10, 5000); g(sb, 20, 4000); g(sb, 30, 0); g(sb, 40, 200);
        g(sb, 0, "INSERT"); g(sb, 8, "S-COLUMNAS"); g(sb, 2, "COL-40"); g(sb, 10, 0); g(sb, 20, 4000); g(sb, 30, 0);
        linea(sb, "VIGAS 1", 0, 0, 5000, 0);
        poli(sb, "VIGAS 1", 0, 5000 + desvioViga, 0, 5000, 4000);          // viga como polilínea de 2 puntos
        linea(sb, "VIGAS 1", 5000, 4000, 0, 4000);
        linea(sb, "VIGAS 1", 0, 4000, 0, 0);
        if (extras) {
            poli(sb, "LOSAS", 1, -200, -200, 5200, -200, 5200, 4200, -200, 4200);
            linea(sb, "MUROS", 2500, 0, 2500, 4000);
        }
        g(sb, 0, "TEXT"); g(sb, 8, "TEXTOS"); g(sb, 10, 0); g(sb, 20, 0); g(sb, 1, "Eje A");
        g(sb, 0, "ENDSEC"); g(sb, 0, "EOF");
        Path f = dir.resolve("planta.dxf");
        Files.writeString(f, sb, StandardCharsets.US_ASCII);
        return f;
    }

    private static Datos datos() {
        return new Datos("S-COLUMNAS", "VIGAS 1", "", 0.001, 2, 3.0, 0.4, 0.3, 0.5, Double.NaN, 1.0);
    }

    @Test
    void importaBloquesCirculosYPolilineasAnclandoVigasALasColumnas(@TempDir Path dir) throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(plano(dir, 100)); // la viga empieza a 10 cm del centro (dentro de la columna)
        var modelo = lector.fernandoHuancaConvertir(plano, datos());
        assertThat(modelo.stream().filter(e -> e.getTipo().equals("COLUMNA"))).hasSize(8);
        assertThat(modelo.stream().filter(e -> e.getTipo().equals("VIGA"))).hasSize(8);
        assertThat(modelo.stream().filter(e -> e.getTipo().equals("COLUMNA")))
                .allSatisfy(e -> { assertThat(e.getLargo()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
                                   assertThat(e.getAncho()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9)); });
    }

    @Test
    void unaVigaQueNoCaeSobreNingunaColumnaSeRechazaConMensajeClaro(@TempDir Path dir) throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(plano(dir, 1000)); // 1 m de la columna
        assertThatThrownBy(() -> lector.fernandoHuancaConvertir(plano, datos()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("extremo de viga");
    }

    @Test
    void sugiereCapasPorNombreYEstimaLaUnidad(@TempDir Path dir) throws Exception {
        var plano = new FernandoHuancaPlanoDxf().fernandoHuancaLeer(plano(dir, 0));
        var mapeo = DeteccionCapasDxf.sugerir(plano);
        assertThat(mapeo.de(Rol.COLUMNAS)).isEqualTo("S-COLUMNAS");
        assertThat(mapeo.de(Rol.VIGAS)).isEqualTo("VIGAS 1");
        assertThat(mapeo.de(Rol.ZAPATAS)).isNull();
        var unidad = DeteccionCapasDxf.unidad(plano);
        assertThat(unidad.declarada()).isFalse();
        assertThat(unidad.metrosPorUnidad()).isEqualTo(0.001);
        var diag = DeteccionCapasDxf.diagnosticar(plano).texto();
        assertThat(diag).contains("S-COLUMNAS").contains("→ columnas").contains("→ vigas")
                .contains("TEXT").contains("no declara unidades").contains("círculos").contains("bloques");
    }

    @Test
    void reconoceNombresPeruanosConTildesYAbreviaturas() {
        assertThat(DeteccionCapasDxf.puntaje("COLUMNAS_ÁREA", Rol.COLUMNAS)).isGreaterThanOrEqualTo(2);
        assertThat(DeteccionCapasDxf.puntaje("A-Cimentación", Rol.ZAPATAS)).isGreaterThanOrEqualTo(2);
        assertThat(DeteccionCapasDxf.puntaje("Placas", Rol.MUROS)).isEqualTo(3);
        assertThat(DeteccionCapasDxf.puntaje("S-BEAM", Rol.VIGAS)).isEqualTo(3);
        assertThat(DeteccionCapasDxf.puntaje("TEXTOS", Rol.COLUMNAS)).isZero();
        assertThat(DeteccionCapasDxf.puntaje("COLINA", Rol.COLUMNAS)).isZero();
    }

    @Test
    void importaLosasYMurosPorPisoYElMetradoNetoNoDuplicaLosCruces(@TempDir Path dir) throws Exception {
        var lector = new FernandoHuancaPlanoDxf();
        var plano = lector.fernandoHuancaLeer(plano(dir, 0, true));
        var mapeo = DeteccionCapasDxf.sugerir(plano);
        assertThat(mapeo.de(Rol.LOSAS)).isEqualTo("LOSAS");
        assertThat(mapeo.de(Rol.MUROS)).isEqualTo("MUROS");
        var modelo = lector.fernandoHuancaConvertir(plano, datos(),
                new FernandoHuancaPlanoDxf.Extra("LOSAS", "MUROS", 0.20, 0.15));
        assertThat(modelo.stream().filter(e -> e.getTipo().equals("LOSA"))).hasSize(2);
        assertThat(modelo.stream().filter(e -> e.getTipo().equals("MURO"))).hasSize(2);
        var losa = modelo.stream().filter(e -> e.getTipo().equals("LOSA")).findFirst().orElseThrow();
        assertThat(losa.getLargo()).isCloseTo(5.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(losa.getAltura()).isEqualTo(0.20);
        assertThat(losa.getZ()).isCloseTo(3.0 - 0.10, org.assertj.core.data.Offset.offset(1e-9)); // cara superior a nivel de piso
        var muro = modelo.stream().filter(e -> e.getTipo().equals("MURO")).findFirst().orElseThrow();
        assertThat(muro.getAncho()).isEqualTo(4.0).isNotEqualTo(0.15); // línea paralela a Y: largo en Y
        assertThat(muro.getLargo()).isEqualTo(0.15);
        var neto = com.concretolab.service.MetradoNeto.calcular(modelo);
        assertThat(neto.volumenNeto()).isLessThan(neto.volumenBruto());
        assertThat(neto.filas()).extracting(f -> f.tipo()).contains("COLUMNA", "VIGA", "MURO", "LOSA");
        // sin capa de losas ni muros el resultado es el de siempre
        assertThat(lector.fernandoHuancaConvertir(plano, datos(), FernandoHuancaPlanoDxf.Extra.NINGUNA))
                .hasSize(lector.fernandoHuancaConvertir(plano, datos()).size());
        // espesor obligatorio
        assertThatThrownBy(() -> lector.fernandoHuancaConvertir(plano, datos(),
                new FernandoHuancaPlanoDxf.Extra("LOSAS", "", Double.NaN, Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("espesor de losa");
    }
}