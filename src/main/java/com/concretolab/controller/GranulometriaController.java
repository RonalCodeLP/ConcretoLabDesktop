package com.concretolab.controller;

import com.concretolab.exception.NegocioException;
import com.concretolab.model.Material;
import com.concretolab.model.RegistroGranulometria;
import com.concretolab.service.GranulometriaService;
import com.concretolab.service.MaterialService;
import com.concretolab.service.RegistroGranulometriaService;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.Tablas;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class GranulometriaController implements Refrescable {

    private static final String RESUMEN_INICIAL = "El resultado se calculará al ingresar las mediciones.";
    private static final String RESUMEN_INVALIDO = "Los datos cambiaron. Calcula nuevamente los resultados.";

    private final MaterialService materiales;
    private final RegistroGranulometriaService registros;
    private final Notificador notificador;

    @FXML private ComboBox<Material> cmbMaterial;
    @FXML private ComboBox<RegistroGranulometria> cmbHistorial;
    @FXML private TextField txtIdentificacion;
    @FXML private TextField txtFecha;
    @FXML private TextField txtMasa;
    @FXML private TextField txtFondo;
    @FXML private TextArea txtMediciones;

    @FXML private Button btnCalcular;
    @FXML private Button btnGuardar;
    @FXML private Label lblResumen;

    @FXML private TableView<GranulometriaService.Fila> tabla;
    @FXML private TableColumn<GranulometriaService.Fila, String> colAbertura;
    @FXML private TableColumn<GranulometriaService.Fila, String> colRetenido;
    @FXML private TableColumn<GranulometriaService.Fila, String> colRetenidoPct;
    @FXML private TableColumn<GranulometriaService.Fila, String> colAcumulado;
    @FXML private TableColumn<GranulometriaService.Fila, String> colPasa;

    @FXML private LineChart<String, Number> grafico;

    private final ObservableList<GranulometriaService.Fila> filas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        columna(colAbertura, f -> fmt(f.aberturaMm()));
        columna(colRetenido, f -> fmt(f.retenidoG()));
        columna(colRetenidoPct, f -> fmt(f.retenidoPorcentaje()));
        columna(colAcumulado, f -> fmt(f.retenidoAcumulado()));
        columna(colPasa, f -> fmt(f.pasaPorcentaje()));

        Tablas.configurar(tabla, "Pulse «Calcular resultados» para ver la tabla de tamizado.", f -> { });
        tabla.setItems(filas);

        txtFecha.setText(LocalDate.now().toString());
        lblResumen.setText(RESUMEN_INICIAL);

        // Cualquier cambio en los datos de calculo invalida el resultado mostrado.
        txtMasa.textProperty().addListener((obs, a, b) -> invalidar());
        txtFondo.textProperty().addListener((obs, a, b) -> invalidar());
        txtMediciones.textProperty().addListener((obs, a, b) -> invalidar());

        Formularios.limpiarAlEditar(txtIdentificacion, txtFecha, txtMasa, txtFondo, txtMediciones);
        cmbMaterial.valueProperty().addListener((obs, a, b) -> Formularios.limpiarError(cmbMaterial));
    }

    @Override
    public void refrescar() {
        // Recarga los agregados de la biblioteca conservando el seleccionado.
        Long idSeleccionado = cmbMaterial.getValue() == null ? null : cmbMaterial.getValue().getId();
        List<Material> agregados = materiales.listar().stream()
                .filter(m -> m.getTipo().startsWith("AGREGADO"))
                .toList();
        cmbMaterial.getItems().setAll(agregados);
        Material actual = idSeleccionado == null ? null : agregados.stream()
                .filter(m -> idSeleccionado.equals(m.getId()))
                .findFirst().orElse(null);
        cmbMaterial.setValue(actual);
        recargarHistorial();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onMaterialSeleccionado() {
        recargarHistorial();
    }

    @FXML
    private void onHistorialSeleccionado() {
        RegistroGranulometria r = cmbHistorial.getValue();
        if (r != null) {
            txtIdentificacion.setText(r.getIdentificacion());
            txtFecha.setText(r.getFecha());
            txtMasa.setText(Double.toString(r.getMasaInicial()));
            txtFondo.setText(Double.toString(r.getFondo()));
            txtMediciones.setText(r.getMediciones());
        }
    }

    @FXML
    private void onCalcular() {
        Formularios.limpiarErrores(txtMasa, txtFondo, txtMediciones);
        try {
            List<GranulometriaService.Tamiz> tamices = RegistroGranulometriaService.leer(txtMediciones.getText());
            double[] masas = leerMasas("Ingrese números válidos.");
            if (masas == null) {
                limpiarResultado();
                lblResumen.setText("Ingrese números válidos.");
                return;
            }
            GranulometriaService.Resultado r = GranulometriaService.calcular(masas[0], tamices, masas[1]);
            filas.setAll(r.filas());

            XYChart.Series<String, Number> serie = new XYChart.Series<>();
            for (GranulometriaService.Fila fila : r.filas()) {
                serie.getData().add(new XYChart.Data<>(fmt(fila.aberturaMm()), fila.pasaPorcentaje()));
            }
            grafico.getData().setAll(serie);

            lblResumen.setText("Masa recuperada: " + fmt(r.recuperadoG())
                    + " g · Diferencia frente a la masa inicial: " + fmt(r.diferenciaG())
                    + " g (" + fmt(r.diferenciaPorcentaje())
                    + " %). Revise cualquier diferencia con el procedimiento del laboratorio.");
        } catch (IllegalArgumentException | NegocioException e) {
            limpiarResultado();
            lblResumen.setText(e.getMessage());
            Control campo = campoDelError(e.getMessage());
            if (campo != null) {
                Formularios.marcarError(campo, e.getMessage());
            }
        } catch (Exception e) {
            limpiarResultado();
            lblResumen.setText(e.getMessage());
        }
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(cmbMaterial, txtIdentificacion, txtFecha, txtMasa, txtFondo, txtMediciones);
        try {
            Material material = cmbMaterial.getValue();
            if (material == null) {
                Formularios.marcarError(cmbMaterial, "Seleccione un agregado de la biblioteca antes de guardar.");
                return;
            }
            double[] masas = leerMasas("Ingrese masas numéricas válidas.");
            if (masas == null) {
                return;
            }

            RegistroGranulometria r = new RegistroGranulometria();
            r.setMaterialId(material.getId());
            r.setIdentificacion(txtIdentificacion.getText().trim());
            r.setFecha(txtFecha.getText().trim());
            r.setMasaInicial(masas[0]);
            r.setFondo(masas[1]);
            r.setMediciones(txtMediciones.getText());
            registros.guardar(r);

            cmbHistorial.getItems().setAll(registros.listar(material.getId()));
            lblResumen.setText("Ensayo guardado. Selecciónalo en el historial para volver a consultarlo.");
            notificador.exito("Ensayo guardado.");
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

    private void recargarHistorial() {
        cmbHistorial.getItems().clear();
        Material material = cmbMaterial.getValue();
        if (material != null) {
            cmbHistorial.getItems().setAll(registros.listar(material.getId()));
        }
    }

    private void invalidar() {
        limpiarResultado();
        lblResumen.setText(RESUMEN_INVALIDO);
    }

    private void limpiarResultado() {
        filas.clear();
        grafico.getData().clear();
    }

    /**
     * Lee masa inicial y masa en fondo. Si alguna no es numerica la marca con el mensaje dado
     * y devuelve {@code null}.
     */
    private double[] leerMasas(String mensaje) {
        Double masa = numero(txtMasa);
        Double fondo = numero(txtFondo);
        if (masa == null || fondo == null) {
            if (masa == null) {
                Formularios.marcarError(txtMasa, mensaje);
            }
            if (fondo == null) {
                Formularios.marcarError(txtFondo, mensaje);
            }
            return null;
        }
        return new double[] {masa, fondo};
    }

    /** Asocia el mensaje de validacion con el campo que corresponde, si se puede identificar. */
    private Control campoDelError(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        if (mensaje.contains("material existente")) return cmbMaterial;
        if (mensaje.contains("identificación")) return txtIdentificacion;
        if (mensaje.contains("fecha")) return txtFecha;
        if (mensaje.contains("tamiz") || mensaje.contains("aberturas")
                || mensaje.contains("masas retenidas") || mensaje.contains("retenida acumulada")) {
            return txtMediciones;
        }
        if (mensaje.contains("masa inicial") || mensaje.contains("demasiado grandes")) return txtMasa;
        if (mensaje.contains("masa del fondo")) return txtFondo;
        return null;
    }

    private static void columna(TableColumn<GranulometriaService.Fila, String> columna,
                                Function<GranulometriaService.Fila, String> valor) {
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
    }

    private static Double numero(TextField campo) {
        try {
            return Double.parseDouble(campo.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double n) {
        return String.format(Locale.ROOT, "%.2f", n);
    }
}
