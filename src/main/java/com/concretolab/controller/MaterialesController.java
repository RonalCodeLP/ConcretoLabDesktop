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
    private static final String YEFRI_LERMA_SIN_SELECCION = "Seleccione un material para ver sus datos de laboratorio.";
    private static final String YEFRI_LERMA_TIPO_POR_DEFECTO = "AGREGADO FINO";
    private static final String YEFRI_LERMA_NUMERO_INVALIDO = "Ingrese números válidos en los resultados de ensayo.";

    private final MaterialService yefriLermaServicio;
    private final Notificador yefriLermaNotificador;

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
    private List<Material> yefriLermaTodos = new ArrayList<>();

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colTipo.setCellFactory(Tablas.fernandoHuancaCeldaChip("—"));
        colProcedencia.setCellValueFactory(c -> new SimpleStringProperty(yefriLermaTexto(c.getValue().getProcedencia())));
        colEnsayo.setCellValueFactory(c -> new SimpleStringProperty(
                yefriLermaTexto(c.getValue().getReferenciaEnsayo()) + " " + yefriLermaTexto(c.getValue().getFechaEnsayo())));

        Tablas.fernandoHuancaConfigurar(tabla, "Aún no hay materiales registrados.", m -> { });
        tabla.setItems(datos);

        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, material) -> lblDetalle.setText(yefriLermaDetalle(material)));

        paginador.setOnCambio(this::yefriLermaMostrarPagina);

        Formularios.fernandoHuancaLimpiarAlEditar(txtNombre, txtProcedencia, txtReferenciaEnsayo, txtFechaEnsayo,
                txtDensidadRelativa, txtAbsorcion, txtHumedad, txtModuloFinura, txtTamanoMaximo,
                txtPesoUnitario, txtObservaciones);
        cmbTipo.valueProperty().addListener((obs, a, b) -> Formularios.fernandoHuancaLimpiarError(cmbTipo));
        cmbTipo.getItems().setAll(com.concretolab.service.MaterialService.YEFRI_LERMA_TIPOS);
    }

    @Override
    public void refrescar() {
        yefriLermaCargarTabla();
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        yefriLermaLimpiarFormulario();
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelFormulario);
        Platform.runLater(txtNombre::requestFocus);
    }

    @FXML
    private void onCerrarFormulario() {
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
    }

    @FXML
    private void onGuardar() {
        yefriLermaLimpiarErrores();

        List<TextField> invalidos = new ArrayList<>();
        Double densidad = yefriLermaNumero(txtDensidadRelativa, invalidos);
        Double absorcion = yefriLermaNumero(txtAbsorcion, invalidos);
        Double humedad = yefriLermaNumero(txtHumedad, invalidos);
        Double moduloFinura = yefriLermaNumero(txtModuloFinura, invalidos);
        Double tamanoMaximo = yefriLermaNumero(txtTamanoMaximo, invalidos);
        Double pesoUnitario = yefriLermaNumero(txtPesoUnitario, invalidos);
        if (!invalidos.isEmpty()) {
            invalidos.forEach(campo -> Formularios.fernandoHuancaMarcarError(campo, YEFRI_LERMA_NUMERO_INVALIDO));
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

            Material guardado = yefriLermaServicio.yefriLermaGuardar(m);
            yefriLermaNotificador.fernandoHuancaCerrarPanel();
            yefriLermaNotificador.exito("Material \"" + guardado.getNombre() + "\" registrado.");
            yefriLermaCargarTabla();
        } catch (IllegalArgumentException | NegocioException e) {
            Control campo = yefriLermaCampoDelError(e.getMessage());
            if (campo != null) {
                Formularios.fernandoHuancaMarcarError(campo, e.getMessage());
            } else {
                yefriLermaNotificador.fernandoHuancaAdvertencia(e.getMessage());
            }
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError("Error inesperado: " + e.getMessage());
        }
    }

    private void yefriLermaCargarTabla() {
        yefriLermaTodos = yefriLermaServicio.yefriLermaListar();
        yefriLermaMostrarPagina();
    }

    private void yefriLermaMostrarPagina() {
        int tamanio = paginador.getTamanio();
        int pagina = paginador.getPagina();
        int desde = Math.min(pagina * tamanio, yefriLermaTodos.size());
        int hasta = Math.min(desde + tamanio, yefriLermaTodos.size());
        Page<Material> resultado = new PageImpl<>(new ArrayList<>(yefriLermaTodos.subList(desde, hasta)),
                PageRequest.of(pagina, tamanio), yefriLermaTodos.size());
        datos.setAll(resultado.getContent());
        paginador.yefriLermaActualizar(resultado);
    }

    private Control yefriLermaCampoDelError(String mensaje) {
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

    private void yefriLermaLimpiarFormulario() {
        yefriLermaLimpiarErrores();
        cmbTipo.setValue(YEFRI_LERMA_TIPO_POR_DEFECTO);
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

    private void yefriLermaLimpiarErrores() {
        Formularios.fernandoHuancaLimpiarErrores(cmbTipo, txtNombre, txtProcedencia, txtReferenciaEnsayo, txtFechaEnsayo,
                txtDensidadRelativa, txtAbsorcion, txtHumedad, txtModuloFinura, txtTamanoMaximo,
                txtPesoUnitario, txtObservaciones);
    }

    private static Double yefriLermaNumero(TextField campo, List<TextField> invalidos) {
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

    private static String yefriLermaDetalle(Material m) {
        if (m == null) {
            return YEFRI_LERMA_SIN_SELECCION;
        }
        return "Densidad relativa: " + yefriLermaFormato(m.getDensidadRelativa())
                + " · Absorción: " + yefriLermaFormato(m.getAbsorcion())
                + " % · Humedad: " + yefriLermaFormato(m.getHumedad()) + " %\n"
                + "Módulo de finura: " + yefriLermaFormato(m.getModuloFinura())
                + " · Tamaño máximo: " + yefriLermaFormato(m.getTamanoMaximoMm())
                + " mm · Peso unitario compactado: " + yefriLermaFormato(m.getPesoUnitarioCompactado()) + " kg/m³\n"
                + "Observaciones: " + yefriLermaTexto(m.getObservaciones());
    }

    private static String yefriLermaTexto(String s) {
        return s == null ? "" : s;
    }

    private static String yefriLermaFormato(Double n) {
        return n == null ? "Sin dato" : String.format(Locale.ROOT, "%.3f", n);
    }
}
