package com.concretolab.controller;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.FernandoHuancaAnalisisSismico;
import com.concretolab.service.FernandoHuancaAnalisisSismico.CargaPiso;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Direccion;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Entrada;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Modo;
import com.concretolab.service.FernandoHuancaAnalisisSismico.PisoBase;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Resultado;
import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Version;
import com.concretolab.service.NormaE030.Zona;
import com.concretolab.service.SistemaEstructural;
import com.concretolab.service.TipoSuelo;
import com.concretolab.service.UsoEdificacion;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.ReporteSismo;
import com.concretolab.service.ResumenTiempoHistoria;
import com.concretolab.service.ZonificacionService;
import com.concretolab.service.ZonificacionService.Ubicacion;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Analisis sismico E.030 guiado en cuatro pasos (ubicacion, uso, suelo y sistema estructural). Los datos de entrada no se
 * guardan en la base: se ingresan en cada sesion. El proyecto analizado es el de {@link SesionProyecto}.
 */
@Component
@RequiredArgsConstructor
public class WalterQuispeSayhuanSismoController implements Refrescable {

    private static final String ESTADO_INICIAL = "Defina los datos y pulse «Calcular».";
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final YefriLermaProyectoService servicio;
    private final SesionProyecto sesion;
    private final Notificador notificador;
    private final Exportador exportador;
    private final ZonificacionService zonificacion;

    @FXML private VBox panelVacio;
    @FXML private HBox panelContenido;

    private boolean actualizandoProyectos;
    private boolean aplicandoBusqueda;
    private boolean zonaForzada;
    private boolean cargando = true;
    @FXML private ComboBox<Proyecto> cmbProyecto;

    // Paso 1
    @FXML private TextField txtBuscarDistrito;
    @FXML private ListView<Ubicacion> lstBusqueda;
    @FXML private ComboBox<String> cmbDepartamento;
    @FXML private ComboBox<String> cmbProvincia;
    @FXML private ComboBox<String> cmbDistrito;
    @FXML private Label lblZonaChip;
    @FXML private CheckBox chkZonaManual;
    @FXML private VBox boxZonaManual;
    @FXML private ComboBox<Zona> cmbZona;
    @FXML private Label lblNotaZona;
    // Paso 2
    @FXML private ComboBox<UsoEdificacion> cmbUso;
    @FXML private Label lblUsoDetalle;
    @FXML private Label lblUsoNota;
    @FXML private Label lblAvisoAislamiento;
    // Paso 3
    @FXML private ComboBox<TipoSuelo> cmbTipoSuelo;
    @FXML private Label lblSueloDetalle;
    @FXML private Label lblAvisoSuelo;
    // Paso 4
    @FXML private ComboBox<SistemaEstructural> cmbSistema;
    @FXML private Label lblSistemaDetalle;
    @FXML private Label lblAvisoSistema;
    @FXML private Label lblAvisoTabla9;
    // Avanzadas
    @FXML private TitledPane panelAvanzado;
    @FXML private ComboBox<Version> cmbVersion;
    @FXML private Label lblNotaVersion;
    @FXML private ComboBox<Double> cmbIa;
    @FXML private ComboBox<Double> cmbIp;
    @FXML private Label lblAvisoIrregularidad;
    @FXML private TextField txtVs30;
    @FXML private TextField txtFc;
    @FXML private TextField txtPoisson;
    @FXML private TextField txtRigidez;
    @FXML private TextField txtEspesor;
    @FXML private Label lblAvisoRigidez;
    @FXML private TextField txtCt;
    @FXML private Label lblAvisoCt;

    @FXML private TableView<FilaPiso> tablaPisos;
    @FXML private TableColumn<FilaPiso, String> colPiso;
    @FXML private TableColumn<FilaPiso, String> colCota;
    @FXML private TableColumn<FilaPiso, String> colArea;
    @FXML private TableColumn<FilaPiso, String> colCm;
    @FXML private TableColumn<FilaPiso, String> colCv;

    @FXML private Label lblFalta;
    @FXML private Button btnCalcular;
    @FXML private Button btnExportar;
    @FXML private Label lblEstado;
    @FXML private TabPane tabsResultados;
    @FXML private Tab tabSimulacionTemporal;
    /** Controlador de la pestana incluida con fx:include (fx:id = tiempoHistoria). */
    @FXML private TiempoHistoriaController tiempoHistoriaController;

    /** Proyecto y pisos con los que se armo la tabla; permiten conservar lo editado si nada cambio. */
    private Long idProyectoCargado;
    private List<PisoBase> pisosCargados = List.of();

    /** Datos del ultimo calculo (los usa la exportacion a PDF). */
    private Proyecto proyectoCalculado;
    private Entrada entrada;
    private Resultado resultado;
    /** El uso elegido al calcular era A1 (hospital): la Tabla 9 no permite pórticos en ninguna zona. */
    private boolean ultimoUsoA1;
    private UsoEdificacion usoCalculado;

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

        // Paso 1: ubicacion en cascada y busqueda
        cmbDepartamento.getItems().setAll(zonificacion.departamentos());
        cmbDepartamento.valueProperty().addListener((o, a, dep) -> {
            cmbProvincia.setValue(null);
            cmbProvincia.getItems().setAll(dep == null ? List.<String>of() : zonificacion.provincias(dep));
        });
        cmbProvincia.valueProperty().addListener((o, a, prov) -> {
            cmbDistrito.setValue(null);
            String dep = cmbDepartamento.getValue();
            cmbDistrito.getItems().setAll(dep == null || prov == null ? List.<String>of() : zonificacion.distritos(dep, prov));
        });
        cmbZona.getItems().addAll(Zona.values());
        lstBusqueda.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Ubicacion u, boolean vacio) {
                super.updateItem(u, vacio);
                setText(vacio || u == null ? null : u.distrito() + " · " + u.provincia() + " · " + u.departamento() + "  (zona " + u.zona() + ")");
            }
        });
        txtBuscarDistrito.textProperty().addListener((o, a, texto) -> {
            if (!aplicandoBusqueda) {
                buscar(texto);
            }
        });
        lstBusqueda.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> {
            if (u != null && !aplicandoBusqueda) {
                aplicarUbicacion(u);
            }
        });

        // Pasos 2 a 4
        cmbUso.getItems().addAll(UsoEdificacion.values());
        cmbTipoSuelo.getItems().addAll(TipoSuelo.values());
        cmbSistema.getItems().addAll(SistemaEstructural.values());

        // Avanzadas
        cmbVersion.getItems().addAll(Version.values());
        cmbVersion.setValue(Version.V2026);
        cmbIa.getItems().addAll(1.0, 0.90, 0.80, 0.75, 0.60, 0.50);
        cmbIa.setValue(1.0);
        cmbIp.getItems().addAll(1.0, 0.90, 0.85, 0.75, 0.60);
        cmbIp.setValue(1.0);

        Formularios.limpiarAlEditar(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt);

        Tablas.configurar(tablaPisos, "No se detectaron pisos en el proyecto.", fila -> { });
        tablaPisos.getStyleClass().add("tabla-compacta");
        columnaFija(colPiso, c -> new SimpleStringProperty("" + c.numero));
        columnaFija(colCota, c -> new SimpleStringProperty(f(c.cota, 2)));
        columnaEditable(colArea, c -> c.area);
        columnaEditable(colCm, c -> c.cm);
        columnaEditable(colCv, c -> c.cv);

        for (TextField campo : List.of(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt)) {
            campo.textProperty().addListener((o, a, b) -> {
                invalidar();
                actualizarEstado();
            });
        }
        for (ComboBox<?> combo : List.of(cmbDepartamento, cmbProvincia, cmbDistrito, cmbZona, cmbTipoSuelo, cmbSistema, cmbIa, cmbIp)) {
            combo.valueProperty().addListener((o, a, b) -> {
                Formularios.limpiarError(combo);
                invalidar();
                actualizarEstado();
            });
        }
        cmbUso.valueProperty().addListener((o, a, uso) -> {
            Formularios.limpiarError(cmbUso);
            aplicarCargasUso();
            invalidar();
            actualizarEstado();
        });
        cmbSistema.valueProperty().addListener((o, a, s) -> {
            if (s != null) {
                txtCt.setText(coma(s.ct(), 0));
            }
        });
        cmbTipoSuelo.valueProperty().addListener((o, a, s) -> {
            if (txtVs30.isDisabled()) {
                txtVs30.clear();
            }
        });
        cargando = false;
        onCambiarVersion();
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
            actualizarEstado();
            return;
        }

        List<PisoBase> pisos = List.of();
        String problema = null;
        try {
            pisos = FernandoHuancaAnalisisSismico.pisos(servicio.elementos(p.getId()));
        } catch (IllegalArgumentException ex) {
            problema = ex.getMessage();
        }
        // Mismo proyecto y mismos pisos: se conservan los valores editados y los resultados.
        if (problema == null && Objects.equals(idProyectoCargado, p.getId()) && pisos.equals(pisosCargados)) {
            if (entrada != null && !geometria(entrada.elementos()).equals(geometria(servicio.elementos(p.getId())))) invalidar();
            return;
        }

        idProyectoCargado = p.getId();
        pisosCargados = pisos;
        tablaPisos.getItems().clear();
        for (PisoBase b : pisos) {
            tablaPisos.getItems().add(new FilaPiso(tablaPisos.getItems().size() + 1, b.cota(), b.areaPorDefecto()));
        }
        aplicarCargasUso();
        limpiarResultados();
        lblEstado.setText(problema != null ? problema : ESTADO_INICIAL);
        actualizarEstado();
    }

    // ------------------------------------------------------------------ asistente

    private void buscar(String texto) {
        List<Ubicacion> lista = texto == null || texto.trim().length() < 2 || cmbVersion.getValue() != Version.V2026
                ? List.of() : zonificacion.buscar(texto.trim(), 8);
        lstBusqueda.getItems().setAll(lista);
        mostrar(lstBusqueda, !lista.isEmpty());
    }

    private void aplicarUbicacion(Ubicacion u) {
        aplicandoBusqueda = true;
        try {
            cmbDepartamento.setValue(u.departamento());
            cmbProvincia.setValue(u.provincia());
            cmbDistrito.setValue(u.distrito());
            txtBuscarDistrito.clear();
            lstBusqueda.getItems().clear();
            mostrar(lstBusqueda, false);
        } finally {
            aplicandoBusqueda = false;
        }
    }

    @FXML
    private void onZonaManual() {
        mostrar(boxZonaManual, chkZonaManual.isSelected());
        actualizarEstado();
    }

    /** Cambia lo que depende de la version: busqueda por distrito, zona manual, Vs30, R0 y CT. */
    @FXML
    private void onCambiarVersion() {
        if (cargando) {
            return;
        }
        Version v = cmbVersion.getValue();
        if (v == null) {
            return;
        }
        boolean v2026 = v == Version.V2026;
        txtBuscarDistrito.setDisable(!v2026);
        cmbDepartamento.setDisable(!v2026);
        cmbProvincia.setDisable(!v2026);
        cmbDistrito.setDisable(!v2026);
        if (!v2026) {
            txtBuscarDistrito.clear();
            if (!chkZonaManual.isSelected()) {
                zonaForzada = true;
                chkZonaManual.setSelected(true);
            }
            chkZonaManual.setDisable(true);
            mostrar(lblNotaZona, true);
            lblNotaZona.setText("La zonificación por distrito es del Anexo II de la E.030 2026; con la versión 2018 el anexo es otro. Elija la zona sísmica a mano.");
            lblNotaVersion.setText("Versión 2018: los nombres de los suelos S2 y S3 no significan lo mismo que en 2026.");
            mostrar(lblNotaVersion, true);
        } else {
            chkZonaManual.setDisable(false);
            if (zonaForzada) {
                zonaForzada = false;
                chkZonaManual.setSelected(false);
            }
            mostrar(lblNotaZona, false);
            mostrar(lblNotaVersion, false);
        }
        mostrar(boxZonaManual, chkZonaManual.isSelected());
        invalidar();
        actualizarEstado();
    }

    private Zona zonaEfectiva() {
        if (chkZonaManual.isSelected()) {
            return cmbZona.getValue();
        }
        String dep = cmbDepartamento.getValue(), prov = cmbProvincia.getValue(), dist = cmbDistrito.getValue();
        if (dep == null || prov == null || dist == null) {
            return null;
        }
        var z = zonificacion.zona(dep, prov, dist);
        return z.isPresent() ? GuiaSismica.zonaDe(z.getAsInt()) : null;
    }

    /** Precarga CM y CV de todos los pisos segun el uso elegido (el usuario puede seguir editando). */
    private void aplicarCargasUso() {
        UsoEdificacion uso = cmbUso.getValue();
        if (uso == null) {
            return;
        }
        double[][] c = GuiaSismica.cargasPorUso(uso, tablaPisos.getItems().size());
        for (int i = 0; i < c.length; i++) {
            tablaPisos.getItems().get(i).cm.set(f(c[i][0], 2));
            tablaPisos.getItems().get(i).cv.set(f(c[i][1], 2));
        }
        tablaPisos.refresh();
    }

    /** Parametros provisionales para mostrar avisos antes de calcular; {@code null} si algo falta o no es valido. */
    private Parametros provisional() {
        Zona zona = zonaEfectiva();
        if (zona == null || cmbUso.getValue() == null || cmbTipoSuelo.getValue() == null || cmbSistema.getValue() == null) {
            return null;
        }
        try {
            double ct = cmbSistema.getValue().ct();
            try {
                ct = num(txtCt.getText());
            } catch (RuntimeException ignorado) {
                // se usa el CT del sistema
            }
            double vs30 = Double.NaN;
            try {
                vs30 = num(txtVs30.getText());
            } catch (RuntimeException ignorado) {
                // sin Vs30
            }
            return GuiaSismica.parametros(cmbVersion.getValue(), zona, cmbUso.getValue(), cmbTipoSuelo.getValue(), cmbSistema.getValue(),
                    cmbIa.getValue(), cmbIp.getValue(), ct, vs30);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Refresca todos los textos de ayuda y avisos del asistente y habilita o no el boton Calcular. */
    private void actualizarEstado() {
        if (cargando) {
            return;
        }
        Version v = cmbVersion.getValue();
        Zona zona = zonaEfectiva();
        UsoEdificacion uso = cmbUso.getValue();
        TipoSuelo tipo = cmbTipoSuelo.getValue();
        SistemaEstructural sis = cmbSistema.getValue();

        // Paso 1: chip de zona
        lblZonaChip.getStyleClass().removeAll("neutro", "exito");
        if (zona == null) {
            lblZonaChip.setText(chkZonaManual.isSelected() ? "Zona sísmica: falta elegir la zona" : "Zona sísmica: falta elegir el distrito");
            lblZonaChip.getStyleClass().add("neutro");
        } else {
            lblZonaChip.setText("Zona sísmica " + GuiaSismica.numeroZona(zona) + " · Z = " + coma(zona.z, 2) + " · "
                    + (chkZonaManual.isSelected() ? "elegida a mano" : "fuente " + zonificacion.fuente()));
            lblZonaChip.getStyleClass().add("exito");
        }

        // Paso 2: uso
        if (uso == null) {
            mostrar(lblUsoDetalle, false);
            mostrar(lblUsoNota, false);
        } else {
            lblUsoDetalle.setText("Ejemplos: " + uso.ejemplos + ".\nCategoría " + uso.categoria().name() + " · U = " + coma(uso.categoria().u, 1)
                    + " · carga viva sugerida " + coma(uso.cvPisos, 1) + " kN/m² (techo " + coma(uso.cvTecho, 1) + ") y carga muerta adicional "
                    + coma(uso.cargaMuertaAdicionalSugerida, 1) + " kN/m².");
            mostrar(lblUsoDetalle, true);
            lblUsoNota.setText("Valor sugerido, no es literal de la E.020: confírmelo con su proyecto.");
            mostrar(lblUsoNota, !uso.verificado);
        }
        boolean aislamiento = uso != null && uso.requiereAislamientoZ3Z4() && (zona == Zona.Z3 || zona == Zona.Z4);
        lblAvisoAislamiento.setText("La nota de la Tabla 7 de la E.030 pide aislamiento sísmico para este uso en zonas 3 y 4. Esta aplicación no modela aislamiento: el resultado no aplica.");
        mostrar(lblAvisoAislamiento, aislamiento);

        // Paso 3: suelo
        if (tipo == null || v == null) {
            mostrar(lblSueloDetalle, false);
            mostrar(lblAvisoSuelo, false);
        } else {
            lblSueloDetalle.setText(tipo.descripcion + "\nPerfil E.030 (" + (v == Version.V2026 ? "2026" : "2018") + "): " + tipo.suelo(v).etiqueta + ".");
            mostrar(lblSueloDetalle, true);
            lblAvisoSuelo.setText(tipo.aviso);
            mostrar(lblAvisoSuelo, tipo.aviso != null && !tipo.aviso.isBlank());
        }
        boolean vsHabilitado = v == Version.V2026 && tipo != null && tipo.suelo(v).interpola();
        txtVs30.setDisable(!vsHabilitado);
        if (!vsHabilitado && !txtVs30.getText().isEmpty()) {
            txtVs30.clear();
        }

        // Paso 4: sistema
        if (sis == null || v == null) {
            mostrar(lblSistemaDetalle, false);
            mostrar(lblAvisoSistema, false);
        } else {
            lblSistemaDetalle.setText(sis.descripcion + "\nR0 = " + coma(sis.r0(v), 1) + " · CT = " + coma(sis.ct(), 0) + " (automáticos). " + sis.nota);
            mostrar(lblSistemaDetalle, true);
            lblAvisoSistema.setText("Esta aplicación solo modela pórticos de concreto armado; para este sistema valide con otro programa.");
            mostrar(lblAvisoSistema, !sis.modelable());
        }
        String t9 = sis == SistemaEstructural.PORTICOS && uso != null && zona != null && v != null
                ? GuiaSismica.avisoTabla9(uso.categoria(), zona, v, uso.requiereAislamientoZ3Z4()) : null;
        if (t9 != null) {
            lblAvisoTabla9.setText(t9);
        }
        mostrar(lblAvisoTabla9, t9 != null);

        // Avanzadas
        Parametros prov = provisional();
        List<String> restr = prov == null ? List.of() : prov.restricciones();
        lblAvisoIrregularidad.setText(String.join("\n", restr));
        mostrar(lblAvisoIrregularidad, !restr.isEmpty());
        boolean rigidezBaja = false;
        try {
            rigidezBaja = num(txtRigidez.getText()) < 1.0;
        } catch (RuntimeException ignorado) {
            // texto no numerico: se avisa al calcular
        }
        lblAvisoRigidez.setText("La norma pide secciones brutas (EI = 1,0): con menos rigidez los periodos y desplazamientos salen mayores.");
        mostrar(lblAvisoRigidez, rigidezBaja);
        boolean ctManual = false;
        if (sis != null) {
            try {
                ctManual = Math.abs(num(txtCt.getText()) - sis.ct()) > 1e-9;
            } catch (RuntimeException ignorado) {
                ctManual = false;
            }
        }
        lblAvisoCt.setText(ctManual ? "Valor cambiado a mano: la E.030 fija CT según el sistema estructural (art. 36.1). Úselo solo si lo puede justificar."
                : "Calculado automáticamente según el sistema estructural.");

        // Boton Calcular
        List<String> falta = GuiaSismica.faltantes(zona, uso, tipo, sis, !tablaPisos.getItems().isEmpty());
        lblFalta.setText(falta.isEmpty() ? "Todo listo: pulse «Calcular»." : "Falta: " + String.join("; ", falta) + ".");
        btnCalcular.setDisable(!falta.isEmpty());
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void onCalcular() {
        Proyecto p = sesion.getProyecto();
        if (p == null) {
            return;
        }
        Zona zona = zonaEfectiva();
        UsoEdificacion uso = cmbUso.getValue();
        TipoSuelo tipo = cmbTipoSuelo.getValue();
        SistemaEstructural sis = cmbSistema.getValue();
        ultimoUsoA1 = uso != null && uso.requiereAislamientoZ3Z4();
        if (zona == null || uso == null || tipo == null || sis == null || !sis.modelable()) {
            if (zona == null) Formularios.marcarError(chkZonaManual.isSelected() ? cmbZona : cmbDistrito, "Elija la ubicación o la zona.");
            if (uso == null) Formularios.marcarError(cmbUso, "Elija el uso de la edificación.");
            if (tipo == null) Formularios.marcarError(cmbTipoSuelo, "Elija el tipo de suelo.");
            if (sis == null) Formularios.marcarError(cmbSistema, "Elija el sistema estructural.");
            else if (!sis.modelable()) Formularios.marcarError(cmbSistema, "Solo se modelan pórticos de concreto armado.");
            return;
        }
        Formularios.limpiarErrores(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt, cmbSistema, cmbTipoSuelo, cmbZona);
        try {
            List<Elemento> el = servicio.elementos(p.getId());
            List<CargaPiso> cargas = new ArrayList<>();
            for (FilaPiso fp : tablaPisos.getItems()) {
                cargas.add(new CargaPiso(num(fp.area.get()), num(fp.cm.get()), num(fp.cv.get())));
            }
            Version version = cmbVersion.getValue();
            double ct = leer(txtCt);
            double vs30 = version == Version.V2026 && !txtVs30.isDisabled() && !txtVs30.getText().isBlank() ? leer(txtVs30) : Double.NaN;
            Parametros norma = GuiaSismica.parametros(version, zona, uso, tipo, sis, cmbIa.getValue(), cmbIp.getValue(), ct, vs30);
            double fc = leer(txtFc);
            double poisson = leer(txtPoisson);
            double rigidez = leer(txtRigidez);
            double espesor = leer(txtEspesor);
            entrada = new Entrada(el, fc, poisson, rigidez, espesor, cargas, norma);
            resultado = FernandoHuancaAnalisisSismico.analizar(entrada);
            proyectoCalculado = p;
            usoCalculado = uso;
            mostrar();
            tiempoHistoriaController.configurar(resultado, entrada.norma());
            tabsResultados.getSelectionModel().select(0);
            btnExportar.setDisable(false);
            lblEstado.setText("Análisis calculado: " + resultado.pisos().size() + " pisos, " + resultado.modos().size() + " modos.");
        } catch (NumberFormatException ex) {
            error("Revise que los campos numéricos contengan solo números.");
        } catch (IllegalArgumentException ex) {
            String m = String.valueOf(ex.getMessage());
            if (m.contains("Vs30")) Formularios.marcarError(txtVs30, m);
            else if (m.startsWith("Suelo S4")) Formularios.marcarError(cmbTipoSuelo, m);
            else if (m.startsWith("R0, Ia, Ip y CT")) Formularios.marcarError(txtCt, "CT, Ia e Ip deben ser positivos (Ia e Ip no mayores que 1).");
            error(m);
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
        ResumenTiempoHistoria th = tiempoHistoriaController.ultimo().orElse(null);
        exportador.guardar(btnExportar.getScene().getWindow(), "Guardar reporte sísmico", "analisis-sismico",
                Exportador.Formato.PDF, destino -> Files.write(destino.toPath(), ReporteSismo.generar(p, e, r, th)));
    }

    // ------------------------------------------------------------------ utilidades

    private void error(String mensaje) {
        lblEstado.setText(mensaje);
        notificador.error(mensaje);
    }

    private void invalidar() {
        if (resultado != null) {
            limpiarResultados();
            lblEstado.setText("Datos modificados. Vuelva a calcular.");
        }
    }

    private static List<?> geometria(List<Elemento> elementos) {
        return elementos.stream().map(e -> List.of(e.getId(), e.getTipo(), e.getX(), e.getY(), e.getZ(), e.getLargo(), e.getAncho(), e.getAltura())).toList();
    }

    private void limpiarResultados() {
        quitarTabsCalculados();
        if (tiempoHistoriaController != null) {
            tiempoHistoriaController.limpiar();
        }
        entrada = null;
        resultado = null;
        proyectoCalculado = null;
        usoCalculado = null;
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

    /** Numero con coma decimal para los textos de ayuda. */
    private static String coma(double v, int d) {
        return String.format(ES, "%." + d + "f", v);
    }

    private void columnaFija(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setEditable(false);
    }

    private void columnaEditable(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setCellFactory(TextFieldTableCell.forTableColumn());
        c.setOnEditCommit(e -> {
            valor.apply(e.getRowValue()).set(e.getNewValue());
            invalidar();
        });
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

    /** Banner semaforo con el veredicto de derivas. */
    private static Label banner(boolean cumple) {
        Label b = new Label(cumple ? "CUMPLE (preliminar)" : "NO CUMPLE");
        b.setGraphic(new FontIcon(cumple ? "fth-check-circle" : "fth-x-circle"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setWrapText(true);
        b.getStyleClass().addAll("sismo-banner", cumple ? "sismo-banner-ok" : "sismo-banner-error");
        return b;
    }

    /** Lista de verificaciones con icono verde, rojo, ambar o informativo. */
    private static VBox listaVerificaciones(List<GuiaSismica.Verificacion> items) {
        VBox caja = new VBox(6);
        caja.getStyleClass().add("sismo-verificaciones");
        caja.setMinHeight(Region.USE_PREF_SIZE);
        Label titulo = new Label("Verificaciones");
        titulo.getStyleClass().add("sismo-verif-titulo");
        caja.getChildren().add(titulo);
        for (GuiaSismica.Verificacion v : items) {
            String icono = switch (v.nivel()) {
                case OK -> "fth-check-circle";
                case ERROR -> "fth-x-circle";
                case ALERTA -> "fth-alert-triangle";
                case INFO -> "fth-info";
            };
            FontIcon fi = new FontIcon(icono);
            Label texto = new Label(v.texto());
            texto.setWrapText(true);
            texto.setMinHeight(Region.USE_PREF_SIZE);
            HBox fila = new HBox(8, fi, texto);
            HBox.setHgrow(texto, Priority.ALWAYS);
            fila.getStyleClass().addAll("sismo-verif-fila", "sismo-verif-" + v.nivel().name().toLowerCase(Locale.ROOT));
            caja.getChildren().add(fila);
        }
        return caja;
    }

    private void mostrar() {
        Resultado r = resultado;
        Parametros n = entrada.norma();
        quitarTabsCalculados();
        boolean cumple = r.estaticoX().cumple() && r.estaticoY().cumple() && r.espectralX().cumple() && r.espectralY().cumple();
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
                new String[]{"Verificación de deriva", cumple ? "CUMPLE (preliminar)" : "NO CUMPLE"}));
        VBox avisos = new VBox(4);
        avisos.setMinHeight(Region.USE_PREF_SIZE);
        String t9 = GuiaSismica.avisoTabla9(n.categoria(), n.zona(), n.version(), ultimoUsoA1);
        if (t9 != null) {
            Label l = new Label("• " + t9);
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE);
            l.getStyleClass().add("paso-alerta-roja");
            avisos.getChildren().add(l);
        }
        for (String a : r.advertencias()) {
            Label l = new Label("• " + a);
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE); // sin esto el VBox encoge el texto largo a una línea con «…»
            avisos.getChildren().add(l);
        }
        TableView<String[]> tablaResumen = tabla(new String[]{"Concepto", "Valor"}, res);
        tablaResumen.setPrefHeight(400);
        tablaResumen.setMinHeight(400);
        VBox resumen = new VBox(12, banner(cumple), listaVerificaciones(GuiaSismica.verificaciones(n, r, usoCalculado)), tablaResumen, avisos);
        resumen.getStyleClass().add("sismo-resumen");
        ScrollPane scroll = new ScrollPane(resumen);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("pagina-scroll");
        agregarTab(tab("Resumen", scroll));
        agregarTab(tab("Simulación 3D", new com.concretolab.ui.VistaModal(entrada.elementos(), r)));
        // Pisos
        agregarTab(tab("Pisos", tabla(new String[]{"Piso", "Cota (m)", "h (m)", "Área (m²)", "Peso (kN)", "X cm", "Y cm", "I polar"},
                r.pisos().stream().map(s -> new String[]{"" + s.numero(), f(s.cota(), 2), f(s.altura(), 2), f(s.area(), 2), f(s.peso(), 1), f(s.xcm(), 3), f(s.ycm(), 3), f(s.inercia(), 0)}).toList())));
        // Modos
        agregarTab(tab("Modos", tabla(new String[]{"Modo", "T (s)", "f (Hz)", "Masa X (%)", "Masa Y (%)"},
                r.modos().stream().map(m -> new String[]{"" + m.numero(), f(m.periodo(), 4), f(1 / m.periodo(), 3), f(m.masaEfectivaX() * 100, 1), f(m.masaEfectivaY() * 100, 1)}).toList())));
        agregarTab(tab("Estático", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.estaticoX(), r.estaticoY()))));
        agregarTab(tab("Espectral", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.espectralX(), r.espectralY()))));
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
        agregarTab(tab("Derivas", tabla(new String[]{"Piso", "Est. X", "Est. Y", "Esp. X", "Esp. Y", "Δ esp. X (mm)", "Δ esp. Y (mm)", "Estado"}, der)));
        agregarTab(tab("Espectro", espectro(r, n)));
    }

    /** Quita las pestanas calculadas y conserva la pestana fija «Simulación temporal». */
    private void quitarTabsCalculados() {
        tabsResultados.getTabs().removeIf(t -> t != tabSimulacionTemporal);
    }

    /** Agrega una pestana calculada antes de la pestana fija «Simulación temporal». */
    private void agregarTab(Tab t) {
        int pos = tabsResultados.getTabs().indexOf(tabSimulacionTemporal);
        tabsResultados.getTabs().add(pos < 0 ? tabsResultados.getTabs().size() : pos, t);
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
