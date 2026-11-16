package com.concretolab.controller;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.AnalisisSismico;
import com.concretolab.service.AnalisisSismico.CargaPiso;
import com.concretolab.service.AnalisisSismico.Direccion;
import com.concretolab.service.AnalisisSismico.Entrada;
import com.concretolab.service.AnalisisSismico.Modo;
import com.concretolab.service.AnalisisSismico.PisoBase;
import com.concretolab.service.AnalisisSismico.Resultado;
import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Suelo;
import com.concretolab.service.NormaE030.Version;
import com.concretolab.service.NormaE030.Zona;
import com.concretolab.service.ProyectoService;
import com.concretolab.service.ReporteSismo;
import com.concretolab.ui.Exportador;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import com.concretolab.ui.Tablas;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Analisis sismico E.030. Los datos de entrada no se guardan en la base: se ingresan en cada sesion.
 * El proyecto analizado es el de {@link SesionProyecto}.
 */
@Component
@RequiredArgsConstructor
public class SismoController implements Refrescable {

    private static final String ESTADO_INICIAL = "Defina los datos y pulse «Calcular».";

    private final ProyectoService servicio;
    private final SesionProyecto sesion;
    private final Notificador notificador;
    private final Exportador exportador;

    @FXML private VBox panelVacio;
    @FXML private HBox panelContenido;

    private boolean actualizandoProyectos;
    @FXML private ComboBox<Proyecto> cmbProyecto;
    @FXML private ComboBox<Version> cmbVersion;
    @FXML private ComboBox<Zona> cmbZona;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<Suelo> cmbSuelo;
    @FXML private ComboBox<Double> cmbIa;
    @FXML private ComboBox<Double> cmbIp;
    @FXML private ComboBox<Double> cmbCt;
    @FXML private TextField txtVs30;
    @FXML private TextField txtR0;
    @FXML private TextField txtFc;
    @FXML private TextField txtPoisson;
    @FXML private TextField txtRigidez;
    @FXML private TextField txtEspesor;

    @FXML private TableView<FilaPiso> tablaPisos;
    @FXML private TableColumn<FilaPiso, String> colPiso;
    @FXML private TableColumn<FilaPiso, String> colCota;
    @FXML private TableColumn<FilaPiso, String> colArea;
    @FXML private TableColumn<FilaPiso, String> colCm;
    @FXML private TableColumn<FilaPiso, String> colCv;

    @FXML private Button btnCalcular;
    @FXML private Button btnExportar;
    @FXML private Label lblEstado;
    @FXML private TabPane tabsResultados;

    /** Proyecto y pisos con los que se armo la tabla; permiten conservar lo editado si nada cambio. */
    private Long idProyectoCargado;
    private List<PisoBase> pisosCargados = List.of();

    /** Datos del ultimo calculo (los usa la exportacion a PDF). */
    private Proyecto proyectoCalculado;
    private Entrada entrada;
    private Resultado resultado;

    /** Fila editable de la tabla de pisos. */
    public static final class FilaPiso {
        final int numero;
        final double cota;
        final StringProperty area;
        final StringProperty cm;
        final StringProperty cv;

        FilaPiso(int numero, double cota, double area) {
            this.numero = numero;
            this.cota = cota;
            this.area = new SimpleStringProperty(f(area, 2));
            this.cm = new SimpleStringProperty("1.0");
            this.cv = new SimpleStringProperty("2.0");
        }
    }

    @FXML
    private void initialize() {
        cmbProyecto.valueProperty().addListener((o, anterior, nuevo) -> {
            if (!actualizandoProyectos && nuevo != null) {
                sesion.setProyecto(nuevo);
                cargarProyectoActivo();
            }
        });
        cmbZona.getItems().addAll(Zona.values());
        cmbZona.setValue(Zona.Z4);
        cmbCategoria.getItems().addAll(Categoria.values());
        cmbCategoria.setValue(Categoria.C);
        cmbVersion.getItems().addAll(Version.values());
        cmbVersion.setValue(Version.V2026);
        onCambiarVersion();
        cmbIa.getItems().addAll(1.0, 0.90, 0.80, 0.75, 0.60, 0.50);
        cmbIa.setValue(1.0);
        cmbIp.getItems().addAll(1.0, 0.90, 0.85, 0.75, 0.60);
        cmbIp.setValue(1.0);
        cmbCt.getItems().addAll(35.0, 45.0, 60.0);
        cmbCt.setValue(35.0);

        Formularios.limpiarAlEditar(txtVs30, txtR0, txtFc, txtPoisson, txtRigidez, txtEspesor);

        Tablas.configurar(tablaPisos, "No se detectaron pisos en el proyecto.", fila -> { });
        tablaPisos.getStyleClass().add("tabla-compacta");
        columnaFija(colPiso, c -> new SimpleStringProperty("" + c.numero));
        columnaFija(colCota, c -> new SimpleStringProperty(f(c.cota, 2)));
        columnaEditable(colArea, c -> c.area);
        columnaEditable(colCm, c -> c.cm);
        columnaEditable(colCv, c -> c.cv);
        for(var campo:List.of(txtVs30,txtR0,txtFc,txtPoisson,txtRigidez,txtEspesor))
            campo.textProperty().addListener((o,a,b)->invalidar());
        for(var combo:List.of(cmbVersion,cmbZona,cmbCategoria,cmbSuelo,cmbIa,cmbIp,cmbCt))
            combo.valueProperty().addListener((o,a,b)->invalidar());
    }

    /** Se llama cada vez que se muestra la vista: relee el proyecto activo y recarga los pisos. */
    @Override
    public void refrescar() {
        Proyecto activo = sesion.getProyecto();
        actualizandoProyectos = true;
        try {
            cmbProyecto.getItems().setAll(servicio.proyectos());
            cmbProyecto.setValue(cmbProyecto.getItems().stream()
                    .filter(p -> activo != null && Objects.equals(p.getId(), activo.getId()))
                    .findFirst().orElse(null));
        } finally {
            actualizandoProyectos = false;
        }
        cargarProyectoActivo();
    }

    private void cargarProyectoActivo() {
        Proyecto p = sesion.getProyecto();
        boolean hayProyecto = p != null;
        panelVacio.setVisible(!hayProyecto);
        panelVacio.setManaged(!hayProyecto);
        panelContenido.setVisible(hayProyecto);
        panelContenido.setManaged(hayProyecto);
        if (!hayProyecto) {
            idProyectoCargado = null;
            pisosCargados = List.of();
            tablaPisos.getItems().clear();
            limpiarResultados();
            lblEstado.setText(ESTADO_INICIAL);
            return;
        }

        List<PisoBase> pisos = List.of();
        String problema = null;
        try {
            pisos = AnalisisSismico.pisos(servicio.elementos(p.getId()));
        } catch (IllegalArgumentException ex) {
            problema = ex.getMessage();
        }
        // Mismo proyecto y mismos pisos: se conservan los valores editados y los resultados.
        if (problema == null && Objects.equals(idProyectoCargado, p.getId()) && pisos.equals(pisosCargados)) {
            if(entrada!=null && !geometria(entrada.elementos()).equals(geometria(servicio.elementos(p.getId())))) invalidar();
            return;
        }

        idProyectoCargado = p.getId();
        pisosCargados = pisos;
        tablaPisos.getItems().clear();
        for (PisoBase b : pisos) {
            tablaPisos.getItems().add(new FilaPiso(tablaPisos.getItems().size() + 1, b.cota(), b.areaPorDefecto()));
        }
        limpiarResultados();
        lblEstado.setText(problema != null ? problema : ESTADO_INICIAL);
    }

    /** Cambia la lista de suelos y habilita Vs30 segun la version de la norma. */
    @FXML
    private void onCambiarVersion() {
        Version v = cmbVersion.getValue();
        if (v == null) {
            return;
        }
        Suelo anterior = cmbSuelo.getValue();
        int idx = anterior == null ? 2 : Math.max(0, cmbSuelo.getItems().indexOf(anterior));
        cmbSuelo.getItems().setAll(Suelo.de(v));
        cmbSuelo.setValue(cmbSuelo.getItems().get(Math.min(idx, cmbSuelo.getItems().size() - 1)));
        txtVs30.setDisable(v != Version.V2026);
        if (v != Version.V2026) {
            txtVs30.clear();
        }
    }

    @FXML
    private void onCalcular() {
        Proyecto p = sesion.getProyecto();
        if (p == null) {
            return;
        }
        Formularios.limpiarErrores(txtVs30, txtR0, txtFc, txtPoisson, txtRigidez, txtEspesor);
        try {
            List<Elemento> el = servicio.elementos(p.getId());
            List<CargaPiso> cargas = new ArrayList<>();
            for (FilaPiso fp : tablaPisos.getItems()) {
                cargas.add(new CargaPiso(num(fp.area.get()), num(fp.cm.get()), num(fp.cv.get())));
            }
            Version version = cmbVersion.getValue();
            double r0 = leer(txtR0);
            double vs30 = version == Version.V2026 && !txtVs30.getText().isBlank() ? leer(txtVs30) : Double.NaN;
            Parametros norma = new Parametros(version, cmbZona.getValue(), cmbCategoria.getValue(), cmbSuelo.getValue(),
                    r0, cmbIa.getValue(), cmbIp.getValue(), cmbCt.getValue(), vs30);
            double fc = leer(txtFc);
            double poisson = leer(txtPoisson);
            double rigidez = leer(txtRigidez);
            double espesor = leer(txtEspesor);
            entrada = new Entrada(el, fc, poisson, rigidez, espesor, cargas, norma);
            resultado = AnalisisSismico.analizar(entrada);
            proyectoCalculado = p;
            mostrar();
            btnExportar.setDisable(false);
            lblEstado.setText("Análisis calculado: " + resultado.pisos().size() + " pisos, " + resultado.modos().size() + " modos.");
        } catch (NumberFormatException ex) {
            error("Revise que los campos numéricos contengan solo números.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    @FXML
    private void onExportarPdf() {
        if (entrada == null || resultado == null) {
            return;
        }
        Proyecto p = proyectoCalculado;
        Entrada e = entrada;
        Resultado r = resultado;
        exportador.guardar(btnExportar.getScene().getWindow(), "Guardar reporte sísmico", "analisis-sismico",
                Exportador.Formato.PDF, destino -> Files.write(destino.toPath(), ReporteSismo.generar(p, e, r)));
    }

    // ------------------------------------------------------------------ utilidades

    private void error(String mensaje) {
        lblEstado.setText(mensaje);
        notificador.error(mensaje);
    }

    private void invalidar(){
        if(resultado!=null){limpiarResultados();lblEstado.setText("Datos modificados. Vuelva a calcular.");}
    }

    private static List<?> geometria(List<Elemento> elementos){
        return elementos.stream().map(e->List.of(e.getId(),e.getTipo(),e.getX(),e.getY(),e.getZ(),e.getLargo(),e.getAncho(),e.getAltura())).toList();
    }

    private void limpiarResultados() {
        tabsResultados.getTabs().clear();
        entrada = null;
        resultado = null;
        proyectoCalculado = null;
        btnExportar.setDisable(true);
    }

    /** Lee un campo numerico; si no es valido lo marca y relanza la excepcion. */
    private double leer(TextField campo) {
        try {
            return num(campo.getText());
        } catch (NumberFormatException ex) {
            Formularios.marcarError(campo, "Ingrese un número válido.");
            throw ex;
        }
    }

    private static double num(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    private void columnaFija(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setEditable(false);
    }

    private void columnaEditable(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setCellFactory(TextFieldTableCell.forTableColumn());
        c.setOnEditCommit(e -> {valor.apply(e.getRowValue()).set(e.getNewValue());invalidar();});
    }

    // ------------------------------------------------------------------ resultados

    private static TableView<String[]> tabla(String[] cab, List<String[]> filas) {
        TableView<String[]> t = new TableView<>();
        Tablas.configurar(t, "Sin datos para mostrar.", fila -> { });
        t.getStyleClass().add("tabla-compacta");
        for (int i = 0; i < cab.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(cab[i]);
            c.setCellValueFactory(x -> new SimpleStringProperty(x.getValue()[k]));
            // Ancho mínimo según el título, para que «Piso» o «Δ esp. X (mm)» no se corten con «…».
            c.setMinWidth(Math.min(112, Math.max(56, cab[i].length() * 7.0 + 22)));
            t.getColumns().add(c);
        }
        t.getItems().setAll(filas);
        return t;
    }

    private void mostrar() {
        Resultado r = resultado;
        Parametros n = entrada.norma();
        tabsResultados.getTabs().clear();
        // Resumen
        List<String[]> res = new ArrayList<>(List.of(
                new String[]{"Versión de la norma", n.version().etiqueta},
                new String[]{"Z · U · S", n.z() + " · " + n.u() + " · " + n.s()},
                new String[]{"Tp · TL (s)", n.tp() + " · " + n.tl()},
                new String[]{"R = R0·Ia·Ip", f(n.r(), 3) + (n.regular() ? "  (regular)" : "  (irregular)")},
                new String[]{"Ec (MPa) · E.060", f(r.ecMpa(), 0)},
                new String[]{"Peso sísmico total (kN)", f(r.pesoTotal(), 1)},
                new String[]{"T fundamental X · Y (s)", f(r.espectralX().periodo(), 3) + " · " + f(r.espectralY().periodo(), 3)},
                new String[]{"T aproximado hn/CT (s)", f(r.estaticoX().periodo(), 3)},
                new String[]{"Masa efectiva acumulada X · Y", f(r.masaAcumuladaX() * 100, 1) + " % · " + f(r.masaAcumuladaY() * 100, 1) + " %"},
                new String[]{"Cortante basal estático X · Y (kN)", f(r.estaticoX().cortanteBase(), 1) + " · " + f(r.estaticoY().cortanteBase(), 1)},
                new String[]{"Cortante basal espectral X · Y (kN)", f(r.espectralX().cortanteBase(), 1) + " · " + f(r.espectralY().cortanteBase(), 1)},
                new String[]{"Deriva máxima (límite " + NormaE030.DERIVA_LIMITE_CONCRETO + ")",
                        "X: " + f(Math.max(r.estaticoX().derivaMaxima(), r.espectralX().derivaMaxima()), 5)
                                + " · Y: " + f(Math.max(r.estaticoY().derivaMaxima(), r.espectralY().derivaMaxima()), 5)},
                new String[]{"Verificación de deriva",
                        r.estaticoX().cumple() && r.estaticoY().cumple() && r.espectralX().cumple() && r.espectralY().cumple()
                                ? "CUMPLE (preliminar)" : "NO CUMPLE"}));
        VBox avisos = new VBox(4);
        avisos.setMinHeight(Region.USE_PREF_SIZE);
        for (String a : r.advertencias()) {
            Label l = new Label("• " + a);
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE); // sin esto el VBox encoge el texto largo a una línea con «…»
            avisos.getChildren().add(l);
        }
        TableView<String[]> tablaResumen = tabla(new String[]{"Concepto", "Valor"}, res);
        VBox resumen = new VBox(10, tablaResumen, avisos);
        VBox.setVgrow(tablaResumen, Priority.ALWAYS);
        tabsResultados.getTabs().add(tab("Resumen", resumen));
        tabsResultados.getTabs().add(tab("Simulación 3D", new com.concretolab.ui.VistaModal(entrada.elementos(), r)));
        // Pisos
        tabsResultados.getTabs().add(tab("Pisos", tabla(new String[]{"Piso", "Cota (m)", "h (m)", "Área (m²)", "Peso (kN)", "X cm", "Y cm", "I polar"},
                r.pisos().stream().map(s -> new String[]{"" + s.numero(), f(s.cota(), 2), f(s.altura(), 2), f(s.area(), 2), f(s.peso(), 1), f(s.xcm(), 3), f(s.ycm(), 3), f(s.inercia(), 0)}).toList())));
        // Modos
        tabsResultados.getTabs().add(tab("Modos", tabla(new String[]{"Modo", "T (s)", "f (Hz)", "Masa X (%)", "Masa Y (%)"},
                r.modos().stream().map(m -> new String[]{"" + m.numero(), f(m.periodo(), 4), f(1 / m.periodo(), 3), f(m.masaEfectivaX() * 100, 1), f(m.masaEfectivaY() * 100, 1)}).toList())));
        tabsResultados.getTabs().add(tab("Estático", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.estaticoX(), r.estaticoY()))));
        tabsResultados.getTabs().add(tab("Espectral", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.espectralX(), r.espectralY()))));
        // Derivas
        List<String[]> der = new ArrayList<>();
        for (int i = r.pisos().size() - 1; i >= 0; i--) {
            double max = Math.max(Math.max(r.estaticoX().derivasMaximas()[i], r.estaticoY().derivasMaximas()[i]),
                    Math.max(r.espectralX().derivasMaximas()[i], r.espectralY().derivasMaximas()[i]));
            der.add(new String[]{"" + (i + 1), f(r.estaticoX().derivasMaximas()[i], 5), f(r.estaticoY().derivasMaximas()[i], 5),
                    f(r.espectralX().derivasMaximas()[i], 5), f(r.espectralY().derivasMaximas()[i], 5),
                    f(r.espectralX().desplazamientosCm()[i] * 1000, 1), f(r.espectralY().desplazamientosCm()[i] * 1000, 1),
                    max <= NormaE030.DERIVA_LIMITE_CONCRETO ? "Cumple" : "NO CUMPLE"});
        }
        tabsResultados.getTabs().add(tab("Derivas", tabla(new String[]{"Piso", "Est. X", "Est. Y", "Esp. X", "Esp. Y", "Δ esp. X (mm)", "Δ esp. Y (mm)", "Estado"}, der)));
        tabsResultados.getTabs().add(tab("Espectro", espectro(r, n)));
    }

    private static Tab tab(String titulo, Node contenido) {
        return new Tab(titulo, contenido);
    }

    private static List<String[]> fuerzas(Direccion x, Direccion y) {
        List<String[]> l = new ArrayList<>();
        for (int i = x.fuerzas().length - 1; i >= 0; i--) {
            l.add(new String[]{"" + (i + 1), f(x.fuerzas()[i], 2), f(x.cortantes()[i], 2), f(y.fuerzas()[i], 2), f(y.cortantes()[i], 2)});
        }
        return l;
    }

    /** Espectro de diseno con los periodos modales; los colores y el estilo de marcadores salen del CSS ("grafico-espectro"). */
    private static Node espectro(Resultado r, Parametros n) {
        NumberAxis ejeX = new NumberAxis(0, 4, 0.5);
        NumberAxis ejeY = new NumberAxis();
        ejeX.setLabel("T (s)");
        ejeY.setLabel("Sa / g");
        LineChart<Number, Number> grafico = new LineChart<>(ejeX, ejeY);
        grafico.setAnimated(false);
        grafico.getStyleClass().add("grafico-espectro");
        XYChart.Series<Number, Number> curva = new XYChart.Series<>();
        curva.setName("Espectro de diseño E.030");
        for (double t = 0.02; t <= 4.0001; t += 0.02) {
            curva.getData().add(new XYChart.Data<>(t, n.saSobreG(t)));
        }
        XYChart.Series<Number, Number> puntos = new XYChart.Series<>();
        puntos.setName("Periodos modales");
        for (Modo m : r.modos()) {
            if (m.periodo() <= 4) {
                puntos.getData().add(new XYChart.Data<>(m.periodo(), n.saSobreG(m.periodo())));
            }
        }
        grafico.getData().addAll(curva, puntos);
        VBox caja = new VBox(grafico);
        caja.getStyleClass().add("grafico");
        VBox.setVgrow(grafico, Priority.ALWAYS);
        return caja;
    }
}
