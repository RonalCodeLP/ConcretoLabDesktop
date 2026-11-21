package com.concretolab;

import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Suelo;
import com.concretolab.service.NormaE030.Version;
import com.concretolab.service.SistemaEstructural;
import com.concretolab.service.TipoSuelo;
import com.concretolab.service.UsoEdificacion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerfilesNormaTest {

    @Test
    void sueloMapeaAPerfilesDeCadaVersion() {
        assertThat(TipoSuelo.ROCA.suelo2026()).isEqualTo(Suelo.S0);
        assertThat(TipoSuelo.MUY_RIGIDO.suelo2026()).isEqualTo(Suelo.S1);
        assertThat(TipoSuelo.RIGIDO.suelo2026()).isEqualTo(Suelo.S2);
        assertThat(TipoSuelo.INTERMEDIO.suelo2026()).isEqualTo(Suelo.S3);
        assertThat(TipoSuelo.BLANDO.suelo2026()).isEqualTo(Suelo.S4);
        assertThat(TipoSuelo.ROCA.suelo2018()).isEqualTo(Suelo.S1_2018);
        assertThat(TipoSuelo.MUY_RIGIDO.suelo2018()).isEqualTo(Suelo.S1_2018);
        assertThat(TipoSuelo.RIGIDO.suelo2018()).isEqualTo(Suelo.S2_2018);
        assertThat(TipoSuelo.INTERMEDIO.suelo2018()).isEqualTo(Suelo.S2_2018);
        assertThat(TipoSuelo.BLANDO.suelo2018()).isEqualTo(Suelo.S3_2018);
        for (TipoSuelo t : TipoSuelo.values()) {
            assertThat(t.suelo2026().version).isEqualTo(Version.V2026);
            assertThat(t.suelo2018().version).isEqualTo(Version.V2018);
            assertThat(t.suelo(Version.V2026)).isEqualTo(t.suelo2026());
            assertThat(t.suelo(Version.V2018)).isEqualTo(t.suelo2018());
        }
    }

    @Test
    void rangosVs30SonLosDeLaTabla3() {
        assertThat(TipoSuelo.ROCA.vs30Min()).isEqualTo(800);
        assertThat(TipoSuelo.MUY_RIGIDO.vs30Min()).isEqualTo(550);
        assertThat(TipoSuelo.MUY_RIGIDO.vs30Max()).isEqualTo(800);
        assertThat(TipoSuelo.RIGIDO.vs30Min()).isEqualTo(350);
        assertThat(TipoSuelo.RIGIDO.vs30Max()).isEqualTo(550);
        assertThat(TipoSuelo.INTERMEDIO.vs30Min()).isEqualTo(200);
        assertThat(TipoSuelo.INTERMEDIO.vs30Max()).isEqualTo(350);
        assertThat(TipoSuelo.BLANDO.vs30Max()).isEqualTo(200);
    }

    @Test
    void noSeUsaPerfilConservadorConAviso() {
        assertThat(TipoSuelo.NO_SE.esSuposicion()).isTrue();
        assertThat(TipoSuelo.NO_SE.aviso).contains("16.1");
        assertThat(TipoSuelo.NO_SE.suelo2026()).isEqualTo(Suelo.S3);
        assertThat(TipoSuelo.NO_SE.suelo2018()).isEqualTo(Suelo.S3_2018);
        assertThat(Double.isNaN(TipoSuelo.NO_SE.vs30Min())).isTrue();
        // tabulado en las cuatro zonas (S4 en zona 4 no lo está)
        for (NormaE030.Zona z : NormaE030.Zona.values()) {
            assertThat(TipoSuelo.NO_SE.suelo2026().s(z, Double.NaN)).isGreaterThan(0);
            assertThat(TipoSuelo.NO_SE.suelo2018().s(z, Double.NaN)).isGreaterThan(0);
        }
    }

    @Test
    void r0PorVersion() {
        assertThat(SistemaEstructural.PORTICOS.r0(Version.V2026)).isEqualTo(8);
        assertThat(SistemaEstructural.DUAL.r0(Version.V2026)).isEqualTo(7);
        assertThat(SistemaEstructural.MUROS.r0(Version.V2026)).isEqualTo(6);
        assertThat(SistemaEstructural.MUROS_DUCTILIDAD_LIMITADA.r0(Version.V2026)).isEqualTo(3.5);
        assertThat(SistemaEstructural.ALBANILERIA.r0(Version.V2026)).isEqualTo(3);
        assertThat(SistemaEstructural.MUROS_DUCTILIDAD_LIMITADA.r0(Version.V2018)).isEqualTo(4);
        assertThat(SistemaEstructural.PORTICOS.r0(Version.V2018)).isEqualTo(8);
        assertThat(SistemaEstructural.DUAL.r0(Version.V2018)).isEqualTo(7);
        assertThat(SistemaEstructural.MUROS.r0(Version.V2018)).isEqualTo(6);
        assertThat(SistemaEstructural.ALBANILERIA.r0(Version.V2018)).isEqualTo(3);
    }

    @Test
    void ctYModelable() {
        assertThat(SistemaEstructural.PORTICOS.ct()).isEqualTo(35);
        assertThat(SistemaEstructural.DUAL.ct()).isEqualTo(60);
        assertThat(SistemaEstructural.MUROS.ct()).isEqualTo(60);
        assertThat(SistemaEstructural.MUROS_DUCTILIDAD_LIMITADA.ct()).isEqualTo(60);
        assertThat(SistemaEstructural.ALBANILERIA.ct()).isEqualTo(60);
        for (SistemaEstructural s : SistemaEstructural.values())
            assertThat(s.modelable()).isEqualTo(s == SistemaEstructural.PORTICOS);
    }

    @Test
    void usoDevuelveCategoriaDeLaTabla7() {
        assertThat(UsoEdificacion.VIVIENDA.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.OFICINAS.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.HOTEL.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.RESTAURANTE.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.INDUSTRIAL_COMUN.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.DEPOSITO.categoria()).isEqualTo(Categoria.C);
        assertThat(UsoEdificacion.COMERCIAL_ASAMBLEA.categoria()).isEqualTo(Categoria.B);
        assertThat(UsoEdificacion.EDUCATIVO.categoria()).isEqualTo(Categoria.A);
        assertThat(UsoEdificacion.SALUD_PRIMER_NIVEL.categoria()).isEqualTo(Categoria.A);
        assertThat(UsoEdificacion.HOSPITAL.categoria()).isEqualTo(Categoria.A);
        assertThat(UsoEdificacion.ESENCIALES.categoria()).isEqualTo(Categoria.A);
        for (UsoEdificacion u : UsoEdificacion.values())
            assertThat(u.requiereAislamientoZ3Z4()).isEqualTo(u == UsoEdificacion.HOSPITAL);
    }

    @Test
    void factoresDeUsoPorCategoria() {
        assertThat(Categoria.A.u).isEqualTo(1.5);
        assertThat(Categoria.B.u).isEqualTo(1.3);
        assertThat(Categoria.C.u).isEqualTo(1.0);
        assertThat(UsoEdificacion.HOSPITAL.categoria().u).isEqualTo(1.5);
        assertThat(UsoEdificacion.EDUCATIVO.categoria().u).isEqualTo(1.5);
        assertThat(UsoEdificacion.COMERCIAL_ASAMBLEA.categoria().u).isEqualTo(1.3);
        assertThat(UsoEdificacion.VIVIENDA.categoria().u).isEqualTo(1.0);
    }

    @Test
    void cargasNoNegativasYNoVerificadasIdentificables() {
        for (UsoEdificacion u : UsoEdificacion.values()) {
            assertThat(u.cvPisos).isGreaterThanOrEqualTo(0);
            assertThat(u.cvCorredoresEscaleras).isGreaterThanOrEqualTo(0);
            assertThat(u.cvTecho).isGreaterThanOrEqualTo(0);
            assertThat(u.cargaMuertaAdicionalSugerida).isGreaterThanOrEqualTo(0);
            assertThat(u.fuente).contains("E.0");
            assertThat(u.fuente.startsWith(u.verificado ? "VERIFICADO" : "NO VERIFICADO")).isTrue();
        }
        assertThat(UsoEdificacion.VIVIENDA.verificado).isTrue();
        assertThat(UsoEdificacion.VIVIENDA.cvPisos).isEqualTo(2.0);
        assertThat(UsoEdificacion.OFICINAS.cvPisos).isEqualTo(2.5);
        assertThat(UsoEdificacion.EDUCATIVO.cvPisos).isEqualTo(2.5);
        assertThat(UsoEdificacion.INDUSTRIAL_COMUN.verificado).isFalse();
        assertThat(UsoEdificacion.DEPOSITO.verificado).isFalse();
        assertThat(UsoEdificacion.ESENCIALES.verificado).isFalse();
    }
}
