package com.concretolab.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.concretolab.service.ManualPdf;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.Test;

class VisorAyudaTest {
    @Test
    void abreElManualIncluidoConSuIndiceYNavega() throws Exception {
        try { Platform.startup(() -> { }); } catch (IllegalStateException ya) { } catch (Throwable t) { assumeTrue(false, t.toString()); }
        Platform.setImplicitExit(false);
        CompletableFuture<Void> abierto = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                assertTrue(VisorAyuda.fernandoHuancaMostrar(null, "ayuda-que-no-existe", null));
                abierto.complete(null);
            } catch (Throwable t) { abierto.completeExceptionally(t); }
        });
        abierto.get(20, TimeUnit.SECONDS);
        Thread.sleep(500);
        CompletableFuture<Void> fin = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                VisorAyuda visor = VisorAyuda.fernandoHuancaActual();
                assertNotNull(visor);
                var escena = visor.fernandoHuancaVentana().getScene();
                TreeView<?> indice = (TreeView<?>) escena.lookup("#ayudaIndice");
                assertFalse(indice.getRoot().getChildren().isEmpty(), "el índice del manual se muestra");
                assertEquals(0, visor.fernandoHuancaPaginaActual(), "un ancla inexistente abre el comienzo");
                visor.fernandoHuancaIrA(new ManualPdf.Lugar(2, 0));
                assertEquals(2, visor.fernandoHuancaPaginaActual());
                assertEquals("3", ((TextField) escena.lookup("#ayudaPagina")).getText());
                visor.fernandoHuancaVentana().close();
                assertNull(VisorAyuda.fernandoHuancaActual(), "al cerrar se libera el documento");
                fin.complete(null);
            } catch (Throwable t) { fin.completeExceptionally(t); }
        });
        fin.get(20, TimeUnit.SECONDS);
    }
}
