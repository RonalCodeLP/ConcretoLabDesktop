package com.concretolab.controller;

import com.concretolab.service.EscaladoRegistros;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Resultado;
import com.concretolab.service.GeneradorAcelerograma;
import com.concretolab.service.ImportadorRegistro;
import com.concretolab.service.ImportadorRegistro.UnidadAceleracion;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.RegistroSismico;
import com.concretolab.service.ResultadoTiempoHistoria;
import com.concretolab.service.ResumenTiempoHistoria;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.ResumenSimulacion;
import com.concretolab.ui.Tablas;
import com.concretolab.ui.VistaTiempoHistoria;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TiempoHistoriaController {
    private static final String WALTER_QUISPESAYHUA_FORMATO_UNO = "Un archivo (3 columnas: t, ax, ay)";
    private static final String WALTER_QUISPESAYHUA_FORMATO_PAR = "Dos archivos (componente X y Y)";
    private static final int WALTER_QUISPESAYHUA_CONJUNTOS_SINTETICOS = 7;
    private static final Locale WALTER_QUISPESAYHUA_ES = Locale.forLanguageTag("es-PE");

    private final Notificador walterQuispesayhuaNotificador;

    @FXML private VBox raiz;
    @FXML private VBox panelVacio;
    @FXML private ScrollPane panelContenido;
    @FXML private Label lblAyuda;
    @FXML private Label lblPeriodo;
    @FXML private TextField txtSemilla;
    @FXML private Button btnGenerar;
    @FXML private ComboBox<String> cmbFormato;
    @FXML private ComboBox<UnidadAceleracion> cmbUnidad;
    @FXML private TextField txtDt;
    @FXML private Button btnImportar;
    @FXML private ListView<String> lstImportados;
    @FXML private Button btnQuitar;
    @FXML private Button btnSimularImportados;
    @FXML private ProgressBar barraProgreso;
    @FXML private Label lblProgreso;
    @FXML private VBox panelResultados;
    @FXML private Label lblBanner;
    @FXML private VBox cajaAvisos;
    @FXML private TabPane tabsTH;
    @FXML private ToggleGroup grupoDireccion;
    @FXML private ToggleButton tbX;
    @FXML private ToggleButton tbY;
    @FXML private ComboBox<String> cmbRegistro;
    @FXML private Button btnPlay;
    @FXML private FontIcon iconoPlay;
    @FXML private Button btnReiniciar;
    @FXML private ComboBox<Double> cmbVelocidad;
    @FXML private Slider sldEscala;
    @FXML private Slider sldTiempo;
    @FXML private Label lblT;
    @FXML private Label lblAg;
    @FXML private Label lblUg;
    @FXML private Label lblTecho;
    @FXML private Label lblDeriva;
    @FXML private VistaTiempoHistoria vista;
    @FXML private Label lblExageracion;
    @FXML private LineChart<Number, Number> grafAceleracion;
    @FXML private LineChart<Number, Number> grafDesplazamiento;
    @FXML private LineChart<Number, Number> grafDeriva;
    @FXML private Label lblTituloDeriva;
    @FXML private TableView<String[]> tablaResultados;
    @FXML private LineChart<Number, Number> grafEspectros;
    @FXML private VBox cajaEscalado;
    @FXML private Label lblAdvertencia;

    private Resultado walterQuispesayhuaResultado;
    private Parametros walterQuispesayhuaNorma;
    private double walterQuispesayhuaPeriodo;
    private final List<RegistroSismico> walterQuispesayhuaImportados = new ArrayList<>();
    private ResumenTiempoHistoria walterQuispesayhuaResumen;
    private ResultadoTiempoHistoria walterQuispesayhuaActual;
    private boolean walterQuispesayhuaEjecutando;
    private int walterQuispesayhuaGeneracion;

    private AnimationTimer walterQuispesayhuaReloj;
    private double walterQuispesayhuaTiempo;
    private boolean walterQuispesayhuaAvanceDelReloj;
    private boolean walterQuispesayhuaCargandoRegistros;

    private XYChart.Series<Number, Number> walterQuispesayhuaCursorAceleracion;
    private XYChart.Series<Number, Number> walterQuispesayhuaCursorDesplazamiento;
    private XYChart.Series<Number, Number> walterQuispesayhuaCursorDeriva;
    private double[] walterQuispesayhuaExtremosAceleracion = {0, 1};
    private double[] walterQuispesayhuaExtremosDesplazamiento = {0, 1};
    private double[] walterQuispesayhuaExtremosDeriva = {0, 1};

    @FXML
    private void initialize() {
        lblAdvertencia.setText("Verificación complementaria: no sustituye al análisis estático ni al modal espectral (arts. 46.1 y 32.3). "
                + "Modelo lineal-elástico con R = 1 (conservador) y base empotrada, sin interacción suelo-estructura. "
                + "Resultado educativo y preliminar: haga validar el análisis por un ingeniero civil colegiado.");
        cmbFormato.getItems().setAll(WALTER_QUISPESAYHUA_FORMATO_UNO, WALTER_QUISPESAYHUA_FORMATO_PAR);
        cmbFormato.setValue(WALTER_QUISPESAYHUA_FORMATO_UNO);
        cmbUnidad.getItems().setAll(UnidadAceleracion.values());
        cmbUnidad.setValue(UnidadAceleracion.M_S2);
        cmbVelocidad.getItems().setAll(0.25, 0.5, 1.0, 2.0, 4.0);
        cmbVelocidad.setValue(1.0);
        cmbVelocidad.setConverter(new StringConverter<>() {
            @Override public String toString(Double v) { return v == null ? "" : "×" + String.format(WALTER_QUISPESAYHUA_ES, "%.2f", v).replaceAll("0+$", "").replaceAll("[,.]$", ""); }
            @Override public Double fromString(String s) { return null; }
        });
        lstImportados.setPlaceholder(new Label("Aún no hay registros importados."));
        Formularios.fernandoHuancaLimpiarAlEditar(txtSemilla, txtDt);

        Tablas.fernandoHuancaConfigurar(tablaResultados, "Aún no hay resultados de la simulación.", fila -> { });
        tablaResultados.getStyleClass().add("tabla-compacta");
        tablaResultados.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(String[] fila, boolean vacio) {
                super.updateItem(fila, vacio);
                getStyleClass().remove("th-fila-resumen");
                if (!vacio && fila != null && getIndex() >= tv.getItems().size() - 2) {
                    getStyleClass().add("th-fila-resumen");
                }
            }
        });
        String[] cab = ResumenTiempoHistoria.CABECERA;
        for (int i = 0; i < cab.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(cab[i]);
            c.setCellValueFactory(x -> new SimpleStringProperty(x.getValue()[k]));
            c.setMinWidth(i == 0 ? 190 : Math.min(130, Math.max(70, cab[i].length() * 7.0 + 22)));
            tablaResultados.getColumns().add(c);
        }

        grupoDireccion.selectedToggleProperty().addListener((o, a, nuevo) -> {
            if (nuevo == null) {
                grupoDireccion.selectToggle(a);
                return;
            }
            if (walterQuispesayhuaActual != null) {
                vista.setDireccion(walterQuispesayhuaDireccion());
                walterQuispesayhuaReconstruirGraficas();
                walterQuispesayhuaActualizar((int) Math.round(sldTiempo.getValue() / walterQuispesayhuaActual.dt()));
            }
        });
        cmbRegistro.getSelectionModel().selectedIndexProperty().addListener((o, a, idx) -> {
            if (!walterQuispesayhuaCargandoRegistros && walterQuispesayhuaResumen != null && idx.intValue() >= 0) {
                walterQuispesayhuaSeleccionarRegistro(idx.intValue());
            }
        });
        sldTiempo.valueProperty().addListener((o, a, v) -> {
            if (walterQuispesayhuaActual == null) {
                return;
            }
            if (!walterQuispesayhuaAvanceDelReloj) {
                walterQuispesayhuaTiempo = v.doubleValue();
            }
            walterQuispesayhuaActualizar(walterQuispesayhuaPasoDe(v.doubleValue()));
        });
        sldEscala.valueProperty().addListener((o, a, v) -> {
            vista.setMultiplicador(v.doubleValue());
            lblExageracion.setText(walterQuispesayhuaTextoEscala());
        });
        tabsTH.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b.intValue() != 0) {
                walterQuispesayhuaPausar();
            }
        });
        raiz.sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                walterQuispesayhuaPausar();
            } else {
                b.windowProperty().addListener((oo, w1, w2) -> {
                    if (w2 == null) {
                        walterQuispesayhuaPausar();
                    }
                });
            }
        });
        walterQuispesayhuaLimpiar();
    }

    public void walterQuispesayhuaConfigurar(Resultado resultado, Parametros norma) {
        walterQuispesayhuaLimpiar();
        this.walterQuispesayhuaResultado = resultado;
        this.walterQuispesayhuaNorma = norma;
        if (resultado == null || norma == null || resultado.modelo() == null) {
            return;
        }
        double tx = resultado.espectralX().periodo(), ty = resultado.espectralY().periodo();
        this.walterQuispesayhuaPeriodo = ResumenSimulacion.walterQuispesayhuaPeriodoFundamental(tx, ty);
        lblPeriodo.setText(String.format(WALTER_QUISPESAYHUA_ES, "T para el escalado = máx(Tx = %.3f s ; Ty = %.3f s) = %.3f s · rejilla %.3f a %.3f s",
                tx, ty, walterQuispesayhuaPeriodo, 0.2 * walterQuispesayhuaPeriodo, 1.5 * walterQuispesayhuaPeriodo));
        lblAyuda.setText("Los registros se escalan para que el promedio SRSS de sus espectros (5 % de amortiguamiento, art. 48.5) no baje del "
                + "espectro de diseño con R = 1 en la rejilla 0,2T a 1,5T (art. 47). T es el mayor de los periodos fundamentales de X e Y, "
                + "así la rejilla cubre ambas direcciones. Luego cada registro se aplica en X e Y a la vez sobre el edificio; el suelo se mueve "
                + "con el desplazamiento del terreno ug(t) y la estructura responde sobre él (desplazamiento absoluto = relativo + suelo). "
                + "Con la versión 2026 se exigen al menos " + EscaladoRegistros.fernandoHuancaMinimoConjuntos(norma.version()) + " conjuntos para la verificación formal"
                + (EscaladoRegistros.fernandoHuancaMinimoConjuntos(norma.version()) == 7 ? "." : " (7 o más para usar el promedio)."));
        walterQuispesayhuaMostrar(panelVacio, false);
        walterQuispesayhuaMostrar(panelContenido, true);
    }

    public void walterQuispesayhuaLimpiar() {
        walterQuispesayhuaGeneracion++;
        walterQuispesayhuaPausar();
        walterQuispesayhuaResultado = null;
        walterQuispesayhuaNorma = null;
        walterQuispesayhuaResumen = null;
        walterQuispesayhuaActual = null;
        walterQuispesayhuaImportados.clear();
        if (lstImportados != null) {
            lstImportados.getItems().clear();
            walterQuispesayhuaMostrar(panelVacio, true);
            walterQuispesayhuaMostrar(panelContenido, false);
            walterQuispesayhuaMostrar(panelResultados, false);
            walterQuispesayhuaMostrar(barraProgreso, false);
            barraProgreso.progressProperty().unbind();
            lblProgreso.textProperty().unbind();
            lblProgreso.setText("");
            cmbRegistro.getItems().clear();
            tablaResultados.getItems().clear();
            for (LineChart<Number, Number> g : List.of(grafAceleracion, grafDesplazamiento, grafDeriva, grafEspectros)) {
                g.getData().clear();
            }
            walterQuispesayhuaEjecutando = false;
            walterQuispesayhuaHabilitar(true);
        }
    }

    public Optional<ResumenTiempoHistoria> walterQuispesayhuaUltimo() {
        return Optional.ofNullable(walterQuispesayhuaResumen);
    }

    @FXML
    private void onGenerar() {
        if (walterQuispesayhuaResultado == null || walterQuispesayhuaEjecutando) {
            return;
        }
        long semilla;
        try {
            semilla = Long.parseLong(txtSemilla.getText().trim());
        } catch (NumberFormatException ex) {
            Formularios.fernandoHuancaMarcarError(txtSemilla, "La semilla debe ser un número entero.");
            walterQuispesayhuaNotificador.fernandoHuancaError("La semilla debe ser un número entero (por ejemplo 2026).");
            return;
        }
        final Parametros n = walterQuispesayhuaNorma;
        final double t = walterQuispesayhuaPeriodo;
        walterQuispesayhuaEjecutar(true, "Sintéticos compatibles con la E.030 (" + WALTER_QUISPESAYHUA_CONJUNTOS_SINTETICOS + " conjuntos, semilla " + semilla + ")",
                () -> GeneradorAcelerograma.fernandoHuancaGenerarConjuntos(n, WALTER_QUISPESAYHUA_CONJUNTOS_SINTETICOS, t, semilla));
    }

    @FXML
    private void onImportar() {
        if (walterQuispesayhuaResultado == null || walterQuispesayhuaEjecutando) {
            return;
        }
        Double dt = walterQuispesayhuaLeerDt();
        if (dt != null && dt < 0) {
            return;
        }
        UnidadAceleracion unidad = cmbUnidad.getValue();
        try {
            RegistroSismico r;
            if (WALTER_QUISPESAYHUA_FORMATO_PAR.equals(cmbFormato.getValue())) {
                File fx = walterQuispesayhuaElegir("Seleccione el archivo de la componente X");
                if (fx == null) {
                    return;
                }
                File fy = walterQuispesayhuaElegir("Seleccione el archivo de la componente Y");
                if (fy == null) {
                    return;
                }
                r = ImportadorRegistro.fernandoHuancaImportarPar(fx.toPath(), fy.toPath(), unidad, dt);
            } else {
                File f = walterQuispesayhuaElegir("Seleccione el acelerograma de 3 columnas (tiempo, ax, ay)");
                if (f == null) {
                    return;
                }
                r = ImportadorRegistro.fernandoHuancaImportar(f.toPath(), unidad, dt);
            }
            walterQuispesayhuaImportados.add(r);
            lstImportados.getItems().add(String.format(WALTER_QUISPESAYHUA_ES, "%s · %d pasos · dt %.4f s · PGA X %.3f g / Y %.3f g", r.nombre(), r.pasos(), r.dt(),
                    r.aceleracionMaxima(0) / EscaladoRegistros.FERNANDO_HUANCA_G, r.aceleracionMaxima(1) / EscaladoRegistros.FERNANDO_HUANCA_G));
            walterQuispesayhuaNotificador.exito("Registro agregado: " + r.nombre());
        } catch (RuntimeException ex) {
            walterQuispesayhuaNotificador.fernandoHuancaError(ex.getMessage() == null ? "No se pudo leer el archivo del acelerograma." : ex.getMessage());
        }
    }

    @FXML
    private void onQuitar() {
        int i = lstImportados.getSelectionModel().getSelectedIndex();
        if (i < 0) {
            walterQuispesayhuaNotificador.fernandoHuancaAdvertencia("Seleccione en la lista el registro que desea quitar.");
            return;
        }
        lstImportados.getItems().remove(i);
        walterQuispesayhuaImportados.remove(i);
    }

    @FXML
    private void onSimularImportados() {
        if (walterQuispesayhuaResultado == null || walterQuispesayhuaEjecutando) {
            return;
        }
        if (walterQuispesayhuaImportados.isEmpty()) {
            walterQuispesayhuaNotificador.fernandoHuancaAdvertencia("Primero importe al menos un acelerograma con «Importar acelerograma…».");
            return;
        }
        List<RegistroSismico> copia = List.copyOf(walterQuispesayhuaImportados);
        walterQuispesayhuaEjecutar(false, "Importados: " + copia.size() + (copia.size() == 1 ? " conjunto" : " conjuntos") + " ("
                + String.join("; ", copia.stream().map(RegistroSismico::nombre).toList()) + ")", () -> copia);
    }

    @FXML
    private void onPlay() {
        if (walterQuispesayhuaActual == null) {
            return;
        }
        if (walterQuispesayhuaReloj != null) {
            walterQuispesayhuaPausar();
            return;
        }
        if (walterQuispesayhuaTiempo >= sldTiempo.getMax() - 1e-9) {
            walterQuispesayhuaTiempo = 0;
        }
        btnPlay.setText("Pausar");
        iconoPlay.setIconLiteral("fth-pause");
        walterQuispesayhuaReloj = new AnimationTimer() {
            private long previo;

            @Override
            public void handle(long ahora) {
                if (!walterQuispesayhuaEnPantalla(raiz)) {
                    walterQuispesayhuaPausar();
                    return;
                }
                if (previo != 0) {
                    double v = cmbVelocidad.getValue() == null ? 1.0 : cmbVelocidad.getValue();
                    walterQuispesayhuaTiempo += (ahora - previo) * 1e-9 * v;
                }
                previo = ahora;
                boolean fin = walterQuispesayhuaTiempo >= sldTiempo.getMax();
                if (fin) {
                    walterQuispesayhuaTiempo = sldTiempo.getMax();
                }
                walterQuispesayhuaAvanceDelReloj = true;
                try {
                    sldTiempo.setValue(walterQuispesayhuaTiempo);
                } finally {
                    walterQuispesayhuaAvanceDelReloj = false;
                }
                if (fin) {
                    walterQuispesayhuaPausar();
                }
            }
        };
        walterQuispesayhuaReloj.start();
    }

    @FXML
    private void onReiniciar() {
        walterQuispesayhuaPausar();
        walterQuispesayhuaTiempo = 0;
        sldTiempo.setValue(0);
    }

    private void walterQuispesayhuaEjecutar(boolean espectroCompatible, String origen, Supplier<List<RegistroSismico>> fuente) {
        final var modelo = walterQuispesayhuaResultado.modelo();
        final Parametros n = walterQuispesayhuaNorma;
        final double t = walterQuispesayhuaPeriodo;
        final int gen = ++walterQuispesayhuaGeneracion;
        walterQuispesayhuaPausar();
        Task<ResumenTiempoHistoria> tarea = new Task<>() {
            @Override
            protected ResumenTiempoHistoria call() {
                updateMessage("Preparando los registros sísmicos…");
                updateProgress(-1, 1);
                List<RegistroSismico> registros = fuente.get();
                return ResumenSimulacion.walterQuispesayhuaSimular(modelo, n, registros, espectroCompatible, t, origen, f -> {
                    updateProgress(f, 1.0);
                    updateMessage(f < 1.0 ? String.format(WALTER_QUISPESAYHUA_ES, "Escalando y simulando… %.0f %%", f * 100) : "Verificando el art. 49…");
                });
            }
        };
        walterQuispesayhuaEjecutando = true;
        walterQuispesayhuaHabilitar(false);
        walterQuispesayhuaMostrar(barraProgreso, true);
        barraProgreso.progressProperty().bind(tarea.progressProperty());
        lblProgreso.textProperty().bind(tarea.messageProperty());
        tarea.setOnSucceeded(e -> {
            walterQuispesayhuaTerminar();
            if (gen != walterQuispesayhuaGeneracion) {
                return;
            }
            lblProgreso.setText("Simulación terminada: " + tarea.getValue().resultados().size() + " registros.");
            walterQuispesayhuaMostrarResumen(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            walterQuispesayhuaTerminar();
            Throwable ex = tarea.getException();
            String m = ex == null || ex.getMessage() == null ? "No se pudo completar la simulación." : ex.getMessage();
            lblProgreso.setText(m);
            walterQuispesayhuaNotificador.fernandoHuancaError(m);
        });
        Thread hilo = new Thread(tarea, "tiempo-historia");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void walterQuispesayhuaTerminar() {
        barraProgreso.progressProperty().unbind();
        lblProgreso.textProperty().unbind();
        walterQuispesayhuaMostrar(barraProgreso, false);
        walterQuispesayhuaEjecutando = false;
        walterQuispesayhuaHabilitar(true);
    }

    private void walterQuispesayhuaHabilitar(boolean si) {
        for (Node n : List.of(btnGenerar, btnImportar, btnQuitar, btnSimularImportados, txtSemilla, txtDt, cmbFormato, cmbUnidad, lstImportados)) {
            n.setDisable(!si);
        }
    }

    private void walterQuispesayhuaMostrarResumen(ResumenTiempoHistoria r) {
        walterQuispesayhuaPausar();
        this.walterQuispesayhuaResumen = r;
        var ver = r.verificacion();
        walterQuispesayhuaMostrar(panelResultados, true);

        String veredicto = r.veredicto();
        if (walterQuispesayhuaResultado != null && !GuiaSismica.walterQuispesayhuaModeloValido(walterQuispesayhuaResultado)) {
            veredicto = "MODELO INCOMPLETO";
        }
        String icono = switch (veredicto) {
            case "CUMPLE" -> "fth-check-circle";
            case "NO CUMPLE", "MODELO INCOMPLETO" -> "fth-x-circle";
            default -> "fth-alert-triangle";
        };
        String detalle = switch (veredicto) {
            case "CUMPLE" -> " · derivas de diseño (" + ver.criterio() + " de " + ver.conjuntos() + " conjuntos) dentro de 0,00875";
            case "NO CUMPLE" -> " · alguna deriva de diseño (" + ver.criterio() + ") supera 0,00875";
            case "MODELO INCOMPLETO" -> " · hay vigas que no unen columnas: corrija el modelo y vuelva a calcular";
            default -> " · hay " + ver.conjuntos() + " conjuntos y se requieren al menos " + ver.conjuntosMinimos();
        };
        lblBanner.setText(veredicto + " (art. 49)" + detalle);
        lblBanner.setGraphic(new FontIcon(icono));
        lblBanner.getStyleClass().removeAll("sismo-banner", "sismo-banner-ok", "sismo-banner-error", "sismo-banner-alerta");
        lblBanner.getStyleClass().addAll("sismo-banner", switch (veredicto) {
            case "CUMPLE" -> "sismo-banner-ok";
            case "NO CUMPLE", "MODELO INCOMPLETO" -> "sismo-banner-error";
            default -> "sismo-banner-alerta";
        });

        cajaAvisos.getChildren().clear();
        boolean importado = r.origenRegistros().startsWith("Importados");
        if (importado && !ver.conjuntosSuficientes()) {
            cajaAvisos.getChildren().add(walterQuispesayhuaAviso("Se simularon " + ver.conjuntos() + " registros importados: no basta para la verificación formal (mínimo "
                    + ver.conjuntosMinimos() + " conjuntos). El resultado es solo orientativo.", "paso-alerta-roja"));
        }
        LinkedHashSet<String> avisos = new LinkedHashSet<>(ver.avisos());
        avisos.addAll(r.escalado().avisos());
        for (ResultadoTiempoHistoria x : r.resultados()) {
            avisos.addAll(x.avisos());
        }
        for (String a : avisos) {
            cajaAvisos.getChildren().add(walterQuispesayhuaAviso("• " + a, "paso-alerta"));
        }

        tablaResultados.getItems().setAll(r.filasTabla());
        walterQuispesayhuaEspectros(r);

        walterQuispesayhuaCargandoRegistros = true;
        try {
            cmbRegistro.getItems().setAll(r.resultados().stream().map(ResultadoTiempoHistoria::nombreRegistro).toList());
            cmbRegistro.getSelectionModel().select(0);
        } finally {
            walterQuispesayhuaCargandoRegistros = false;
        }
        tabsTH.getSelectionModel().select(0);
        walterQuispesayhuaSeleccionarRegistro(0);
    }

    private static Label walterQuispesayhuaAviso(String texto, String clase) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add(clase);
        return l;
    }

    private void walterQuispesayhuaEspectros(ResumenTiempoHistoria r) {
        var esc = r.escalado();
        XYChart.Series<Number, Number> diseno = new XYChart.Series<>();
        diseno.setName("Diseño (R = 1)");
        XYChart.Series<Number, Number> prom = new XYChart.Series<>();
        prom.setName("Promedio SRSS escalado");
        for (int i = 0; i < esc.periodos().length; i++) {
            diseno.getData().add(new XYChart.Data<>(esc.periodos()[i], esc.espectroDiseno()[i] / EscaladoRegistros.FERNANDO_HUANCA_G));
            prom.getData().add(new XYChart.Data<>(esc.periodos()[i], esc.espectroPromedioSrss()[i] / EscaladoRegistros.FERNANDO_HUANCA_G));
        }
        grafEspectros.getData().setAll(List.of(diseno, prom));
        cajaEscalado.getChildren().clear();
        cajaEscalado.getChildren().add(walterQuispesayhuaAviso(String.format(WALTER_QUISPESAYHUA_ES, "Razón mínima promedio SRSS / diseño = %.3f: %s el escalado del art. 47. T = %.3f s.",
                esc.razonMinima(), esc.cumple() ? "cumple" : "NO cumple", r.periodoFundamental()), esc.cumple() ? "paso-detalle" : "paso-alerta"));
        StringBuilder fac = new StringBuilder("Factores de escala: ");
        for (int i = 0; i < esc.factores().length; i++) {
            fac.append(i == 0 ? "" : " · ").append(i + 1).append(": ").append(String.format(WALTER_QUISPESAYHUA_ES, "%.3f", esc.factores()[i]));
        }
        cajaEscalado.getChildren().add(walterQuispesayhuaAviso(fac.toString(), "paso-detalle"));
        for (String a : esc.avisos()) {
            cajaEscalado.getChildren().add(walterQuispesayhuaAviso("• " + a, "paso-detalle"));
        }
    }

    private int walterQuispesayhuaDireccion() {
        return tbY.isSelected() ? 1 : 0;
    }

    private String walterQuispesayhuaTextoEscala() {
        return String.format(java.util.Locale.forLanguageTag("es-PE"),
                "Escala visual ×%.2f sobre el factor automático: solo cambia el dibujo, no los resultados.", sldEscala.getValue());
    }

    private void walterQuispesayhuaSeleccionarRegistro(int idx) {
        walterQuispesayhuaPausar();
        walterQuispesayhuaActual = walterQuispesayhuaResumen.resultados().get(idx);
        vista.walterQuispesayhuaMostrar(walterQuispesayhuaActual, walterQuispesayhuaResultado.modelo().cotas());
        vista.setDireccion(walterQuispesayhuaDireccion());
        vista.setMultiplicador(sldEscala.getValue());
        lblExageracion.setText(walterQuispesayhuaTextoEscala());
        walterQuispesayhuaTiempo = 0;
        sldTiempo.setMax(walterQuispesayhuaActual.tiempo()[walterQuispesayhuaActual.pasos() - 1]);
        sldTiempo.setBlockIncrement(walterQuispesayhuaActual.dt() * 10);
        walterQuispesayhuaReconstruirGraficas();
        sldTiempo.setValue(0);
        walterQuispesayhuaActualizar(0);
    }

    private int walterQuispesayhuaPasoDe(double t) {
        return Math.max(0, Math.min(walterQuispesayhuaActual.pasos() - 1, (int) Math.round(t / walterQuispesayhuaActual.dt())));
    }

    private void walterQuispesayhuaReconstruirGraficas() {
        if (walterQuispesayhuaActual == null) {
            return;
        }
        int dir = walterQuispesayhuaDireccion();
        double[] t = walterQuispesayhuaActual.tiempo();
        double dur = t[t.length - 1];

        double[][] ag = ResumenSimulacion.walterQuispesayhuaDecimar(t, ResumenSimulacion.walterQuispesayhuaAceleracionSuelo(walterQuispesayhuaActual, dir), ResumenSimulacion.WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS);
        walterQuispesayhuaExtremosAceleracion = walterQuispesayhuaAjustar(grafAceleracion, dur, minimo(ag[1]), walterQuispesayhuaMaximo(ag[1]), false);
        walterQuispesayhuaCursorAceleracion = walterQuispesayhuaCursor("Instante", walterQuispesayhuaExtremosAceleracion);
        grafAceleracion.getData().setAll(List.of(walterQuispesayhuaSerie("ag (m/s²)", ag[0], ag[1], 1), walterQuispesayhuaCursorAceleracion));

        double[][] ug = ResumenSimulacion.walterQuispesayhuaDecimar(t, ResumenSimulacion.walterQuispesayhuaSuelo(walterQuispesayhuaActual, dir), ResumenSimulacion.WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS);
        double[][] rel = ResumenSimulacion.walterQuispesayhuaDecimar(t, ResumenSimulacion.walterQuispesayhuaTechoRelativo(walterQuispesayhuaActual, dir), ResumenSimulacion.WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS);
        double[][] abs = ResumenSimulacion.walterQuispesayhuaDecimar(t, ResumenSimulacion.walterQuispesayhuaTechoAbsoluto(walterQuispesayhuaActual, dir), ResumenSimulacion.WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS);
        double mn = Math.min(minimo(ug[1]), Math.min(minimo(rel[1]), minimo(abs[1]))) * 100;
        double mx = Math.max(walterQuispesayhuaMaximo(ug[1]), Math.max(walterQuispesayhuaMaximo(rel[1]), walterQuispesayhuaMaximo(abs[1]))) * 100;
        walterQuispesayhuaExtremosDesplazamiento = walterQuispesayhuaAjustar(grafDesplazamiento, dur, mn, mx, false);
        walterQuispesayhuaCursorDesplazamiento = walterQuispesayhuaCursor("Instante", walterQuispesayhuaExtremosDesplazamiento);
        grafDesplazamiento.getData().setAll(List.of(walterQuispesayhuaSerie("Suelo ug(t)", ug[0], ug[1], 100), walterQuispesayhuaSerie("Techo relativo", rel[0], rel[1], 100),
                walterQuispesayhuaSerie("Techo absoluto", abs[0], abs[1], 100), walterQuispesayhuaCursorDesplazamiento));

        int critico = ResumenSimulacion.walterQuispesayhuaPisoCritico(walterQuispesayhuaActual, dir);
        double[][] dv = ResumenSimulacion.walterQuispesayhuaDecimar(t, ResumenSimulacion.walterQuispesayhuaDerivaPiso(walterQuispesayhuaActual, dir, critico), ResumenSimulacion.WALTER_QUISPESAYHUA_PUNTOS_MAXIMOS);
        walterQuispesayhuaExtremosDeriva = walterQuispesayhuaAjustar(grafDeriva, dur, 0, Math.max(walterQuispesayhuaMaximo(dv[1]), ResumenSimulacion.WALTER_QUISPESAYHUA_LIMITE_DERIVA) * 1.1, true);
        walterQuispesayhuaCursorDeriva = walterQuispesayhuaCursor("Instante", walterQuispesayhuaExtremosDeriva);
        XYChart.Series<Number, Number> limite = walterQuispesayhuaSerie("Límite 0,00875", new double[]{0, dur},
                new double[]{ResumenSimulacion.WALTER_QUISPESAYHUA_LIMITE_DERIVA, ResumenSimulacion.WALTER_QUISPESAYHUA_LIMITE_DERIVA}, 1);
        grafDeriva.getData().setAll(List.of(walterQuispesayhuaSerie("Deriva |Δ/h|", dv[0], dv[1], 1), limite, walterQuispesayhuaCursorDeriva));
        lblTituloDeriva.setText("Deriva del entrepiso crítico (piso " + (critico + 1) + ", dirección " + (dir == 0 ? "X" : "Y") + ") y límite 0,00875");
    }

    private void walterQuispesayhuaActualizar(int paso) {
        if (walterQuispesayhuaActual == null) {
            return;
        }
        int dir = walterQuispesayhuaDireccion();
        vista.setPaso(paso);
        double t = walterQuispesayhuaActual.tiempo()[paso];
        double ag = ResumenSimulacion.walterQuispesayhuaAceleracionSuelo(walterQuispesayhuaActual, dir)[paso];
        double ug = ResumenSimulacion.walterQuispesayhuaSuelo(walterQuispesayhuaActual, dir)[paso] * 100;
        double techo = ResumenSimulacion.walterQuispesayhuaRelativo(walterQuispesayhuaActual, dir, paso, walterQuispesayhuaActual.pisos() - 1) * 100;
        double deriva = ResumenSimulacion.walterQuispesayhuaDerivaMaximaInstante(walterQuispesayhuaActual, dir, paso);
        lblT.setText(String.format(WALTER_QUISPESAYHUA_ES, "t = %.2f s", t));
        lblAg.setText(String.format(WALTER_QUISPESAYHUA_ES, "ag = %.3f m/s² (%.3f g)", ag, ag / EscaladoRegistros.FERNANDO_HUANCA_G));
        lblUg.setText(String.format(WALTER_QUISPESAYHUA_ES, "ug = %.2f cm", ug));
        lblTecho.setText(String.format(WALTER_QUISPESAYHUA_ES, "Techo relativo = %.2f cm", techo));
        lblDeriva.setText(String.format(WALTER_QUISPESAYHUA_ES, "Deriva máx. del instante = %.5f (%.0f %% del límite)", deriva, 100 * deriva / ResumenSimulacion.WALTER_QUISPESAYHUA_LIMITE_DERIVA));
        walterQuispesayhuaMover(walterQuispesayhuaCursorAceleracion, t);
        walterQuispesayhuaMover(walterQuispesayhuaCursorDesplazamiento, t);
        walterQuispesayhuaMover(walterQuispesayhuaCursorDeriva, t);
    }

    private static void walterQuispesayhuaMover(XYChart.Series<Number, Number> cursor, double t) {
        if (cursor != null && cursor.getData().size() == 2) {
            cursor.getData().get(0).setXValue(t);
            cursor.getData().get(1).setXValue(t);
        }
    }

    private void walterQuispesayhuaPausar() {
        if (walterQuispesayhuaReloj != null) {
            walterQuispesayhuaReloj.stop();
            walterQuispesayhuaReloj = null;
        }
        if (btnPlay != null) {
            btnPlay.setText("Reproducir");
            iconoPlay.setIconLiteral("fth-play");
        }
    }

    private static XYChart.Series<Number, Number> walterQuispesayhuaSerie(String nombre, double[] x, double[] y, double factorY) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(nombre);
        List<XYChart.Data<Number, Number>> datos = new ArrayList<>(x.length);
        for (int i = 0; i < x.length; i++) {
            datos.add(new XYChart.Data<>(x[i], y[i] * factorY));
        }
        s.getData().setAll(datos);
        return s;
    }

    private static XYChart.Series<Number, Number> walterQuispesayhuaCursor(String nombre, double[] extremos) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(nombre);
        s.getData().add(new XYChart.Data<>(0, extremos[0]));
        s.getData().add(new XYChart.Data<>(0, extremos[1]));
        return s;
    }

    private static double[] walterQuispesayhuaAjustar(LineChart<Number, Number> g, double duracion, double min, double max, boolean desdeCero) {
        NumberAxis x = (NumberAxis) g.getXAxis();
        NumberAxis y = (NumberAxis) g.getYAxis();
        x.setAutoRanging(false);
        x.setLowerBound(0);
        x.setUpperBound(duracion);
        x.setTickUnit(walterQuispesayhuaPasoLindo(duracion / 8));
        if (max - min < 1e-12) {
            min -= 1;
            max += 1;
        }
        double pad = (max - min) * 0.05;
        double lo = desdeCero ? 0 : min - pad, hi = max + (desdeCero ? 0 : pad);
        double tick = walterQuispesayhuaPasoLindo((hi - lo) / 6);
        lo = Math.floor(lo / tick) * tick;
        hi = Math.ceil(hi / tick) * tick;
        y.setAutoRanging(false);
        y.setLowerBound(lo);
        y.setUpperBound(hi);
        y.setTickUnit(tick);
        return new double[]{lo, hi};
    }

    static double walterQuispesayhuaPasoLindo(double aprox) {
        if (!(aprox > 0)) {
            return 1;
        }
        double e = Math.pow(10, Math.floor(Math.log10(aprox)));
        double m = aprox / e;
        double base = m < 1.5 ? 1 : m < 3 ? 2 : m < 7 ? 5 : 10;
        return base * e;
    }

    private static double minimo(double[] v) {
        double m = Double.MAX_VALUE;
        for (double x : v) {
            m = Math.min(m, x);
        }
        return m;
    }

    private static double walterQuispesayhuaMaximo(double[] v) {
        double m = -Double.MAX_VALUE;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    private Double walterQuispesayhuaLeerDt() {
        String s = txtDt.getText() == null ? "" : txtDt.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            double v = Double.parseDouble(s.replace(',', '.'));
            if (!(v > 0)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException ex) {
            Formularios.fernandoHuancaMarcarError(txtDt, "Ingrese un paso de tiempo positivo (ej. 0,01).");
            walterQuispesayhuaNotificador.fernandoHuancaError("El paso de tiempo dt debe ser un número positivo, por ejemplo 0,01 s.");
            return -1.0;
        }
    }

    private File walterQuispesayhuaElegir(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Acelerogramas (*.txt, *.dat, *.csv, *.at2, *.asc)", "*.txt", "*.dat", "*.csv", "*.at2", "*.asc"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        return fc.showOpenDialog(raiz.getScene() == null ? null : raiz.getScene().getWindow());
    }

    private static boolean walterQuispesayhuaEnPantalla(Node n) {
        if (n == null || n.getScene() == null) {
            return false;
        }
        for (Node p = n; p != null; p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static void walterQuispesayhuaMostrar(Node nodo, boolean visible) {
        if (nodo != null) {
            nodo.setVisible(visible);
            nodo.setManaged(visible);
        }
    }
}
