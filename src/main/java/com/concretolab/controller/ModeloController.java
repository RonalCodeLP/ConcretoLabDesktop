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
import com.concretolab.ui.MiajeldDelgadoVisor3D;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

    private final YefriLermaProyectoService servicio;
    private final SesionProyecto sesion;
    private final Notificador notificador;
    private final EditorCuadricula editorCuadricula;
    private final MovimientoElementoService movimientos;

    /** Se crea en initialize(): construye nodos 3D de JavaFX, que no existen todavía cuando Spring instancia el controlador. */
    private MiajeldDelgadoVisor3D visor3d;

    // ---- cabecera
    @FXML private ComboBox<Proyecto> cmbProyecto;
    @FXML private Button btnNuevoProyecto;

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
    private boolean sincronizando;
    /** Id del elemento en edicion; {@code null} cuando el panel registra uno nuevo. */
    private Long idEditando;
    private EditorMovimiento editorMovimiento;
    private Operacion ultimoMovimiento;

    @FXML
    public void initialize() {
        visor3d = new MiajeldDelgadoVisor3D();
        contenedorVisor.getChildren().add(visor3d.nodo());

        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colVolumen.setCellValueFactory(c -> new SimpleStringProperty(volumen(c.getValue().volumen())));
        Tablas.configurar(tabla, "Agregue elementos al proyecto", this::editar);
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> dibujar(false));

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnEliminar.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnMover.disableProperty().bind(seleccion.isNull().or(panelMovimiento.visibleProperty()));
        btnDeshacerMovimiento.setDisable(true);
        editorMovimiento = new EditorMovimiento(movimientos, visor3d, panelMovimiento, guardado -> {
            ultimoMovimiento = guardado;
            btnDeshacerMovimiento.setDisable(false);
            cargarElementos();
            notificador.exito("Movimiento guardado.");
        }, () -> {
            habilitarEdicion(true);
            dibujar(false);
        });

        cmbProyecto.valueProperty().addListener((o, a, b) -> {
            if (sincronizando) {
                return;
            }
            sesion.setProyecto(b);
            ultimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
            cargarElementos();
        });

        cmbTipo.setItems(FXCollections.observableArrayList("ZAPATA", "COLUMNA", "VIGA"));
        cmbTipo.valueProperty().addListener((o, a, b) -> Formularios.limpiarError(cmbTipo));
        Formularios.limpiarAlEditar(txtNombreProyecto, txtUbicacionProyecto, txtNombreElemento,
                txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        Formularios.limpiarAlEditar(camposPlantilla().toArray(new TextField[0]));
    }

    @Override
    public void refrescar() {
        Proyecto activo = sesion.getProyecto();
        if (panelMovimiento.isVisible()) editorMovimiento.cerrar();
        if (ultimoMovimiento != null && (activo == null || !ultimoMovimiento.vista().original().proyectoId().equals(activo.getId()))) {
            ultimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
        }
        cargarProyectos(activo == null ? null : activo.getId());
        cargarElementos();
    }

    // ------------------------------------------------------------- proyecto

    @FXML
    private void onNuevoProyecto() {
        Formularios.limpiarErrores(txtNombreProyecto, txtUbicacionProyecto);
        txtNombreProyecto.clear();
        txtUbicacionProyecto.clear();
        notificador.abrirPanel(panelProyecto);
        Platform.runLater(txtNombreProyecto::requestFocus);
    }

    @FXML
    private void onGuardarProyecto() {
        Formularios.limpiarErrores(txtNombreProyecto, txtUbicacionProyecto);
        String nombre = txtNombreProyecto.getText();
        String ubicacion = txtUbicacionProyecto.getText();
        boolean valido = true;
        if (nombre.isBlank() || nombre.trim().length() > 120) {
            Formularios.marcarError(txtNombreProyecto, "Ingrese un nombre de proyecto de hasta 120 caracteres.");
            valido = false;
        }
        if (ubicacion.length() > 200) {
            Formularios.marcarError(txtUbicacionProyecto, "La ubicación no puede superar 200 caracteres.");
            valido = false;
        }
        if (!valido) {
            return;
        }
        try {
            Proyecto nuevo = servicio.crearProyecto(nombre, ubicacion);
            notificador.cerrarPanel();
            notificador.exito("Proyecto \"" + nuevo.getNombre() + "\" creado.");
            cargarProyectos(nuevo.getId());
            cargarElementos();
        } catch (Exception e) {
            notificador.error(mensaje(e));
        }
    }

    // ------------------------------------------------------------- elementos

    @FXML
    private void onNuevoElemento() {
        if (!hayProyecto()) {
            return;
        }
        tabla.getSelectionModel().clearSelection();
        abrirElemento(null);
    }

    @FXML
    private void onEditarElemento() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Elemento e) {
        if (e == null || !hayProyecto()) {
            return;
        }
        abrirElemento(e);
    }

    private void abrirElemento(Elemento anterior) {
        Formularios.limpiarErrores(cmbTipo, txtNombreElemento, txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        idEditando = anterior == null ? null : anterior.getId();
        lblTituloElemento.setText(anterior == null ? "Nuevo elemento" : "Editar elemento");
        cmbTipo.setValue(anterior == null ? "ZAPATA" : anterior.getTipo());
        txtNombreElemento.setText(anterior == null ? "" : anterior.getNombre());
        txtLargo.setText(Double.toString(anterior == null ? 1 : anterior.getLargo()));
        txtAncho.setText(Double.toString(anterior == null ? 1 : anterior.getAncho()));
        txtAltura.setText(Double.toString(anterior == null ? 1 : anterior.getAltura()));
        txtX.setText(Double.toString(anterior == null ? 0 : anterior.getX()));
        txtY.setText(Double.toString(anterior == null ? 0 : anterior.getY()));
        txtZ.setText(Double.toString(anterior == null ? 0 : anterior.getZ()));
        notificador.abrirPanel(panelElemento);
        Platform.runLater(txtNombreElemento::requestFocus);
    }

    @FXML
    private void onGuardarElemento() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            notificador.advertencia("Seleccione o cree un proyecto.");
            return;
        }
        Formularios.limpiarErrores(cmbTipo, txtNombreElemento, txtLargo, txtAncho, txtAltura, txtX, txtY, txtZ);
        boolean valido = true;
        if (cmbTipo.getValue() == null) {
            Formularios.marcarError(cmbTipo, "Seleccione un tipo válido.");
            valido = false;
        }
        if (txtNombreElemento.getText().isBlank()) {
            Formularios.marcarError(txtNombreElemento, "Ingrese un nombre.");
            valido = false;
        }
        Double largo = numero(txtLargo), ancho = numero(txtAncho), altura = numero(txtAltura);
        Double x = numero(txtX), y = numero(txtY), z = numero(txtZ);
        if (!valido || largo == null || ancho == null || altura == null || x == null || y == null || z == null) {
            return;
        }
        try {
            Elemento elemento = new Elemento();
            if (idEditando != null) {
                elemento.setId(idEditando);
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
            boolean esNuevo = idEditando == null;
            Elemento guardado = servicio.guardar(elemento);
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? "Elemento \"" + guardado.getNombre() + "\" registrado."
                    : "Elemento \"" + guardado.getNombre() + "\" actualizado.");
            cargarElementos();
        } catch (Exception e) {
            notificador.error(mensaje(e));
        }
    }

    @FXML
    private void onEliminarElemento() {
        Elemento seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("Eliminar elemento", "¿Eliminar el elemento seleccionado?", "Eliminar", () -> {
            try {
                servicio.eliminar(seleccionado.getId());
                notificador.exito("Elemento eliminado.");
                cargarElementos();
            } catch (Exception e) {
                notificador.error(mensaje(e));
            }
        });
    }

    // ------------------------------------------------------ plantilla rectangular

    @FXML
    private void onPlantilla() {
        if (!hayProyecto()) {
            return;
        }
        Formularios.limpiarErrores(camposPlantilla().toArray(new TextField[0]));
        camposPlantilla().forEach(TextField::clear);
        notificador.abrirPanel(panelPlantilla);
        Platform.runLater(txtSeparacionX::requestFocus);
    }

    @FXML
    private void onCuadricula() {
        if (!hayProyecto()) {
            return;
        }
        try {
            if (editorCuadricula.abrir(cmbProyecto.getValue().getId(), List.copyOf(tabla.getItems()))) {
                cargarElementos();
                notificador.exito("Elementos generados.");
            }
        } catch (Exception e) {
            notificador.error(mensaje(e));
        }
    }

    @FXML
    private void onMoverElemento() {
        Elemento seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) return;
        try {
            habilitarEdicion(false);
            editorMovimiento.iniciar(seleccionado);
        } catch (Exception e) {
            editorMovimiento.cerrar();
            notificador.error(mensaje(e));
        }
    }

    @FXML
    private void onDeshacerMovimiento() {
        if (ultimoMovimiento == null) return;
        try {
            movimientos.deshacer(ultimoMovimiento);
            ultimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
            cargarElementos();
            notificador.exito("Movimiento deshecho.");
        } catch (Exception e) {
            ultimoMovimiento = null;
            btnDeshacerMovimiento.setDisable(true);
            notificador.error(mensaje(e));
        }
    }

    private void habilitarEdicion(boolean habilitada) {
        cmbProyecto.setDisable(!habilitada);
        btnNuevoProyecto.setDisable(!habilitada);
        btnNuevoElemento.setDisable(!habilitada);
        btnPlantilla.setDisable(!habilitada);
        btnCuadricula.setDisable(!habilitada);
        tabla.setDisable(!habilitada);
        btnDeshacerMovimiento.setDisable(!habilitada || ultimoMovimiento == null);
    }

    @FXML
    private void onGuardarPlantilla() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            notificador.advertencia("Seleccione o cree un proyecto.");
            return;
        }
        Formularios.limpiarErrores(camposPlantilla().toArray(new TextField[0]));
        double[] v = new double[9];
        List<TextField> campos = camposPlantilla();
        boolean valido = true;
        for (int i = 0; i < campos.size(); i++) {
            Double n = numero(campos.get(i), "Ingrese un número válido.");
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
            servicio.crearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
            notificador.cerrarPanel();
            notificador.exito("Plantilla rectangular creada: 4 zapatas, 4 columnas y 4 vigas.");
            cargarElementos();
        } catch (Exception e) {
            notificador.error(mensaje(e));
        }
    }

    @FXML
    private void onCerrarPanel() {
        notificador.cerrarPanel();
    }

    // ----------------------------------------------------------------- apoyo

    private List<TextField> camposPlantilla() {
        return List.of(txtSeparacionX, txtSeparacionY, txtLargoZapata, txtAnchoZapata, txtAlturaZapata,
                txtLadoColumna, txtAlturaColumna, txtAnchoViga, txtAlturaViga);
    }

    /** Recarga el combo y deja seleccionado (y en la sesion) el proyecto con ese id, si existe. */
    private void cargarProyectos(Long idActivo) {
        sincronizando = true;
        try {
            List<Proyecto> lista = servicio.proyectos();
            cmbProyecto.setItems(FXCollections.observableArrayList(lista));
            Proyecto activo = idActivo == null ? null : lista.stream()
                    .filter(p -> idActivo.equals(p.getId()))
                    .findFirst().orElse(null);
            cmbProyecto.setValue(activo);
            sesion.setProyecto(activo);
        } finally {
            sincronizando = false;
        }
    }

    private void cargarElementos() {
        Proyecto p = cmbProyecto.getValue();
        tabla.getItems().clear();
        if (p == null) {
            lblResumen.setText("Seleccione un proyecto");
            dibujar(true);
            return;
        }
        tabla.getItems().setAll(servicio.elementos(p.getId()));
        double suma = tabla.getItems().stream().mapToDouble(Elemento::volumen).sum();
        lblResumen.setText("Suma bruta: " + volumen(suma) + " m³\nNo descuenta superposiciones.");
        dibujar(true);
    }

    /** Reencuadra la camara al cargar datos, pero no al seleccionar una fila. */
    private void dibujar(boolean reencuadrar) {
        visor3d.mostrar(tabla.getItems(), tabla.getSelectionModel().getSelectedItem(),
                e -> tabla.getSelectionModel().select(e), reencuadrar);
    }

    private boolean hayProyecto() {
        if (cmbProyecto.getValue() == null) {
            notificador.advertencia("Seleccione o cree un proyecto.");
            return false;
        }
        return true;
    }

    private Double numero(TextField campo) {
        return numero(campo, "Ingrese un número válido.");
    }

    /** Lee un decimal (acepta coma); si no es valido marca el campo y devuelve {@code null}. */
    private Double numero(TextField campo, String mensajeError) {
        try {
            return Double.parseDouble(campo.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            Formularios.marcarError(campo, mensajeError);
            return null;
        }
    }

    private String mensaje(Exception e) {
        return e.getMessage() == null ? "No se pudo completar la operación." : e.getMessage();
    }

    /** Mismo formato que {@code FormatoNumeros.volumen} (esa clase no es visible fuera de {@code ui}). */
    private static String volumen(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }
}
