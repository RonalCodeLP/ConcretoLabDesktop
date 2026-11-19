package com.concretolab.controller;

import com.concretolab.exception.NegocioException;
import com.concretolab.model.Material;
import com.concretolab.service.MaterialService;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Paginador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.Tablas;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MaterialesController implements Refrescable {

    private static final String SIN_SELECCION = "Seleccione un material para ver sus datos de laboratorio.";
    private static final String TIPO_POR_DEFECTO = "AGREGADO FINO";
    private static final String NUMERO_INVALIDO = "Ingrese números válidos en los resultados de ensayo.";

    private final MaterialService servicio;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private ComboBox<String> cmbTipo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtProcedencia;
    @FXML private TextField txtReferenciaEnsayo;
    @FXML private TextField txtFechaEnsayo;
    @FXML private TextField txtDensidadRelativa;
    @FXML private TextField txtAbsorcion;
    @FXML private TextField txtHumedad;
    @FXML private TextField txtModuloFinura;
    @FXML private TextField txtTamanoMaximo;
    @FXML private TextField txtPesoUnitario;
    @FXML private TextArea txtObservaciones;

    @FXML private TableView<Material> tabla;
    @FXML private TableColumn<Material, String> colNombre;
    @FXML private TableColumn<Material, String> colTipo;
    @FXML private TableColumn<Material, String> colProcedencia;
    @FXML private TableColumn<Material, String> colEnsayo;

    @FXML private Label lblDetalle;
    @FXML private Paginador paginador;

    private final ObservableList<Material> datos = FXCollections.observableArrayList();
    /** Lista completa del servicio; el paginador solo recorta lo que se muestra. */
    private List<Material> todos = new ArrayList<>();

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colTipo.setCellFactory(Tablas.celdaChip("—"));
        colProcedencia.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getProcedencia())));
        colEnsayo.setCellValueFactory(c -> new SimpleStringProperty(
                texto(c.getValue().getReferenciaEnsayo()) + " " + texto(c.getValue().getFechaEnsayo())));

        // Los materiales no se editan: cada registro nuevo conserva la trazabilidad de los anteriores.
        Tablas.configurar(tabla, "Aún no hay materiales registrados.", m -> { });
        tabla.setItems(datos);

        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, material) -> lblDetalle.setText(detalle(material)));

        paginador.setOnCambio(this::mostrarPagina);

        Formularios.limpiarAlEditar(txtNombre, txtProcedencia, txtReferenciaEnsayo, txtFechaEnsayo,
                txtDensidadRelativa, txtAbsorcion, txtHumedad, txtModuloFinura, txtTamanoMaximo,
                txtPesoUnitario, txtObservaciones);
        cmbTipo.valueProperty().addListener((obs, a, b) -> Formularios.limpiarError(cmbTipo));
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtNombre::requestFocus);
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        limpiarErrores();

        List<TextField> invalidos = new ArrayList<>();
        Double densidad = numero(txtDensidadRelativa, invalidos);
        Double absorcion = numero(txtAbsorcion, invalidos);
        Double humedad = numero(txtHumedad, invalidos);
        Double moduloFinura = numero(txtModuloFinura, invalidos);
        Double tamanoMaximo = numero(txtTamanoMaximo, invalidos);
        Double pesoUnitario = numero(txtPesoUnitario, invalidos);
        if (!invalidos.isEmpty()) {
            invalidos.forEach(campo -> Formularios.marcarError(campo, NUMERO_INVALIDO));
            return;
        }

        try {
            Material m = new Material();
            m.setTipo(cmbTipo.getValue());
            m.setNombre(txtNombre.getText());
            m.setProcedencia(txtProcedencia.getText());
            m.setReferenciaEnsayo(txtReferenciaEnsayo.getText());
            m.setFechaEnsayo(txtFechaEnsayo.getText());
            m.setDensidadRelativa(densidad);
            m.setAbsorcion(absorcion);
            m.setHumedad(humedad);
            m.setModuloFinura(moduloFinura);
            m.setTamanoMaximoMm(tamanoMaximo);
            m.setPesoUnitarioCompactado(pesoUnitario);
            m.setObservaciones(txtObservaciones.getText());

            Material guardado = servicio.guardar(m);
            notificador.cerrarPanel();
            notificador.exito("Material \"" + guardado.getNombre() + "\" registrado.");
            cargarTabla();
        } catch (IllegalArgumentException | NegocioException e) {
            Control campo = campoDelError(e.getMessage());
            if (campo != null) {
                Formularios.marcarError(campo, e.getMessage());
            } else {
                notificador.advertencia(e.getMessage());
            }
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------- apoyo

    private void cargarTabla() {
        todos = servicio.listar();
        mostrarPagina();
    }

    private void mostrarPagina() {
        int tamanio = paginador.getTamanio();
        int pagina = paginador.getPagina();
        int desde = Math.min(pagina * tamanio, todos.size());
        int hasta = Math.min(desde + tamanio, todos.size());
        Page<Material> resultado = new PageImpl<>(new ArrayList<>(todos.subList(desde, hasta)),
                PageRequest.of(pagina, tamanio), todos.size());
        datos.setAll(resultado.getContent());
        paginador.actualizar(resultado);
    }

    /** Asocia el mensaje de validacion del servicio con el campo que corresponde, si se puede. */
    private Control campoDelError(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        if (mensaje.contains("nombre")) return txtNombre;
        if (mensaje.contains("tipo de material")) return cmbTipo;
        if (mensaje.contains("densidad relativa")) return txtDensidadRelativa;
        if (mensaje.contains("módulo de finura")) return txtModuloFinura;
        if (mensaje.contains("tamaño máximo")) return txtTamanoMaximo;
        if (mensaje.contains("peso unitario compactado")) return txtPesoUnitario;
        if (mensaje.contains("absorción")) return txtAbsorcion;
        if (mensaje.contains("humedad")) return txtHumedad;
        if (mensaje.contains("fecha de ensayo")) return txtFechaEnsayo;
        return null;
    }

    private void limpiarFormulario() {
        limpiarErrores();
        cmbTipo.setValue(TIPO_POR_DEFECTO);
        txtNombre.clear();
        txtProcedencia.clear();
        txtReferenciaEnsayo.clear();
        txtFechaEnsayo.clear();
        txtDensidadRelativa.clear();
        txtAbsorcion.clear();
        txtHumedad.clear();
        txtModuloFinura.clear();
        txtTamanoMaximo.clear();
        txtPesoUnitario.clear();
        txtObservaciones.clear();
    }

    private void limpiarErrores() {
        Formularios.limpiarErrores(cmbTipo, txtNombre, txtProcedencia, txtReferenciaEnsayo, txtFechaEnsayo,
                txtDensidadRelativa, txtAbsorcion, txtHumedad, txtModuloFinura, txtTamanoMaximo,
                txtPesoUnitario, txtObservaciones);
    }

    /** Vacio = sin dato ({@code null}); si no es numerico registra el campo como invalido. */
    private static Double numero(TextField campo, List<TextField> invalidos) {
        if (campo.getText().isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(campo.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            invalidos.add(campo);
            return null;
        }
    }

    private static String detalle(Material m) {
        if (m == null) {
            return SIN_SELECCION;
        }
        return "Densidad relativa: " + formato(m.getDensidadRelativa())
                + " · Absorción: " + formato(m.getAbsorcion())
                + " % · Humedad: " + formato(m.getHumedad()) + " %\n"
                + "Módulo de finura: " + formato(m.getModuloFinura())
                + " · Tamaño máximo: " + formato(m.getTamanoMaximoMm())
                + " mm · Peso unitario compactado: " + formato(m.getPesoUnitarioCompactado()) + " kg/m³\n"
                + "Observaciones: " + texto(m.getObservaciones());
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static String formato(Double n) {
        return n == null ? "Sin dato" : String.format(Locale.ROOT, "%.3f", n);
    }
}
