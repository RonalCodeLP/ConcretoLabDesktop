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

@Component
@RequiredArgsConstructor
public class ModeloController implements Refrescable {
    private final YefriLermaProyectoService yefriLermaServicio;
    private final com.concretolab.service.FernandoHuancaPlanoDxf yefriLermaImportadorDxf;

    @FXML private void onExportarDxf() {
        Proyecto activo = yefriLermaSesion.getProyecto();
        if (activo == null) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Seleccione el proyecto que desea exportar.").showAndWait();
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
                    "DXF guardado. Abra el archivo en AutoCAD y seleccione una vista isométrica.").showAndWait();
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

    private ErickDelgadoVisor3D yefriLermaVisor3d;

    @FXML private ComboBox<Proyecto> cmbProyecto;
    @FXML private MenuButton btnNuevoProyecto;
    @FXML private Button btnEliminarProyecto;

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
    private Label lblResumen;
    @FXML private Button btnMetrado;
    @FXML private StackPane contenedorVisor;
    @FXML private Button btnExportarDxf;

    @FXML private ComboBox<String> cmbFiltroTipo;
    @FXML private Button btnSeleccionarTodo;
    @FXML private Label lblSeleccion;
    private final ObservableList<Elemento> yefriLermaTodos = FXCollections.observableArrayList();
    private FilteredList<Elemento> yefriLermaFiltrados;
    static final String YEFRI_LERMA_TODOS_LOS_TIPOS = "Todos los tipos";
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

    @FXML private VBox panelLote;
    @FXML private Label lblTituloLote;
    @FXML private Label lblAvisoLote;
    @FXML private Label lblDetalleLote;
    @FXML private TextField txtLoteLargo;
    @FXML private TextField txtLoteAncho;
    @FXML private TextField txtLoteAltura;
    @FXML private Button btnAplicarLote;
    @FXML private Button btnMoverDesdeEditar;
    private List<Elemento> yefriLermaLote = List.of();
    private Popover yefriLermaGloboMetrado;
    private List<Elemento> yefriLermaSeleccionPrevia = List.of();
    private javafx.animation.PauseTransition yefriLermaClicPendiente;

    @FXML private VBox panelProyecto;
    @FXML private TextField txtNombreProyecto;
    @FXML private TextField txtUbicacionProyecto;

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

    private boolean yefriLermaSincronizando;
    private Long yefriLermaIdEditando;
    private EditorMovimiento yefriLermaEditorMovimiento;
    private final java.util.Deque<YefriLermaPaso> yefriLermaHistorial = new java.util.ArrayDeque<>();
    static final int YEFRI_LERMA_MAXIMO_DESHACER = 20;
    private boolean yefriLermaEdicionHabilitada = true;

    sealed interface YefriLermaPaso permits YefriLermaPasoEdicion, YefriLermaPasoMovimiento {
        String descripcion();
        Long proyectoId();
    }

    record YefriLermaPasoEdicion(String descripcion, Long proyectoId, List<Elemento> antes, List<Elemento> despues) implements YefriLermaPaso { }

    record YefriLermaPasoMovimiento(String descripcion, Long proyectoId, Operacion operacion) implements YefriLermaPaso { }

    @FXML
    public void initialize() {
        yefriLermaVisor3d = new ErickDelgadoVisor3D();
        contenedorVisor.getChildren().add(yefriLermaVisor3d.erickDelgadoNodo());

        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colVolumen.setCellValueFactory(c -> new SimpleStringProperty(yefriLermaVolumen(c.getValue().volumen())));
        Tablas.fernandoHuancaConfigurar(tabla, "Agregue elementos al proyecto", this::yefriLermaDobleClicFila);
        tabla.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getClickCount() == 1) yefriLermaSeleccionPrevia = List.copyOf(tabla.getSelectionModel().getSelectedItems());
        });

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

    record YefriLermaAjuste(boolean relativo, double valor) {
        double aplicar(double actual) { return relativo ? actual + valor : valor; }
    }

    static YefriLermaAjuste yefriLermaLeerAjuste(String texto) {
        String t = texto == null ? "" : texto.trim().replace(',', '.').replace(" ", "");
        if (t.isEmpty()) return null;
        boolean relativo = t.startsWith("+") || t.startsWith("-");
        double v = Double.parseDouble(t);
        if (!Double.isFinite(v)) throw new NumberFormatException(t);
        return new YefriLermaAjuste(relativo, v);
    }

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
                yefriLermaNotificador.exito(nuevos.size() + " elementos actualizados. Si no era lo que buscaba, pulse «Deshacer» (Ctrl+Z).");
            } catch (Exception e) {
                yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            }
        });
    }

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
            return List.of();
        }
    }

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

    public void yefriLermaMostrarElementos(Set<Long> ids, String aviso) {
        if (panelMovimiento.isVisible()) yefriLermaEditorMovimiento.walterQuispesayhuaCerrar();
        boolean soloVigas = !ids.isEmpty() && yefriLermaTodos.stream().filter(e -> ids.contains(e.getId())).allMatch(e -> "VIGA".equals(e.getTipo()));
        cmbFiltroTipo.setValue(soloVigas ? "Solo vigas" : YEFRI_LERMA_TODOS_LOS_TIPOS);
        yefriLermaSeleccionarIds(ids);
        if (aviso != null) yefriLermaNotificador.fernandoHuancaAdvertencia(aviso);
    }

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

    private void yefriLermaActualizarSeleccion() {
        int n = tabla.getSelectionModel().getSelectedItems().size();
        lblSeleccion.setText(n <= 1 ? tabla.getItems().size() + " en la lista" : n + " seleccionados");
    }

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
            tabla.getSelectionModel().selectIndices(indices[0], java.util.Arrays.copyOfRange(indices, 1, indices.length));
        }
        var primero = tabla.getSelectionModel().getSelectedItems().stream().findFirst();
        primero.ifPresent(tabla::scrollTo);
    }

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

    private List<TextField> yefriLermaCamposPlantilla() {
        return List.of(txtSeparacionX, txtSeparacionY, txtLargoZapata, txtAnchoZapata, txtAlturaZapata,
                txtLadoColumna, txtAlturaColumna, txtAnchoViga, txtAlturaViga);
    }

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

    private static String yefriLermaVolumen(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }
}
