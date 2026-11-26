package com.concretolab.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BienvenidaTest {
    @TempDir Path carpeta;

    @Test
    void sinPreferenciaGuardadaSeMuestraYLaPropiedadDelSistemaLaDesactiva() {
        assertTrue(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
        String antes = System.getProperty(Bienvenida.FERNANDO_HUANCA_PROPIEDAD);
        try {
            System.setProperty(Bienvenida.FERNANDO_HUANCA_PROPIEDAD, "no");
            assertFalse(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
        } finally {
            if (antes == null) System.clearProperty(Bienvenida.FERNANDO_HUANCA_PROPIEDAD);
            else System.setProperty(Bienvenida.FERNANDO_HUANCA_PROPIEDAD, antes);
        }
        Bienvenida.fernandoHuancaGuardar(carpeta, false);
        assertFalse(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
        Bienvenida.fernandoHuancaGuardar(carpeta, true);
        assertTrue(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
    }

    @Test
    void unArchivoDePreferenciasDanadoNoImpideAbrir() throws Exception {
        Files.writeString(carpeta.resolve(Bienvenida.FERNANDO_HUANCA_ARCHIVO), "bienvenida.mostrar=\\u00zz roto");
        assertTrue(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
    }

    @Test
    void recorreLasCincoPaginasYGuardaNoVolverAMostrar() throws Exception {
        try { Platform.startup(() -> { }); } catch (IllegalStateException ya) { } catch (Throwable t) { assumeTrue(false, t.toString()); }
        CompletableFuture<Void> fin = new CompletableFuture<>();
        AtomicInteger cierres = new AtomicInteger(), ejemplos = new AtomicInteger();
        Platform.runLater(() -> {
            try {
                VBox recorrido = Bienvenida.fernandoHuancaCrear(carpeta, cierres::incrementAndGet, ejemplos::incrementAndGet);
                new Scene(recorrido);
                Label titulo = (Label) recorrido.lookup("#bienvenidaTitulo");
                Label pasos = (Label) recorrido.lookup("#bienvenidaPasos");
                Button siguiente = (Button) recorrido.lookup("#bienvenidaSiguiente");
                Button anterior = (Button) recorrido.lookup("#bienvenidaAnterior");
                Button ejemplo = (Button) recorrido.lookup("#bienvenidaEjemplo");
                CheckBox noMostrar = (CheckBox) recorrido.lookup("#bienvenidaNoMostrar");

                assertEquals("Bienvenido a ConcretoLab", titulo.getText());
                assertEquals("1 de 5", pasos.getText());
                assertFalse(anterior.isVisible());
                assertFalse(ejemplo.isVisible());
                assertFalse(noMostrar.isSelected());

                for (int i = 0; i < 4; i++) siguiente.fire();
                assertEquals("5 de 5", pasos.getText());
                assertEquals("Empezar", siguiente.getText());
                assertTrue(ejemplo.isVisible());
                anterior.fire();
                assertEquals("4 de 5", pasos.getText());
                assertEquals("Siguiente", siguiente.getText());
                siguiente.fire();

                noMostrar.setSelected(true);
                siguiente.fire();
                assertEquals(1, cierres.get());
                assertEquals(0, ejemplos.get());
                assertFalse(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));

                VBox otra = Bienvenida.fernandoHuancaCrear(carpeta, cierres::incrementAndGet, ejemplos::incrementAndGet);
                new Scene(otra);
                assertTrue(((CheckBox) otra.lookup("#bienvenidaNoMostrar")).isSelected());
                for (int i = 0; i < 4; i++) ((Button) otra.lookup("#bienvenidaSiguiente")).fire();
                ((CheckBox) otra.lookup("#bienvenidaNoMostrar")).setSelected(false);
                ((Button) otra.lookup("#bienvenidaEjemplo")).fire();
                assertEquals(2, cierres.get());
                assertEquals(1, ejemplos.get());
                assertTrue(Bienvenida.fernandoHuancaDebeMostrarse(carpeta));
                fin.complete(null);
            } catch (Throwable t) { fin.completeExceptionally(t); }
        });
        fin.get(20, TimeUnit.SECONDS);
    }
}
