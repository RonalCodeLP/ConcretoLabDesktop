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

    private static final String SIN_MEZCLA = "Seleccione una mezcla.";
    private static final String ERROR_NUMERO = "Ingrese valores numéricos válidos.";

    private final YefriLermaProyectoService proyectos;
    private final MezclaService servicio;
    private final MaterialService materiales;
    private final SesionProyecto sesion;
    private final Notificador notificador;
    private final Exportador exportador;

    // ---- paneles laterales
    @FXML private VBox panelMezcla;
    @FXML private VBox panelEnsayo;
    @FXML private VBox panelElementos;

    // ---- formulario de mezcla
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

    // ---- asistente de diseno
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
    @FXML private TextField txtMfDiseno;
    @FXML private TextField txtDensFinoDiseno;
    @FXML private TextField txtTmaDiseno;
    @FXML private TextField txtPucDiseno;
    @FXML private TextField txtDensGruesoDiseno;
    @FXML private TextField txtAbsGruesoDiseno;
    @FXML private TextField txtDensCemento;
    @FXML private Label lblResultadoDiseno;
    @FXML private Button btnUsarDiseno;
    private DisenoMezclaACI.Resultado ultimoDiseno;
    private double ultimaFc;

    // ---- formulario de ensayo
    @FXML private Label lblSubtituloEnsayo;
    @FXML private TextField txtIdentificacion;
    @FXML private TextField txtFecha;
    @FXML private TextField txtEdad;
    @FXML private TextField txtResistenciaEnsayo;
    @FXML private TextField txtAsentamiento;
    @FXML private TextArea txtObservacionesEnsayo;

    // ---- elegir elementos
    @FXML private ListView<Elemento> lstElementos;

    // ---- pagina
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

    private final ObservableList<Mezcla> datosMezclas = FXCollections.observableArrayList();
    private final ObservableList<Ensayo> datosEnsayos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        columna(colMezcla, Mezcla::getNombre);
        columna(colResistencia, m -> fmt(m.getResistencia()));
        columna(colFuente, m -> m.getReferencia() == null ? "" : m.getReferencia());
        columna(colProbeta, Ensayo::getIdentificacion);
        columna(colFecha, Ensayo::getFecha);
        columna(colEdad, e -> fmt(e.getEdadDias()));
        columna(colResistenciaEnsayo, e -> fmt(e.getResistenciaMpa()));
        columna(colAsentamiento, e -> fmt(e.getAsentamientoMm()));

        Tablas.configurar(tablaMezclas,
                "Seleccione un proyecto en «Modelo 3D y metrado» y registre una mezcla.", m -> { });
        Tablas.configurar(tablaEnsayos, "Seleccione una mezcla para ver sus ensayos.", e -> { });
        tablaMezclas.setItems(datosMezclas);
        tablaEnsayos.setItems(datosEnsayos);

        var seleccion = tablaMezclas.getSelectionModel().selectedItemProperty();
        seleccion.addListener((obs, anterior, actual) -> actualizar());
        btnCalcular.disableProperty().bind(seleccion.isNull());
        btnPdf.disableProperty().bind(seleccion.isNull());
        btnEnsayo.disableProperty().bind(seleccion.isNull());

        lstElementos.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lstElementos.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Elemento item, boolean vacia) {
                super.updateItem(item, vacia);
                setText(vacia || item == null ? null
                        : item.getNombre() + " · " + item.getTipo() + " · " + volumen(item.volumen()) + " m³");
            }
        });
        lstElementos.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<Elemento>) c -> Formularios.limpiarError(lstElementos));

        cmbExposicion.setItems(FXCollections.observableArrayList(DisenoMezclaACI.Exposicion.values()));
        cmbExposicion.setValue(DisenoMezclaACI.Exposicion.NORMAL);
        cmbSeveridad.setItems(FXCollections.observableArrayList(DisenoMezclaACI.SeveridadAire.values()));
        cmbSeveridad.setValue(DisenoMezclaACI.SeveridadAire.MODERADA);
        cmbSeveridad.disableProperty().bind(chkAire.selectedProperty().not());
        Formularios.limpiarAlEditar(txtFcDiseno, txtAsentDiseno, txtDesviacion, txtNumEnsayos, txtMfDiseno,
                txtDensFinoDiseno, txtTmaDiseno, txtPucDiseno, txtDensGruesoDiseno, txtAbsGruesoDiseno, txtDensCemento);

        Formularios.limpiarAlEditar(txtVolumen, txtNombre, txtReferencia, txtResistencia, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones, txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
    }

    @Override
    public void refrescar() {
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto == null) {
            lblProyecto.setText("Seleccione un proyecto");
            if (!lblProyecto.getStyleClass().contains("neutro")) {
                lblProyecto.getStyleClass().add("neutro");
            }
        } else {
            lblProyecto.setText(proyecto.getNombre());
            lblProyecto.getStyleClass().remove("neutro");
        }
        cargar();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onCerrarPanel() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onCalcular() {
        actualizar();
    }

    // -------------------------------------------------- asistente de diseño

    @FXML
    private void onDisenar() {
        cmbFinoDiseno.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO FINO")));
        cmbGruesoDiseno.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO GRUESO")));
        ultimoDiseno = null;
        btnUsarDiseno.setDisable(true);
        lblResultadoDiseno.setText("Complete los datos y pulse «Calcular diseño».");
        notificador.abrirPanel(panelDiseno);
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
    private void onCalcularDiseno() {
        List<TextField> campos = List.of(txtFcDiseno, txtAsentDiseno, txtDesviacion, txtNumEnsayos, txtMfDiseno,
                txtDensFinoDiseno, txtTmaDiseno, txtPucDiseno, txtDensGruesoDiseno, txtAbsGruesoDiseno, txtDensCemento);
        Formularios.limpiarErrores(campos.toArray(new TextField[0]));
        ultimoDiseno = null;
        btnUsarDiseno.setDisable(true);
        boolean faltan = false;
        for (TextField c : campos) {
            if (c != txtDesviacion && c != txtNumEnsayos && c.getText().isBlank()) {
                Formularios.marcarError(c, "Complete este dato.");
                faltan = true;
            }
        }
        if (faltan) {
            lblResultadoDiseno.setText("Faltan datos: complete los campos marcados.");
            return;
        }
        try {
            double fc = num(txtFcDiseno);
            Double s = txtDesviacion.getText().isBlank() ? null : num(txtDesviacion);
            int n = txtNumEnsayos.getText().isBlank() ? 0 : (int) Math.round(num(txtNumEnsayos));
            var entrada = new DisenoMezclaACI.Entrada(fc, s, n, num(txtAsentDiseno), num(txtTmaDiseno), num(txtMfDiseno),
                    num(txtPucDiseno), num(txtDensCemento), num(txtDensFinoDiseno), num(txtDensGruesoDiseno),
                    num(txtAbsGruesoDiseno), cmbExposicion.getValue(), chkAire.isSelected(), cmbSeveridad.getValue());
            ultimoDiseno = DisenoMezclaACI.disenar(entrada);
            ultimaFc = fc;
            lblResultadoDiseno.setText(textoDiseno(ultimoDiseno));
            btnUsarDiseno.setDisable(false);
        } catch (NumberFormatException e) {
            lblResultadoDiseno.setText(ERROR_NUMERO);
        } catch (IllegalArgumentException e) {
            lblResultadoDiseno.setText(mensaje(e));
        }
    }

        private static String f1(double n) { return String.format(Locale.ROOT, "%.1f", n); }
    private static String f2(double n) { return String.format(Locale.ROOT, "%.2f", n); }

    private static String textoDiseno(DisenoMezclaACI.Resultado r) {
        StringBuilder sb = new StringBuilder();
        sb.append("f’cr requerida: ").append(f1(r.fcrMpa())).append(" MPa\n").append(r.criterioFcr()).append("\n\n");
        sb.append("a/c: ").append(f2(r.relacionAc())).append(" (por resistencia ").append(f2(r.relacionAcResistencia()));
        if (r.relacionAcDurabilidad() > 0) {
            sb.append(", máxima por durabilidad ").append(f2(r.relacionAcDurabilidad()));
        }
        sb.append(")\nTMA de tabla: ").append(f1(r.tmaTabla())).append(" mm · aire: ").append(f1(r.airePorcentaje())).append(" %\n\n");
        sb.append("Cemento: ").append(f1(r.cemento())).append(" kg/m³ (").append(f1(r.bolsasCemento())).append(" bolsas de 42,5 kg)\n");
        sb.append("Agua de mezclado: ").append(f1(r.agua())).append(" kg/m³\n");
        sb.append("Fino SSS: ").append(f1(r.finoSss())).append(" kg/m³\n");
        sb.append("Grueso SSS: ").append(f1(r.gruesoSss())).append(" kg/m³ (seco ").append(f1(r.gruesoSeco()))
                .append(" kg, volumen relativo ").append(f2(r.volumenRelativoGrueso())).append(")\n");
        sb.append("Proporción en peso: ").append(r.proporcionEnPeso()).append("\n\nAvisos:");
        r.advertencias().forEach(a -> sb.append("\n• ").append(a));
        return sb.toString();
    }

    @FXML
    private void onUsarDiseno() {
        if (ultimoDiseno == null) {
            return;
        }
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto == null) {
            notificador.advertencia("Seleccione un proyecto en «Modelo 3D y metrado» para guardar la mezcla.");
            return;
        }
        var r = ultimoDiseno;
        onNuevaMezcla();
        txtNombre.setText("Diseño ACI f’c " + fmt(ultimaFc) + " MPa");
        txtReferencia.setText("Asistente ACI 211.1 + E.060");
        txtResistencia.setText(Double.toString(ultimaFc));
        txtCemento.setText(fmt(r.cemento()));
        txtAgua.setText(fmt(r.agua()));
        txtFinoSss.setText(fmt(r.finoSss()));
        txtGruesoSss.setText(fmt(r.gruesoSss()));
        Material fino = cmbFinoDiseno.getValue();
        Material grueso = cmbGruesoDiseno.getValue();
        cmbFino.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO FINO")));
        cmbGrueso.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO GRUESO")));
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

    @FXML
    private void onNuevaMezcla() {
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto == null) {
            notificador.advertencia("Seleccione un proyecto en «Modelo 3D y metrado».");
            return;
        }
        Formularios.limpiarErrores(txtNombre, txtReferencia, txtResistencia, cmbFino, cmbGrueso, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones);
        for (TextField campo : List.of(txtNombre, txtReferencia, txtResistencia, txtCemento, txtAgua, txtFinoSss,
                txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso)) {
            campo.clear();
        }
        txtObservaciones.clear();
        cmbFino.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO FINO")));
        cmbGrueso.setItems(FXCollections.observableArrayList(materiales.porTipo("AGREGADO GRUESO")));
        cmbFino.setValue(null);
        cmbGrueso.setValue(null);
        lblSubtituloMezcla.setText("Proyecto: " + proyecto.getNombre());
        notificador.abrirPanel(panelMezcla);
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
        Formularios.limpiarErrores(txtNombre, txtReferencia, txtResistencia, cmbFino, cmbGrueso, txtCemento, txtAgua,
                txtFinoSss, txtGruesoSss, txtAbsorcionFino, txtHumedadFino, txtAbsorcionGrueso, txtHumedadGrueso,
                txtObservaciones);
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto == null) {
            notificador.advertencia("Seleccione un proyecto en «Modelo 3D y metrado».");
            return;
        }

        Double resistencia = leer(txtResistencia);
        Double cemento = leer(txtCemento);
        Double agua = leer(txtAgua);
        Double finoSss = leer(txtFinoSss);
        Double gruesoSss = leer(txtGruesoSss);
        Double absorcionFino = leer(txtAbsorcionFino);
        Double humedadFino = leer(txtHumedadFino);
        Double absorcionGrueso = leer(txtAbsorcionGrueso);
        Double humedadGrueso = leer(txtHumedadGrueso);
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
        m.setMaterialFino(referencia(cmbFino.getValue()));
        m.setMaterialGrueso(referencia(cmbGrueso.getValue()));

        try {
            servicio.guardar(m);
        } catch (NumberFormatException e) {
            notificador.advertencia(ERROR_NUMERO);
            return;
        } catch (IllegalArgumentException | NegocioException e) {
            String mensaje = mensaje(e);
            if (mensaje.startsWith("Ingrese el nombre")) {
                Formularios.marcarError(txtNombre, mensaje);
            } else {
                notificador.advertencia(mensaje);
            }
            return;
        } catch (Exception e) {
            notificador.error(mensaje(e));
            return;
        }
        notificador.cerrarPanel();
        notificador.exito("Mezcla registrada.");
        cargar();
    }

    @FXML
    private void onNuevoEnsayo() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            notificador.advertencia(SIN_MEZCLA);
            return;
        }
        Formularios.limpiarErrores(txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
        txtIdentificacion.clear();
        txtFecha.setText(LocalDate.now().toString());
        txtEdad.clear();
        txtResistenciaEnsayo.clear();
        txtAsentamiento.clear();
        txtObservacionesEnsayo.clear();
        lblSubtituloEnsayo.setText("Mezcla: " + m.getNombre());
        notificador.abrirPanel(panelEnsayo);
        Platform.runLater(txtIdentificacion::requestFocus);
    }

    @FXML
    private void onGuardarEnsayo() {
        Formularios.limpiarErrores(txtIdentificacion, txtFecha, txtEdad, txtResistenciaEnsayo, txtAsentamiento,
                txtObservacionesEnsayo);
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            notificador.advertencia(SIN_MEZCLA);
            return;
        }

        Double edad = leer(txtEdad);
        Double resistencia = leer(txtResistenciaEnsayo);
        Double asentamiento = leer(txtAsentamiento);
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
            servicio.guardarEnsayo(x);
        } catch (NumberFormatException e) {
            notificador.advertencia(ERROR_NUMERO);
            return;
        } catch (IllegalArgumentException | NegocioException e) {
            String mensaje = mensaje(e);
            if (mensaje.startsWith("Ingrese la identificación")) {
                Formularios.marcarError(txtIdentificacion, mensaje);
            } else if (mensaje.startsWith("Ingrese una fecha")) {
                Formularios.marcarError(txtFecha, mensaje);
            } else {
                notificador.advertencia(mensaje);
            }
            return;
        } catch (Exception e) {
            notificador.error(mensaje(e));
            return;
        }
        notificador.cerrarPanel();
        notificador.exito("Ensayo registrado.");
        actualizar();
    }

    @FXML
    private void onElegirElementos() {
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto == null) {
            notificador.advertencia("Seleccione un proyecto.");
            return;
        }
        List<Elemento> elementos = proyectos.elementos(proyecto.getId());
        if (elementos.isEmpty()) {
            notificador.advertencia("El proyecto no tiene elementos.");
            return;
        }
        Formularios.limpiarError(lstElementos);
        lstElementos.setItems(FXCollections.observableArrayList(elementos));
        lstElementos.getSelectionModel().clearSelection();
        notificador.abrirPanel(panelElementos);
    }

    @FXML
    private void onUsarSeleccion() {
        List<Elemento> elegidos = lstElementos.getSelectionModel().getSelectedItems();
        if (elegidos.isEmpty()) {
            Formularios.marcarError(lstElementos, "Seleccione al menos un elemento.");
            return;
        }
        double suma = elegidos.stream().mapToDouble(Elemento::volumen).sum();
        txtVolumen.setText(Double.toString(suma));
        notificador.cerrarPanel();
        actualizar();
    }

    @FXML
    private void onExportar() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            notificador.advertencia(SIN_MEZCLA);
            return;
        }
        Formularios.limpiarError(txtVolumen);
        Proyecto proyecto = sesion.getProyecto();
        double volumen;
        List<Ensayo> ensayos;
        try {
            texto();
            volumen = num(txtVolumen);
            ensayos = servicio.ensayos(m.getId());
        } catch (Exception ex) {
            marcarVolumen(ex);
            notificador.advertencia(mensaje(ex));
            return;
        }
        exportador.guardar(tablaMezclas.getScene().getWindow(), "Guardar reporte", "mezcla", Exportador.Formato.PDF,
                destino -> Files.write(destino.toPath(), ReporteMezcla.generar(proyecto, m, volumen, ensayos)));
    }

    // ----------------------------------------------------------------- apoyo

    private void cargar() {
        datosMezclas.clear();
        datosEnsayos.clear();
        Proyecto proyecto = sesion.getProyecto();
        if (proyecto != null) {
            datosMezclas.setAll(servicio.listar(proyecto.getId()));
        }
        lblResultado.setText(SIN_MEZCLA);
    }

    private void actualizar() {
        Formularios.limpiarError(txtVolumen);
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        datosEnsayos.clear();
        if (m == null) {
            lblResultado.setText(SIN_MEZCLA);
            return;
        }
        datosEnsayos.setAll(servicio.ensayos(m.getId()));
        try {
            lblResultado.setText(texto() + "\n\n" + evaluacion(m));
        } catch (Exception ex) {
            lblResultado.setText(mensaje(ex));
            marcarVolumen(ex);
        }
    }

    /** Evaluación E.060 (art. 5.6) con los ensayos de 28 días en el orden en que se registraron. */
    private String evaluacion(Mezcla m) {
        List<Double> resultados = new java.util.ArrayList<>();
        for (int i = datosEnsayos.size() - 1; i >= 0; i--) { // la lista viene del más reciente al más antiguo
            Ensayo e = datosEnsayos.get(i);
            if (Math.abs(e.getEdadDias() - 28) <= 0.5) {
                resultados.add(e.getResistenciaMpa());
            }
        }
        return "Evaluación E.060 (f’c " + fmt(m.getResistencia()) + " MPa): "
                + EvaluacionResistencia.evaluar(m.getResistencia(), resultados).mensaje();
    }

    private String texto() {
        Mezcla m = tablaMezclas.getSelectionModel().getSelectedItem();
        if (m == null) {
            throw new IllegalArgumentException(SIN_MEZCLA);
        }
        double v = num(txtVolumen);
        var r = MezclaService.calcular(m, v);
        return "Mezcla: " + m.getNombre() + " · f’c especificada: " + fmt(m.getResistencia()) + " MPa\n"
                + "Volumen: " + volumen(v) + " m³ · relación agua/cemento de diseño: " + fmt(r.relacionAguaCemento()) + "\n"
                + "Cemento: " + fmt(m.getCemento() * v) + " kg · agua a agregar: " + fmt(r.aguaAgregar()) + " kg\n"
                + "Agregado fino húmedo: " + fmt(r.finoHumedo()) + " kg · agregado grueso húmedo: " + fmt(r.gruesoHumedo()) + " kg\n"
                + "Base: masas de agregados SSS por m³; humedad y absorción sobre masa seca. Revise los datos y valide con mezcla de prueba.";
    }

    /** Marca el campo de volumen cuando el error del calculo proviene de el. */
    private void marcarVolumen(Exception ex) {
        String mensaje = mensaje(ex);
        if (ex instanceof NumberFormatException || mensaje.startsWith("Ingrese un volumen")) {
            Formularios.marcarError(txtVolumen, mensaje);
        }
    }

    /** Lee un decimal del campo; si no es valido lo marca y devuelve {@code null}. */
    private Double leer(TextField campo) {
        try {
            return num(campo);
        } catch (NumberFormatException e) {
            Formularios.marcarError(campo, ERROR_NUMERO);
            return null;
        }
    }

    private <T> void columna(TableColumn<T, String> col, Function<T, String> f) {
        col.setCellValueFactory(v -> new SimpleStringProperty(f.apply(v.getValue())));
    }

    private static String campo(Double n) {
        return n == null ? "" : n.toString();
    }

    private static String referencia(Material m) {
        return m == null ? "No especificado"
                : m.getNombre() + " | informe: " + (m.getReferenciaEnsayo() == null ? "" : m.getReferenciaEnsayo())
                + " | fecha: " + (m.getFechaEnsayo() == null ? "" : m.getFechaEnsayo());
    }

    private static double num(TextField f) {
        return Double.parseDouble(f.getText().trim().replace(',', '.'));
    }

    private static String fmt(double n) {
        return String.format(Locale.ROOT, "%.3f", n);
    }

    /** Mismo formato que {@code FormatoNumeros.volumen} (clase de uso interno del paquete ui). */
    private static String volumen(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                .replace('.', ',');
    }

    private static String mensaje(Exception ex) {
        if (ex instanceof NumberFormatException) {
            return ERROR_NUMERO;
        }
        return ex.getMessage() == null ? "No se pudo completar la operación." : ex.getMessage();
    }
}
