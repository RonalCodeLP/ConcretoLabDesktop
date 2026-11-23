package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;

import com.concretolab.controller.GuiaSismica;
import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.NormaE030;
import com.concretolab.service.RecomendacionSismica;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Botón «Cómo corregir»: la medida recomendada sale de volver a analizar con columnas más grandes. */
class RecomendacionSismicaTest {

    private static final double LIM = NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO;

    /** Pórtico de 4 pisos con columnas delgadas en Y (0,80 en X × 0,40 en Y): falla solo la deriva en Y. */
    private static List<Elemento> modeloFlexibleEnY() {
        var l = SismoE030Test.porticos(4, 5, 5, 3.0, 0.40, 0.30, 0.60);
        long id = 1;
        for (Elemento e : l) {
            e.setId(id++);
            if ("COLUMNA".equals(e.getTipo())) { e.setLargo(0.80); e.setAncho(0.40); }
        }
        return l;
    }

    @Test
    void recomiendaElMenorAumentoQueHaceCumplirLaDerivaYSoloEnLaDireccionQueFalla() {
        var entrada = SismoE030Test.entrada(modeloFlexibleEnY(), 4, SismoE030Test.norma(1, 1));
        var r = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(entrada);
        assertTrue(RecomendacionSismica.fernandoHuancaDerivaY(r) > LIM, "el modelo de prueba debe fallar en Y");
        assertTrue(RecomendacionSismica.fernandoHuancaDerivaX(r) <= LIM, "y cumplir en X");

        var rec = RecomendacionSismica.fernandoHuancaRecomendar(entrada, r);
        assertTrue(rec.necesaria());
        assertTrue(rec.suficiente(), rec.texto());
        assertEquals(0, rec.aumentoX(), 1e-12, "X ya cumple: no se toca");
        assertEquals(0.10, rec.aumentoY(), 1e-9); // 0,45 todavía falla (0,0075); 0,50 cumple (0,0069)
        assertTrue(rec.derivaYDespues() <= LIM, rec.texto());
        assertEquals(4, rec.cambios().size()); // las 4 columnas
        for (var c : rec.cambios()) {
            assertEquals(0.80, c.largoNuevo(), 1e-9);
            assertEquals(0.40 + rec.aumentoY(), c.anchoNuevo(), 1e-9);
            assertNotNull(c.elemento().getId());
        }
        // es el mínimo: 5 cm menos todavía falla
        if (rec.aumentoY() > RecomendacionSismica.FERNANDO_HUANCA_PASO) {
            var menos = modeloFlexibleEnY();
            menos.stream().filter(e -> "COLUMNA".equals(e.getTipo()))
                    .forEach(e -> e.setAncho(0.40 + rec.aumentoY() - RecomendacionSismica.FERNANDO_HUANCA_PASO));
            var rMenos = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(SismoE030Test.entrada(menos, 4, SismoE030Test.norma(1, 1)));
            assertTrue(RecomendacionSismica.fernandoHuancaDerivaY(rMenos) > LIM);
        }
        // los elementos originales no se modifican
        assertTrue(entrada.elementos().stream().filter(e -> "COLUMNA".equals(e.getTipo())).allMatch(e -> e.getAncho() == 0.40));
        assertTrue(rec.texto().contains("Ancho eje Y"), rec.texto());
    }

    @Test
    void sinFallasNoRecomiendaCambios() {
        var l = SismoE030Test.porticos(2, 5, 5, 3.0, 0.70, 0.30, 0.60);
        var entrada = SismoE030Test.entrada(l, 2, SismoE030Test.norma(1, 1));
        var r = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(entrada);
        var rec = RecomendacionSismica.fernandoHuancaRecomendar(entrada, r);
        assertFalse(rec.necesaria());
        assertTrue(rec.cambios().isEmpty());
    }

    @Test
    void lasDerivasQueNoCumplenOfrecenCorreccionEnElModelo() {
        var entrada = SismoE030Test.entrada(modeloFlexibleEnY(), 4, SismoE030Test.norma(1, 1));
        var r = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(entrada);
        var v = GuiaSismica.walterQuispesayhuaVerificaciones(entrada.norma(), r, null);
        assertTrue(v.stream().filter(x -> x.texto().startsWith("Deriva") && x.nivel() == GuiaSismica.Nivel.ERROR)
                .allMatch(x -> x.correccion() == GuiaSismica.Correccion.DERIVA && x.consejo().contains("Y")));
        assertTrue(v.stream().anyMatch(x -> x.correccion() == GuiaSismica.Correccion.DERIVA));
        // las que cumplen no llevan botón
        assertTrue(v.stream().filter(x -> x.nivel() == GuiaSismica.Nivel.OK).allMatch(x -> x.correccion() == GuiaSismica.Correccion.NINGUNA));
    }
}
