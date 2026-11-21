package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;

import com.concretolab.service.DisenoMezclaACI;
import com.concretolab.service.DisenoMezclaACI.Entrada;
import com.concretolab.service.DisenoMezclaACI.Exposicion;
import com.concretolab.service.EvaluacionResistencia;
import com.concretolab.service.GranulometriaService;
import com.concretolab.service.HusosGranulometricos;
import com.concretolab.service.HusosGranulometricos.Huso;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisenoMezclaTest {

    private static Entrada base(double fc, Exposicion ex, boolean aire) {
        return new Entrada(fc, null, 0, 87.5, 19.0, 2.7, 1600.0, 3.15, 2.65, 2.65, 1.0, ex, aire,
                DisenoMezclaACI.SeveridadAire.MODERADA);
    }

    @Test
    void fcrSinRegistroEstadistico() {
        assertEquals(14.0 + 7.0, DisenoMezclaACI.fcr(14, null, 0, new StringBuilder())[0], 1e-9);
        assertEquals(28.0 + 8.5, DisenoMezclaACI.fcr(28, null, 10, new StringBuilder())[0], 1e-9);
        assertEquals(42.0 + 9.8, DisenoMezclaACI.fcr(42, null, 0, new StringBuilder())[0], 1e-9);
    }

    @Test
    void fcrConDesviacion() {
        // f'c 28, s = 3 MPa, 30 ensayos: max(28+4.02, 28+6.99-3.5=31.49) = 32.02
        assertEquals(32.02, DisenoMezclaACI.fcr(28, 3.0, 30, new StringBuilder())[0], 1e-9);
        // 20 ensayos: s corregida = 3*1.08 = 3.24 -> max(32.34, 28+7.549-3.5=32.05)
        assertEquals(28 + 1.34 * 3.24, DisenoMezclaACI.fcr(28, 3.0, 20, new StringBuilder())[0], 1e-9);
        // f'c > 35: 0,9 f'c + 2,33 s
        assertEquals(Math.max(40 + 1.34 * 3, 0.9 * 40 + 2.33 * 3), DisenoMezclaACI.fcr(40, 3.0, 30, new StringBuilder())[0], 1e-9);
    }

    @Test
    void ejemploFc28SinAire() {
        var r = DisenoMezclaACI.disenar(base(28, Exposicion.NORMAL, false));
        assertEquals(36.5, r.fcrMpa(), 1e-9);
        assertEquals(0.455, r.relacionAcResistencia(), 1e-9); // 0,47 - 0,05*(1,5/5)... interpolación 35->40
        assertEquals(205.0, r.agua(), 1e-9);
        assertEquals(2.0, r.airePorcentaje(), 1e-9);
        assertEquals(205.0 / 0.455, r.cemento(), 1e-6);
        assertEquals(0.63, r.volumenRelativoGrueso(), 1e-9);
        assertEquals(0.63 * 1600, r.gruesoSeco(), 1e-6);
        double suma = r.volumenAgua() + r.volumenCemento() + r.volumenGrueso() + r.volumenFino() + r.volumenAire();
        assertEquals(1.0, suma, 1e-9);
        assertTrue(r.finoSss() > 500 && r.finoSss() < 900, "fino " + r.finoSss());
    }

    @Test
    void durabilidadGobiernaLaRelacionAc() {
        var r = DisenoMezclaACI.disenar(base(28, Exposicion.AGUA_DE_MAR, false));
        assertEquals(0.40, r.relacionAc(), 1e-9);
        assertTrue(r.advertencias().stream().anyMatch(a -> a.contains("durabilidad")));
        assertTrue(r.advertencias().stream().anyMatch(a -> a.contains("f'c ≥ 35")));
    }

    @Test
    void congelamientoExigeAireIncorporado() {
        var r = DisenoMezclaACI.disenar(base(25, Exposicion.CONGELAMIENTO, false));
        assertEquals(5.0, r.airePorcentaje(), 1e-9); // moderada, TMA 19
        assertEquals(184.0, r.agua(), 1e-9);
    }

    @Test
    void fueraDeTablaSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> DisenoMezclaACI.disenar(base(60, Exposicion.NORMAL, false)));
        assertThrows(IllegalArgumentException.class, () -> DisenoMezclaACI.disenar(base(35, Exposicion.NORMAL, true)));
        assertThrows(IllegalArgumentException.class, () -> DisenoMezclaACI.disenar(base(-1, Exposicion.NORMAL, false)));
    }

    @Test
    void evaluacionDeResistencia() {
        var ok = EvaluacionResistencia.evaluar(21, List.of(23.0, 22.0, 21.5, 24.0));
        assertTrue(ok.conforme());
        var promBajo = EvaluacionResistencia.evaluar(21, List.of(21.0, 20.5, 20.0, 25.0));
        assertFalse(promBajo.cumplePromedios());
        var individual = EvaluacionResistencia.evaluar(21, List.of(30.0, 30.0, 17.0));
        assertFalse(individual.cumpleIndividuales());
        assertEquals(17.5, individual.limiteIndividual(), 1e-9);
        var pocos = EvaluacionResistencia.evaluar(21, List.of(25.0, 24.0));
        assertFalse(pocos.completo());
        assertFalse(pocos.conforme());
        assertEquals(0.9 * 40, EvaluacionResistencia.evaluar(40, List.of(41.0)).limiteIndividual(), 1e-9);
    }

    @Test
    void husoDelAgregadoFinoYModuloDeFinura() {
        // Arena típica: % retenido por tamiz sobre 500 g
        var r = GranulometriaService.calcular(500, List.of(
                new GranulometriaService.Tamiz(9.5, 0), new GranulometriaService.Tamiz(4.75, 25),
                new GranulometriaService.Tamiz(2.36, 70), new GranulometriaService.Tamiz(1.18, 85),
                new GranulometriaService.Tamiz(0.60, 100), new GranulometriaService.Tamiz(0.30, 120),
                new GranulometriaService.Tamiz(0.15, 75)), 25);
        var v = HusosGranulometricos.verificar(Huso.FINO, r.filas());
        assertTrue(v.cumple(), v.mensaje());
        // acumulados: 0, 5, 19, 36, 56, 80, 95 -> MF = (5+19+36+56+80+95)/100 = 2.91
        assertEquals(2.91, v.moduloFinura(), 1e-9);
        // una arena demasiado fina queda fuera
        var fina = GranulometriaService.calcular(500, List.of(
                new GranulometriaService.Tamiz(9.5, 0), new GranulometriaService.Tamiz(4.75, 0),
                new GranulometriaService.Tamiz(2.36, 5), new GranulometriaService.Tamiz(1.18, 10),
                new GranulometriaService.Tamiz(0.60, 20), new GranulometriaService.Tamiz(0.30, 100),
                new GranulometriaService.Tamiz(0.15, 250)), 115);
        assertFalse(HusosGranulometricos.verificar(Huso.FINO, fina.filas()).cumple());
    }
}
