package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.concretolab.model.Proyecto;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Resultado;
import com.concretolab.service.GeneradorAcelerograma;
import com.concretolab.service.ReporteSismo;
import com.concretolab.service.ResumenTiempoHistoria;
import com.concretolab.ui.ResumenSimulacion;
import com.concretolab.ui.ResumenSimulacion.NivelDeriva;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Lógica pura de la pestaña «Simulación temporal» (sin JavaFX ni Spring) y su sección del reporte PDF. */
class TiempoHistoriaPestanaTest {

    private static FernandoHuancaAnalisisSismico.Entrada entrada;
    private static Resultado resultado;
    private static ResumenTiempoHistoria resumen;

    @BeforeAll
    static void preparar() {
        var norma = SismoE030Test.norma(1, 1);
        entrada = SismoE030Test.entrada(SismoE030Test.porticos(3, 5, 4, 3, 0.4, 0.3, 0.5), 3, norma);
        resultado = FernandoHuancaAnalisisSismico.analizar(entrada);
        double t = ResumenSimulacion.periodoFundamental(resultado.espectralX().periodo(), resultado.espectralY().periodo());
        var registros = GeneradorAcelerograma.generarConjuntos(norma, 3, t, 2026);
        List<Double> avance = new ArrayList<>();
        resumen = ResumenSimulacion.simular(resultado.modelo(), norma, registros, true, t, "Sintéticos de prueba", avance::add);
        assertThat(avance).isNotEmpty().isSorted();
        assertThat(avance.get(avance.size() - 1)).isEqualTo(1.0);
    }

    @Test
    void construyeElResumenConUnResultadoPorRegistro() {
        assertThat(resumen.resultados()).hasSize(3);
        assertThat(resumen.escalado().escalados()).hasSize(3);
        assertThat(resumen.verificacion().conjuntos()).isEqualTo(3);
        assertThat(resumen.periodoFundamental()).isGreaterThan(0);
        var filas = resumen.filasTabla();
        assertThat(filas).hasSize(5); // 3 registros + promedio/máximo + límite
        assertThat(filas.get(0)).hasSize(ResumenTiempoHistoria.CABECERA.length);
        assertThat(filas.get(3)[0]).isIn("Promedio", "Máximo");
        assertThat(filas.get(4)[0]).startsWith("Límite 1,25×0,007 = 0,00875");
        // con 3 conjuntos en el texto 2026 el resultado no es concluyente
        assertThat(resumen.veredicto()).isEqualTo("NO CONCLUYENTE");
        assertThat(resumen.pgaG(0)).isGreaterThan(0.01).isLessThan(5);
    }

    @Test
    void elSueloSeMueveYLaEstructuraSeMideEnAbsoluto() {
        var r = resumen.resultados().get(0);
        assertThat(ResumenSimulacion.desplazamientoAbsolutoMaximo(r, 0)).isGreaterThan(0);
        double maxSuelo = 0;
        for (double v : r.sueloX()) maxSuelo = Math.max(maxSuelo, Math.abs(v));
        assertThat(maxSuelo).as("el terreno tiene desplazamiento").isGreaterThan(1e-4);
        int paso = r.pasos() / 3;
        assertThat(ResumenSimulacion.absoluto(r, 0, paso, 0)).isEqualTo(r.ux()[paso][0] + r.sueloX()[paso], within(1e-12));
    }

    @Test
    void decimaConservandoElMaximoAbsolutoYElLimiteDePuntos() {
        int n = 50_000;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i * 0.01;
            y[i] = Math.sin(i * 0.05) * 0.3;
        }
        y[31_337] = -7.5; // pico negativo aislado
        var d = ResumenSimulacion.decimar(x, y, ResumenSimulacion.PUNTOS_MAXIMOS);
        assertThat(d[0].length).isLessThanOrEqualTo(2000).isEqualTo(d[1].length);
        double max = 0;
        for (double v : d[1]) max = Math.max(max, Math.abs(v));
        assertThat(max).isEqualTo(7.5);
        assertThat(d[0][0]).isEqualTo(x[0]);
        assertThat(d[0][d[0].length - 1]).isEqualTo(x[n - 1]);
        for (int i = 1; i < d[0].length; i++) assertThat(d[0][i]).isGreaterThan(d[0][i - 1]);
        // una serie corta no se modifica
        assertThat(ResumenSimulacion.decimar(new double[]{0, 1, 2}, new double[]{1, 2, 3}, 2000)[1]).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> ResumenSimulacion.decimar(new double[1], new double[2], 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laEscalaVisualAutomaticaLlevaElMayorDesplazamientoAl17PorCientoDeLaAltura() {
        double altura = 12.0, maxDesp = 0.04;
        double f = ResumenSimulacion.factorVisual(maxDesp, altura);
        assertThat(f * maxDesp / altura).isEqualTo(0.175, within(1e-12));
        assertThat(f).isBetween(0.15 * altura / maxDesp, 0.20 * altura / maxDesp);
        assertThat(ResumenSimulacion.factorVisual(0, altura)).isEqualTo(1.0);
        assertThat(ResumenSimulacion.factorVisual(0.04, 0)).isEqualTo(1.0);
        var r = resumen.resultados().get(0);
        double fr = ResumenSimulacion.factorVisual(ResumenSimulacion.desplazamientoAbsolutoMaximo(r, 0), resultado.modelo().cotas()[2]);
        assertThat(fr).isGreaterThan(1);
    }

    @Test
    void elColorPorDerivaRespetaLosUmbrales() {
        double lim = ResumenSimulacion.LIMITE_DERIVA;
        assertThat(lim).isEqualTo(0.00875, within(1e-12));
        assertThat(ResumenSimulacion.nivel(0)).isEqualTo(NivelDeriva.BAJO);
        assertThat(ResumenSimulacion.nivel(0.6 * lim)).isEqualTo(NivelDeriva.BAJO);
        assertThat(ResumenSimulacion.nivel(0.61 * lim)).isEqualTo(NivelDeriva.ALERTA);
        assertThat(ResumenSimulacion.nivel(lim)).isEqualTo(NivelDeriva.ALERTA);
        assertThat(ResumenSimulacion.nivel(1.01 * lim)).isEqualTo(NivelDeriva.EXCEDE);
        assertThat(ResumenSimulacion.nivel(-2 * lim)).isEqualTo(NivelDeriva.EXCEDE); // usa el valor absoluto
    }

    @Test
    void seleccionaElMayorPeriodoFundamental() {
        assertThat(ResumenSimulacion.periodoFundamental(0.42, 0.55)).isEqualTo(0.55);
        assertThat(ResumenSimulacion.periodoFundamental(0.80, 0.55)).isEqualTo(0.80);
        assertThatThrownBy(() -> ResumenSimulacion.periodoFundamental(0, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResumenSimulacion.periodoFundamental(Double.NaN, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elReportePdfSeGeneraConYSinTiempoHistoria() {
        Proyecto p = new Proyecto();
        p.setNombre("Edificio de prueba");
        byte[] sin = ReporteSismo.generar(p, entrada, resultado);
        byte[] con = ReporteSismo.generar(p, entrada, resultado, resumen);
        byte[] nulo = ReporteSismo.generar(p, entrada, resultado, null);
        assertThat(new String(con, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(sin, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(con.length).isGreaterThan(sin.length);
        assertThat(nulo.length).isEqualTo(sin.length);
    }
}
