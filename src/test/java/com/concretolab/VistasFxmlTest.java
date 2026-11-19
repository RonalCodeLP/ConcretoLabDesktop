package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Prueba de humo: carga cada vista FXML con los controladores de Spring y llama a refrescar(), primero sin proyecto
 * y luego con un proyecto que tiene la plantilla rectangular. Detecta fx:id sin campo, controladores que no se pueden
 * construir y errores al cargar datos. Se omite sola si no hay entorno gráfico.
 */
@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class VistasFxmlTest {

    private static final List<String> VISTAS = List.of(
            "main-view", "inicio-view", "modelo-view", "cercha-view", "sismo-view",
            "materiales-view", "granulometria-view", "balance-view", "mezclas-view");

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("vistas-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
    }

    @Autowired ApplicationContext context;
    @Autowired SesionProyecto sesion;
    @Autowired YefriLermaProyectoService proyectos;

    @Test
    void todasLasVistasCarganSinProyecto() throws Exception {
        iniciarJavaFx();
        sesion.setProyecto(null);
        cargarTodas();
    }

    @Test
    void todasLasVistasCarganConUnProyectoConElementos() throws Exception {
        iniciarJavaFx();
        var p = proyectos.crearProyecto("Proyecto de prueba de vistas", "Lima");
        proyectos.crearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.2, 1.2, 0.5, 0.4, 3, 0.3, 0.5));
        sesion.setProyecto(p);
        cargarTodas();
    }

    private void cargarTodas() throws Exception {
        for (String vista : VISTAS) {
            Object controlador = cargar(vista);
            if (controlador instanceof Refrescable r && !vista.equals("main-view")) ejecutarEnFx(r::refrescar);
        }
    }

    private Object cargar(String vista) throws Exception {
        Object[] resultado = new Object[1];
        ejecutarEnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + vista + ".fxml"));
                loader.setControllerFactory(context::getBean);
                Parent raiz = loader.load();
                assertThat(raiz).as(vista).isNotNull();
                resultado[0] = loader.getController();
            } catch (Exception e) {
                throw new IllegalStateException("No carga " + vista + ": " + e, e);
            }
        });
        return resultado[0];
    }

    private static void iniciarJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException yaIniciado) {
            // Otra prueba ya arrancó el toolkit.
        } catch (Throwable sinPantalla) {
            assumeTrue(false, "Sin entorno gráfico para JavaFX: " + sinPantalla);
        }
    }

    private static void ejecutarEnFx(Runnable tarea) throws Exception {
        CompletableFuture<Void> fin = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                tarea.run();
                fin.complete(null);
            } catch (Throwable t) {
                fin.completeExceptionally(t);
            }
        });
        fin.get(60, TimeUnit.SECONDS);
    }
}
