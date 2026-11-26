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
import com.concretolab.service.RecomendacionSismica;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class WalterQuispesayhuaSismoController implements Refrescable {
    private static final String WALTER_QUISPESAYHUA_ESTADO_INICIAL = "Defina los datos y pulse «Calcular».";
    private static final Locale WALTER_QUISPESAYHUA_ES = Locale.forLanguageTag("es-PE");

    private final YefriLermaProyectoService walterQuispesayhuaServicio;
    private final SesionProyecto walterQuispesayhuaSesion;
    private final Notificador walterQuispesayhuaNotificador;
    private final Exportador exportador;
    private final ZonificacionService walterQuispesayhuaZonificacion;
    private final ObjectProvider<MainController> walterQuispesayhuaPrincipal;
    private final ObjectProvider<ModeloController> walterQuispesayhuaModelo;

    @FXML private VBox panelVacio;
    @FXML private HBox panelContenido;

    private boolean walterQuispesayhuaActualizandoProyectos;
    private boolean walterQuispesayhuaAplicandoBusqueda;
    private boolean walterQuispesayhuaZonaForzada;
    private boolean walterQuispesayhuaCargando = true;
    @FXML private ComboBox<Proyecto> cmbProyecto;

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
    @FXML private ComboBox<UsoEdificacion> cmbUso;
    @FXML private Label lblUsoDetalle;
    @FXML private Label lblUsoNota;
    @FXML private Label lblAvisoAislamiento;
    @FXML private ComboBox<TipoSuelo> cmbTipoSuelo;
    @FXML private Label lblSueloDetalle;
    @FXML private Label lblAvisoSuelo;
    @FXML private ComboBox<SistemaEstructural> cmbSistema;
    @FXML private Label lblSistemaDetalle;
    @FXML private Label lblAvisoSistema;
    @FXML private Label lblAvisoTabla9;
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
    @FXML private TiempoHistoriaController tiempoHistoriaController;

    private Long walterQuispesayhuaIdProyectoCargado;
    private List<PisoBase> walterQuispesayhuaPisosCargados = List.of();

    private Proyecto walterQuispesayhuaProyectoCalculado;
    private Entrada walterQuispesayhuaEntrada;
    private Resultado walterQuispesayhuaResultado;
    private boolean walterQuispesayhuaUltimoUsoA1;
    private UsoEdificacion walterQuispesayhuaUsoCalculado;
    private RecomendacionSismica.Recomendacion walterQuispesayhuaRecomendacion;

    public static final class FilaPiso {
        final int walterQuispesayhuaNumero;
        final double walterQuispesayhuaCota;
        final StringProperty walterQuispesayhuaArea;
        final StringProperty walterQuispesayhuaCm;
        final StringProperty walterQuispesayhuaCv;

        FilaPiso(int numero, double cota, double area) {
            this.walterQuispesayhuaNumero = numero;
            this.walterQuispesayhuaCota = cota;
            this.walterQuispesayhuaArea = new SimpleStringProperty(f(area, 2));
            this.walterQuispesayhuaCm = new SimpleStringProperty("1.0");
            this.walterQuispesayhuaCv = new SimpleStringProperty("2.0");
        }
    }

    @FXML
    private void initialize() {
        cmbProyecto.valueProperty().addListener((o, anterior, nuevo) -> {
            if (!walterQuispesayhuaActualizandoProyectos && nuevo != null) {
                walterQuispesayhuaSesion.setProyecto(nuevo);
                walterQuispesayhuaCargarProyectoActivo();
            }
        });

        cmbDepartamento.getItems().setAll(walterQuispesayhuaZonificacion.fernandoHuancaDepartamentos());
        cmbDepartamento.valueProperty().addListener((o, a, dep) -> {
            cmbProvincia.setValue(null);
            cmbProvincia.getItems().setAll(dep == null ? List.<String>of() : walterQuispesayhuaZonificacion.fernandoHuancaProvincias(dep));
        });
        cmbProvincia.valueProperty().addListener((o, a, prov) -> {
            cmbDistrito.setValue(null);
            String dep = cmbDepartamento.getValue();
            cmbDistrito.getItems().setAll(dep == null || prov == null ? List.<String>of() : walterQuispesayhuaZonificacion.fernandoHuancaDistritos(dep, prov));
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
            if (!walterQuispesayhuaAplicandoBusqueda) {
                walterQuispesayhuaBuscar(texto);
            }
        });
        lstBusqueda.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> {
            if (u != null && !walterQuispesayhuaAplicandoBusqueda) {
                walterQuispesayhuaAplicarUbicacion(u);
            }
        });

        cmbUso.getItems().addAll(UsoEdificacion.values());
        cmbTipoSuelo.getItems().addAll(TipoSuelo.values());
        cmbSistema.getItems().addAll(SistemaEstructural.values());

        cmbVersion.getItems().addAll(Version.values());
        cmbVersion.setValue(Version.V2026);
        cmbIa.getItems().addAll(1.0, 0.90, 0.80, 0.75, 0.60, 0.50);
        cmbIa.setValue(1.0);
        cmbIp.getItems().addAll(1.0, 0.90, 0.85, 0.75, 0.60);
        cmbIp.setValue(1.0);

        Formularios.fernandoHuancaLimpiarAlEditar(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt);

        Tablas.fernandoHuancaConfigurar(tablaPisos, "No se detectaron pisos en el proyecto.", fila -> { });
        tablaPisos.getStyleClass().add("tabla-compacta");
        walterQuispesayhuaColumnaFija(colPiso, c -> new SimpleStringProperty("" + c.walterQuispesayhuaNumero));
        walterQuispesayhuaColumnaFija(colCota, c -> new SimpleStringProperty(f(c.walterQuispesayhuaCota, 2)));
        walterQuispesayhuaColumnaEditable(colArea, c -> c.walterQuispesayhuaArea);
        walterQuispesayhuaColumnaEditable(colCm, c -> c.walterQuispesayhuaCm);
        walterQuispesayhuaColumnaEditable(colCv, c -> c.walterQuispesayhuaCv);

        for (TextField campo : List.of(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt)) {
            campo.textProperty().addListener((o, a, b) -> {
                walterQuispesayhuaInvalidar();
                walterQuispesayhuaActualizarEstado();
            });
        }
        for (ComboBox<?> combo : List.of(cmbDepartamento, cmbProvincia, cmbDistrito, cmbZona, cmbTipoSuelo, cmbSistema, cmbIa, cmbIp)) {
            combo.valueProperty().addListener((o, a, b) -> {
                Formularios.fernandoHuancaLimpiarError(combo);
                walterQuispesayhuaInvalidar();
                walterQuispesayhuaActualizarEstado();
            });
        }
        cmbUso.valueProperty().addListener((o, a, uso) -> {
            Formularios.fernandoHuancaLimpiarError(cmbUso);
            walterQuispesayhuaAplicarCargasUso();
            walterQuispesayhuaInvalidar();
            walterQuispesayhuaActualizarEstado();
        });
        cmbSistema.valueProperty().addListener((o, a, s) -> {
            if (s != null) {
                txtCt.setText(walterQuispesayhuaComa(s.ct(), 0));
            }
        });
        cmbTipoSuelo.valueProperty().addListener((o, a, s) -> {
            if (txtVs30.isDisabled()) {
                txtVs30.clear();
            }
        });
        walterQuispesayhuaCargando = false;
        onCambiarVersion();
    }

    @Override
    public void refrescar() {
        Proyecto activo = walterQuispesayhuaSesion.getProyecto();
        walterQuispesayhuaActualizandoProyectos = true;
        try {
            cmbProyecto.getItems().setAll(walterQuispesayhuaServicio.proyectos());
            cmbProyecto.setValue(cmbProyecto.getItems().stream()
                    .filter(p -> activo != null && Objects.equals(p.getId(), activo.getId()))
                    .findFirst().orElse(null));
        } finally {
            walterQuispesayhuaActualizandoProyectos = false;
        }
        walterQuispesayhuaCargarProyectoActivo();
    }

    private void walterQuispesayhuaCargarProyectoActivo() {
        Proyecto p = walterQuispesayhuaSesion.getProyecto();
        boolean hayProyecto = p != null;
        panelVacio.setVisible(!hayProyecto);
        panelVacio.setManaged(!hayProyecto);
        panelContenido.setVisible(hayProyecto);
        panelContenido.setManaged(hayProyecto);
        if (!hayProyecto) {
            walterQuispesayhuaIdProyectoCargado = null;
            walterQuispesayhuaPisosCargados = List.of();
            tablaPisos.getItems().clear();
            walterQuispesayhuaLimpiarResultados();
            lblEstado.setText(WALTER_QUISPESAYHUA_ESTADO_INICIAL);
            walterQuispesayhuaActualizarEstado();
            return;
        }

        List<PisoBase> pisos = List.of();
        String problema = null;
        try {
            pisos = FernandoHuancaAnalisisSismico.fernandoHuancaPisos(walterQuispesayhuaServicio.elementos(p.getId()));
        } catch (IllegalArgumentException ex) {
            problema = ex.getMessage();
        }
        if (problema == null && Objects.equals(walterQuispesayhuaIdProyectoCargado, p.getId()) && pisos.equals(walterQuispesayhuaPisosCargados)) {
            if (walterQuispesayhuaEntrada != null && !walterQuispesayhuaGeometria(walterQuispesayhuaEntrada.elementos()).equals(walterQuispesayhuaGeometria(walterQuispesayhuaServicio.elementos(p.getId())))) walterQuispesayhuaInvalidar();
            return;
        }

        walterQuispesayhuaIdProyectoCargado = p.getId();
        walterQuispesayhuaPisosCargados = pisos;
        tablaPisos.getItems().clear();
        for (PisoBase b : pisos) {
            tablaPisos.getItems().add(new FilaPiso(tablaPisos.getItems().size() + 1, b.cota(), b.areaPorDefecto()));
        }
        walterQuispesayhuaAplicarCargasUso();
        walterQuispesayhuaLimpiarResultados();
        lblEstado.setText(problema != null ? problema : WALTER_QUISPESAYHUA_ESTADO_INICIAL);
        walterQuispesayhuaActualizarEstado();
    }

    private void walterQuispesayhuaBuscar(String texto) {
        List<Ubicacion> lista = texto == null || texto.trim().length() < 2 || cmbVersion.getValue() != Version.V2026
                ? List.of() : walterQuispesayhuaZonificacion.fernandoHuancaBuscar(texto.trim(), 8);
        lstBusqueda.getItems().setAll(lista);
        walterQuispesayhuaMostrar(lstBusqueda, !lista.isEmpty());
    }

    private void walterQuispesayhuaAplicarUbicacion(Ubicacion u) {
        walterQuispesayhuaAplicandoBusqueda = true;
        try {
            cmbDepartamento.setValue(u.departamento());
            cmbProvincia.setValue(u.provincia());
            cmbDistrito.setValue(u.distrito());
            txtBuscarDistrito.clear();
            lstBusqueda.getItems().clear();
            walterQuispesayhuaMostrar(lstBusqueda, false);
        } finally {
            walterQuispesayhuaAplicandoBusqueda = false;
        }
    }

    @FXML
    private void onZonaManual() {
        walterQuispesayhuaMostrar(boxZonaManual, chkZonaManual.isSelected());
        walterQuispesayhuaActualizarEstado();
    }

    @FXML
    private void onCambiarVersion() {
        if (walterQuispesayhuaCargando) {
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
                walterQuispesayhuaZonaForzada = true;
                chkZonaManual.setSelected(true);
            }
            chkZonaManual.setDisable(true);
            walterQuispesayhuaMostrar(lblNotaZona, true);
            lblNotaZona.setText("La zonificación por distrito es del Anexo II de la E.030 2026; con la versión 2018 el anexo es otro. Elija la zona sísmica a mano.");
            lblNotaVersion.setText("Versión 2018: los nombres de los suelos S2 y S3 no significan lo mismo que en 2026.");
            walterQuispesayhuaMostrar(lblNotaVersion, true);
        } else {
            chkZonaManual.setDisable(false);
            if (walterQuispesayhuaZonaForzada) {
                walterQuispesayhuaZonaForzada = false;
                chkZonaManual.setSelected(false);
            }
            walterQuispesayhuaMostrar(lblNotaZona, false);
            walterQuispesayhuaMostrar(lblNotaVersion, false);
        }
        walterQuispesayhuaMostrar(boxZonaManual, chkZonaManual.isSelected());
        walterQuispesayhuaInvalidar();
        walterQuispesayhuaActualizarEstado();
    }

    private Zona walterQuispesayhuaZonaEfectiva() {
        if (chkZonaManual.isSelected()) {
            return cmbZona.getValue();
        }
        String dep = cmbDepartamento.getValue(), prov = cmbProvincia.getValue(), dist = cmbDistrito.getValue();
        if (dep == null || prov == null || dist == null) {
            return null;
        }
        var z = walterQuispesayhuaZonificacion.fernandoHuancaZona(dep, prov, dist);
        return z.isPresent() ? GuiaSismica.walterQuispesayhuaZonaDe(z.getAsInt()) : null;
    }

    private void walterQuispesayhuaAplicarCargasUso() {
        UsoEdificacion uso = cmbUso.getValue();
        if (uso == null) {
            return;
        }
        double[][] c = GuiaSismica.walterQuispesayhuaCargasPorUso(uso, tablaPisos.getItems().size());
        for (int i = 0; i < c.length; i++) {
            tablaPisos.getItems().get(i).walterQuispesayhuaCm.set(f(c[i][0], 2));
            tablaPisos.getItems().get(i).walterQuispesayhuaCv.set(f(c[i][1], 2));
        }
        tablaPisos.refresh();
    }

    private Parametros walterQuispesayhuaProvisional() {
        Zona zona = walterQuispesayhuaZonaEfectiva();
        if (zona == null || cmbUso.getValue() == null || cmbTipoSuelo.getValue() == null || cmbSistema.getValue() == null) {
            return null;
        }
        try {
            double ct = cmbSistema.getValue().ct();
            try {
                ct = walterQuispesayhuaNum(txtCt.getText());
            } catch (RuntimeException ignorado) {
            }
            double vs30 = Double.NaN;
            try {
                vs30 = walterQuispesayhuaNum(txtVs30.getText());
            } catch (RuntimeException ignorado) {
            }
            return GuiaSismica.walterQuispesayhuaParametros(cmbVersion.getValue(), zona, cmbUso.getValue(), cmbTipoSuelo.getValue(), cmbSistema.getValue(),
                    cmbIa.getValue(), cmbIp.getValue(), ct, vs30);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void walterQuispesayhuaActualizarEstado() {
        if (walterQuispesayhuaCargando) {
            return;
        }
        Version v = cmbVersion.getValue();
        Zona zona = walterQuispesayhuaZonaEfectiva();
        UsoEdificacion uso = cmbUso.getValue();
        TipoSuelo tipo = cmbTipoSuelo.getValue();
        SistemaEstructural sis = cmbSistema.getValue();

        lblZonaChip.getStyleClass().removeAll("neutro", "exito");
        if (zona == null) {
            lblZonaChip.setText(chkZonaManual.isSelected() ? "Zona sísmica: falta elegir la zona" : "Zona sísmica: falta elegir el distrito");
            lblZonaChip.getStyleClass().add("neutro");
        } else {
            lblZonaChip.setText("Zona sísmica " + GuiaSismica.walterQuispesayhuaNumeroZona(zona) + " · Z = " + walterQuispesayhuaComa(zona.z, 2) + " · "
                    + (chkZonaManual.isSelected() ? "elegida a mano" : "fuente " + walterQuispesayhuaZonificacion.fernandoHuancaFuente()));
            lblZonaChip.getStyleClass().add("exito");
        }

        if (uso == null) {
            walterQuispesayhuaMostrar(lblUsoDetalle, false);
            walterQuispesayhuaMostrar(lblUsoNota, false);
        } else {
            lblUsoDetalle.setText("Ejemplos: " + uso.ejemplos + ".\nCategoría " + uso.categoria().name() + " · U = " + walterQuispesayhuaComa(uso.categoria().u, 1)
                    + " · carga viva sugerida " + walterQuispesayhuaComa(uso.cvPisos, 1) + " kN/m² (techo " + walterQuispesayhuaComa(uso.cvTecho, 1) + ") y carga muerta adicional "
                    + walterQuispesayhuaComa(uso.cargaMuertaAdicionalSugerida, 1) + " kN/m².");
            walterQuispesayhuaMostrar(lblUsoDetalle, true);
            lblUsoNota.setText("Valor sugerido, no es literal de la E.020: confírmelo con su proyecto.");
            walterQuispesayhuaMostrar(lblUsoNota, !uso.verificado);
        }
        boolean aislamiento = uso != null && uso.requiereAislamientoZ3Z4() && (zona == Zona.Z3 || zona == Zona.Z4);
        lblAvisoAislamiento.setText("La nota de la Tabla 7 de la E.030 pide aislamiento sísmico para este uso en zonas 3 y 4. Esta aplicación no modela aislamiento: el resultado no aplica.");
        walterQuispesayhuaMostrar(lblAvisoAislamiento, aislamiento);

        if (tipo == null || v == null) {
            walterQuispesayhuaMostrar(lblSueloDetalle, false);
            walterQuispesayhuaMostrar(lblAvisoSuelo, false);
        } else {
            lblSueloDetalle.setText(tipo.descripcion + "\nPerfil E.030 (" + (v == Version.V2026 ? "2026" : "2018") + "): " + tipo.suelo(v).etiqueta + ".");
            walterQuispesayhuaMostrar(lblSueloDetalle, true);
            lblAvisoSuelo.setText(tipo.aviso);
            walterQuispesayhuaMostrar(lblAvisoSuelo, tipo.aviso != null && !tipo.aviso.isBlank());
        }
        boolean vsHabilitado = v == Version.V2026 && tipo != null && tipo.suelo(v).interpola();
        txtVs30.setDisable(!vsHabilitado);
        if (!vsHabilitado && !txtVs30.getText().isEmpty()) {
            txtVs30.clear();
        }

        if (sis == null || v == null) {
            walterQuispesayhuaMostrar(lblSistemaDetalle, false);
            walterQuispesayhuaMostrar(lblAvisoSistema, false);
        } else {
            lblSistemaDetalle.setText(sis.descripcion + "\nR0 = " + walterQuispesayhuaComa(sis.r0(v), 1) + " · CT = " + walterQuispesayhuaComa(sis.ct(), 0) + " (automáticos). " + sis.nota);
            walterQuispesayhuaMostrar(lblSistemaDetalle, true);
            lblAvisoSistema.setText("Esta aplicación solo modela pórticos de concreto armado; para este sistema valide con otro programa.");
            walterQuispesayhuaMostrar(lblAvisoSistema, !sis.modelable());
        }
        String t9 = sis == SistemaEstructural.PORTICOS && uso != null && zona != null && v != null
                ? GuiaSismica.walterQuispesayhuaAvisoTabla9(uso.categoria(), zona, v, uso.requiereAislamientoZ3Z4()) : null;
        if (t9 != null) {
            lblAvisoTabla9.setText(t9);
        }
        walterQuispesayhuaMostrar(lblAvisoTabla9, t9 != null);

        Parametros prov = walterQuispesayhuaProvisional();
        List<String> restr = prov == null ? List.of() : prov.restricciones();
        lblAvisoIrregularidad.setText(String.join("\n", restr));
        walterQuispesayhuaMostrar(lblAvisoIrregularidad, !restr.isEmpty());
        boolean rigidezBaja = false;
        try {
            rigidezBaja = walterQuispesayhuaNum(txtRigidez.getText()) < 1.0;
        } catch (RuntimeException ignorado) {
        }
        lblAvisoRigidez.setText("La norma pide secciones brutas (EI = 1,0): con menos rigidez los periodos y desplazamientos salen mayores.");
        walterQuispesayhuaMostrar(lblAvisoRigidez, rigidezBaja);
        boolean ctManual = false;
        if (sis != null) {
            try {
                ctManual = Math.abs(walterQuispesayhuaNum(txtCt.getText()) - sis.ct()) > 1e-9;
            } catch (RuntimeException ignorado) {
                ctManual = false;
            }
        }
        lblAvisoCt.setText(ctManual ? "Valor cambiado a mano: la E.030 fija CT según el sistema estructural (art. 36.1). Úselo solo si lo puede justificar."
                : "Calculado automáticamente según el sistema estructural.");

        List<String> falta = GuiaSismica.walterQuispesayhuaFaltantes(zona, uso, tipo, sis, !tablaPisos.getItems().isEmpty());
        lblFalta.setText(falta.isEmpty() ? "Todo listo: pulse «Calcular»." : "Falta: " + String.join("; ", falta) + ".");
        btnCalcular.setDisable(!falta.isEmpty());
    }

    private static void walterQuispesayhuaMostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    @FXML
    private void onCalcular() {
        Proyecto p = walterQuispesayhuaSesion.getProyecto();
        if (p == null) {
            return;
        }
        Zona zona = walterQuispesayhuaZonaEfectiva();
        UsoEdificacion uso = cmbUso.getValue();
        TipoSuelo tipo = cmbTipoSuelo.getValue();
        SistemaEstructural sis = cmbSistema.getValue();
        walterQuispesayhuaUltimoUsoA1 = uso != null && uso.requiereAislamientoZ3Z4();
        if (zona == null || uso == null || tipo == null || sis == null || !sis.modelable()) {
            if (zona == null) Formularios.fernandoHuancaMarcarError(chkZonaManual.isSelected() ? cmbZona : cmbDistrito, "Elija la ubicación o la zona.");
            if (uso == null) Formularios.fernandoHuancaMarcarError(cmbUso, "Elija el uso de la edificación.");
            if (tipo == null) Formularios.fernandoHuancaMarcarError(cmbTipoSuelo, "Elija el tipo de suelo.");
            if (sis == null) Formularios.fernandoHuancaMarcarError(cmbSistema, "Elija el sistema estructural.");
            else if (!sis.modelable()) Formularios.fernandoHuancaMarcarError(cmbSistema, "Solo se modelan pórticos de concreto armado.");
            return;
        }
        Formularios.fernandoHuancaLimpiarErrores(txtVs30, txtFc, txtPoisson, txtRigidez, txtEspesor, txtCt, cmbSistema, cmbTipoSuelo, cmbZona);
        try {
            List<Elemento> el = walterQuispesayhuaServicio.elementos(p.getId());
            List<CargaPiso> cargas = new ArrayList<>();
            for (FilaPiso fp : tablaPisos.getItems()) {
                cargas.add(new CargaPiso(walterQuispesayhuaNum(fp.walterQuispesayhuaArea.get()), walterQuispesayhuaNum(fp.walterQuispesayhuaCm.get()), walterQuispesayhuaNum(fp.walterQuispesayhuaCv.get())));
            }
            Version version = cmbVersion.getValue();
            double ct = walterQuispesayhuaLeer(txtCt);
            double vs30 = version == Version.V2026 && !txtVs30.isDisabled() && !txtVs30.getText().isBlank() ? walterQuispesayhuaLeer(txtVs30) : Double.NaN;
            Parametros norma = GuiaSismica.walterQuispesayhuaParametros(version, zona, uso, tipo, sis, cmbIa.getValue(), cmbIp.getValue(), ct, vs30);
            double fc = walterQuispesayhuaLeer(txtFc);
            double poisson = walterQuispesayhuaLeer(txtPoisson);
            double rigidez = walterQuispesayhuaLeer(txtRigidez);
            double espesor = walterQuispesayhuaLeer(txtEspesor);
            walterQuispesayhuaEntrada = new Entrada(el, fc, poisson, rigidez, espesor, cargas, norma);
            walterQuispesayhuaResultado = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(walterQuispesayhuaEntrada);
            walterQuispesayhuaRecomendacion = null;
            walterQuispesayhuaProyectoCalculado = p;
            walterQuispesayhuaUsoCalculado = uso;
            walterQuispesayhuaMostrar();
            tiempoHistoriaController.walterQuispesayhuaConfigurar(walterQuispesayhuaResultado, walterQuispesayhuaEntrada.norma());
            tabsResultados.getSelectionModel().select(0);
            btnExportar.setDisable(false);
            lblEstado.setText("Análisis calculado: " + walterQuispesayhuaResultado.pisos().size() + " pisos, " + walterQuispesayhuaResultado.modos().size() + " modos.");
        } catch (NumberFormatException ex) {
            walterQuispesayhuaError("Revise que los campos numéricos contengan solo números.");
        } catch (IllegalArgumentException ex) {
            String m = String.valueOf(ex.getMessage());
            if (m.contains("Vs30")) Formularios.fernandoHuancaMarcarError(txtVs30, m);
            else if (m.startsWith("Suelo S4")) Formularios.fernandoHuancaMarcarError(cmbTipoSuelo, m);
            else if (m.startsWith("R0, Ia, Ip y CT")) Formularios.fernandoHuancaMarcarError(txtCt, "CT, Ia e Ip deben ser positivos (Ia e Ip no mayores que 1).");
            walterQuispesayhuaError(m);
        }
    }

    @FXML
    private void onExportarPdf() {
        if (walterQuispesayhuaEntrada == null || walterQuispesayhuaResultado == null) {
            return;
        }
        Proyecto p = walterQuispesayhuaProyectoCalculado;
        Entrada e = walterQuispesayhuaEntrada;
        Resultado r = walterQuispesayhuaResultado;
        ResumenTiempoHistoria th = tiempoHistoriaController.walterQuispesayhuaUltimo().orElse(null);
        exportador.fernandoHuancaGuardar(btnExportar.getScene().getWindow(), "Guardar reporte sísmico", "analisis-sismico",
                Exportador.Formato.PDF, destino -> Files.write(destino.toPath(), ReporteSismo.fernandoHuancaGenerar(p, e, r, th)));
    }

    private void walterQuispesayhuaError(String mensaje) {
        lblEstado.setText(mensaje);
        walterQuispesayhuaNotificador.fernandoHuancaError(mensaje);
    }

    private void walterQuispesayhuaInvalidar() {
        if (walterQuispesayhuaResultado != null) {
            walterQuispesayhuaLimpiarResultados();
            lblEstado.setText("Datos modificados. Vuelva a calcular.");
        }
    }

    private static List<?> walterQuispesayhuaGeometria(List<Elemento> elementos) {
        return elementos.stream().map(e -> List.of(e.getId(), e.getTipo(), e.getX(), e.getY(), e.getZ(), e.getLargo(), e.getAncho(), e.getAltura())).toList();
    }

    private void walterQuispesayhuaLimpiarResultados() {
        walterQuispesayhuaQuitarTabsCalculados();
        if (tiempoHistoriaController != null) {
            tiempoHistoriaController.walterQuispesayhuaLimpiar();
        }
        walterQuispesayhuaEntrada = null;
        walterQuispesayhuaResultado = null;
        walterQuispesayhuaRecomendacion = null;
        walterQuispesayhuaProyectoCalculado = null;
        walterQuispesayhuaUsoCalculado = null;
        btnExportar.setDisable(true);
    }

    private double walterQuispesayhuaLeer(TextField campo) {
        try {
            return walterQuispesayhuaNum(campo.getText());
        } catch (NumberFormatException ex) {
            Formularios.fernandoHuancaMarcarError(campo, "Ingrese un número válido.");
            throw ex;
        }
    }

    private static double walterQuispesayhuaNum(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    private static String walterQuispesayhuaComa(double v, int d) {
        return String.format(WALTER_QUISPESAYHUA_ES, "%." + d + "f", v);
    }

    private void walterQuispesayhuaColumnaFija(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setEditable(false);
    }

    private void walterQuispesayhuaColumnaEditable(TableColumn<FilaPiso, String> c, Function<FilaPiso, StringProperty> valor) {
        c.setCellValueFactory(x -> valor.apply(x.getValue()));
        c.setCellFactory(TextFieldTableCell.forTableColumn());
        c.setOnEditCommit(e -> {
            valor.apply(e.getRowValue()).set(e.getNewValue());
            walterQuispesayhuaInvalidar();
        });
    }

    private static TableView<String[]> tabla(String[] cab, List<String[]> filas) {
        TableView<String[]> t = new TableView<>();
        Tablas.fernandoHuancaConfigurar(t, "Sin datos para mostrar.", fila -> { });
        t.getStyleClass().add("tabla-compacta");
        for (int i = 0; i < cab.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(cab[i]);
            c.setCellValueFactory(x -> new SimpleStringProperty(x.getValue()[k]));
            c.setMinWidth(Math.min(112, Math.max(56, cab[i].length() * 7.0 + 22)));
            t.getColumns().add(c);
        }
        t.getItems().setAll(filas);
        return t;
    }

    static String walterQuispesayhuaVeredicto(Resultado r) {
        if (!GuiaSismica.walterQuispesayhuaModeloValido(r)) return "MODELO INCOMPLETO: las vigas no forman pórticos";
        boolean cumple = r.estaticoX().cumple() && r.estaticoY().cumple() && r.espectralX().cumple() && r.espectralY().cumple();
        return cumple ? "CUMPLE (preliminar)" : "NO CUMPLE";
    }

    private static Label walterQuispesayhuaBanner(String veredicto) {
        boolean cumple = veredicto.startsWith("CUMPLE");
        Label b = new Label(veredicto);
        b.setGraphic(new FontIcon(cumple ? "fth-check-circle" : "fth-x-circle"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setWrapText(true);
        b.getStyleClass().addAll("sismo-banner", cumple ? "sismo-banner-ok" : "sismo-banner-error");
        return b;
    }

    private VBox walterQuispesayhuaListaVerificaciones(List<GuiaSismica.Verificacion> items) {
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
            texto.setMaxWidth(Double.MAX_VALUE);
            texto.setMinHeight(Region.USE_PREF_SIZE);
            HBox fila = new HBox(8, fi, texto);
            HBox.setHgrow(texto, Priority.ALWAYS);
            if (v.correccion() != GuiaSismica.Correccion.NINGUNA) {
                boolean enModelo = v.correccion() == GuiaSismica.Correccion.DERIVA || v.correccion() == GuiaSismica.Correccion.MODELO;
                FontIcon iconoBoton = new FontIcon("fth-zap");
                if (enModelo) iconoBoton.setStyle("-fx-icon-color: white;");
                Button corregir = new Button(v.correccion() == GuiaSismica.Correccion.MODELO ? "Ver en el modelo 3D" : "Cómo corregir", iconoBoton);
                corregir.getStyleClass().addAll("button-small", enModelo ? "accent" : "button-outlined");
                corregir.setTooltip(new javafx.scene.control.Tooltip(switch (v.correccion()) {
                    case DERIVA -> "Calcula cuánto agrandar las columnas y lo lleva al modelo 3D para que lo revise";
                    case MODELO -> "Abre el modelo 3D con las vigas que no llegan a las columnas ya seleccionadas";
                    default -> "Ver cómo mejorar esta verificación";
                }));
                corregir.setMinWidth(Region.USE_PREF_SIZE);
                corregir.setOnAction(e -> walterQuispesayhuaCorregir(v));
                fila.getChildren().add(corregir);
                fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            fila.getStyleClass().addAll("sismo-verif-fila", "sismo-verif-" + v.nivel().name().toLowerCase(Locale.ROOT));
            caja.getChildren().add(fila);
        }
        return caja;
    }

    private void walterQuispesayhuaCorregir(GuiaSismica.Verificacion v) {
        var ventana = tabsResultados.getScene().getWindow();
        if (v.correccion() == GuiaSismica.Correccion.MODELO && walterQuispesayhuaResultado != null) {
            var sueltas = walterQuispesayhuaResultado.conexion().idsSueltas().stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            walterQuispesayhuaPrincipal.getObject().fernandoHuancaIrA("modelo-view");
            walterQuispesayhuaModelo.getObject().yefriLermaMostrarElementos(sueltas, "Seleccionadas: " + sueltas.size()
                    + " vigas que no llegan a las columnas. " + v.consejo());
            return;
        }
        if (v.correccion() != GuiaSismica.Correccion.DERIVA || walterQuispesayhuaEntrada == null || walterQuispesayhuaResultado == null) {
            var a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, v.consejo());
            a.initOwner(ventana);
            a.setTitle("Cómo corregir");
            a.setHeaderText(v.texto());
            a.getDialogPane().setMinWidth(520);
            a.setResizable(true);
            a.showAndWait();
            return;
        }
        RecomendacionSismica.Recomendacion rec;
        var escena = tabsResultados.getScene();
        escena.setCursor(javafx.scene.Cursor.WAIT);
        try {
            if (walterQuispesayhuaRecomendacion == null) {
                walterQuispesayhuaRecomendacion = RecomendacionSismica.fernandoHuancaRecomendar(walterQuispesayhuaEntrada, walterQuispesayhuaResultado);
            }
            rec = walterQuispesayhuaRecomendacion;
        } catch (IllegalArgumentException ex) {
            walterQuispesayhuaError("No se pudo calcular la recomendación: " + ex.getMessage());
            return;
        } finally {
            escena.setCursor(null);
        }
        var dialogo = new javafx.scene.control.Dialog<javafx.scene.control.ButtonType>();
        dialogo.initOwner(ventana);
        dialogo.setTitle("Cómo corregir la deriva");
        dialogo.setHeaderText(v.texto() + "\n" + v.consejo());
        Label cuerpo = new Label(rec.texto());
        cuerpo.setWrapText(true);
        cuerpo.setMaxWidth(560);
        cuerpo.setMinHeight(Region.USE_PREF_SIZE);
        dialogo.getDialogPane().setContent(cuerpo);
        var irModelo = new javafx.scene.control.ButtonType("Ir al modelo 3D y precargar el cambio", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        if (!rec.cambios().isEmpty()) dialogo.getDialogPane().getButtonTypes().add(irModelo);
        dialogo.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialogo.setResizable(true);
        if (dialogo.showAndWait().filter(irModelo::equals).isPresent()) {
            walterQuispesayhuaPrincipal.getObject().fernandoHuancaIrA("modelo-view");
            walterQuispesayhuaModelo.getObject().yefriLermaPrecargarCorreccion(rec.cambios(),
                    "Recomendación del análisis sísmico (revise y pulse «Aplicar»; luego vuelva a calcular el análisis):\n" + rec.texto());
        }
    }

    private void walterQuispesayhuaMostrar() {
        Resultado r = walterQuispesayhuaResultado;
        Parametros n = walterQuispesayhuaEntrada.norma();
        walterQuispesayhuaQuitarTabsCalculados();
        String veredicto = walterQuispesayhuaVeredicto(r);
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
                new String[]{"Deriva máxima (límite " + NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO + ")",
                        "X: " + f(Math.max(r.estaticoX().derivaMaxima(), r.espectralX().derivaMaxima()), 5)
                                + " · Y: " + f(Math.max(r.estaticoY().derivaMaxima(), r.espectralY().derivaMaxima()), 5)},
                new String[]{"Verificación de deriva", veredicto}));
        VBox avisos = new VBox(4);
        avisos.setMinHeight(Region.USE_PREF_SIZE);
        String t9 = GuiaSismica.walterQuispesayhuaAvisoTabla9(n.categoria(), n.zona(), n.version(), walterQuispesayhuaUltimoUsoA1);
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
            l.setMinHeight(Region.USE_PREF_SIZE);
            avisos.getChildren().add(l);
        }
        TableView<String[]> tablaResumen = tabla(new String[]{"Concepto", "Valor"}, res);
        tablaResumen.setPrefHeight(400);
        tablaResumen.setMinHeight(400);
        VBox resumen = new VBox(12, walterQuispesayhuaBanner(veredicto), walterQuispesayhuaListaVerificaciones(GuiaSismica.walterQuispesayhuaVerificaciones(n, r, walterQuispesayhuaUsoCalculado)), tablaResumen, avisos);
        resumen.getStyleClass().add("sismo-resumen");
        ScrollPane scroll = new ScrollPane(resumen);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("pagina-scroll");
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Resumen", scroll));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Simulación 3D", new com.concretolab.ui.VistaModal(walterQuispesayhuaEntrada.elementos(), r)));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Pisos", tabla(new String[]{"Piso", "Cota (m)", "h (m)", "Área (m²)", "Peso (kN)", "X cm", "Y cm", "I polar"},
                r.pisos().stream().map(s -> new String[]{"" + s.numero(), f(s.cota(), 2), f(s.altura(), 2), f(s.area(), 2), f(s.peso(), 1), f(s.xcm(), 3), f(s.ycm(), 3), f(s.inercia(), 0)}).toList())));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Modos", tabla(new String[]{"Modo", "T (s)", "f (Hz)", "Masa X (%)", "Masa Y (%)"},
                r.modos().stream().map(m -> new String[]{"" + m.numero(), f(m.periodo(), 4), f(1 / m.periodo(), 3), f(m.masaEfectivaX() * 100, 1), f(m.masaEfectivaY() * 100, 1)}).toList())));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Estático", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, walterQuispesayhuaFuerzas(r.estaticoX(), r.estaticoY()))));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Espectral", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, walterQuispesayhuaFuerzas(r.espectralX(), r.espectralY()))));
        List<String[]> der = new ArrayList<>();
        for (int i = r.pisos().size() - 1; i >= 0; i--) {
            double max = Math.max(Math.max(r.estaticoX().derivasMaximas()[i], r.estaticoY().derivasMaximas()[i]),
                    Math.max(r.espectralX().derivasMaximas()[i], r.espectralY().derivasMaximas()[i]));
            der.add(new String[]{"" + (i + 1), f(r.estaticoX().derivasMaximas()[i], 5), f(r.estaticoY().derivasMaximas()[i], 5),
                    f(r.espectralX().derivasMaximas()[i], 5), f(r.espectralY().derivasMaximas()[i], 5),
                    f(r.espectralX().desplazamientosCm()[i] * 1000, 1), f(r.espectralY().desplazamientosCm()[i] * 1000, 1),
                    !GuiaSismica.walterQuispesayhuaModeloValido(r) ? "No válida"
                            : max <= NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO ? "Cumple" : "NO CUMPLE"});
        }
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Derivas", tabla(new String[]{"Piso", "Est. X", "Est. Y", "Esp. X", "Esp. Y", "Δ esp. X (mm)", "Δ esp. Y (mm)", "Estado"}, der)));
        walterQuispesayhuaAgregarTab(walterQuispesayhuaTab("Espectro", walterQuispesayhuaEspectro(r, n)));
    }

    private void walterQuispesayhuaQuitarTabsCalculados() {
        tabsResultados.getTabs().removeIf(t -> t != tabSimulacionTemporal);
    }

    private void walterQuispesayhuaAgregarTab(Tab t) {
        int pos = tabsResultados.getTabs().indexOf(tabSimulacionTemporal);
        tabsResultados.getTabs().add(pos < 0 ? tabsResultados.getTabs().size() : pos, t);
    }

    private static Tab walterQuispesayhuaTab(String titulo, Node contenido) {
        return new Tab(titulo, contenido);
    }

    private static List<String[]> walterQuispesayhuaFuerzas(Direccion x, Direccion y) {
        List<String[]> l = new ArrayList<>();
        for (int i = x.fuerzas().length - 1; i >= 0; i--) {
            l.add(new String[]{"" + (i + 1), f(x.fuerzas()[i], 2), f(x.cortantes()[i], 2), f(y.fuerzas()[i], 2), f(y.cortantes()[i], 2)});
        }
        return l;
    }

    private static Node walterQuispesayhuaEspectro(Resultado r, Parametros n) {
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
