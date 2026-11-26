package com.concretolab.controller;

import com.concretolab.exception.NegocioException;
import com.concretolab.model.Elemento;
import com.concretolab.model.Ensayo;
import com.concretolab.model.Material;
import com.concretolab.model.Mezcla;
import com.concretolab.model.Proyecto;
import com.concretolab.service.DisenoMezclaACI;
import com.concretolab.service.EvaluacionResistencia;
import com.concretolab.service.MaterialService;
import com.concretolab.service.MezclaService;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.ReporteMezcla;
import com.concretolab.ui.Exportador;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import com.concretolab.ui.Tablas;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MezclasController implements Refrescable {
    private static final String YEFRI_LERMA_SIN_MEZCLA = "Seleccione una mezcla.";
    private static final String YEFRI_LERMA_ERROR_NUMERO = "Ingrese valores numéricos válidos.";

    private final YefriLermaProyectoService proyectos;
    private final MezclaService yefriLermaServicio;
    private final MaterialService materiales;
    private final SesionProyecto yefriLermaSesion;
    private final Notificador yefriLermaNotificador;
    private final Exportador exportador;

    @FXML private VBox panelMezcla;
    @FXML private VBox panelEnsayo;
    @FXML private VBox panelElementos;

    @FXML private Label lblSubtituloMezcla;
    @FXML private TextField txtNombre;
    @FXML private TextField txtReferencia;
    @FXML private TextField txtResistencia;
    @FXML private ComboBox<Material> cmbFino;
    @FXML private ComboBox<Material> cmbGrueso;
    @FXML private TextField txtCemento;
    @FXML private TextField txtAgua;
    @FXML private TextField txtFinoSss;
    @FXML private TextField txtGruesoSss;
    @FXML private TextField txtAbsorcionFino;
    @FXML private TextField txtHumedadFino;
    @FXML private TextField txtAbsorcionGrueso;
    @FXML private TextField txtHumedadGrueso;
    @FXML private TextArea txtObservaciones;

    @FXML private VBox panelDiseno;
    @FXML private TextField txtFcDiseno;
    @FXML private TextField txtAsentDiseno;
    @FXML private TextField txtDesviacion;
    @FXML private TextField txtNumEnsayos;
    @FXML private ComboBox<DisenoMezclaACI.Exposicion> cmbExposicion;
    @FXML private javafx.scene.control.CheckBox chkAire;
    @FXML private ComboBox<DisenoMezclaACI.SeveridadAire> cmbSeveridad;
    @FXML private ComboBox<Material> cmbFinoDiseno;
    @FXML private ComboBox<Material> cmbGruesoDiseno;
    @FXML private ComboBox<Material> cmbCementoDiseno;
    @FXML private TextField txtMfDiseno;
    @FXML private TextField txtDensFinoDiseno;
    @FXML private TextField txtTmaDiseno;
    @FXML private TextField txtPucDiseno;
    @FXML private TextField txtDensGruesoDiseno;
    @FXML private TextField txtAbsGruesoDiseno;
    @FXML private TextField txtDensCemento;
    @FXML private Label lblResultadoDiseno;
    @FXML private Button btnUsarDiseno;
    private DisenoMezclaACI.Resultado yefriLermaUltimoDiseno;
    private double yefriLermaUltimaFc;

    @FXML private Label lblSubtituloEnsayo;
    @FXML private TextField txtIdentificacion;
    @FXML private TextField txtFecha;
    @FXML private TextField txtEdad;
    @FXML private TextField txtResistenciaEnsayo;
    @FXML private TextField txtAsentamiento;
    @FXML private TextArea txtObservacionesEnsayo;

    @FXML private ListView<Elemento> lstElementos;

    @FXML private Label lblProyecto;
    @FXML private TextField txtVolumen;
    @FXML private Label lblResultado;
    @FXML private Button btnCalcular;
    @FXML private Button btnElementos;
    @FXML private Button btnPdf;
    @FXML private Button btnEnsayo;

    @FXML private TableView<Mezcla> tablaMezclas;
    @FXML private TableColumn<Mezcla, String> colMezcla;
    @FXML private TableColumn<Mezcla, String> colResistencia;
    @FXML private TableColumn<Mezcla, String> colFuente;

    @FXML private TableView<Ensayo> tablaEnsayos;
    @FXML private TableColumn<Ensayo, String> colProbeta;
    @FXML private TableColumn<Ensayo, String> colFecha;
    @FXML private TableColumn<Ensayo, String> colEdad;
    @FXML private TableColumn<Ensayo, String> colResistenciaEnsayo;
    @FXML private TableColumn<Ensayo, String> colAsentamiento;

    private final ObservableList<Mezcla> yefriLermaDatosMezclas = FXCollections.observableArrayList();
    private final ObservableList<Ensayo> yefriLermaDatosEnsayos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        yefriLermaColumna(colMezcla, Mezcla::getNombre);
        yefriLermaColumna(colResistencia, m -> yefriLermaFmt(m.getResistencia()));
        yefriLermaColumna(colFuente, m -> m.getReferencia() == null ? "" : m.getReferencia());
        yefriLermaColumna(colProbeta, Ensayo::getIdentificacion);
        yefriLermaColumna(colFecha, Ensayo::getFecha);
        yefriLermaColumna(colEdad, e -> yefriLermaFmt(e.getEdadDias()));
        yefriLermaColumna(colResistenciaEnsayo, e -> yefriLermaFmt(e.getResistenciaMpa()));
        yefriLermaColumna(colAsentamiento, e -> yefriLermaFmt(e.getAsentamientoMm()));

        Tablas.fernandoHuancaConfigurar(tablaMezclas,
                "Seleccione un proyecto en «Modelo 3D y metrado» y registre una mezcla.", m -> { });
        Tablas.fernandoHuancaConfigurar(tablaEnsayos, "Seleccione una mezcla para ver sus ensayos.", e -> { });
        tablaMezclas.setItems(yefriLermaDatosMezclas);
        tablaEnsayos.setItems(yefriLermaDatosEnsayos);

        var seleccion = tablaMezclas.getSelectionModel().selectedItemProperty();
        seleccion.addListener((obs, anterior, actual) -> yefriLermaActualizar());
        btnCalcular.disableProperty().bind(seleccion.isNull());
        btnPdf.disableProperty().bind(seleccion.isNull());
        btnEnsayo.disableProperty().bind(seleccion.isNull());

        lstElementos.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lstElementos.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Elemento item, boolean vacia) {
                super.updateItem(item, vacia);
                setText(vacia || item == null ? null
                        : item.getNombre() + " · " + item.getTipo() + " · " + yefriLermaVolumen(item.volumen()) + " m³");
            }
        });
        lstElementos.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<Elemento>) c -> Formularios.fernandoHuancaLimpiarError(lstElementos));

        cmbExposicion.setItems(FXCollections.observableArrayList(DisenoMezclaACI.Exposicion.values()));
        cmbExposicion.setValue(DisenoMezclaACI.Exposicion.NORMAL);
        cmbSeveridad.setItems(FXCollections.observableArrayList(DisenoMezclaACI.SeveridadAire.values()));
        cmbSeveridad.setValue(DisenoMezclaACI.SeveridadAire.MODERADA);
        cmbSeveridad.disableProperty().bind(chkAire.selectedProperty().not());
        Formularios.fernandoHuancaLimpiarAlEditar(txtFcDiseno, txtAsentDiseno, txtDesviacion, txtNumEnsayos, txtMfDiseno,
                txtDensFinoDiseno, txtTmaDiseno, txtPucDiseno, txtDensGruesoDiseno, txtAbsGruesoDiseno, txtDensCemento);

        Formularios.fernandoHuancaLimpiarAlEditar(txtVolumen, txtNombre, txtReferencia, txtResistencia, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones, txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
    }

    @Override
    public void refrescar() {
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto == null) {
            lblProyecto.setText("Seleccione un proyecto");
            if (!lblProyecto.getStyleClass().contains("neutro")) {
                lblProyecto.getStyleClass().add("neutro");
            }
        } else {
            lblProyecto.setText(proyecto.getNombre());
            lblProyecto.getStyleClass().remove("neutro");
        }
        yefriLermaCargar();
    }

    @FXML
    private void onCerrarPanel() {
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
    }

    @FXML
    private void onCalcular() {
        yefriLermaActualizar();
    }

    @FXML
    private void onDisenar() {
        cmbFinoDiseno.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO FINO")));
        cmbGruesoDiseno.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO GRUESO")));
        cmbCementoDiseno.setItems(FXCollections.observableArrayList(materiales.yefriLermaCementos()));
        yefriLermaUltimoDiseno = null;
        btnUsarDiseno.setDisable(true);
        lblResultadoDiseno.setText("Complete los datos y pulse «Calcular diseño».");
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelDiseno);
        Platform.runLater(txtFcDiseno::requestFocus);
    }

    @FXML
    private void onElegirFinoDiseno() {
        Material m = cmbFinoDiseno.getValue();
        if (m != null) {
            txtMfDiseno.setText(campo(m.getModuloFinura()));
            txtDensFinoDiseno.setText(campo(m.getDensidadRelativa()));
        }
    }

    @FXML
    private void onElegirGruesoDiseno() {
        Material m = cmbGruesoDiseno.getValue();
        if (m != null) {
            txtTmaDiseno.setText(campo(m.getTamanoMaximoMm()));
            txtPucDiseno.setText(campo(m.getPesoUnitarioCompactado()));
            txtDensGruesoDiseno.setText(campo(m.getDensidadRelativa()));
            txtAbsGruesoDiseno.setText(campo(m.getAbsorcion()));
        }
    }

    @FXML
    private void onElegirCementoDiseno() {
        Material m = cmbCementoDiseno.getValue();
        if (m != null && m.getDensidadRelativa() != null) {
            txtDensCemento.setText(campo(m.getDensidadRelativa()));
        }
    }

    @FXML
    private void onCalcularDiseno() {
        List<TextField> campos = List.of(txtFcDiseno, txtAsentDiseno, txtDesviacion, txtNumEnsayos, txtMfDiseno,
                txtDensFinoDiseno, txtTmaDiseno, txtPucDiseno, txtDensGruesoDiseno, txtAbsGruesoDiseno, txtDensCemento);
        Formularios.fernandoHuancaLimpiarErrores(campos.toArray(new TextField[0]));
        yefriLermaUltimoDiseno = null;
        btnUsarDiseno.setDisable(true);
        boolean faltan = false;
        for (TextField c : campos) {
            if (c != txtDesviacion && c != txtNumEnsayos && c.getText().isBlank()) {
                Formularios.fernandoHuancaMarcarError(c, "Complete este dato.");
                faltan = true;
            }
        }
        if (faltan) {
            lblResultadoDiseno.setText("Faltan datos: complete los campos marcados.");
            return;
        }
        try {
            double fc = yefriLermaNum(txtFcDiseno);
            Double s = txtDesviacion.getText().isBlank() ? null : yefriLermaNum(txtDesviacion);
            int n = txtNumEnsayos.getText().isBlank() ? 0 : (int) Math.round(yefriLermaNum(txtNumEnsayos));
            var entrada = new DisenoMezclaACI.Entrada(fc, s, n, yefriLermaNum(txtAsentDiseno), yefriLermaNum(txtTmaDiseno), yefriLermaNum(txtMfDiseno),
                    yefriLermaNum(txtPucDiseno), yefriLermaNum(txtDensCemento), yefriLermaNum(txtDensFinoDiseno), yefriLermaNum(txtDensGruesoDiseno),
                    yefriLermaNum(txtAbsGruesoDiseno), cmbExposicion.getValue(), chkAire.isSelected(), cmbSeveridad.getValue());
            yefriLermaUltimoDiseno = DisenoMezclaACI.yefriLermaDisenar(entrada);
            yefriLermaUltimaFc = fc;
            String texto = yefriLermaTextoDiseno(yefriLermaUltimoDiseno);
            Material cemento = cmbCementoDiseno.getValue();
            var aviso = DisenoMezclaACI.yefriLermaAvisoCemento(cmbExposicion.getValue(), cemento == null ? null : cemento.getTipo());
            if (aviso.isPresent()) {
                texto += "\n• " + aviso.get();
            } else if (cemento == null && cmbExposicion.getValue() != null && cmbExposicion.getValue().name().startsWith("SULFATOS")) {
                texto += "\n• Elija el cemento de la biblioteca para verificar su tipo frente a los sulfatos.";
            }
            lblResultadoDiseno.setText(texto);
            btnUsarDiseno.setDisable(false);
        } catch (NumberFormatException e) {
            lblResultadoDiseno.setText(YEFRI_LERMA_ERROR_NUMERO);
        } catch (IllegalArgumentException e) {
            lblResultadoDiseno.setText(yefriLermaMensaje(e));
        }
    }

        private static String yefriLermaF1(double n) { return String.format(Locale.ROOT, "%.1f", n); }
    private static String yefriLermaF2(double n) { return String.format(Locale.ROOT, "%.2f", n); }

    private static String yefriLermaTextoDiseno(DisenoMezclaACI.Resultado r) {
        StringBuilder sb = new StringBuilder();
        sb.append("f’cr requerida: ").append(yefriLermaF1(r.fcrMpa())).append(" MPa\n").append(r.criterioFcr()).append("\n\n");
        sb.append("a/c: ").append(yefriLermaF2(r.relacionAc())).append(" (por resistencia ").append(yefriLermaF2(r.relacionAcResistencia()));
        if (r.relacionAcDurabilidad() > 0) {
            sb.append(", máxima por durabilidad ").append(yefriLermaF2(r.relacionAcDurabilidad()));
        }
        sb.append(")\nTMA de tabla: ").append(yefriLermaF1(r.tmaTabla())).append(" mm · aire: ").append(yefriLermaF1(r.airePorcentaje())).append(" %\n\n");
        sb.append("Cemento: ").append(yefriLermaF1(r.cemento())).append(" kg/m³ (").append(yefriLermaF1(r.bolsasCemento())).append(" bolsas de 42,5 kg)\n");
        sb.append("Agua de mezclado: ").append(yefriLermaF1(r.agua())).append(" kg/m³\n");
        sb.append("Fino SSS: ").append(yefriLermaF1(r.finoSss())).append(" kg/m³\n");
        sb.append("Grueso SSS: ").append(yefriLermaF1(r.gruesoSss())).append(" kg/m³ (seco ").append(yefriLermaF1(r.gruesoSeco()))
                .append(" kg, volumen relativo ").append(yefriLermaF2(r.volumenRelativoGrueso())).append(")\n");
        sb.append("Proporción en peso: ").append(r.proporcionEnPeso()).append("\n\nAvisos:");
        r.advertencias().forEach(a -> sb.append("\n• ").append(a));
        return sb.toString();
    }

    @FXML
    private void onUsarDiseno() {
        if (yefriLermaUltimoDiseno == null) {
            return;
        }
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione un proyecto en «Modelo 3D y metrado» para guardar la mezcla.");
            return;
        }
        var r = yefriLermaUltimoDiseno;
        onNuevaMezcla();
        txtNombre.setText("Diseño ACI f’c " + yefriLermaFmt(yefriLermaUltimaFc) + " MPa");
        txtReferencia.setText("Asistente ACI 211.1 + E.060");
        txtResistencia.setText(Double.toString(yefriLermaUltimaFc));
        txtCemento.setText(yefriLermaFmt(r.cemento()));
        txtAgua.setText(yefriLermaFmt(r.agua()));
        txtFinoSss.setText(yefriLermaFmt(r.finoSss()));
        txtGruesoSss.setText(yefriLermaFmt(r.gruesoSss()));
        Material fino = cmbFinoDiseno.getValue();
        Material grueso = cmbGruesoDiseno.getValue();
        cmbFino.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO FINO")));
        cmbGrueso.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO GRUESO")));
        if (fino != null) {
            cmbFino.getItems().stream().filter(m -> Objects.equals(m.getId(), fino.getId())).findFirst().ifPresent(cmbFino::setValue);
            onElegirFino();
        }
        if (grueso != null) {
            cmbGrueso.getItems().stream().filter(m -> Objects.equals(m.getId(), grueso.getId())).findFirst().ifPresent(cmbGrueso::setValue);
            onElegirGrueso();
        }
        if (fino == null) {
            txtAbsorcionFino.clear();
            txtHumedadFino.clear();
        }
        if (grueso == null) {
            txtAbsorcionGrueso.setText(txtAbsGruesoDiseno.getText());
            txtHumedadGrueso.clear();
        }
        txtObservaciones.setText("Dosificación de partida (ACI 211.1). Completar humedad de obra y validar con mezcla de prueba. "
                + String.join(" ", r.advertencias()));
    }

    public boolean yefriLermaNuevaMezclaDesde(String nombre, String referencia, double cemento, double agua,
                                              double finoSss, double gruesoSss, String observaciones) {
        if (yefriLermaSesion.getProyecto() == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione un proyecto en «Modelo 3D y metrado» para guardar la mezcla.");
            return false;
        }
        onNuevaMezcla();
        txtNombre.setText(nombre);
        txtReferencia.setText(referencia);
        txtCemento.setText(yefriLermaFmt(cemento));
        txtAgua.setText(yefriLermaFmt(agua));
        txtFinoSss.setText(yefriLermaFmt(finoSss));
        txtGruesoSss.setText(yefriLermaFmt(gruesoSss));
        txtObservaciones.setText(observaciones);
        javafx.application.Platform.runLater(txtResistencia::requestFocus);
        return true;
    }

    @FXML
    private void onNuevaMezcla() {
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione un proyecto en «Modelo 3D y metrado».");
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(txtNombre, txtReferencia, txtResistencia, cmbFino, cmbGrueso, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones);
        for (TextField campo : List.of(txtNombre, txtReferencia, txtResistencia, txtCemento, txtAgua, txtFinoSss,
                txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso)) {
            campo.clear();
        }
        txtObservaciones.clear();
        cmbFino.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO FINO")));
        cmbGrueso.setItems(FXCollections.observableArrayList(materiales.yefriLermaPorTipo("AGREGADO GRUESO")));
        cmbFino.setValue(null);
        cmbGrueso.setValue(null);
        lblSubtituloMezcla.setText("Proyecto: " + proyecto.getNombre());
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelMezcla);
        Platform.runLater(txtNombre::requestFocus);
    }

    @FXML
    private void onElegirFino() {
        Material m = cmbFino.getValue();
        if (m != null) {
            txtAbsorcionFino.setText(campo(m.getAbsorcion()));
            txtHumedadFino.setText(campo(m.getHumedad()));
        }
    }

    @FXML
    private void onElegirGrueso() {
        Material m = cmbGrueso.getValue();
        if (m != null) {
            txtAbsorcionGrueso.setText(campo(m.getAbsorcion()));
            txtHumedadGrueso.setText(campo(m.getHumedad()));
        }
    }

    @FXML
    private void onGuardarMezcla() {
        Formularios.fernandoHuancaLimpiarErrores(txtNombre, txtReferencia, txtResistencia, cmbFino, cmbGrueso, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones);
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione un proyecto en «Modelo 3D y metrado».");
            return;
        }

        Double resistencia = yefriLermaLeer(txtResistencia);
        Double cemento = yefriLermaLeer(txtCemento);
        Double agua = yefriLermaLeer(txtAgua);
        Double finoSss = yefriLermaLeer(txtFinoSss);
        Double gruesoSss = yefriLermaLeer(txtGruesoSss);
        Double absorcionFino = yefriLermaLeer(txtAbsorcionFino);
        Double humedadFino = yefriLermaLeer(txtHumedadFino);
        Double absorcionGrueso = yefriLermaLeer(txtAbsorcionGrueso);
        Double humedadGrueso = yefriLermaLeer(txtHumedadGrueso);
        if (List.of(resistencia, cemento, agua, finoSss, gruesoSss, absorcionFino, humedadFino, absorcionGrueso,
                humedadGrueso).stream().anyMatch(Objects::isNull)) {
            return;
        }

        Mezcla m = new Mezcla();
        m.setProyectoId(proyecto.getId());
        m.setNombre(txtNombre.getText().trim());
        m.setReferencia(txtReferencia.getText().trim());
        m.setResistencia(resistencia);
        m.setCemento(cemento);
        m.setAgua(agua);
        m.setFinoSss(finoSss);
        m.setGruesoSss(gruesoSss);
        m.setAbsorcionFino(absorcionFino);
        m.setHumedadFino(humedadFino);
        m.setAbsorcionGrueso(absorcionGrueso);
        m.setHumedadGrueso(humedadGrueso);
        m.setObservaciones(txtObservaciones.getText().trim());
        m.setMaterialFino(yefriLermaReferencia(cmbFino.getValue()));
        m.setMaterialGrueso(yefriLermaReferencia(cmbGrueso.getValue()));

        try {
            yefriLermaServicio.yefriLermaGuardar(m);
        } catch (NumberFormatException e) {
            yefriLermaNotificador.fernandoHuancaAdvertencia(YEFRI_LERMA_ERROR_NUMERO);
            return;
        } catch (IllegalArgumentException | NegocioException e) {
            String mensaje = yefriLermaMensaje(e);
            if (mensaje.startsWith("Ingrese el nombre")) {
                Formularios.fernandoHuancaMarcarError(txtNombre, mensaje);
            } else {
                yefriLermaNotificador.fernandoHuancaAdvertencia(mensaje);
            }
            return;
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            return;
        }
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
        yefriLermaNotificador.exito("Mezcla registrada.");
        yefriLermaCargar();
    }

    @FXML
    private void onNuevoEnsayo() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia(YEFRI_LERMA_SIN_MEZCLA);
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
        txtIdentificacion.clear();
        txtFecha.setText(LocalDate.now().toString());
        txtEdad.clear();
        txtResistenciaEnsayo.clear();
        txtAsentamiento.clear();
        txtObservacionesEnsayo.clear();
        lblSubtituloEnsayo.setText("Mezcla: " + m.getNombre());
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelEnsayo);
        Platform.runLater(txtIdentificacion::requestFocus);
    }

    @FXML
    private void onGuardarEnsayo() {
        Formularios.fernandoHuancaLimpiarErrores(txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia(YEFRI_LERMA_SIN_MEZCLA);
            return;
        }

        Double edad = yefriLermaLeer(txtEdad);
        Double resistencia = yefriLermaLeer(txtResistenciaEnsayo);
        Double asentamiento = yefriLermaLeer(txtAsentamiento);
        if (edad == null || resistencia == null || asentamiento == null) {
            return;
        }

        Ensayo x = new Ensayo();
        x.setMezclaId(m.getId());
        x.setIdentificacion(txtIdentificacion.getText().trim());
        x.setFecha(txtFecha.getText().trim());
        x.setEdadDias(edad);
        x.setResistenciaMpa(resistencia);
        x.setAsentamientoMm(asentamiento);
        x.setObservaciones(txtObservacionesEnsayo.getText().trim());

        try {
            yefriLermaServicio.yefriLermaGuardarEnsayo(x);
        } catch (NumberFormatException e) {
            yefriLermaNotificador.fernandoHuancaAdvertencia(YEFRI_LERMA_ERROR_NUMERO);
            return;
        } catch (IllegalArgumentException | NegocioException e) {
            String mensaje = yefriLermaMensaje(e);
            if (mensaje.startsWith("Ingrese la identificación")) {
                Formularios.fernandoHuancaMarcarError(txtIdentificacion, mensaje);
            } else if (mensaje.startsWith("Ingrese una fecha")) {
                Formularios.fernandoHuancaMarcarError(txtFecha, mensaje);
            } else {
                yefriLermaNotificador.fernandoHuancaAdvertencia(mensaje);
            }
            return;
        } catch (Exception e) {
            yefriLermaNotificador.fernandoHuancaError(yefriLermaMensaje(e));
            return;
        }
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
        yefriLermaNotificador.exito("Ensayo registrado.");
        yefriLermaActualizar();
    }

    @FXML
    private void onElegirElementos() {
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("Seleccione un proyecto.");
            return;
        }
        List<Elemento> elementos = proyectos.elementos(proyecto.getId());
        if (elementos.isEmpty()) {
            yefriLermaNotificador.fernandoHuancaAdvertencia("El proyecto no tiene elementos.");
            return;
        }
        Formularios.fernandoHuancaLimpiarError(lstElementos);
        lstElementos.setItems(FXCollections.observableArrayList(elementos));
        lstElementos.getSelectionModel().clearSelection();
        yefriLermaNotificador.fernandoHuancaAbrirPanel(panelElementos);
    }

    @FXML
    private void onUsarSeleccion() {
        List<Elemento> elegidos = lstElementos.getSelectionModel().getSelectedItems();
        if (elegidos.isEmpty()) {
            Formularios.fernandoHuancaMarcarError(lstElementos, "Seleccione al menos un elemento.");
            return;
        }
        double suma = elegidos.stream().mapToDouble(Elemento::volumen).sum();
        txtVolumen.setText(Double.toString(suma));
        yefriLermaNotificador.fernandoHuancaCerrarPanel();
        yefriLermaActualizar();
    }

    @FXML
    private void onExportar() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            yefriLermaNotificador.fernandoHuancaAdvertencia(YEFRI_LERMA_SIN_MEZCLA);
            return;
        }
        Formularios.fernandoHuancaLimpiarError(txtVolumen);
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        double volumen;
        List<Ensayo> ensayos;
        try {
            yefriLermaTexto();
            volumen = yefriLermaNum(txtVolumen);
            ensayos = yefriLermaServicio.ensayos(m.getId());
        } catch (Exception ex) {
            yefriLermaMarcarVolumen(ex);
            yefriLermaNotificador.fernandoHuancaAdvertencia(yefriLermaMensaje(ex));
            return;
        }
        exportador.fernandoHuancaGuardar(tablaMezclas.getScene().getWindow(), "Guardar reporte", "mezcla", Exportador.Formato.PDF,
                destino -> Files.write(destino.toPath(), ReporteMezcla.yefriLermaGenerar(proyecto, m, volumen, ensayos)));
    }

    private void yefriLermaCargar() {
        yefriLermaDatosMezclas.clear();
        yefriLermaDatosEnsayos.clear();
        Proyecto proyecto = yefriLermaSesion.getProyecto();
        if (proyecto != null) {
            yefriLermaDatosMezclas.setAll(yefriLermaServicio.yefriLermaListar(proyecto.getId()));
        }
        lblResultado.setText(YEFRI_LERMA_SIN_MEZCLA);
    }

    private void yefriLermaActualizar() {
        Formularios.fernandoHuancaLimpiarError(txtVolumen);
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        yefriLermaDatosEnsayos.clear();
        if (m == null) {
            lblResultado.setText(YEFRI_LERMA_SIN_MEZCLA);
            return;
        }
        yefriLermaDatosEnsayos.setAll(yefriLermaServicio.ensayos(m.getId()));
        try {
            lblResultado.setText(yefriLermaTexto() + "\n\n" + yefriLermaEvaluacion(m));
        } catch (Exception ex) {
            lblResultado.setText(yefriLermaMensaje(ex));
            yefriLermaMarcarVolumen(ex);
        }
    }

    private String yefriLermaEvaluacion(Mezcla m) {
        List<Double> resultados = new java.util.ArrayList<>();
        for (int i = yefriLermaDatosEnsayos.size() - 1; i >= 0; i--) {
            Ensayo e = yefriLermaDatosEnsayos.get(i);
            if (Math.abs(e.getEdadDias() - 28) <= 0.5) {
                resultados.add(e.getResistenciaMpa());
            }
        }
        return "Evaluación E.060 (f’c " + yefriLermaFmt(m.getResistencia()) + " MPa): "
                + EvaluacionResistencia.yefriLermaEvaluar(m.getResistencia(), resultados).mensaje();
    }

    private String yefriLermaTexto() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            throw new IllegalArgumentException(YEFRI_LERMA_SIN_MEZCLA);
        }
        double v = yefriLermaNum(txtVolumen);
        var r = MezclaService.yefriLermaCalcular(m, v);
        return "Mezcla: " + m.getNombre() + " · f’c especificada: " + yefriLermaFmt(m.getResistencia()) + " MPa\n"
                + "Volumen: " + yefriLermaVolumen(v) + " m³ · relación agua/cemento de diseño: " + yefriLermaFmt(r.relacionAguaCemento()) + "\n"
                + "Cemento: " + yefriLermaFmt(m.getCemento() * v) + " kg · agua a agregar: " + yefriLermaFmt(r.aguaAgregar()) + " kg\n"
                + "Agregado fino húmedo: " + yefriLermaFmt(r.finoHumedo()) + " kg · agregado grueso húmedo: " + yefriLermaFmt(r.gruesoHumedo()) + " kg\n"
                + "Base: masas de agregados SSS por m³; humedad y absorción sobre masa seca. Revise los datos y valide con mezcla de prueba.";
    }

    private void yefriLermaMarcarVolumen(Exception ex) {
        String mensaje = yefriLermaMensaje(ex);
        if (ex instanceof NumberFormatException || mensaje.startsWith("Ingrese un volumen")) {
            Formularios.fernandoHuancaMarcarError(txtVolumen, mensaje);
        }
    }

    private Double yefriLermaLeer(TextField campo) {
        try {
            return yefriLermaNum(campo);
        } catch (NumberFormatException e) {
            Formularios.fernandoHuancaMarcarError(campo, YEFRI_LERMA_ERROR_NUMERO);
            return null;
        }
    }

    private <T> void yefriLermaColumna(TableColumn<T, String> col, Function<T, String> f) {
        col.setCellValueFactory(v -> new SimpleStringProperty(f.apply(v.getValue())));
    }

    private static String campo(Double n) {
        return n == null ? "" : n.toString();
    }

    private static String yefriLermaReferencia(Material m) {
        return m == null ? "No especificado"
                : m.getNombre() + " | informe: " + (m.getReferenciaEnsayo() == null ? "" : m.getReferenciaEnsayo())
                + " | fecha: " + (m.getFechaEnsayo() == null ? "" : m.getFechaEnsayo());
    }

    private static double yefriLermaNum(TextField f) {
        return Double.parseDouble(f.getText().trim().replace(',', '.'));
    }

    private static String yefriLermaFmt(double n) {
        return String.format(Locale.ROOT, "%.3f", n);
    }

    private static String yefriLermaVolumen(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                .replace('.', ',');
    }

    private static String yefriLermaMensaje(Exception ex) {
        if (ex instanceof NumberFormatException) {
            return YEFRI_LERMA_ERROR_NUMERO;
        }
        return ex.getMessage() == null ? "No se pudo completar la operación." : ex.getMessage();
    }
}
