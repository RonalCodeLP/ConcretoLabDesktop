package com.concretolab.controller;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.MovimientoElementoService;
import com.concretolab.service.MovimientoElementoService.Operacion;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.EditorCuadricula;
import com.concretolab.ui.EditorMovimiento;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import com.concretolab.ui.Tablas;
import com.concretolab.ui.ErickDelgadoVisor3D;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import atlantafx.base.controls.Popover;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pantalla «Modelo 3D y metrado». Es la duena del selector de proyecto: lo mantiene sincronizado
 * con {@link SesionProyecto} para que las demas vistas lean de ahi el proyecto activo.
 */
@Component
@RequiredArgsConstructor
public class ModeloController implements Refrescable {

    private final YefriLermaProyectoService yefriLermaServicio;
    private final com.concretolab.service.FernandoHuancaPlanoDxf yefriLermaImportadorDxf;

    @FXML private void onExportarDxf() {
        Proyecto activo = yefriLermaSesion.getProyecto();
        if (activo == null) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Selecciona el proyecto que quieres exportar.").showAndWait();
            return;
        }
        var selector = new javafx.stage.FileChooser();
        selector.setTitle("Exportar modelo 3D en DXF");
        selector.setInitialFileName("modelo-concretolab.dxf");
        selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("AutoCAD DXF","*.dxf"));
        var destino = selector.showSaveDialog(tabla.getScene().getWindow());
        if (destino == null) return;
        try {
            yefriLermaImportadorDxf.fernandoHuancaExportar(destino.toPath(),yefriLermaServicio.elementos(activo.getId()));
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "DXF guardado. Abre el archivo en AutoCAD y selecciona una vista isométrica.").showAndWait();
        } catch (java.io.IOException | IllegalArgumentException ex) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,ex.getMessage()).showAndWait();
        }
    }

    @FXML private void onExportarIfc() {
        Proyecto activo = yefriLermaSesion.getProyecto();
        if (activo == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione el proyecto que quiere exportar.");
            return;
        }
        yefriLermaGuardarTexto("Exportar modelo en IFC", activo.getNombre() + ".ifc", "Modelo IFC (IFC2X3)", "*.ifc",
                () -> com.concretolab.service.ExportadorIfc.erickDelgadoGenerar(activo.getNombre(), yefriLermaServicio.elementos(activo.getId())),
                "IFC guardado. Ábralo en un visor IFC (BIMvision, Solibri, Revit…) para verificarlo.");
    }

    @FXML private void onExportarEtabs() {
        Proyecto activo = yefriLermaSesion.getProyecto();
        if (activo == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione el proyecto que quiere exportar.");
            return;
        }
        var dialogo = new javafx.scene.control.TextInputDialog("21");
        dialogo.setTitle("Exportar a ETABS (experimental)");
        dialogo.setHeaderText("Resistencia del concreto f’c (MPa) para el material del modelo");
        dialogo.setContentText("f’c (MPa):");
        var respuesta = dialogo.showAndWait();
        if (respuesta.isEmpty()) return;
        double fc;
        try {
            fc = Double.parseDouble(respuesta.get().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Ingrese una f’c numérica.");
            return;
        }
        double fcFinal = fc;
        var avisos = new java.util.ArrayList<String>();
        yefriLermaGuardarTexto("Exportar modelo a ETABS (.e2k)", activo.getNombre() + ".e2k", "Texto de ETABS", "*.e2k",
                () -> {
                    var r = com.concretolab.service.ExportadorEtabs.erickDelgadoGenerar(activo.getNombre(), fcFinal, yefriLermaServicio.elementos(activo.getId()));
                    avisos.addAll(r.avisos());
                    return r.texto();
                },
                "e2k guardado (experimental, sin verificar en ETABS). Revise el modelo al importarlo.");
        if (!avisos.isEmpty()) {
            var a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, String.join("\n• ", avisos));
            a.setHeaderText("Avisos de la exportación");
            a.setResizable(true);
            a.showAndWait();
        }
    }

    private void yefriLermaGuardarTexto(String titulo, String nombre, String descripcion, String patron,
                              java.util.function.Supplier<String> contenido, String mensajeOk) {
        var selector = new javafx.stage.FileChooser();
        selector.setTitle(titulo);
        selector.setInitialFileName(nombre.replaceAll("[\\\\/:*?\"<>|]", "_"));
        selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(descripcion, patron));
        var destino = selector.showSaveDialog(tabla.getScene().getWindow());
        if (destino == null) return;
        try {
            java.nio.file.Files.writeString(destino.toPath(), contenido.get(), java.nio.charset.StandardCharsets.UTF_8);
            yefriLermaNotificador.exito(mensajeOk);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            yefriLermaNotificador.fernandoHuancaError(ex.getMessage() == null ? "No se pudo exportar." : ex.getMessage());
        }
    }

    @FXML private void onImportarDxf() {
        new com.concretolab.ui.ErickDelgadoImportacionDxf(yefriLermaImportadorDxf, yefriLermaServicio)
                .erickDelgadoAbrir(tabla.getScene().getWindow(), yefriLermaSesion.getProyecto())
                .ifPresent(p -> {
                    yefriLermaCargarProyectos(p.getId());
                    yefriLermaCargarElementos();
                });
    }
    private final SesionProyecto yefriLermaSesion;
    private final Notificador yefriLermaNotificador;
    private final EditorCuadricula yefriLermaEditorCuadricula;
    private final MovimientoElementoService yefriLermaMovimientos;

    /** Se crea en initialize(): construye nodos 3D de JavaFX, que no existen todavía cuando Spring instancia el controlador. */
    private ErickDelgadoVisor3D yefriLermaVisor3d;

    // ---- cabecera
    @FXML private ComboBox<Proyecto> cmbProyecto;
    @FXML private MenuButton btnNuevoProyecto;
    @FXML private Button btnEliminarProyecto;

    // ---- elementos
    @FXML private TableView<Elemento> tabla;
    @FXML private TableColumn<Elemento, String> colTipo;
    @FXML private TableColumn<Elemento, String> colNombre;
    @FXML private TableColumn<Elemento, String> colVolumen;
    @FXML private Button btnNuevoElemento;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnPlantilla;
    @FXML private Button btnCuadricula;
    @FXML private Button btnMover;
    @FXML private Button btnDeshacer;
    @FXML private VBox panelMovimiento;
    /** Texto completo del metrado: se muestra en un globo al pulsar «Metrado» para no tapar la tabla. */
    private Label lblResumen; // se crea en initialize(): un Label no puede crearse antes de iniciar JavaFX
    @FXML private Button btnMetrado;
    @FXML private StackPane contenedorVisor;
    @FXML private Button btnExportarDxf;

    // ---- filtro y selección múltiple
    @FXML private ComboBox<String> cmbFiltroTipo;
    @FXML private Button btnSeleccionarTodo;
    @FXML private Label lblSeleccion;
    /** Todos los elementos del proyecto; la tabla muestra solo los del tipo filtrado, el visor 3D muestra todos. */
    private final ObservableList<Elemento> yefriLermaTodos = FXCollections.observableArrayList();
    private FilteredList<Elemento> yefriLermaFiltrados;
    static final String YEFRI_LERMA_TODOS_LOS_TIPOS = "Todos los tipos";
    /** Texto del filtro → tipo de elemento. */
    static final Map<String, String> YEFRI_LERMA_FILTROS = filtros();

    private static Map<String, String> filtros() {
        var m = new LinkedHashMap<String, String>();
        m.put(YEFRI_LERMA_TODOS_LOS_TIPOS, null);
        m.put("Solo zapatas", "ZAPATA");
        m.put("Solo columnas", "COLUMNA");
        m.put("Solo vigas", "VIGA");
        m.put("Solo losas", "LOSA");
        m.put("Solo muros", "MURO");
        return Collections.unmodifiableMap(m);
    }

    // ---- panel: varios elementos a la vez
    @FXML private VBox panelLote;
    @FXML private Label lblTituloLote;
    @FXML private Label lblAvisoLote;
    @FXML private Label lblDetalleLote;
    @FXML private TextField txtLoteLargo;
    @FXML private TextField txtLoteAncho;
    @FXML private TextField txtLoteAltura;
    @FXML private Button btnAplicarLote;
    @FXML private Button btnMoverDesdeEditar;
    /** Elementos que edita el panel de lote. */
    private List<Elemento> yefriLermaLote = List.of();
    private Popover yefriLermaGloboMetrado;
    /** Selección que había al empezar el último clic en la tabla (para el doble clic sobre varias filas). */
    private List<Elemento> yefriLermaSeleccionPrevia = List.of();
    /** Clic simple en 3D sobre una selección múltiple: espera por si es el primer clic de un doble clic. */
    private javafx.animation.PauseTransition yefriLermaClicPendiente;

    // ---- panel: proyecto
    @FXML private VBox panelProyecto;
    @FXML private TextField txtNombreProyecto;
    @FXML private TextField txtUbicacionProyecto;

    // ---- panel: elemento
    @FXML private VBox panelElemento;
    @FXML private Label lblTituloElemento;
    @FXML private ComboBox<String> cmbTipo;
    @FXML private TextField txtNombreElemento;
    @FXML private TextField txtLargo;
    @FXML private TextField txtAncho;
    @FXML private TextField txtAltura;
    @FXML private TextField txtX;
    @FXML private TextField txtY;
    @FXML private TextField txtZ;

    // ---- panel: plantilla rectangular
    @FXML private VBox panelPlantilla;
    @FXML private TextField txtSeparacionX;
    @FXML private TextField txtSeparacionY;
    @FXML private TextField txtLargoZapata;
    @FXML private TextField txtAnchoZapata;
    @FXML private TextField txtAlturaZapata;
    @FXML private TextField txtLadoColumna;
    @FXML private TextField txtAlturaColumna;
    @FXML private TextField txtAnchoViga;
    @FXML private TextField txtAlturaViga;

    /** Evita que los cambios hechos por codigo en el combo se propaguen como si los hubiera hecho el usuario. */
    private boolean yefriLermaSincronizando;
    /** Id del elemento en edicion; {@code null} cuando el panel registra uno nuevo. */
    private Long yefriLermaIdEditando;
    private EditorMovimiento yefriLermaEditorMovimiento;
    /** Historial de «Deshacer» (el más reciente primero): ediciones de uno o varios elementos y movimientos en 3D. */
    private final java.util.Deque<YefriLermaPaso> yefriLermaHistorial = new java.util.ArrayDeque<>();
    static final int YEFRI_LERMA_MAXIMO_DESHACER = 20;
    /** La edición está habilitada (falsa mientras se mueve un elemento en 3D). */
    private boolean yefriLermaEdicionHabilitada = true;

    /** Un cambio que se puede deshacer. */
    sealed interface YefriLermaPaso permits YefriLermaPasoEdicion, YefriLermaPasoMovimiento {
        String descripcion();
        Long proyectoId();
    }

    /** Edición de uno o varios elementos: estado antes y después (copias, mismos ids). */
    record YefriLermaPasoEdicion(String descripcion, Long proyectoId, List<Elemento> antes, List<Elemento> despues) implements YefriLermaPaso { }

    /** Movimiento en 3D (lo deshace MovimientoElementoService, que también revisa los vínculos viga-columna). */
    record YefriLermaPasoMovimiento(String descripcion, Long proyectoId, Operacion operacion) implements YefriLermaPaso { }

    @FXML
    public void initialize() {
        yefriLermaVisor3d = new ErickDelgadoVisor3D();
        contenedorVisor.getChildren().add(yefriLermaVisor3d.erickDelgadoNodo());

        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colVolumen.setCellValueFactory(c -> new SimpleStringProperty(yefriLermaVolumen(c.getValue().volumen())));
        Tablas.fernandoHuancaConfigurar(tabla, "Agregue elementos al proyecto", this::yefriLermaDobleClicFila);
        // El primer clic de un doble clic deja seleccionada solo esa fila: se recuerda la selección de antes para
        // que el doble clic sobre una fila de una selección múltiple edite todas (como en el explorador de archivos).
        tabla.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getClickCount() == 1) yefriLermaSeleccionPrevia = List.copyOf(tabla.getSelectionModel().getSelectedItems());
        });

        // La tabla muestra los elementos filtrados por tipo; SortedList mantiene el orden por columnas.
        yefriLermaFiltrados = new FilteredList<>(yefriLermaTodos, e -> true);
        var ordenados = new SortedList<>(yefriLermaFiltrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        tabla.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tabla.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Elemento>) c -> {
            yefriLermaActualizarSeleccion();
            yefriLermaDibujar(false);
        });
        cmbFiltroTipo.setItems(FXCollections.observableArrayList(YEFRI_LERMA_FILTROS.keySet()));
        cmbFiltroTipo.setValue(YEFRI_LERMA_TODOS_LOS_TIPOS);
        cmbFiltroTipo.valueProperty().addListener((o, a, b) -> {
            String tipo = b == null ? null : YEFRI_LERMA_FILTROS.get(b);
            tabla.getSelectionModel().clearSelection();
            yefriLermaFiltrados.setPredicate(e -> tipo == null || tipo.equals(e.getTipo()));
            yefriLermaActualizarSeleccion();
        });
        lblResumen = new Label("Seleccione un proyecto");
        lblResumen.setWrapText(true);
        lblResumen.setMaxWidth(420);
        lblResumen.getStyleClass().add("modelo-resumen");
        btnMetrado.setTooltip(new Tooltip("Ver el metrado completo: concreto neto, encofrado y acero por tipo"));

        var seleccionados = tabla.getSelectionModel().getSelectedItems();
        var cuantos = Bindings.size(seleccionados);
        btnEditar.disableProperty().bind(cuantos.isEqualTo(0).or(panelMovimiento.visibleProperty()));
        btnEliminar.disableProperty().bind(cuantos.isEqualTo(0).or(panelMovimiento.visibleProperty()));
        btnMover.disableProperty().bind(cuantos.isNotEqualTo(1).or(panelMovimiento.visibleProperty()));
        btnSeleccionarTodo.disableProperty().bind(Bindings.isEmpty(tabla.getItems()).or(panelMovimiento.visibleProperty()));
        btnMetrado.disableProperty().bind(Bindings.isEmpty(yefriLermaTodos));
        Formularios.fernandoHuancaLimpiarAlEditar(txtLoteLargo, txtLoteAncho, txtLoteAltura);
        yefriLermaActualizarDeshacer();
        // Ctrl+Z deshace el último cambio mientras se ve esta pantalla (los campos de texto tienen su propio Ctrl+Z).
        tabla.sceneProperty().addListener((o, a, escena) -> {
            if (escena != null) {
                escena.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Z,
                        javafx.scene.input.KeyCombination.SHORTCUT_DOWN), this::yefriLermaDeshacerConAtajo);
            }
        });
        yefriLermaEditorMovimiento = new EditorMovimiento(yefriLermaMovimientos, yefriLermaVisor3d, panelMovimiento, guardado -> {
            var original = guardado.vista().original();
            yefriLermaRegistrarPaso(new YefriLermaPasoMovimiento("Mover «" + original.nombre() + "»", original.proyectoId(), guardado));
            yefriLermaCargarElementos();
            yefriLermaNotificador.exito("Movimiento guardado.");
        }, () -> {
            yefriLermaHabilitarEdicion(true);
            yefriLermaDibujar(false);
        });
        btnEliminarProyecto.disableProperty().bind(cmbProyecto.valueProperty().isNull().or(panelMovimiento.visibleProperty()));
        btnExportarDxf.disableProperty().bind(cmbProyecto.valueProperty().isNull()
                .or(Bindings.isEmpty(yefriLermaTodos)).or(panelMovimiento.visibleProperty()));

        cmbProyecto.valueProperty().addListener((o, a, b) -> {
            if (yefriLermaSincronizando) {
                return;
            }
            yefriLermaSesion.setProyecto(b);
            yefriLermaLimpiarHistorial();
            yefriLermaCargarElementos();
        });

        cmbTipo.setItems(FXCollections.observableArrayList("ZAPATA", "COLUMNA", "VIGA", "LOSA", "MURO"));
        cmbTipo.valueProperty().addListener((o, a, b) -> Formularios.fernandoHuancaLimpiarError(cmbTipo));
        Formularios.fernandoHuancaLimpiarAlEditar(txtNombreProyecto, txtUbicacionProyecto, txtNombreElemento,
                txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        Formularios.fernandoHuancaLimpiarAlEditar(yefriLermaCamposPlantilla().toArray(new TextField[0]));
    }

    @Override
    public void refrescar() {
        Proyecto activo = yefriLermaSesion.getProyecto();
        if (panelMovimiento.isVisible()) yefriLermaEditorMovimiento.walterQuispesayhuaCerrar();
        if (activo == null || yefriLermaHistorial.stream().anyMatch(paso -> !activo.getId().equals(paso.proyectoId()))) {
            yefriLermaLimpiarHistorial();
        }
        yefriLermaCargarProyectos(activo == null ? null : activo.getId());
        yefriLermaCargarElementos();
    }

    // ------------------------------------------------------------- proyecto

    @FXML
    private void onNuevoProyecto() {
        Formularios.fernandoHuancaLimpiarErrores(txtNombreProyecto, txtUbicacionProyecto);
        txtNombreProyecto.clear();
        txtUbicacionProyecto.clear();
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelProyecto);
        Platform.runLater(txtNombreProyecto::requestFocus);
    }

    @FXML
    private void onGuardarProyecto() {
        Formularios.fernandoHuancaLimpiarErrores(txtNombreProyecto, txtUbicacionProyecto);
        String nombre = txtNombreProyecto.getText();
        String ubicacion = txtUbicacionProyecto.getText();
        boolean valido = true;
        if (nombre.isBlank() || nombre.trim().length() > 120) {
            Formularios.fernandoHuancaMarcarError(txtNombreProyecto, "Ingrese un nombre de proyecto de hasta 120 caracteres.");
            valido = false;
        }
        if (ubicacion.length() > 200) {
            Formularios.fernandoHuancaMarcarError(txtUbicacionProyecto, "La ubicación no puede superar 200 caracteres.");
            valido = false;
        }
        if (!valido) {
            return;
        }
        try {
            Proyecto nuevo = yefriLermaServicio.yefriLermaCrearProyecto(nombre, ubicacion);
            yefriLermaNotificador.fernandoHuancaCerrarPanel();
            yefriLermaNotificador.exito("Proyecto \"" + nuevo.getNombre() + "\" creado.");
            yefriLermaCargarProyectos(nuevo.getId());
            yefriLermaCargarElementos();
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    // ------------------------------------------------------------- elementos

    @FXML
    private void onNuevoElemento() {
        if (!yefriLermaHayProyecto()) {
            return;
        }
        tabla.getSelectionModel().clearSelection();
        yefriLermaAbrirElemento(null);
    }

    @FXML
    private void onEditarElemento() {
        var seleccion = List.copyOf(tabla.getSelectionModel().getSelectedItems());
        if (seleccion.size() > 1) {
            yefriLermaAbrirLote(seleccion, null, "", "", "");
        } else if (seleccion.size() == 1) {
            yefriLermaEditar(seleccion.get(0));
        }
    }

    private void yefriLermaEditar(Elemento e) {
        if (e == null || !yefriLermaHayProyecto()) {
            return;
        }
        yefriLermaAbrirElemento(e);
    }

    private void yefriLermaAbrirElemento(Elemento anterior) {
        Formularios.fernandoHuancaLimpiarErrores(cmbTipo, txtNombreElemento, txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        yefriLermaIdEditando = anterior == null ? null : anterior.getId();
        lblTituloElemento.setText(anterior == null ? "Nuevo elemento" : "Editar elemento");
        cmbTipo.setValue(anterior == null ? "ZAPATA" : anterior.getTipo());
        txtNombreElemento.setText(anterior == null ? "" : anterior.getNombre());
        txtLargo.setText(Double.toString(anterior == null ? 1 : anterior.getLargo()));
        txtAncho.setText(Double.toString(anterior == null ? 1 : anterior.getAncho()));
        txtAltura.setText(Double.toString(anterior == null ? 1 : anterior.getAltura()));
        txtX.setText(Double.toString(anterior == null ? 0 : anterior.getX()));
        txtY.setText(Double.toString(anterior == null ? 0 : anterior.getY()));
        txtZ.setText(Double.toString(anterior == null ? 0 : anterior.getZ()));
        btnMoverDesdeEditar.setVisible(anterior != null);
        btnMoverDesdeEditar.setManaged(anterior != null);
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelElemento);
        Platform.runLater(txtNombreElemento::requestFocus);
    }

    @FXML
    private void onGuardarElemento() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione o cree un proyecto.");
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(cmbTipo, txtNombreElemento, txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        boolean valido = true;
        if (cmbTipo.getValue() == null) {
            Formularios.fernandoHuancaMarcarError(cmbTipo, "Seleccione un tipo válido.");
            valido = false;
        }
        if (txtNombreElemento.getText().isBlank()) {
            Formularios.fernandoHuancaMarcarError(txtNombreElemento, "Ingrese un nombre.");
            valido = false;
        }
        Double largo = yefriLermaNumero(txtLargo), ancho = yefriLermaNumero(txtAncho), altura = yefriLermaNumero(txtAltura);
        Double x = yefriLermaNumero(txtX), y = yefriLermaNumero(txtY), z = yefriLermaNumero(txtZ);
        if (!valido || largo == null || ancho == null || altura == null || x == null || y == null || z == null) {
            return;
        }
        try {
            Elemento elemento = new Elemento();
            if (yefriLermaIdEditando != null) {
                elemento.setId(yefriLermaIdEditando);
            }
            elemento.setProyectoId(p.getId());
            elemento.setTipo(cmbTipo.getValue());
            elemento.setNombre(txtNombreElemento.getText().trim());
            elemento.setLargo(largo);
            elemento.setAncho(ancho);
            elemento.setAltura(altura);
            elemento.setX(x);
            elemento.setY(y);
            elemento.setZ(z);
            boolean esNuevo = yefriLermaIdEditando == null;
            Elemento anterior = esNuevo ? null : yefriLermaTodos.stream().filter(e -> elemento.getId().equals(e.getId())).findFirst().map(ModeloController::yefriLermaCopia).orElse(null);
            Runnable guardar = () -> {
                try {
                    Elemento guardado = yefriLermaServicio.yefriLermaGuardar(elemento);
                    if (anterior != null) {
                        yefriLermaRegistrarPaso(new YefriLermaPasoEdicion("Editar «" + guardado.getNombre() + "»", p.getId(),
                                List.of(anterior), List.of(yefriLermaCopia(guardado))));
                    }
                    yefriLermaNotificador.fernandoHuancaCerrarPanel();
                    yefriLermaNotificador.exito(esNuevo
                            ? "Elemento \"" + guardado.getNombre() + "\" registrado."
                            : "Elemento \"" + guardado.getNombre() + "\" actualizado.");
                    yefriLermaCargarElementos();
                } catch (Exception e) {
                    yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
                }
            };
            yefriLermaConfirmarSiSueltaVigas(List.of(elemento), guardar);
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    @FXML
    private void onEliminarElemento() {
        var seleccion = List.copyOf(tabla.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) {
            return;
        }
        String pregunta = seleccion.size() == 1 ? "¿Eliminar el elemento seleccionado?"
                : "¿Eliminar los " + seleccion.size() + " elementos seleccionados?";
        yefriLermaNotificador.fernandoHuancaConfirmar("Eliminar elemento", pregunta, "Eliminar", () -> {
            try {
                for (Elemento e : seleccion) {
                    yefriLermaServicio.yefriLermaEliminar(e.getId());
                }
                yefriLermaNotificador.exito(seleccion.size() == 1 ? "Elemento eliminado." : seleccion.size() + " elementos eliminados.");
                yefriLermaCargarElementos();
            } catch (Exception e) {
                yefriLermaCargarElementos();
                yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            }
        });
    }

    /** Desde el panel «Editar elemento»: cierra el panel y empieza a mover ese elemento en el visor 3D. */
    @FXML
    private void onMoverDesdeEditar() {
        Long id = yefriLermaIdEditando;
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
        if (id == null) return;
        yefriLermaTodos.stream().filter(e -> id.equals(e.getId())).findFirst().ifPresent(e -> {
            yefriLermaSeleccionarSolo(e);
            onMoverElemento();
        });
    }

    @FXML
    private void onSeleccionarTodo() {
        tabla.getSelectionModel().selectAll();
        tabla.requestFocus();
    }

    // ------------------------------------------------------ varios elementos a la vez

    /**
     * Abre el panel de lote para {@code lista}. Los textos iniciales pueden venir vacíos (sin cambio), con una medida
     * (0,50) o con un incremento (+0,05). {@code aviso} se muestra arriba (por ejemplo, la recomendación sísmica).
     */
    private void yefriLermaAbrirLote(List<Elemento> lista, String aviso, String largo, String ancho, String altura) {
        yefriLermaLote = List.copyOf(lista);
        Formularios.fernandoHuancaLimpiarErrores(txtLoteLargo, txtLoteAncho, txtLoteAltura);
        lblTituloLote.setText("Editar " + lista.size() + " elementos");
        lblDetalleLote.setText(yefriLermaDescribirLote(lista));
        lblAvisoLote.setText(aviso == null ? "" : aviso);
        lblAvisoLote.setVisible(aviso != null);
        lblAvisoLote.setManaged(aviso != null);
        txtLoteLargo.setText(largo);
        txtLoteAncho.setText(ancho);
        txtLoteAltura.setText(altura);
        btnAplicarLote.setText("Aplicar a " + lista.size());
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelLote);
        Platform.runLater(txtLoteLargo::requestFocus);
    }

    /** «24 columnas, 4 vigas · secciones 40×40, 30×50 cm». */
    static String yefriLermaDescribirLote(List<Elemento> lista) {
        var porTipo = lista.stream().collect(Collectors.groupingBy(Elemento::getTipo, LinkedHashMap::new, Collectors.counting()));
        String tipos = porTipo.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().toLowerCase(Locale.ROOT)
                + (e.getValue() == 1 ? "" : (e.getKey().endsWith("A") || e.getKey().endsWith("O") ? "s" : "es"))).collect(Collectors.joining(", "));
        var secciones = lista.stream().map(e -> yefriLermaCm(e.getLargo()) + "×" + yefriLermaCm(e.getAncho()) + "×" + yefriLermaCm(e.getAltura()))
                .distinct().toList();
        String texto = secciones.size() <= 4 ? String.join(", ", secciones) : String.join(", ", secciones.subList(0, 4)) + "…";
        return "Seleccionados: " + tipos + ".\nMedidas actuales X×Y×Z (cm): " + texto + ".";
    }

    private static String yefriLermaCm(double metros) {
        return BigDecimal.valueOf(metros * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** Cambio pedido en un campo del lote: vacío = sin cambio; «0,5» fija; «+0,05» o «-0,05» suma o resta. */
    record YefriLermaAjuste(boolean relativo, double valor) {
        double aplicar(double actual) { return relativo ? actual + valor : valor; }
    }

    /** {@code null} si el campo está vacío; lanza NumberFormatException si no es un número. */
    static YefriLermaAjuste yefriLermaLeerAjuste(String texto) {
        String t = texto == null ? "" : texto.trim().replace(',', '.').replace(" ", "");
        if (t.isEmpty()) return null;
        boolean relativo = t.startsWith("+") || t.startsWith("-");
        double v = Double.parseDouble(t);
        if (!Double.isFinite(v)) throw new NumberFormatException(t);
        return new YefriLermaAjuste(relativo, v);
    }

    /** Copias de {@code lista} con los ajustes aplicados; valida que las medidas queden positivas. */
    static List<Elemento> yefriLermaAplicarAjustes(List<Elemento> lista, YefriLermaAjuste largo, YefriLermaAjuste ancho, YefriLermaAjuste altura) {
        List<Elemento> r = new ArrayList<>();
        for (Elemento o : lista) {
            Elemento c = new Elemento();
            c.setId(o.getId()); c.setProyectoId(o.getProyectoId()); c.setTipo(o.getTipo()); c.setNombre(o.getNombre());
            c.setX(o.getX()); c.setY(o.getY()); c.setZ(o.getZ());
            c.setLargo(yefriLermaRedondear(largo == null ? o.getLargo() : largo.aplicar(o.getLargo())));
            c.setAncho(yefriLermaRedondear(ancho == null ? o.getAncho() : ancho.aplicar(o.getAncho())));
            c.setAltura(yefriLermaRedondear(altura == null ? o.getAltura() : altura.aplicar(o.getAltura())));
            if (!(c.getLargo() > 0 && c.getAncho() > 0 && c.getAltura() > 0)) {
                throw new IllegalArgumentException("Con ese cambio «" + o.getNombre() + "» quedaría con una medida de cero o negativa.");
            }
            r.add(c);
        }
        return r;
    }

    private static double yefriLermaRedondear(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    @FXML
    private void onGuardarLote() {
        Formularios.fernandoHuancaLimpiarErrores(txtLoteLargo, txtLoteAncho, txtLoteAltura);
        YefriLermaAjuste[] ajustes = new YefriLermaAjuste[3];
        TextField[] campos = {txtLoteLargo, txtLoteAncho, txtLoteAltura};
        boolean valido = true;
        for (int i = 0; i < 3; i++) {
            try {
                ajustes[i] = yefriLermaLeerAjuste(campos[i].getText());
            } catch (NumberFormatException e) {
                Formularios.fernandoHuancaMarcarError(campos[i], "Escriba un número (0,50) o un cambio (+0,05 / -0,05).");
                valido = false;
            }
        }
        if (!valido) return;
        if (ajustes[0] == null && ajustes[1] == null && ajustes[2] == null) {
            Formularios.fernandoHuancaMarcarError(txtLoteLargo, "Llene al menos una medida para cambiar.");
            return;
        }
        List<Elemento> propuestos;
        try {
            propuestos = yefriLermaAplicarAjustes(yefriLermaLote, ajustes[0], ajustes[1], ajustes[2]);
        } catch (IllegalArgumentException e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            return;
        }
        List<Elemento> antes = yefriLermaLote.stream().map(ModeloController::yefriLermaCopia).toList();
        Long proyectoId = cmbProyecto.getValue() == null ? null : cmbProyecto.getValue().getId();
        yefriLermaConfirmarSiSueltaVigas(propuestos, () -> {
            try {
                var nuevos = yefriLermaServicio.yefriLermaGuardarVarios(propuestos);
                yefriLermaRegistrarPaso(new YefriLermaPasoEdicion("Editar " + nuevos.size() + " elementos", proyectoId, antes,
                        nuevos.stream().map(ModeloController::yefriLermaCopia).toList()));
                Set<Long> ids = nuevos.stream().map(Elemento::getId).collect(Collectors.toSet());
                yefriLermaNotificador.fernandoHuancaCerrarPanel();
                yefriLermaCargarElementos();
                yefriLermaSeleccionarIds(ids);
                yefriLermaNotificador.exito(nuevos.size() + " elementos actualizados. Si no era lo que querías, pulsa «Deshacer» (Ctrl+Z).");
            } catch (Exception e) {
                yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            }
        });
    }

    /**
     * Antes de guardar: si con estos cambios alguna viga deja de unir dos columnas (por ejemplo, al poner una medida fija a
     * vigas en X y en Y a la vez), pide confirmación. Si no, guarda directamente.
     */
    private void yefriLermaConfirmarSiSueltaVigas(List<Elemento> cambiados, Runnable guardar) {
        List<String> sueltas = yefriLermaVigasQueSeSueltan(List.copyOf(yefriLermaTodos), cambiados);
        if (sueltas.isEmpty()) {
            guardar.run();
            return;
        }
        String lista = String.join(", ", sueltas.stream().limit(6).toList()) + (sueltas.size() > 6 ? "…" : "");
        yefriLermaNotificador.fernandoHuancaConfirmar("Vigas que quedarían sueltas",
                "Con este cambio " + sueltas.size() + " viga(s) dejarían de unir dos columnas (" + lista + ") y el análisis sísmico ya no las "
                        + "tomaría como pórtico.\n\nRecuerda: en una viga en dirección X, «Largo eje X» es su longitud; en una viga en Y, lo es "
                        + "«Ancho eje Y». ¿Guardar de todos modos? (Se puede deshacer.)", "Guardar igual", guardar);
    }

    /** Nombres de las vigas que unían dos columnas y dejarían de hacerlo si se guardan {@code cambiados}. */
    static List<String> yefriLermaVigasQueSeSueltan(List<Elemento> actuales, List<Elemento> cambiados) {
        try {
            var antes = new HashSet<>(com.concretolab.service.FernandoHuancaAnalisisSismico.fernandoHuancaConexionVigas(actuales).idsSueltas());
            var porId = cambiados.stream().filter(e -> e.getId() != null).collect(Collectors.toMap(Elemento::getId, e -> e, (a, b) -> b));
            List<Elemento> despues = new ArrayList<>(actuales.stream().map(e -> porId.getOrDefault(e.getId(), e)).toList());
            cambiados.stream().filter(e -> e.getId() == null).forEach(despues::add);
            var conexion = com.concretolab.service.FernandoHuancaAnalisisSismico.fernandoHuancaConexionVigas(despues);
            List<String> nuevas = new ArrayList<>();
            for (int i = 0; i < conexion.idsSueltas().size(); i++) {
                Long id = conexion.idsSueltas().get(i);
                if (id == null || !antes.contains(id)) nuevas.add(conexion.sueltas().get(i));
            }
            return nuevas;
        } catch (IllegalArgumentException sinPorticos) {
            return List.of(); // proyecto sin columnas o sin vigas: no hay pórticos que romper
        }
    }

    // ------------------------------------------------------ deshacer

    private void yefriLermaRegistrarPaso(YefriLermaPaso paso) {
        yefriLermaHistorial.push(paso);
        while (yefriLermaHistorial.size() > YEFRI_LERMA_MAXIMO_DESHACER) yefriLermaHistorial.removeLast();
        yefriLermaActualizarDeshacer();
    }

    private void yefriLermaLimpiarHistorial() {
        yefriLermaHistorial.clear();
        yefriLermaActualizarDeshacer();
    }

    private void yefriLermaActualizarDeshacer() {
        boolean hay = !yefriLermaHistorial.isEmpty();
        btnDeshacer.setDisable(!hay || !yefriLermaEdicionHabilitada);
        btnDeshacer.setTooltip(new Tooltip(hay ? "Deshacer: " + yefriLermaHistorial.peek().descripcion() + " (Ctrl+Z). Quedan "
                + yefriLermaHistorial.size() + " paso(s)." : "No hay cambios para deshacer"));
    }

    /** Ctrl+Z: solo cuando se ve esta pantalla, sin paneles abiertos ni un movimiento en curso. */
    private void yefriLermaDeshacerConAtajo() {
        if (tabla.getScene() == null || !yefriLermaEdicionHabilitada || yefriLermaNotificador.fernandoHuancaHayVentanaAbierta()) return;
        onDeshacer();
    }

    @FXML
    private void onDeshacer() {
        if (yefriLermaHistorial.isEmpty() || !yefriLermaEdicionHabilitada) return;
        YefriLermaPaso paso = yefriLermaHistorial.pop();
        try {
            Set<Long> ids = new HashSet<>();
            if (paso instanceof YefriLermaPasoMovimiento m) {
                yefriLermaMovimientos.walterQuispesayhuaDeshacer(m.operacion());
                ids.add(m.operacion().vista().original().id());
            } else if (paso instanceof YefriLermaPasoEdicion ed) {
                yefriLermaDeshacerEdicion(ed);
                ed.antes().forEach(e -> ids.add(e.getId()));
            }
            yefriLermaCargarElementos();
            yefriLermaSeleccionarIds(ids);
            yefriLermaNotificador.exito("Se deshizo: " + paso.descripcion() + ".");
        } catch (Exception e) {
            yefriLermaCargarElementos();
            yefriLermaNotificador.fernandoHuancaError("No se pudo deshacer «" + paso.descripcion() + "»: " + yefriLermaMensaje(e));
        }
        yefriLermaActualizarDeshacer();
    }

    /** Vuelve a guardar el estado anterior, solo si los elementos siguen como quedaron tras ese cambio. */
    private void yefriLermaDeshacerEdicion(YefriLermaPasoEdicion paso) {
        var actuales = yefriLermaServicio.elementos(paso.proyectoId()).stream().collect(Collectors.toMap(Elemento::getId, e -> e));
        for (Elemento d : paso.despues()) {
            Elemento actual = actuales.get(d.getId());
            if (actual == null) throw new IllegalStateException("«" + d.getNombre() + "» ya no existe.");
            if (!yefriLermaMismoEstado(actual, d)) throw new IllegalStateException("«" + d.getNombre() + "» cambió después de ese paso.");
        }
        yefriLermaServicio.yefriLermaGuardarVarios(paso.antes().stream().map(ModeloController::yefriLermaCopia).toList());
    }

    static Elemento yefriLermaCopia(Elemento o) {
        Elemento c = new Elemento();
        c.setId(o.getId()); c.setProyectoId(o.getProyectoId()); c.setTipo(o.getTipo()); c.setNombre(o.getNombre());
        c.setLargo(o.getLargo()); c.setAncho(o.getAncho()); c.setAltura(o.getAltura());
        c.setX(o.getX()); c.setY(o.getY()); c.setZ(o.getZ());
        return c;
    }

    static boolean yefriLermaMismoEstado(Elemento a, Elemento b) {
        double t = 1e-9;
        return Objects.equals(a.getTipo(), b.getTipo()) && Objects.equals(a.getNombre(), b.getNombre())
                && Math.abs(a.getLargo() - b.getLargo()) < t && Math.abs(a.getAncho() - b.getAncho()) < t && Math.abs(a.getAltura() - b.getAltura()) < t
                && Math.abs(a.getX() - b.getX()) < t && Math.abs(a.getY() - b.getY()) < t && Math.abs(a.getZ() - b.getZ()) < t;
    }

    /**
     * Lo llama el análisis sísmico: muestra en el modelo los elementos indicados (por ejemplo, vigas que no llegan a las
     * columnas), ya seleccionados, con un aviso. No cambia nada.
     */
    public void yefriLermaMostrarElementos(Set<Long> ids, String aviso) {
        if (panelMovimiento.isVisible()) yefriLermaEditorMovimiento.walterQuispesayhuaCerrar();
        boolean soloVigas = !ids.isEmpty() && yefriLermaTodos.stream().filter(e -> ids.contains(e.getId())).allMatch(e -> "VIGA".equals(e.getTipo()));
        cmbFiltroTipo.setValue(soloVigas ? "Solo vigas" : YEFRI_LERMA_TODOS_LOS_TIPOS);
        yefriLermaSeleccionarIds(ids);
        if (aviso != null) yefriLermaNotificador.fernandoHuancaAdvertencia(aviso);
    }

    /**
     * Lo llama el análisis sísmico: muestra las columnas que conviene agrandar, ya seleccionadas, con el panel de lote
     * precargado. No guarda nada: el usuario revisa y pulsa «Aplicar».
     */
    public void yefriLermaPrecargarCorreccion(List<com.concretolab.service.RecomendacionSismica.Cambio> cambios, String aviso) {
        if (panelMovimiento.isVisible()) yefriLermaEditorMovimiento.walterQuispesayhuaCerrar();
        var porId = yefriLermaTodos.stream().filter(e -> e.getId() != null).collect(Collectors.toMap(Elemento::getId, e -> e));
        List<Elemento> actuales = new ArrayList<>();
        for (var c : cambios) {
            Elemento e = c.elemento().getId() == null ? null : porId.get(c.elemento().getId());
            if (e != null) actuales.add(e);
        }
        if (actuales.isEmpty()) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("El modelo cambió desde el análisis: vuelva a calcular el análisis sísmico.");
            return;
        }
        cmbFiltroTipo.setValue("Solo columnas");
        yefriLermaSeleccionarIds(actuales.stream().map(Elemento::getId).collect(Collectors.toSet()));
        yefriLermaAbrirLote(actuales, aviso,
                yefriLermaTextoCambio(cambios, actuales, true),
                yefriLermaTextoCambio(cambios, actuales, false), "");
    }

    /** Medida fija si todas las columnas quedan iguales; si no, el incremento común (+0,05). Vacío si no cambia. */
    static String yefriLermaTextoCambio(List<com.concretolab.service.RecomendacionSismica.Cambio> cambios, List<Elemento> actuales, boolean enX) {
        var porId = actuales.stream().collect(Collectors.toMap(Elemento::getId, e -> e));
        Set<Double> nuevos = new HashSet<>(), deltas = new HashSet<>();
        for (var c : cambios) {
            Elemento e = porId.get(c.elemento().getId());
            if (e == null) continue;
            double nuevo = enX ? c.largoNuevo() : c.anchoNuevo();
            double actual = enX ? e.getLargo() : e.getAncho();
            nuevos.add(yefriLermaRedondear(nuevo));
            deltas.add(yefriLermaRedondear(nuevo - actual));
        }
        if (deltas.isEmpty() || (deltas.size() == 1 && deltas.contains(0.0))) return "";
        if (nuevos.size() == 1) return yefriLermaComa(nuevos.iterator().next());
        double d = deltas.stream().mapToDouble(x -> x).max().orElse(0);
        return (d >= 0 ? "+" : "") + yefriLermaComa(d);
    }

    private static String yefriLermaComa(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------ selección

    private void yefriLermaActualizarSeleccion() {
        int n = tabla.getSelectionModel().getSelectedItems().size();
        lblSeleccion.setText(n <= 1 ? tabla.getItems().size() + " en la lista" : n + " seleccionados");
    }

    /** Selecciona solo {@code e}; si el filtro lo oculta, vuelve a «Todos los tipos». */
    private void yefriLermaSeleccionarSolo(Elemento e) {
        if (!tabla.getItems().contains(e)) cmbFiltroTipo.setValue(YEFRI_LERMA_TODOS_LOS_TIPOS);
        tabla.getSelectionModel().clearSelection();
        tabla.getSelectionModel().select(e);
        tabla.scrollTo(e);
    }

    private void yefriLermaSeleccionarIds(Set<Long> ids) {
        tabla.getSelectionModel().clearSelection();
        int[] indices = java.util.stream.IntStream.range(0, tabla.getItems().size())
                .filter(i -> ids.contains(tabla.getItems().get(i).getId())).toArray();
        if (indices.length > 0) {
            // una sola llamada: así el visor 3D se redibuja una vez y no por cada fila
            tabla.getSelectionModel().selectIndices(indices[0], java.util.Arrays.copyOfRange(indices, 1, indices.length));
        }
        var primero = tabla.getSelectionModel().getSelectedItems().stream().findFirst();
        primero.ifPresent(tabla::scrollTo);
    }

    /**
     * Clic en el visor 3D. Con Ctrl suma o quita el elemento de la selección (aunque sea de otro tipo: el filtro pasa a
     * «Todos los tipos»). Sin Ctrl deja seleccionado solo ese; si ya era parte de una selección múltiple espera el tiempo
     * de doble clic, porque un doble clic sobre la selección debe editar todos.
     */
    private void yefriLermaClicEn3d(Elemento e, boolean agregar) {
        yefriLermaCancelarClicPendiente();
        var seleccion = tabla.getSelectionModel().getSelectedItems();
        if (agregar) {
            Set<Long> ids = seleccion.stream().map(Elemento::getId).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            if (!ids.remove(e.getId())) ids.add(e.getId());
            if (!tabla.getItems().contains(e)) cmbFiltroTipo.setValue(YEFRI_LERMA_TODOS_LOS_TIPOS);
            yefriLermaSeleccionarIds(ids);
            return;
        }
        if (seleccion.size() > 1 && seleccion.contains(e)) {
            yefriLermaClicPendiente = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(ErickDelgadoVisor3D.ERICK_DELGADO_DOBLE_CLIC_MS + 30));
            yefriLermaClicPendiente.setOnFinished(ev -> yefriLermaSeleccionarSolo(e));
            yefriLermaClicPendiente.play();
            return;
        }
        if (seleccion.size() == 1 && seleccion.get(0) == e) return;
        yefriLermaSeleccionarSolo(e);
    }

    private void yefriLermaCancelarClicPendiente() {
        if (yefriLermaClicPendiente != null) {
            yefriLermaClicPendiente.stop();
            yefriLermaClicPendiente = null;
        }
    }

    /**
     * Doble clic en el visor 3D: si el elemento es parte de una selección múltiple abre «Editar N elementos» para todos;
     * si no, «Editar elemento» (con el botón «Mover en 3D»).
     */
    private void yefriLermaEditarDesde3d(Elemento e) {
        yefriLermaCancelarClicPendiente();
        if (panelMovimiento.isVisible()) return;
        var seleccion = List.copyOf(tabla.getSelectionModel().getSelectedItems());
        if (seleccion.size() > 1 && seleccion.contains(e)) {
            yefriLermaAbrirLote(seleccion, null, "", "", "");
            return;
        }
        yefriLermaSeleccionarSolo(e);
        yefriLermaEditar(e);
    }

    /** Doble clic en una fila de la tabla: igual que en 3D, sobre una selección múltiple edita todas. */
    private void yefriLermaDobleClicFila(Elemento e) {
        var previa = yefriLermaSeleccionPrevia;
        if (previa.size() > 1 && previa.contains(e)) {
            yefriLermaSeleccionarIds(previa.stream().map(Elemento::getId).collect(Collectors.toSet()));
            yefriLermaAbrirLote(previa, null, "", "", "");
            return;
        }
        yefriLermaEditar(e);
    }

    @FXML
    private void onEliminarProyecto() {
        Proyecto seleccionado = cmbProyecto.getValue();
        if (seleccionado == null) return;
        yefriLermaNotificador.fernandoHuancaConfirmar("Eliminar proyecto",
                "¿Eliminar «" + seleccionado.getNombre() + "»? Se borrarán sus elementos, mezclas y ensayos. "
                        + "Esta acción es permanente.", "Eliminar proyecto", () -> {
                    try {
                        yefriLermaServicio.yefriLermaEliminarProyecto(seleccionado.getId());
                        yefriLermaLimpiarHistorial();
                        yefriLermaCargarProyectos(null);
                        yefriLermaCargarElementos();
                        yefriLermaNotificador.exito("Proyecto eliminado.");
                    } catch (Exception e) {
                        yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
                    }
                });
    }

    // ------------------------------------------------------ plantilla rectangular

    @FXML
    private void onPlantilla() {
        if (!yefriLermaHayProyecto()) {
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(yefriLermaCamposPlantilla().toArray(new TextField[0]));
        yefriLermaCamposPlantilla().forEach(TextField::clear);
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelPlantilla);
        Platform.runLater(txtSeparacionX::requestFocus);
    }

    @FXML
    private void onCuadricula() {
        if (!yefriLermaHayProyecto()) {
            return;
        }
        try {
            if (yefriLermaEditorCuadricula.walterQuispesayhuaAbrir(cmbProyecto.getValue().getId(), List.copyOf(yefriLermaTodos))) {
                yefriLermaCargarElementos();
                yefriLermaNotificador.exito("Elementos generados.");
            }
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    @FXML
    private void onMoverElemento() {
        Elemento seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) return;
        try {
            yefriLermaHabilitarEdicion(false);
            yefriLermaEditorMovimiento.walterQuispesayhuaIniciar(seleccionado);
        } catch (Exception e) {
            yefriLermaEditorMovimiento.walterQuispesayhuaCerrar();
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    private void yefriLermaHabilitarEdicion(boolean habilitada) {
        cmbProyecto.setDisable(!habilitada);
        btnNuevoProyecto.setDisable(!habilitada);
        btnNuevoElemento.setDisable(!habilitada);
        btnPlantilla.setDisable(!habilitada);
        btnCuadricula.setDisable(!habilitada);
        tabla.setDisable(!habilitada);
        cmbFiltroTipo.setDisable(!habilitada);
        yefriLermaEdicionHabilitada = habilitada;
        yefriLermaActualizarDeshacer();
    }

    @FXML
    private void onGuardarPlantilla() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione o cree un proyecto.");
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(yefriLermaCamposPlantilla().toArray(new TextField[0]));
        double[] v = new double[9];
        List<TextField> campos = yefriLermaCamposPlantilla();
        boolean valido = true;
        for (int i = 0; i < campos.size(); i++) {
            Double n = yefriLermaNumero(campos.get(i), "Ingrese un número válido.");
            if (n == null) {
                valido = false;
            } else {
                v[i] = n;
            }
        }
        if (!valido) {
            return;
        }
        try {
            yefriLermaServicio.yefriLermaCrearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
            yefriLermaNotificador.fernandoHuancaCerrarPanel();
            yefriLermaNotificador.exito("Plantilla rectangular creada: 4 zapatas, 4 columnas y 4 vigas.");
            yefriLermaCargarElementos();
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    @FXML
    private void onCerrarPanel() {
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
    }

    // ----------------------------------------------------------------- apoyo

    private List<TextField> yefriLermaCamposPlantilla() {
        return List.of(txtSeparacionX, txtSeparacionY, txtLargoZapata, txtAnchoZapata, txtAlturaZapata,
                txtLadoColumna, txtAlturaColumna, txtAnchoViga, txtAlturaViga);
    }

    /** Recarga el combo y deja seleccionado (y en la sesion) el proyecto con ese id, si existe. */
    private void yefriLermaCargarProyectos(Long idActivo) {
        yefriLermaSincronizando = true;
        try {
            List<Proyecto> lista = yefriLermaServicio.proyectos();
            cmbProyecto.setItems(FXCollections.observableArrayList(lista));
            Proyecto activo = idActivo == null ? null : lista.stream()
                    .filter(p -> idActivo.equals(p.getId()))
                    .findFirst().orElse(null);
            cmbProyecto.setValue(activo);
            yefriLermaSesion.setProyecto(activo);
        } finally {
            yefriLermaSincronizando = false;
        }
    }

    private void yefriLermaCargarElementos() {
        Proyecto p = cmbProyecto.getValue();
        tabla.getSelectionModel().clearSelection();
        if (p == null) {
            yefriLermaTodos.clear();
            yefriLermaActualizarMetrado();
            yefriLermaActualizarSeleccion();
            yefriLermaDibujar(true);
            return;
        }
        yefriLermaTodos.setAll(yefriLermaServicio.elementos(p.getId()));
        yefriLermaActualizarMetrado();
        yefriLermaActualizarSeleccion();
        yefriLermaDibujar(true);
    }

    /** Metrado de concreto neto (sin doble conteo en cruces) con encofrado y acero referenciales por tipo. */
    static String yefriLermaResumenMetrado(java.util.List<Elemento> elementos) {
        if (elementos.isEmpty()) {
            return "El proyecto no tiene elementos.";
        }
        var cuantias = com.concretolab.service.CuantiasAcero.yefriLermaLeer();
        var m = com.concretolab.service.MetradoNeto.yefriLermaCalcular(elementos, cuantias);
        boolean propias = !cuantias.equals(com.concretolab.service.MetradoNeto.yefriLermaCuantiasReferenciales());
        var sb = new StringBuilder();
        sb.append("Concreto neto ").append(yefriLermaVolumen(m.volumenNeto())).append(" m³ (bruto ").append(yefriLermaVolumen(m.volumenBruto()))
                .append(", cruces ").append(yefriLermaVolumen(m.descontado())).append(")\n");
        sb.append("Por tipo: concreto neto · encofrado · acero ref.\n");
        for (var f : m.filas()) {
            sb.append(f.tipo()).append(" ×").append(f.cantidad()).append(" · ").append(yefriLermaVolumen(f.volumenNeto())).append(" m³ · ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", f.encofradoM2())).append(" m² · ")
                    .append(String.format(java.util.Locale.ROOT, "%.0f", f.aceroKg())).append(" kg\n");
        }
        sb.append("Total ").append(String.format(java.util.Locale.ROOT, "%.1f", m.encofradoM2()))
                .append(" m² · ").append(String.format(java.util.Locale.ROOT, "%.0f", m.aceroKg()))
                .append(propias ? " kg (cuantías propias, anteproyecto)" : " kg (cuantías típicas, anteproyecto)");
        return sb.toString();
    }

    /** Edita las cuantías de acero (kg/m³) que usa el metrado; se guardan en la carpeta de datos. */
    @FXML
    private void onEditarCuantias() {
        var actuales = com.concretolab.service.CuantiasAcero.yefriLermaLeer();
        var dialogo = new javafx.scene.control.Dialog<java.util.Map<String, Double>>();
        dialogo.initOwner(tabla.getScene().getWindow());
        dialogo.setTitle("Cuantías de acero");
        dialogo.setHeaderText("Cuantía de acero por tipo de elemento (kg por m³ de concreto)");
        var grilla = new javafx.scene.layout.GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        var campos = new java.util.LinkedHashMap<String, TextField>();
        int fila = 0;
        for (var e : actuales.entrySet()) {
            var campo = new TextField(String.format(java.util.Locale.ROOT, "%.0f", e.getValue()));
            campo.setPrefColumnCount(6);
            grilla.addRow(fila++, new javafx.scene.control.Label(e.getKey()), campo);
            campos.put(e.getKey(), campo);
        }
        var aviso = new javafx.scene.control.Label("Valores de 0 a " + (int) com.concretolab.service.CuantiasAcero.YEFRI_LERMA_MAXIMO
                + " kg/m³. Use las del proyecto o de un metrado similar; las referenciales son solo para anteproyecto.");
        aviso.setWrapText(true);
        aviso.setPrefWidth(360);
        var restaurar = new Button("Restaurar referenciales");
        restaurar.setOnAction(ev -> com.concretolab.service.MetradoNeto.yefriLermaCuantiasReferenciales()
                .forEach((k, v) -> campos.get(k).setText(String.format(java.util.Locale.ROOT, "%.0f", v))));
        var contenido = new javafx.scene.layout.VBox(10, grilla, restaurar, aviso);
        contenido.setPadding(new javafx.geometry.Insets(10));
        dialogo.getDialogPane().setContent(contenido);
        var guardar = new javafx.scene.control.ButtonType("Guardar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, javafx.scene.control.ButtonType.CANCEL);
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                var valores = new java.util.LinkedHashMap<String, Double>();
                campos.forEach((k, c) -> valores.put(k, Double.parseDouble(c.getText().trim().replace(',', '.'))));
                com.concretolab.service.CuantiasAcero.yefriLermaGuardar(valores);
            } catch (NumberFormatException ex) {
                aviso.setText("Ingrese números válidos en todas las cuantías.");
                ev.consume();
            } catch (IllegalArgumentException | java.io.IOException ex) {
                aviso.setText(ex.getMessage());
                ev.consume();
            }
        });
        dialogo.showAndWait();
        if (cmbProyecto.getValue() != null) {
            yefriLermaActualizarMetrado();
        }
    }

    /** Texto corto en el botón («Metrado · 57,86 m³») y texto completo para el globo. */
    private void yefriLermaActualizarMetrado() {
        if (cmbProyecto.getValue() == null) {
            lblResumen.setText("Seleccione un proyecto");
            btnMetrado.setText("Metrado");
        } else {
            lblResumen.setText(yefriLermaResumenMetrado(yefriLermaTodos));
            btnMetrado.setText(yefriLermaTodos.isEmpty() ? "Metrado"
                    : "Metrado · " + yefriLermaVolumenCorto(com.concretolab.service.MetradoNeto
                            .yefriLermaCalcular(yefriLermaTodos, com.concretolab.service.CuantiasAcero.yefriLermaLeer()).volumenNeto()) + " m³");
        }
        if (yefriLermaGloboMetrado != null && yefriLermaGloboMetrado.isShowing()) yefriLermaGloboMetrado.hide();
    }

    /** Muestra u oculta el metrado completo en un globo junto al botón, sin ocupar espacio de la tabla. */
    @FXML
    private void onVerMetrado() {
        if (yefriLermaGloboMetrado != null && yefriLermaGloboMetrado.isShowing()) {
            yefriLermaGloboMetrado.hide();
            return;
        }
        var texto = new Label(lblResumen.getText());
        texto.setWrapText(true);
        texto.setMaxWidth(420);
        texto.setMinHeight(Label.USE_PREF_SIZE);
        texto.getStyleClass().add("modelo-resumen");
        yefriLermaGloboMetrado = new Popover(texto);
        yefriLermaGloboMetrado.setTitle("Metrado del proyecto");
        yefriLermaGloboMetrado.setHeaderAlwaysVisible(true);
        yefriLermaGloboMetrado.setDetachable(false);
        yefriLermaGloboMetrado.setArrowLocation(Popover.ArrowLocation.BOTTOM_LEFT);
        yefriLermaGloboMetrado.show(btnMetrado);
    }

    private static String yefriLermaVolumenCorto(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Reencuadra la camara al cargar datos, pero no al seleccionar una fila. */
    private void yefriLermaDibujar(boolean reencuadrar) {
        Set<Elemento> marcados = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        marcados.addAll(tabla.getSelectionModel().getSelectedItems());
        yefriLermaVisor3d.erickDelgadoMostrar(yefriLermaTodos, marcados::contains,
                this::yefriLermaClicEn3d, this::yefriLermaEditarDesde3d, reencuadrar);
    }

    private boolean yefriLermaHayProyecto() {
        if (cmbProyecto.getValue() == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione o cree un proyecto.");
            return false;
        }
        return true;
    }

    private Double yefriLermaNumero(TextField campo) {
        return yefriLermaNumero(campo, "Ingrese un número válido.");
    }

    /** Lee un decimal (acepta coma); si no es valido marca el campo y devuelve {@code null}. */
    private Double yefriLermaNumero(TextField campo, String mensajeError) {
        try {
            return Double.parseDouble(campo.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            Formularios.fernandoHuancaMarcarError(campo, mensajeError);
            return null;
        }
    }

    private String yefriLermaMensaje(Exception e) {
        return e.getMessage() == null ? "No se pudo completar la operación." : e.getMessage();
    }

    /** Mismo formato que {@code FormatoNumeros.volumen} (esa clase no es visible fuera de {@code ui}). */
    private static String yefriLermaVolumen(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }
}
