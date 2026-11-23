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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
    @FXML private Button btnDeshacerMovimiento;
    @FXML private VBox panelMovimiento;
    @FXML private Label lblResumen;
    @FXML private StackPane contenedorVisor;
    @FXML private Button btnExportarDxf;

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
    private Operacion yefriLermaUltimoMovimiento;

    @FXML
    public void initialize() {
        yefriLermaVisor3d = new ErickDelgadoVisor3D();
        contenedorVisor.getChildren().add(yefriLermaVisor3d.erickDelgadoNodo());

        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colVolumen.setCellValueFactory(c -> new SimpleStringProperty(yefriLermaVolumen(c.getValue().volumen())));
        Tablas.fernandoHuancaConfigurar(tabla, "Agregue elementos al proyecto", this::yefriLermaEditar);
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> yefriLermaDibujar(false));

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnEliminar.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnMover.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnDeshacerMovimiento.setDisable(true);
        yefriLermaEditorMovimiento = new EditorMovimiento(yefriLermaMovimientos, yefriLermaVisor3d, panelMovimiento, guardado -> {
            yefriLermaUltimoMovimiento = guardado;
            btnDeshacerMovimiento.setDisable(false);
            yefriLermaCargarElementos();
            yefriLermaNotificador.exito("Movimiento guardado.");
        }, () -> {
            yefriLermaHabilitarEdicion(true);
            yefriLermaDibujar(false);
        });
        btnEliminarProyecto.disableProperty().bind(cmbProyecto.valueProperty().isNull().or(panelMovimiento.visibleProperty()));
        btnExportarDxf.disableProperty().bind(cmbProyecto.valueProperty().isNull()
                .or(Bindings.isEmpty(tabla.getItems())).or(panelMovimiento.visibleProperty()));

        cmbProyecto.valueProperty().addListener((o, a, b) -> {
            if (yefriLermaSincronizando) {
                return;
            }
            yefriLermaSesion.setProyecto(b);
            yefriLermaUltimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
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
        if (yefriLermaUltimoMovimiento != null && (activo == null || !yefriLermaUltimoMovimiento.vista().original().proyectoId().equals(activo.getId()))) {
            yefriLermaUltimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
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
        yefriLermaEditar(tabla.getSelectionModel().getSelectedItem());
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
            Elemento guardado = yefriLermaServicio.yefriLermaGuardar(elemento);
            yefriLermaNotificador.fernandoHuancaCerrarPanel();
            yefriLermaNotificador.exito(esNuevo
                    ? "Elemento \"" + guardado.getNombre() + "\" registrado."
                    : "Elemento \"" + guardado.getNombre() + "\" actualizado.");
            yefriLermaCargarElementos();
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
        }
    }

    @FXML
    private void onEliminarElemento() {
        Elemento seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        yefriLermaNotificador.fernandoHuancaConfirmar("Eliminar elemento", "¿Eliminar el elemento seleccionado?", "Eliminar", () -> {
            try {
                yefriLermaServicio.yefriLermaEliminar(seleccionado.getId());
                yefriLermaNotificador.exito("Elemento eliminado.");
                yefriLermaCargarElementos();
            } catch (Exception e) {
                yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            }
        });
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
                        yefriLermaUltimoMovimiento = null;
                        btnDeshacerMovimiento.setDisable(true);
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
            if (yefriLermaEditorCuadricula.walterQuispesayhuaAbrir(cmbProyecto.getValue().getId(), List.copyOf(tabla.getItems()))) {
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

    @FXML
    private void onDeshacerMovimiento() {
        if (yefriLermaUltimoMovimiento == null) return;
        try {
            yefriLermaMovimientos.walterQuispesayhuaDeshacer(yefriLermaUltimoMovimiento);
            yefriLermaUltimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
            yefriLermaCargarElementos();
            yefriLermaNotificador.exito("Movimiento deshecho.");
        } catch (Exception e) {
            yefriLermaUltimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
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
        btnDeshacerMovimiento.setDisable(!habilitada || yefriLermaUltimoMovimiento == null);
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
        tabla.getItems().clear();
        if (p == null) {
            lblResumen.setText("Seleccione un proyecto");
            yefriLermaDibujar(true);
            return;
        }
        tabla.getItems().setAll(yefriLermaServicio.elementos(p.getId()));
        lblResumen.setText(yefriLermaResumenMetrado(tabla.getItems()));
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
            lblResumen.setText(yefriLermaResumenMetrado(tabla.getItems()));
        }
    }

    /** Reencuadra la camara al cargar datos, pero no al seleccionar una fila. */
    private void yefriLermaDibujar(boolean reencuadrar) {
        yefriLermaVisor3d.erickDelgadoMostrar(tabla.getItems(), tabla.getSelectionModel().getSelectedItem(),
                e -> tabla.getSelectionModel().select(e), reencuadrar);
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
