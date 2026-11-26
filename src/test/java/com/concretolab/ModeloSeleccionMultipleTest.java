package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.concretolab.controller.MainController;
import com.concretolab.controller.ModeloController;
import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.ui.SesionProyecto;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Box;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ModeloSeleccionMultipleTest {
    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("seleccion-multiple", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
    }

    @Autowired ApplicationContext context;
    @Autowired SesionProyecto sesion;
    @Autowired YefriLermaProyectoService proyectos;
    @Autowired MainController principal;
    @Autowired ModeloController modelo;

    private Stage ventana;
    private Proyecto proyecto;

    @BeforeEach
    void abrir() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException yaIniciado) {
        } catch (Throwable sinPantalla) {
            assumeTrue(false, "Sin entorno gráfico para JavaFX: " + sinPantalla);
        }
        Platform.setImplicitExit(false);
        proyecto = proyectos.yefriLermaCrearProyecto("Selección múltiple " + System.nanoTime(), "Lima");
        proyectos.yefriLermaCrearPlantilla(proyecto.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.2, 1.2, 0.5, 0.4, 3, 0.3, 0.5));
        sesion.setProyecto(proyecto);
        fx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
                loader.setControllerFactory(context::getBean);
                Parent raiz = loader.load();
                ventana = new Stage();
                ventana.setScene(new Scene(raiz, 1500, 850));
                ventana.show();
                principal.fernandoHuancaIrA("modelo-view");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        esperarFx(400);
    }

    @AfterEach
    void cerrar() throws Exception {
        if (ventana != null) fx(() -> { cerrarPanel(); ventana.close(); });
    }

    @Test
    void conTodosLasZapatasSeleccionadasEditarCambiaLasCuatro() throws Exception {
        fx(() -> {
            filtro().setValue("Solo zapatas");
            campo("btnSeleccionarTodo", Button.class).fire();
        });
        assertThat(seleccionados()).hasSize(4).allMatch(e -> e.getTipo().equals("ZAPATA"));
        fx(() -> campo("btnEditar", Button.class).fire());
        esperarFx(400);
        assertThat(fxValor(() -> campo("lblTituloLote", Label.class).getText())).isEqualTo("Editar 4 elementos");
        assertThat(fxValor(() -> campo("panelLote", VBox.class).getScene())).isNotNull();
        fx(() -> {
            campo("txtLoteLargo", TextField.class).setText("1,5");
            campo("txtLoteAltura", TextField.class).setText("+0,1");
            campo("btnAplicarLote", Button.class).fire();
        });
        var zapatas = tipo("ZAPATA");
        assertThat(zapatas).hasSize(4).allSatisfy(z -> {
            assertThat(z.getLargo()).isEqualTo(1.5);
            assertThat(z.getAncho()).isEqualTo(1.2);
            assertThat(z.getAltura()).isEqualTo(0.6);
        });
        assertThat(tipo("COLUMNA")).allMatch(c -> c.getLargo() == 0.4);
        assertThat(seleccionados()).hasSize(4);
    }

    @Test
    void dobleClicEnUnaFilaConVariasSeleccionadasAbreLaEdicionDeTodas() throws Exception {
        fx(() -> {
            filtro().setValue("Solo zapatas");
            campo("btnSeleccionarTodo", Button.class).fire();
        });
        esperarFx(300);
        fx(() -> {
            TableCell<?, ?> celda = celdaDe(tabla().getItems().get(1));
            for (int clic = 1; clic <= 2; clic++) {
                Event.fireEvent(celda, raton(MouseEvent.MOUSE_PRESSED, clic, false));
                Event.fireEvent(celda, raton(MouseEvent.MOUSE_RELEASED, clic, false));
                Event.fireEvent(celda, raton(MouseEvent.MOUSE_CLICKED, clic, false));
            }
        });
        esperarFx(400);
        assertThat(fxValor(() -> campo("panelLote", VBox.class).getScene())).as("se abre el panel de varios").isNotNull();
        assertThat(fxValor(() -> campo("lblTituloLote", Label.class).getText())).isEqualTo("Editar 4 elementos");
        assertThat(seleccionados()).hasSize(4);
    }

    @Test
    void ctrlClicEnElVisor3dSeleccionaVariosAunqueSeanDeOtroTipo() throws Exception {
        var zapata = tipo("ZAPATA").get(0);
        var columna = tipo("COLUMNA").get(0);
        var otraColumna = tipo("COLUMNA").get(1);
        fx(() -> filtro().setValue("Solo zapatas"));
        fx(() -> Event.fireEvent(caja(zapata.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, false)));
        assertThat(ids(seleccionados())).containsExactly(zapata.getId());
        esperarFx(600);
        fx(() -> Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, true)));
        esperarFx(600);
        fx(() -> Event.fireEvent(caja(otraColumna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, true)));
        assertThat(ids(seleccionados())).containsExactlyInAnyOrder(zapata.getId(), columna.getId(), otraColumna.getId());
        assertThat(fxValor(() -> filtro().getValue())).isEqualTo("Todos los tipos");
        esperarFx(600);
        fx(() -> Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, true)));
        assertThat(ids(seleccionados())).containsExactlyInAnyOrder(zapata.getId(), otraColumna.getId());
        esperarFx(600);
        fx(() -> Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, false)));
        esperarFx(700);
        assertThat(ids(seleccionados())).containsExactly(columna.getId());
    }

    @Test
    void dobleClicEn3dSobreUnoDeVariosSeleccionadosAbreLaEdicionDeTodos() throws Exception {
        var zapatas = tipo("ZAPATA");
        fx(() -> {
            filtro().setValue("Solo zapatas");
            campo("btnSeleccionarTodo", Button.class).fire();
        });
        assertThat(seleccionados()).hasSize(4);
        fx(() -> {
            Event.fireEvent(caja(zapatas.get(2).getId()), raton(MouseEvent.MOUSE_CLICKED, 1, false));
            Event.fireEvent(caja(zapatas.get(2).getId()), raton(MouseEvent.MOUSE_CLICKED, 2, false));
        });
        esperarFx(700);
        assertThat(fxValor(() -> campo("lblTituloLote", Label.class).getText())).isEqualTo("Editar 4 elementos");
        assertThat(fxValor(() -> campo("panelLote", VBox.class).getScene())).isNotNull();
        assertThat(seleccionados()).hasSize(4);
    }

    @Test
    void dobleClicEn3dSobreUnoSoloAbreEditarElementoConMoverEn3d() throws Exception {
        var columna = tipo("COLUMNA").get(3);
        fx(() -> {
            Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, false));
            Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 2, false));
        });
        esperarFx(400);
        assertThat(fxValor(() -> campo("panelElemento", VBox.class).getScene())).isNotNull();
        assertThat(fxValor(() -> campo("txtNombreElemento", TextField.class).getText())).isEqualTo(columna.getNombre());
        assertThat(fxValor(() -> campo("btnMoverDesdeEditar", Button.class).isVisible())).isTrue();
    }

    @Test
    void editarVariosYDeshacerDevuelveLasMedidasAnteriores() throws Exception {
        assertThat(fxValor(() -> campo("btnDeshacer", Button.class).isDisabled())).as("sin cambios no hay nada que deshacer").isTrue();
        editarZapatas("1,5", "");
        assertThat(tipo("ZAPATA")).allMatch(z -> z.getLargo() == 1.5);
        assertThat(fxValor(() -> campo("btnDeshacer", Button.class).isDisabled())).isFalse();
        assertThat(fxValor(() -> campo("btnDeshacer", Button.class).getTooltip().getText())).contains("Editar 4 elementos");
        fx(() -> campo("btnDeshacer", Button.class).fire());
        assertThat(tipo("ZAPATA")).hasSize(4).allMatch(z -> z.getLargo() == 1.2 && z.getAncho() == 1.2);
        assertThat(tipo("COLUMNA")).allMatch(c -> c.getLargo() == 0.4);
        assertThat(fxValor(() -> campo("btnDeshacer", Button.class).isDisabled())).isTrue();
    }

    @Test
    void ctrlZDeshaceVariosPasosEnOrdenInverso() throws Exception {
        editarZapatas("1,5", "");
        editarZapatas("", "+0,3");
        assertThat(tipo("ZAPATA")).allMatch(z -> z.getLargo() == 1.5 && z.getAncho() == 1.5);
        fx(() -> filtro().setValue("Todos los tipos"));
        Runnable ctrlZ = fxValor(() -> ventana.getScene().getAccelerators().get(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Z, javafx.scene.input.KeyCombination.SHORTCUT_DOWN)));
        assertThat(ctrlZ).as("Ctrl+Z registrado").isNotNull();
        fx(ctrlZ);
        assertThat(tipo("ZAPATA")).allMatch(z -> z.getLargo() == 1.5 && z.getAncho() == 1.2);
        fx(ctrlZ);
        assertThat(tipo("ZAPATA")).allMatch(z -> z.getLargo() == 1.2 && z.getAncho() == 1.2);
        fx(ctrlZ);
        assertThat(tipo("ZAPATA")).allMatch(z -> z.getLargo() == 1.2);
    }

    @Test
    void editarUnElementoTambienSeDeshace() throws Exception {
        var columna = tipo("COLUMNA").get(0);
        fx(() -> {
            Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 1, false));
            Event.fireEvent(caja(columna.getId()), raton(MouseEvent.MOUSE_CLICKED, 2, false));
        });
        esperarFx(400);
        fx(() -> {
            campo("txtLargo", TextField.class).setText("0.55");
            invocar("onGuardarElemento");
        });
        esperarFx(300);
        assertThat(proyectos.elementos(proyecto.getId()).stream().filter(e -> e.getId().equals(columna.getId())).findFirst().orElseThrow().getLargo()).isEqualTo(0.55);
        fx(() -> campo("btnDeshacer", Button.class).fire());
        assertThat(proyectos.elementos(proyecto.getId()).stream().filter(e -> e.getId().equals(columna.getId())).findFirst().orElseThrow().getLargo()).isEqualTo(0.4);
    }

    @Test
    void noDeshaceSiElElementoCambioDespuesPorOtroCamino() throws Exception {
        editarZapatas("1,5", "");
        var z = tipo("ZAPATA").get(0);
        z.setAltura(0.9);
        proyectos.yefriLermaGuardar(z);
        fx(() -> campo("btnDeshacer", Button.class).fire());
        assertThat(tipo("ZAPATA")).allMatch(x -> x.getLargo() == 1.5);
        assertThat(fxValor(() -> campo("btnDeshacer", Button.class).isDisabled())).as("el paso que no se pudo deshacer se descarta").isTrue();
    }

    @Test
    void avisaAntesDeGuardarUnCambioQueSueltaLasVigasYSePuedeDeshacer() throws Exception {
        fx(() -> {
            filtro().setValue("Solo vigas");
            campo("btnSeleccionarTodo", Button.class).fire();
            campo("btnEditar", Button.class).fire();
        });
        esperarFx(400);
        fx(() -> {
            campo("txtLoteLargo", TextField.class).setText("0,6");
            campo("txtLoteAncho", TextField.class).setText("0,6");
            campo("btnAplicarLote", Button.class).fire();
        });
        esperarFx(500);
        Button guardarIgual = fxValor(() -> botonEnVentana("Guardar igual"));
        assertThat(guardarIgual).as("aparece la confirmación de vigas sueltas").isNotNull();
        assertThat(tipo("VIGA")).as("todavía no se guardó nada").noneMatch(v -> v.getLargo() == 0.6 && v.getAncho() == 0.6);
        fx(guardarIgual::fire);
        esperarFx(500);
        assertThat(tipo("VIGA")).allMatch(v -> v.getLargo() == 0.6 && v.getAncho() == 0.6);
        fx(() -> campo("btnDeshacer", Button.class).fire());
        assertThat(tipo("VIGA")).noneMatch(v -> v.getLargo() == 0.6 && v.getAncho() == 0.6);
        assertThat(com.concretolab.service.FernandoHuancaAnalisisSismico.fernandoHuancaConexionVigas(proyectos.elementos(proyecto.getId())).sueltas()).isEmpty();
    }

    private void editarZapatas(String largo, String ancho) throws Exception {
        fx(() -> {
            filtro().setValue("Solo zapatas");
            campo("btnSeleccionarTodo", Button.class).fire();
            campo("btnEditar", Button.class).fire();
        });
        esperarFx(400);
        fx(() -> {
            campo("txtLoteLargo", TextField.class).setText(largo);
            campo("txtLoteAncho", TextField.class).setText(ancho);
            campo("btnAplicarLote", Button.class).fire();
        });
        esperarFx(500);
    }

    private Button botonEnVentana(String texto) {
        return ventana.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && texto.equals(b.getText()) && b.isVisible())
                .map(n -> (Button) n).findFirst().orElse(null);
    }

    private void invocar(String metodo) {
        try {
            var m = ModeloController.class.getDeclaredMethod(metodo);
            m.setAccessible(true);
            m.invoke(modelo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Elemento> tipo(String tipo) {
        return proyectos.elementos(proyecto.getId()).stream().filter(e -> tipo.equals(e.getTipo())).toList();
    }

    @SuppressWarnings("unchecked")
    private TableView<Elemento> tabla() {
        return campo("tabla", TableView.class);
    }

    private List<Elemento> seleccionados() throws Exception {
        return fxValor(() -> List.copyOf(tabla().getSelectionModel().getSelectedItems()));
    }

    private static List<Long> ids(List<Elemento> l) {
        return l.stream().map(Elemento::getId).toList();
    }

    private TableCell<?, ?> celdaDe(Elemento item) {
        tabla().layout();
        return tabla().lookupAll(".table-cell").stream()
                .filter(n -> n instanceof TableCell<?, ?> c && c.getTableRow() != null && c.getTableRow().getItem() == item
                        && "NOMBRE".equals(c.getTableColumn().getText()))
                .map(n -> (TableCell<?, ?>) n).findFirst().orElseThrow(() -> new AssertionError("sin celda para " + item.getNombre()));
    }

    private Box caja(Long id) {
        Pane contenedor = campo("contenedorVisor", Pane.class);
        Node visor = contenedor.getChildren().get(0);
        SubScene escena = (SubScene) ((Parent) visor).getChildrenUnmodifiable().get(0);
        Box b = buscar(escena.getRoot(), id);
        if (b == null) throw new AssertionError("sin caja 3D para " + id);
        return b;
    }

    private static Box buscar(Node n, Long id) {
        if (n instanceof Box b && id.equals(b.getUserData())) return b;
        if (n instanceof Parent p) {
            for (Node h : p.getChildrenUnmodifiable()) {
                Box r = buscar(h, id);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static MouseEvent raton(EventType<MouseEvent> tipo, int clics, boolean ctrl) {
        return new MouseEvent(tipo, 5, 5, 5, 5, MouseButton.PRIMARY, clics,
                false, ctrl, false, false, tipo == MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null);
    }

    private <T> T campo(String nombre, Class<T> tipo) {
        try {
            var f = ModeloController.class.getDeclaredField(nombre);
            f.setAccessible(true);
            return tipo.cast(f.get(modelo));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> filtro() {
        return campo("cmbFiltroTipo", ComboBox.class);
    }

    private void cerrarPanel() {
        try {
            var m = ModeloController.class.getDeclaredMethod("onCerrarPanel");
            m.setAccessible(true);
            m.invoke(modelo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperarFx(long ms) throws Exception {
        Thread.sleep(ms);
        fx(() -> { });
    }

    private static <T> T fxValor(Supplier<T> s) throws Exception {
        CompletableFuture<T> r = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { r.complete(s.get()); } catch (Throwable t) { r.completeExceptionally(t); }
        });
        return r.get(30, TimeUnit.SECONDS);
    }

    private static void fx(Runnable tarea) throws Exception {
        fxValor(() -> { tarea.run(); return null; });
    }
}
