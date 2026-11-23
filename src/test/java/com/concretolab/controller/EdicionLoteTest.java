package com.concretolab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.model.Elemento;
import com.concretolab.service.RecomendacionSismica;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edición de varios elementos a la vez en «Modelo 3D y metrado». */
class EdicionLoteTest {

    private static Elemento col(long id, double largo, double ancho) {
        var e = new Elemento();
        e.setId(id); e.setProyectoId(1L); e.setTipo("COLUMNA"); e.setNombre("C" + id);
        e.setLargo(largo); e.setAncho(ancho); e.setAltura(3.0); e.setX(id); e.setY(0); e.setZ(1.5);
        return e;
    }

    @Test
    void leeVacioMedidaFijaEIncrementos() {
        assertThat(ModeloController.yefriLermaLeerAjuste("  ")).isNull();
        assertThat(ModeloController.yefriLermaLeerAjuste("0,50")).isEqualTo(new ModeloController.YefriLermaAjuste(false, 0.5));
        assertThat(ModeloController.yefriLermaLeerAjuste("+0,05")).isEqualTo(new ModeloController.YefriLermaAjuste(true, 0.05));
        assertThat(ModeloController.yefriLermaLeerAjuste("-0.10")).isEqualTo(new ModeloController.YefriLermaAjuste(true, -0.10));
        assertThatThrownBy(() -> ModeloController.yefriLermaLeerAjuste("abc")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void aplicaSoloLosCamposLlenosSinTocarLosOriginales() {
        var a = col(1, 0.40, 0.40);
        var b = col(2, 0.30, 0.60);
        var r = ModeloController.yefriLermaAplicarAjustes(List.of(a, b), null,
                ModeloController.yefriLermaLeerAjuste("+0,05"), ModeloController.yefriLermaLeerAjuste("3,2"));
        assertThat(r).extracting(Elemento::getLargo).containsExactly(0.40, 0.30);
        assertThat(r).extracting(Elemento::getAncho).containsExactly(0.45, 0.65);
        assertThat(r).extracting(Elemento::getAltura).containsExactly(3.2, 3.2);
        assertThat(r).extracting(Elemento::getId).containsExactly(1L, 2L);
        assertThat(a.getAncho()).isEqualTo(0.40);
        assertThatThrownBy(() -> ModeloController.yefriLermaAplicarAjustes(List.of(a), ModeloController.yefriLermaLeerAjuste("-0,40"), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("C1");
    }

    @Test
    void laRecomendacionSePrecargaComoMedidaFijaOComoIncremento() {
        var a = col(1, 0.40, 0.40);
        var b = col(2, 0.40, 0.40);
        var iguales = List.of(new RecomendacionSismica.Cambio(a, 0.40, 0.50), new RecomendacionSismica.Cambio(b, 0.40, 0.50));
        assertThat(ModeloController.yefriLermaTextoCambio(iguales, List.of(a, b), false)).isEqualTo("0,5");
        assertThat(ModeloController.yefriLermaTextoCambio(iguales, List.of(a, b), true)).isEmpty();

        var c = col(3, 0.40, 0.30);
        var distintas = List.of(new RecomendacionSismica.Cambio(a, 0.40, 0.50), new RecomendacionSismica.Cambio(c, 0.40, 0.40));
        assertThat(ModeloController.yefriLermaTextoCambio(distintas, List.of(a, c), false)).isEqualTo("+0,1");
    }

    @Test
    void describeLaSeleccion() {
        String t = ModeloController.yefriLermaDescribirLote(List.of(col(1, 0.4, 0.4), col(2, 0.4, 0.4)));
        assertThat(t).contains("2 columnas").contains("40×40×300");
    }
}
