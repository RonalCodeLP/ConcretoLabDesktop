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
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "clave-segura".toCharArray());
        assertThat(cifrado).isNotEqualTo(TEXTO);
        assertThat(CifradoArchivo.descifrar(cifrado, "clave-segura".toCharArray())).isEqualTo(TEXTO);
    }

    @Test
    void contrasenaIncorrectaSeRechazaConMensajeGenerico() {
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "correcta".toCharArray());
        assertThatThrownBy(() -> CifradoArchivo.descifrar(cifrado, "otra".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contraseña incorrecta o archivo dañado.");
    }

    @Test
    void unBitAlteradoSeDetecta() {
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        cifrado[cifrado.length - 20] ^= 0x01;
        assertThatThrownBy(() -> CifradoArchivo.descifrar(cifrado, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contraseña incorrecta o archivo dañado.");
    }

    @Test
    void laCabeceraAlteradaTambienSeDetecta() {
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        cifrado[8] ^= 0x01; // dentro de la sal
        assertThatThrownBy(() -> CifradoArchivo.descifrar(cifrado, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosCifradosDelMismoTextoSonDistintos() {
        byte[] a = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        byte[] b = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void estaCifradoReconoceLaMagia() {
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        assertThat(CifradoArchivo.estaCifrado(cifrado)).isTrue();
        assertThat(CifradoArchivo.estaCifrado(TEXTO)).isFalse();
        assertThat(CifradoArchivo.estaCifrado(new byte[] {1, 2})).isFalse();
        assertThat(CifradoArchivo.estaCifrado(null)).isFalse();
    }

    @Test
    void datosTruncadosOSinMagiaSeRechazan() {
        assertThatThrownBy(() -> CifradoArchivo.descifrar(TEXTO, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] cifrado = CifradoArchivo.cifrar(TEXTO, "clave".toCharArray());
        byte[] corto = java.util.Arrays.copyOf(cifrado, 20);
        assertThatThrownBy(() -> CifradoArchivo.descifrar(corto, "clave".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
