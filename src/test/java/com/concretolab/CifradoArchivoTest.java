package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.service.CifradoArchivo;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CifradoArchivoTest {
    private static final byte[] TEXTO = "Proyecto de prueba: zapatas, columnas y vigas".getBytes(StandardCharsets.UTF_8);

    @Test
    void idaYVueltaRecuperaElTextoOriginal() {
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave-segura".toCharArray());
        assertThat(cifrado).isNotEqualTo(TEXTO);
        assertThat(CifradoArchivo.erickDelgadoDescifrar(cifrado, "clave-segura".toCharArray())).isEqualTo(TEXTO);
    }

    @Test
    void contrasenaIncorrectaSeRechazaConMensajeGenerico() {
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "correcta".toCharArray());
        assertThatThrownBy(() -> CifradoArchivo.erickDelgadoDescifrar(cifrado, "otra".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contraseña incorrecta o archivo dañado.");
    }

    @Test
    void unBitAlteradoSeDetecta() {
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        cifrado[cifrado.length - 20] ^= 0x01;
        assertThatThrownBy(() -> CifradoArchivo.erickDelgadoDescifrar(cifrado, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contraseña incorrecta o archivo dañado.");
    }

    @Test
    void laCabeceraAlteradaTambienSeDetecta() {
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        cifrado[8] ^= 0x01;
        assertThatThrownBy(() -> CifradoArchivo.erickDelgadoDescifrar(cifrado, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosCifradosDelMismoTextoSonDistintos() {
        byte[] a = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        byte[] b = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void estaCifradoReconoceLaMagia() {
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        assertThat(CifradoArchivo.erickDelgadoEstaCifrado(cifrado)).isTrue();
        assertThat(CifradoArchivo.erickDelgadoEstaCifrado(TEXTO)).isFalse();
        assertThat(CifradoArchivo.erickDelgadoEstaCifrado(new byte[] {1, 2})).isFalse();
        assertThat(CifradoArchivo.erickDelgadoEstaCifrado(null)).isFalse();
    }

    @Test
    void datosTruncadosOSinMagiaSeRechazan() {
        assertThatThrownBy(() -> CifradoArchivo.erickDelgadoDescifrar(TEXTO, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] cifrado = CifradoArchivo.erickDelgadoCifrar(TEXTO, "clave".toCharArray());
        byte[] corto = java.util.Arrays.copyOf(cifrado, 20);
        assertThatThrownBy(() -> CifradoArchivo.erickDelgadoDescifrar(corto, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
