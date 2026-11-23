package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.concretolab.model.Elemento;
import com.concretolab.service.MetradoNeto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetradoNetoTest {

    private static long ids = 0;

    private static Elemento el(String tipo, double x, double y, double z, double l, double a, double h) {
        var e = new Elemento();
        e.setId(++ids); e.setProyectoId(1L); e.setTipo(tipo); e.setNombre(tipo + ids);
        e.setX(x); e.setY(y); e.setZ(z); e.setLargo(l); e.setAncho(a); e.setAltura(h);
        return e;
    }

    @Test
    void descuentaLosCrucesUnaSolaVezSegunPrioridad() {
        var columna = el("COLUMNA", 0, 0, 1.5, 0.4, 0.4, 3.0);
        var viga = el("VIGA", 1.0, 0, 2.75, 2.4, 0.3, 0.5);
        var losa = el("LOSA", 1.0, 1.0, 2.9, 4.0, 4.0, 0.2);
        // la losa se pasa primero en la lista: la prioridad la fija el tipo, no el orden
        var r = MetradoNeto.yefriLermaCalcular(List.of(losa, viga, columna));
        assertThat(r.volumenBruto()).isCloseTo(0.48 + 0.36 + 3.2, within(1e-9));
        assertThat(r.netoPorElemento().get(columna.getId())).isCloseTo(0.48, within(1e-9));
        assertThat(r.netoPorElemento().get(viga.getId())).isCloseTo(0.36 - 0.06, within(1e-9));
        // losa: 3.2 - (columna 0.032 + viga 0.144 - cruce triple 0.024)
        assertThat(r.netoPorElemento().get(losa.getId())).isCloseTo(3.2 - 0.152, within(1e-9));
        // la suma neta es el volumen de la unión
        assertThat(r.volumenNeto()).isCloseTo(0.48 + 0.30 + 3.048, within(1e-9));
    }

    @Test
    void doblesIdenticosSeCuentanUnaVez() {
        var a = el("VIGA", 0, 0, 0, 1, 1, 1);
        var b = el("VIGA", 0, 0, 0, 1, 1, 1);
        var r = MetradoNeto.yefriLermaCalcular(List.of(a, b));
        assertThat(r.volumenBruto()).isCloseTo(2.0, within(1e-12));
        assertThat(r.volumenNeto()).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void elementosSeparadosNoSeDescuentanYElEncofradoYElAceroSeEstiman() {
        var col = el("COLUMNA", 0, 0, 1.5, 0.5, 0.5, 3.0);
        var losa = el("LOSA", 10, 10, 2.9, 4, 5, 0.2);
        var r = MetradoNeto.yefriLermaCalcular(List.of(col, losa));
        assertThat(r.descontado()).isZero();
        assertThat(r.encofradoM2()).isCloseTo(2 * (0.5 + 0.5) * 3.0 + 4 * 5, within(1e-9));
        assertThat(r.aceroKg()).isCloseTo(0.75 * 150 + 4.0 * 80, within(1e-9));
    }
}