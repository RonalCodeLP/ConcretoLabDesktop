package com.concretolab.controller;

import com.concretolab.exception.NegocioException;
import com.concretolab.model.Material;
import com.concretolab.model.RegistroGranulometria;
import com.concretolab.service.GranulometriaService;
import com.concretolab.service.HusosGranulometricos;
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
    private static final String YEFRI_LERMA_RESUMEN_INICIAL = "El resultado se calculará al ingresar las mediciones.";
    private static final String YEFRI_LERMA_RESUMEN_INVALIDO = "Los datos cambiaron. Calcule nuevamente los resultados.";

    private final MaterialService materiales;
    private final RegistroGranulometriaService yefriLermaRegistros;
    private final Notificador yefriLermaNotificador;

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

    private final ObservableList<GranulometriaService.Fila> yefriLermaFilas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        yefriLermaColumna(colAbertura, f -> yefriLermaFmt(f.aberturaMm()));
        yefriLermaColumna(colRetenido, f -> yefriLermaFmt(f.retenidoG()));
        yefriLermaColumna(colRetenidoPct, f -> yefriLermaFmt(f.retenidoPorcentaje()));
        yefriLermaColumna(colAcumulado, f -> yefriLermaFmt(f.retenidoAcumulado()));
        yefriLermaColumna(colPasa, f -> yefriLermaFmt(f.pasaPorcentaje()));

        Tablas.fernandoHuancaConfigurar(tabla, "Pulse «Calcular resultados» para ver la tabla de tamizado.", f -> { });
        tabla.setItems(yefriLermaFilas);

        txtFecha.setText(LocalDate.now().toString());
        lblResumen.setText(YEFRI_LERMA_RESUMEN_INICIAL);

        txtMasa.textProperty().addListener((obs, a, b) -> yefriLermaInvalidar());
        txtFondo.textProperty().addListener((obs, a, b) -> yefriLermaInvalidar());
        txtMediciones.textProperty().addListener((obs, a, b) -> yefriLermaInvalidar());

        Formularios.fernandoHuancaLimpiarAlEditar(txtIdentificacion, txtFecha, txtMasa, txtFondo, txtMediciones);
        cmbMaterial.valueProperty().addListener((obs, a, b) -> Formularios.fernandoHuancaLimpiarError(cmbMaterial));
    }

    @Override
    public void refrescar() {
        Long idSeleccionado = cmbMaterial.getValue() == null ? null : cmbMaterial.getValue().getId();
        List<Material> agregados = materiales.yefriLermaListar().stream()
                .filter(m -> m.getTipo().startsWith("AGREGADO"))
                .toList();
        cmbMaterial.getItems().setAll(agregados);
        Material actual = idSeleccionado == null ? null : agregados.stream()
                .filter(m -> idSeleccionado.equals(m.getId()))
                .findFirst().orElse(null);
        cmbMaterial.setValue(actual);
        yefriLermaRecargarHistorial();
    }

    @FXML
    private void onMaterialSeleccionado() {
        yefriLermaRecargarHistorial();
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
        Formularios.fernandoHuancaLimpiarErrores(txtMasa, txtFondo, txtMediciones);
        try {
            List<GranulometriaService.Tamiz> tamices = RegistroGranulometriaService.yefriLermaLeer(txtMediciones.getText());
            double[] masas = yefriLermaLeerMasas("Ingrese números válidos.");
            if (masas == null) {
                yefriLermaLimpiarResultado();
                lblResumen.setText("Ingrese números válidos.");
                return;
            }
            GranulometriaService.Resultado r = GranulometriaService.yefriLermaCalcular(masas[0], tamices, masas[1]);
            yefriLermaFilas.setAll(r.filas());

            XYChart.Series<String, Number> serie = new XYChart.Series<>();
            for (GranulometriaService.Fila fila : r.filas()) {
                serie.getData().add(new XYChart.Data<>(yefriLermaFmt(fila.aberturaMm()), fila.pasaPorcentaje()));
            }
            grafico.getData().setAll(serie);

            lblResumen.setText("Masa recuperada: " + yefriLermaFmt(r.recuperadoG())
                    + " g · Diferencia frente a la masa inicial: " + yefriLermaFmt(r.diferenciaG())
                    + " g (" + yefriLermaFmt(r.diferenciaPorcentaje())
                    + " %). Revise cualquier diferencia con el procedimiento del laboratorio.\n"
                    + yefriLermaVerificarHuso(r.filas()));
        } catch (IllegalArgumentException | NegocioException e) {
            yefriLermaLimpiarResultado();
            lblResumen.setText(e.getMessage());
            Control campo = yefriLermaCampoDelError(e.getMessage());
            if (campo != null) {
                Formularios.fernandoHuancaMarcarError(campo, e.getMessage());
            }
        } catch (Exception e) {
            yefriLermaLimpiarResultado();
            lblResumen.setText(e.getMessage());
        }
    }

    @FXML
    private void onGuardar() {
        Formularios.fernandoHuancaLimpiarErrores(cmbMaterial, txtIdentificacion, txtFecha, txtMasa, txtFondo, txtMediciones);
        try {
            Material material = cmbMaterial.getValue();
            if (material == null) {
                Formularios.fernandoHuancaMarcarError(cmbMaterial, "Seleccione un agregado de la biblioteca antes de guardar.");
                return;
            }
            double[] masas = yefriLermaLeerMasas("Ingrese masas numéricas válidas.");
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
            yefriLermaRegistros.yefriLermaGuardar(r);

            cmbHistorial.getItems().setAll(yefriLermaRegistros.yefriLermaListar(material.getId()));
            lblResumen.setText("Ensayo guardado. Selecciónelo en el historial para volver a consultarlo.");
            yefriLermaNotificador.exito("Ensayo guardado.");
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

    private void yefriLermaRecargarHistorial() {
        cmbHistorial.getItems().clear();
        Material material = cmbMaterial.getValue();
        if (material != null) {
            cmbHistorial.getItems().setAll(yefriLermaRegistros.yefriLermaListar(material.getId()));
        }
    }

    private String yefriLermaVerificarHuso(List<GranulometriaService.Fila> filas) {
        Material m = cmbMaterial.getValue();
        boolean fino = m != null && "AGREGADO FINO".equals(m.getTipo());
        if (m == null) {
            var mf = HusosGranulometricos.yefriLermaModuloFinura(filas);
            return mf == null ? "Elija un agregado de la biblioteca para verificar el huso."
                    : String.format(Locale.ROOT, "Módulo de finura calculado: %.2f. Elija un agregado para verificar el huso.", mf);
        }
        if (fino) {
            return HusosGranulometricos.yefriLermaVerificar(HusosGranulometricos.Huso.FINO, filas).mensaje();
        }
        StringBuilder sb = new StringBuilder("Agregado grueso: ");
        for (var huso : List.of(HusosGranulometricos.Huso.GRUESO_57, HusosGranulometricos.Huso.GRUESO_67,
                HusosGranulometricos.Huso.GRUESO_7)) {
            var v = HusosGranulometricos.yefriLermaVerificar(huso, filas);
            if (v.cumple()) {
                return sb.append("cumple el huso ").append(huso).append(" (NTP 400.037 / ASTM C33).").toString();
            }
        }
        return sb.append("no cumple por completo los husos Nº 57, 67 ni 7 con los tamices ingresados "
                + "(verifique que estén todos los tamices de la serie).").toString();
    }

    private void yefriLermaInvalidar() {
        yefriLermaLimpiarResultado();
        lblResumen.setText(YEFRI_LERMA_RESUMEN_INVALIDO);
    }

    private void yefriLermaLimpiarResultado() {
        yefriLermaFilas.clear();
        grafico.getData().clear();
    }

    private double[] yefriLermaLeerMasas(String mensaje) {
        Double masa = yefriLermaNumero(txtMasa);
        Double fondo = yefriLermaNumero(txtFondo);
        if (masa == null || fondo == null) {
            if (masa == null) {
                Formularios.fernandoHuancaMarcarError(txtMasa, mensaje);
            }
            if (fondo == null) {
                Formularios.fernandoHuancaMarcarError(txtFondo, mensaje);
            }
            return null;
        }
        return new double[] {masa, fondo};
    }

    private Control yefriLermaCampoDelError(String mensaje) {
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

    private static void yefriLermaColumna(TableColumn<GranulometriaService.Fila, String> columna,
                                Function<GranulometriaService.Fila, String> valor) {
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
    }

    private static Double yefriLermaNumero(TextField campo) {
        try {
            return Double.parseDouble(campo.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String yefriLermaFmt(double n) {
        return String.format(Locale.ROOT, "%.2f", n);
    }
}
