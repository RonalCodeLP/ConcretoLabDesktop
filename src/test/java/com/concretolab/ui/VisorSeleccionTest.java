package com.concretolab.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.concretolab.model.Elemento;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Box;
import org.junit.jupiter.api.Test;

/** Clics del visor 3D: clic, Ctrl+clic (agregar a la selección) y doble clic. */
class VisorSeleccionTest {

    private static Elemento el(long id, String tipo) {
        Elemento e = new Elemento();
        e.setId(id); e.setProyectoId(1L); e.setTipo(tipo); e.setNombre(tipo + id);
        e.setLargo(.4); e.setAncho(.4); e.setAltura(3); e.setX(id); e.setZ(1.5);
        return e;
    }

    @Test
    void distingueClicCtrlClicYDobleClic() throws Exception {
        try { Platform.startup(() -> { }); } catch (IllegalStateException ya) { } catch (Throwable t) { assumeTrue(false, t.toString()); }
        CompletableFuture<Void> fin = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                var a = el(1, "ZAPATA");
                var b = el(2, "COLUMNA");
                var visor = new ErickDelgadoVisor3D();
                new Scene(visor.erickDelgadoNodo());
                List<String> eventos = new ArrayList<>();
                visor.erickDelgadoMostrar(List.of(a, b), e -> e == a,
                        (e, agregar) -> eventos.add((agregar ? "agregar " : "clic ") + e.getId()),
                        e -> eventos.add("doble " + e.getId()), true);
                Box cajaA = (Box) visor.erickDelgadoCapaElementos().getChildren().get(0);
                Box cajaB = (Box) visor.erickDelgadoCapaElementos().getChildren().get(1);

                Event.fireEvent(cajaB, clic(1, true));            // Ctrl+clic
                Event.fireEvent(cajaB, clic(2, true));            // otro Ctrl+clic rápido: no es doble clic
                Event.fireEvent(cajaA, clic(1, false));           // clic
                Event.fireEvent(cajaA, clic(2, false));           // segundo clic: doble clic, sin volver a seleccionar
                assertEquals(List.of("agregar 2", "agregar 2", "clic 1", "doble 1"), eventos);
                // el resaltado sigue al predicado (selección múltiple)
                assertNotEquals(((javafx.scene.paint.PhongMaterial) cajaA.getMaterial()).getDiffuseColor(),
                        ((javafx.scene.paint.PhongMaterial) cajaB.getMaterial()).getDiffuseColor());
                fin.complete(null);
            } catch (Throwable t) { fin.completeExceptionally(t); }
        });
        fin.get(20, TimeUnit.SECONDS);
    }

    private static MouseEvent clic(int clics, boolean ctrl) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, clics,
                false, ctrl, false, false, false, false, false, false, false, true, null);
    }
}
