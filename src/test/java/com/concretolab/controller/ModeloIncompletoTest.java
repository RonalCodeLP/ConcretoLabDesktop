package com.concretolab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.FernandoHuancaAnalisisSismico.CargaPiso;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Suelo;
import com.concretolab.service.NormaE030.Zona;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Un «edificio» cuyas vigas no llegan a las columnas no es un pórtico: el análisis no debe decir «CUMPLE» aunque las
 * columnas sueltas (muy gruesas) den derivas bajas. Caso reportado: columnas agrandadas y vigas convertidas en bloques.
 */
class ModeloIncompletoTest {

    private static long ids = 0;

    private static Elemento el(String tipo, String n, double largo, double ancho, double alto, double x, double y, double z) {
        Elemento e = new Elemento();
        e.setId(++ids); e.setProyectoId(1L); e.setTipo(tipo); e.setNombre(n);
        e.setLargo(largo); e.setAncho(ancho); e.setAltura(alto); e.setX(x); e.setY(y); e.setZ(z);
        return e;
    }

    /** Pórtico de 3 pisos, 3×2 columnas a 5 m × 4 m, como el escenario LAB. */
    static List<Elemento> portico(double lado) {
        List<Elemento> l = new ArrayList<>();
        double z = 0;
        for (int piso = 1; piso <= 3; piso++) {
            double h = 3.1;
            for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) {
                double x = (i - 1) * 5, y = (j - 0.5) * 4;
                l.add(el("COLUMNA", "C-" + piso + i + j, lado, lado, h, x, y, z + h / 2));
                if (i < 2) l.add(el("VIGA", "VX-" + piso + i + j, 5 - lado, 0.3, 0.5, x + 2.5, y, z + h - 0.25));
                if (j == 0) l.add(el("VIGA", "VY-" + piso + i + j, 0.3, 4 - lado, 0.5, x, 0, z + h - 0.25));
            }
            z += h;
        }
        return l;
    }

    private static FernandoHuancaAnalisisSismico.Resultado analizar(List<Elemento> l) {
        var base = FernandoHuancaAnalisisSismico.fernandoHuancaPisos(l);
        List<CargaPiso> cargas = new ArrayList<>();
        for (var b : base) cargas.add(new CargaPiso(b.areaPorDefecto(), 1.0, 2.0));
        return FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(new FernandoHuancaAnalisisSismico.Entrada(l, 21, 0.2, 1.0, 0.20, cargas,
                new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35)));
    }

    /** Las vigas como bloques de 1,1 × 1,1 (medida fija puesta a todas): ninguna llega de columna a columna. */
    private static List<Elemento> conVigasComoBloques(List<Elemento> l) {
        for (Elemento e : l) if (e.getTipo().equals("VIGA")) { e.setLargo(0.6); e.setAncho(0.6); }
        return l;
    }

    @Test
    void unPorticoBienArmadoNoTieneVigasSueltas() {
        var r = analizar(portico(0.45));
        assertThat(r.conexion().sueltas()).isEmpty();
        assertThat(r.conexion().formaPorticos()).isTrue();
        assertThat(GuiaSismica.walterQuispesayhuaModeloValido(r)).isTrue();
        assertThat(WalterQuispesayhuaSismoController.walterQuispesayhuaVeredicto(r)).isIn("CUMPLE (preliminar)", "NO CUMPLE");
        assertThat(GuiaSismica.walterQuispesayhuaVerificaciones(parametros(), r, null))
                .noneMatch(v -> v.texto().startsWith("Modelo incompleto"));
    }

    @Test
    void conLasVigasSueltasNuncaDiceCumpleAunqueLasColumnasGruesasDenDerivasBajas() {
        var r = analizar(conVigasComoBloques(portico(1.1)));
        // las columnas de 1,1 m solas sí dan derivas bajas: justamente el caso engañoso
        assertThat(r.estaticoX().cumple() && r.estaticoY().cumple() && r.espectralX().cumple() && r.espectralY().cumple()).isTrue();
        assertThat(r.conexion().sueltas()).hasSize(r.conexion().vigas());
        assertThat(r.conexion().pisosSinPortico()).containsExactly(1, 2, 3);
        assertThat(GuiaSismica.walterQuispesayhuaModeloValido(r)).isFalse();
        assertThat(WalterQuispesayhuaSismoController.walterQuispesayhuaVeredicto(r)).startsWith("MODELO INCOMPLETO");

        var v = GuiaSismica.walterQuispesayhuaVerificaciones(parametros(), r, null);
        assertThat(v.get(0).nivel()).isEqualTo(GuiaSismica.Nivel.ERROR);
        assertThat(v.get(0).texto()).startsWith("Modelo incompleto").contains("NO son válidas");
        assertThat(v.get(0).correccion()).isEqualTo(GuiaSismica.Correccion.MODELO);
        assertThat(v.get(0).consejo()).contains("«Largo eje X» es su longitud");
        // ninguna deriva aparece en verde
        assertThat(v.stream().filter(x -> x.texto().startsWith("Deriva")))
                .hasSize(4).allMatch(x -> x.nivel() == GuiaSismica.Nivel.ALERTA && x.texto().contains("no válida"));
    }

    @Test
    void unaVigaSueltaEntreVariasEsSoloUnaAlerta() {
        var l = portico(0.45);
        l.stream().filter(e -> e.getNombre().equals("VX-200")).findFirst().orElseThrow().setLargo(0.5);
        var r = analizar(l);
        assertThat(r.conexion().sueltas()).containsExactly("VX-200");
        assertThat(GuiaSismica.walterQuispesayhuaModeloValido(r)).isTrue();
        var v = GuiaSismica.walterQuispesayhuaVerificaciones(parametros(), r, null);
        assertThat(v).anyMatch(x -> x.nivel() == GuiaSismica.Nivel.ALERTA && x.texto().contains("VX-200") && x.correccion() == GuiaSismica.Correccion.MODELO);
    }

    @Test
    void laEdicionAvisaSoloDeLasVigasQueSeSueltanPorElCambio() {
        var actuales = portico(0.45);
        var vx = actuales.stream().filter(e -> e.getNombre().equals("VX-100")).findFirst().orElseThrow();
        var vy = actuales.stream().filter(e -> e.getNombre().equals("VY-100")).findFirst().orElseThrow();
        // medida fija «Largo X = 0,6» a una viga en X (se acorta) y a una en Y (solo se ensancha): se suelta solo la de X
        var cambiados = ModeloController.yefriLermaAplicarAjustes(List.of(vx, vy), ModeloController.yefriLermaLeerAjuste("0,6"), null, null);
        assertThat(ModeloController.yefriLermaVigasQueSeSueltan(actuales, cambiados)).containsExactly("VX-100");
        // agrandar columnas no suelta ninguna viga
        var columnas = actuales.stream().filter(e -> e.getTipo().equals("COLUMNA")).toList();
        var mas = ModeloController.yefriLermaAplicarAjustes(columnas, ModeloController.yefriLermaLeerAjuste("+0,3"), ModeloController.yefriLermaLeerAjuste("+0,3"), null);
        assertThat(ModeloController.yefriLermaVigasQueSeSueltan(actuales, mas)).isEmpty();
        // proyecto sin vigas: nada que avisar
        assertThat(ModeloController.yefriLermaVigasQueSeSueltan(columnas, List.of())).isEmpty();
    }

    private static Parametros parametros() {
        return new Parametros(Zona.Z4, Categoria.C, Suelo.S2, 8, 1, 1, 35);
    }
}
