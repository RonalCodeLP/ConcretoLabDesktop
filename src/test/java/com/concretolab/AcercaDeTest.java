package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.ui.AcercaDe;
import org.junit.jupiter.api.Test;

class AcercaDeTest {
    @Test
    void incluyeVersionAutoresNormasYAviso() {
        String texto = AcercaDe.fernandoHuancaTexto("0.1.0", "C:\\datos");
        assertThat(texto).contains("ConcretoLab Desktop 0.1.0").contains("C:\\datos")
                .contains("E.030").contains("E.060").contains("ACI 211.1").contains("no sustituyen la revisión")
                .contains("no está validado para diseñar ni construir obras");
        assertThat(texto).doesNotContain("Base de datos cifrada").doesNotContain("contraseña");
        for (String autor : AcercaDe.FERNANDO_HUANCA_AUTORES) assertThat(texto).contains(autor);
        assertThat(AcercaDe.FERNANDO_HUANCA_AUTORES).containsExactly("Yefri Ronaldiño Lerma Pacompia", "Erick Mijael Delgado Lima", "Fernando André Huanca Huallpa", "Walter David Quispesayhua Quispe");
    }
}
