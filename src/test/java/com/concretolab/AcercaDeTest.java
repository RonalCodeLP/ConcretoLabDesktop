package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.ui.AcercaDe;
import org.junit.jupiter.api.Test;

class AcercaDeTest {
    @Test
    void incluyeVersionAutoresNormasYAviso() {
        String texto = AcercaDe.fernandoHuancaTexto("0.1.0", true, "C:\\datos");
        assertThat(texto).contains("ConcretoLab Desktop 0.1.0").contains("C:\\datos").contains("sí (AES-256)")
                .contains("E.030").contains("E.060").contains("ACI 211.1").contains("no sustituyen la revisión");
        for (String autor : AcercaDe.FERNANDO_HUANCA_AUTORES) assertThat(texto).contains(autor);
        assertThat(AcercaDe.FERNANDO_HUANCA_AUTORES).containsExactly("Yefri Ronaldiño Lerma Pacompia", "Erick Mijael Delgado Lima", "Fernando André Huanca Huallpa", "Walter David Quispesayhua Quispe");
        assertThat(AcercaDe.fernandoHuancaTexto("0.1.0", false, "x")).contains("cifrada: no");
    }
}