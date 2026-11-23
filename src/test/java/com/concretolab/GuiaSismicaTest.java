package com.concretolab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.concretolab.controller.GuiaSismica;
import com.concretolab.controller.GuiaSismica.Nivel;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Suelo;
import com.concretolab.service.NormaE030.Version;
import com.concretolab.service.NormaE030.Zona;
import com.concretolab.service.SistemaEstructural;
import com.concretolab.service.TipoSuelo;
import com.concretolab.service.UsoEdificacion;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Logica no grafica de la pantalla sismica guiada (no necesita JavaFX). */
class GuiaSismicaTest {

    @Test
    void tabla9PorticosNoPermitidosEnCategoriaAFueraDeZona1() {
        assertFalse(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.A, Zona.Z4));
        assertFalse(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.A, Zona.Z3));
        assertFalse(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.A, Zona.Z2));
        assertTrue(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.A, Zona.Z1));
        for (Zona z : Zona.values()) {
            assertTrue(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.B, z));
            assertTrue(GuiaSismica.walterQuispesayhuaPorticosPermitidos(Categoria.C, z));
        }
        assertNull(GuiaSismica.walterQuispesayhuaAvisoTabla9(Categoria.C, Zona.Z4, Version.V2026));
        assertTrue(GuiaSismica.walterQuispesayhuaAvisoTabla9(Categoria.A, Zona.Z4, Version.V2026).contains("Tabla 9"));
    }

    @Test
    void zonaSeConvierteDesdeElNumeroDelAnexo() {
        assertEquals(Zona.Z1, GuiaSismica.walterQuispesayhuaZonaDe(1));
        assertEquals(Zona.Z4, GuiaSismica.walterQuispesayhuaZonaDe(4));
        assertEquals(3, GuiaSismica.walterQuispesayhuaNumeroZona(Zona.Z3));
        assertThrows(IllegalArgumentException.class, () -> GuiaSismica.walterQuispesayhuaZonaDe(5));
    }

    @Test
    void parametrosSeArmanDesdeLasCuatroElecciones() {
        Parametros p = GuiaSismica.walterQuispesayhuaParametros(Version.V2026, Zona.Z3, UsoEdificacion.VIVIENDA, TipoSuelo.INTERMEDIO,
                SistemaEstructural.PORTICOS, 1.0, 0.9, null, 250);
        assertEquals(Categoria.C, p.categoria());
        assertEquals(Suelo.S3, p.suelo());
        assertEquals(8, p.r0());
        assertEquals(35, p.ct());
        assertEquals(250, p.vs30());
        assertEquals(0.9, p.ip());
        // En 2018 el suelo se traduce al perfil de esa version y Vs30 se ignora
        Parametros p18 = GuiaSismica.walterQuispesayhuaParametros(Version.V2018, Zona.Z3, UsoEdificacion.EDUCATIVO, TipoSuelo.INTERMEDIO,
                SistemaEstructural.PORTICOS, 1.0, 1.0, 40.0, 250);
        assertEquals(Suelo.S2_2018, p18.suelo());
        assertTrue(Double.isNaN(p18.vs30()));
        assertEquals(Categoria.A, p18.categoria());
        assertEquals(40, p18.ct());
        // Vs30 no aplica a suelos que no interpolan
        assertTrue(Double.isNaN(GuiaSismica.walterQuispesayhuaParametros(Version.V2026, Zona.Z2, UsoEdificacion.OFICINAS, TipoSuelo.MUY_RIGIDO,
                SistemaEstructural.PORTICOS, 1, 1, null, 600).vs30()));
    }

    @Test
    void cargasSeprecargaPorUsoConTechoDistinto() {
        double[][] c = GuiaSismica.walterQuispesayhuaCargasPorUso(UsoEdificacion.OFICINAS, 3);
        assertEquals(3, c.length);
        for (int i = 0; i < 3; i++) assertEquals(UsoEdificacion.OFICINAS.cargaMuertaAdicionalSugerida, c[i][0]);
        assertEquals(UsoEdificacion.OFICINAS.cvPisos, c[0][1]);
        assertEquals(UsoEdificacion.OFICINAS.cvPisos, c[1][1]);
        assertEquals(UsoEdificacion.OFICINAS.cvTecho, c[2][1]);
        assertEquals(UsoEdificacion.VIVIENDA.cvTecho, GuiaSismica.walterQuispesayhuaCargasPorUso(UsoEdificacion.VIVIENDA, 1)[0][1]);
        assertEquals(0, GuiaSismica.walterQuispesayhuaCargasPorUso(UsoEdificacion.VIVIENDA, 0).length);
    }

    @Test
    void faltantesListaLoQueNoSeEligio() {
        assertEquals(5, GuiaSismica.walterQuispesayhuaFaltantes(null, null, null, null, false).size());
        assertEquals(1, GuiaSismica.walterQuispesayhuaFaltantes(Zona.Z4, UsoEdificacion.VIVIENDA, TipoSuelo.ROCA, SistemaEstructural.DUAL, true).size());
        assertTrue(GuiaSismica.walterQuispesayhuaFaltantes(Zona.Z4, UsoEdificacion.VIVIENDA, TipoSuelo.ROCA, SistemaEstructural.PORTICOS, true).isEmpty());
    }

    @Test
    void verificacionesIncluyenJuntaYTabla9() {
        var el = SismoE030Test.porticos(3, 4, 4, 3, 0.5, 0.3, 0.5);
        Parametros norma = GuiaSismica.walterQuispesayhuaParametros(Version.V2026, Zona.Z4, UsoEdificacion.EDUCATIVO, TipoSuelo.RIGIDO,
                SistemaEstructural.PORTICOS, 1, 1, null, Double.NaN);
        var in = SismoE030Test.entrada(el, 3, norma);
        var r = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(in);
        var v = GuiaSismica.walterQuispesayhuaVerificaciones(norma, r, UsoEdificacion.EDUCATIVO);
        assertNotNull(v);
        assertTrue(v.stream().anyMatch(x -> x.nivel() == Nivel.INFO && x.texto().contains("Junta sísmica")));
        assertTrue(v.stream().anyMatch(x -> x.nivel() == Nivel.ERROR && x.texto().contains("Tabla 9")));
        assertEquals(4, v.stream().filter(x -> x.texto().startsWith("Deriva")).count());
        // Categoria C: Tabla 9 en verde
        Parametros c = GuiaSismica.walterQuispesayhuaParametros(Version.V2026, Zona.Z4, UsoEdificacion.VIVIENDA, TipoSuelo.RIGIDO,
                SistemaEstructural.PORTICOS, 1, 1, null, Double.NaN);
        var vc = GuiaSismica.walterQuispesayhuaVerificaciones(c, FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(SismoE030Test.entrada(el, 3, c)), UsoEdificacion.VIVIENDA);
        assertTrue(vc.stream().noneMatch(x -> x.texto().contains("Tabla 9")));
        assertTrue(vc.stream().anyMatch(x -> x.nivel() == Nivel.OK && x.texto().startsWith("Sistema estructural")));
    }

    @Test void categoriaA1NoPermitePorticosEnNingunaZona() {
        for (var z : com.concretolab.service.NormaE030.Zona.values())
            assertFalse(GuiaSismica.walterQuispesayhuaPorticosPermitidos(com.concretolab.service.NormaE030.Categoria.A, z, true), z.name());
        assertTrue(GuiaSismica.walterQuispesayhuaPorticosPermitidos(com.concretolab.service.NormaE030.Categoria.A, com.concretolab.service.NormaE030.Zona.Z1, false)); // A2 en zona 1: cualquier sistema
        assertNotNull(GuiaSismica.walterQuispesayhuaAvisoTabla9(com.concretolab.service.NormaE030.Categoria.A, com.concretolab.service.NormaE030.Zona.Z1, com.concretolab.service.NormaE030.Version.V2026, true));
        assertNull(GuiaSismica.walterQuispesayhuaAvisoTabla9(com.concretolab.service.NormaE030.Categoria.A, com.concretolab.service.NormaE030.Zona.Z1, com.concretolab.service.NormaE030.Version.V2026, false));
    }
}
