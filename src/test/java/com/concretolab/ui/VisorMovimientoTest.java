package com.concretolab.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.concretolab.model.Elemento;
import com.concretolab.service.MovimientoElementoService.Estado;
import com.concretolab.service.MovimientoElementoService.Posicion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Box;
import org.junit.jupiter.api.Test;

class VisorMovimientoTest {
    @Test
    void arrastrarElementoEnModoMovimientoEnviaDesplazamientoYActualizaVista() throws Exception {
        try { Platform.startup(() -> { }); } catch (IllegalStateException yaIniciado) { }
        catch (Throwable sinPantalla) { org.junit.jupiter.api.Assumptions.assumeTrue(false, "Sin entorno gráfico: " + sinPantalla); }
        CompletableFuture<Void> fin = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Elemento elemento = new Elemento();
                elemento.setId(1L); elemento.setProyectoId(1L); elemento.setTipo("COLUMNA"); elemento.setNombre("C1");
                elemento.setLargo(.4); elemento.setAncho(.4); elemento.setAltura(3); elemento.setZ(1.5);
                ErickDelgadoVisor3D visor = new ErickDelgadoVisor3D();
                new Scene(visor.erickDelgadoNodo());
                visor.erickDelgadoMostrar(List.of(elemento), elemento, e -> { }, true);
                AtomicReference<Double> desplazamiento = new AtomicReference<>();
                visor.erickDelgadoIniciarMovimiento(1L, desplazamiento::set);
                Box caja = (Box) visor.erickDelgadoCapaElementos().getChildren().get(0);
                Event.fireEvent(caja, raton(MouseEvent.MOUSE_PRESSED, 100));
                Event.fireEvent(caja, raton(MouseEvent.MOUSE_DRAGGED, 120));
                assertNotNull(desplazamiento.get());
                assertTrue(desplazamiento.get() > 0);
                Estado vista = new Estado(1L, 1L, "COLUMNA", "C1", .4, .4, 3, new Posicion(2, 1, 1.5));
                visor.erickDelgadoPrevisualizar(vista);
                assertEquals(2 * ErickDelgadoVisor3D.erickDelgadoUnidades(), caja.getTranslateX(), 1e-9);
                assertEquals(0, elemento.getX(), 1e-9);
                visor.erickDelgadoTerminarMovimiento();
                fin.complete(null);
            } catch (Throwable e) { fin.completeExceptionally(e); }
        });
        fin.get(20, TimeUnit.SECONDS);
    }

    private MouseEvent raton(javafx.event.EventType<MouseEvent> tipo, double x) {
        return new MouseEvent(tipo, x, 20, x, 20, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
