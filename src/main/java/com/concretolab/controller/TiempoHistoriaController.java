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

/**
 * Pestaña «Simulación temporal»: análisis tiempo-historia con el suelo móvil. Genera o importa registros sísmicos, los
 * escala (art. 47), los simula sobre el modelo dinámico calculado en la pantalla sísmica (en segundo plano), verifica el
 * art. 49 y anima el edificio sobre el terreno en movimiento con gráficas sincronizadas.
 */
@Component
@RequiredArgsConstructor
public class TiempoHistoriaController {

    private static final String FORMATO_UNO = "Un archivo (3 columnas: t, ax, ay)";
    private static final String FORMATO_PAR = "Dos archivos (componente X y Y)";
    private static final int CONJUNTOS_SINTETICOS = 7;
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final Notificador notificador;

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

    private Resultado resultado;
    private Parametros norma;
    private double periodo;
    private final List<RegistroSismico> importados = new ArrayList<>();
    private ResumenTiempoHistoria resumen;
    private ResultadoTiempoHistoria actual;
    private boolean ejecutando;
    private int generacion;

    private AnimationTimer reloj;
    private double tiempo;
    private boolean avanceDelReloj;
    private boolean cargandoRegistros;

    private XYChart.Series<Number, Number> cursorAceleracion;
    private XYChart.Series<Number, Number> cursorDesplazamiento;
    private XYChart.Series<Number, Number> cursorDeriva;
    private double[] extremosAceleracion = {0, 1};
    private double[] extremosDesplazamiento = {0, 1};
    private double[] extremosDeriva = {0, 1};

    @FXML
    private void initialize() {
        lblAdvertencia.setText("Verificación complementaria: no sustituye al análisis estático ni al modal espectral (arts. 46.1 y 32.3). "
                + "Modelo lineal-elástico con R = 1 (conservador) y base empotrada, sin interacción suelo-estructura. "
                + "Resultado educativo y preliminar: haga validar el análisis por un ingeniero civil colegiado.");
        cmbFormato.getItems().setAll(FORMATO_UNO, FORMATO_PAR);
        cmbFormato.setValue(FORMATO_UNO);
        cmbUnidad.getItems().setAll(UnidadAceleracion.values());
        cmbUnidad.setValue(UnidadAceleracion.M_S2);
        cmbVelocidad.getItems().setAll(0.25, 0.5, 1.0, 2.0, 4.0);
        cmbVelocidad.setValue(1.0);
        cmbVelocidad.setConverter(new StringConverter<>() {
            @Override public String toString(Double v) { return v == null ? "" : "×" + String.format(ES, "%.2f", v).replaceAll("0+$", "").replaceAll("[,.]$", ""); }
            @Override public Double fromString(String s) { return null; }
        });
        lstImportados.setPlaceholder(new Label("Aún no hay registros importados."));
        Formularios.limpiarAlEditar(txtSemilla, txtDt);

        Tablas.configurar(tablaResultados, "Aún no hay resultados de la simulación.", fila -> { });
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
            if (actual != null) {
                vista.setDireccion(direccion());
                reconstruirGraficas();
                actualizar((int) Math.round(sldTiempo.getValue() / actual.dt()));
            }
        });
        cmbRegistro.getSelectionModel().selectedIndexProperty().addListener((o, a, idx) -> {
            if (!cargandoRegistros && resumen != null && idx.intValue() >= 0) {
                seleccionarRegistro(idx.intValue());
            }
        });
        sldTiempo.valueProperty().addListener((o, a, v) -> {
            if (actual == null) {
                return;
            }
            if (!avanceDelReloj) {
                tiempo = v.doubleValue();
            }
            actualizar(pasoDe(v.doubleValue()));
        });
        sldEscala.valueProperty().addListener((o, a, v) -> {
            vista.setMultiplicador(v.doubleValue());
            lblExageracion.setText(vista.textoExageracion());
        });
        tabsTH.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b.intValue() != 0) {
                pausar();
            }
        });
        raiz.sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                pausar();
            } else {
                b.windowProperty().addListener((oo, w1, w2) -> {
                    if (w2 == null) {
                        pausar();
                    }
                });
            }
        });
        limpiar();
    }

    // ------------------------------------------------------------------ API para la pantalla sísmica

    /** Recibe el resultado del análisis sísmico recién calculado y reinicia la pestaña. */
    public void configurar(Resultado resultado, Parametros norma) {
        limpiar();
        this.resultado = resultado;
        this.norma = norma;
        if (resultado == null || norma == null || resultado.modelo() == null) {
            return;
        }
        double tx = resultado.espectralX().periodo(), ty = resultado.espectralY().periodo();
        this.periodo = ResumenSimulacion.periodoFundamental(tx, ty);
        lblPeriodo.setText(String.format(ES, "T para el escalado = máx(Tx = %.3f s ; Ty = %.3f s) = %.3f s · rejilla %.3f a %.3f s",
                tx, ty, periodo, 0.2 * periodo, 1.5 * periodo));
        lblAyuda.setText("Los registros se escalan para que el promedio SRSS de sus espectros (5 % de amortiguamiento, art. 48.5) no baje del "
                + "espectro de diseño con R = 1 en la rejilla 0,2T a 1,5T (art. 47). T es el mayor de los periodos fundamentales de X e Y, "
                + "así la rejilla cubre ambas direcciones. Luego cada registro se aplica en X e Y a la vez sobre el edificio; el suelo se mueve "
                + "con el desplazamiento del terreno ug(t) y la estructura responde sobre él (desplazamiento absoluto = relativo + suelo). "
                + "Con la versión 2026 se exigen al menos " + EscaladoRegistros.minimoConjuntos(norma.version()) + " conjuntos para la verificación formal"
                + (EscaladoRegistros.minimoConjuntos(norma.version()) == 7 ? "." : " (7 o más para usar el promedio)."));
        mostrar(panelVacio, false);
        mostrar(panelContenido, true);
    }

    /** Vuelve al estado inicial (cambio de proyecto o datos modificados). */
    public void limpiar() {
        generacion++;
        pausar();
        resultado = null;
        norma = null;
        resumen = null;
        actual = null;
        importados.clear();
        if (lstImportados != null) {
            lstImportados.getItems().clear();
            mostrar(panelVacio, true);
            mostrar(panelContenido, false);
            mostrar(panelResultados, false);
            mostrar(barraProgreso, false);
            barraProgreso.progressProperty().unbind();
            lblProgreso.textProperty().unbind();
            lblProgreso.setText("");
            cmbRegistro.getItems().clear();
            tablaResultados.getItems().clear();
            for (LineChart<Number, Number> g : List.of(grafAceleracion, grafDesplazamiento, grafDeriva, grafEspectros)) {
                g.getData().clear();
            }
            ejecutando = false;
            habilitar(true);
        }
    }

    /** Última simulación completada (para el reporte PDF). */
    public Optional<ResumenTiempoHistoria> ultimo() {
        return Optional.ofNullable(resumen);
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void onGenerar() {
        if (resultado == null || ejecutando) {
            return;
        }
        long semilla;
        try {
            semilla = Long.parseLong(txtSemilla.getText().trim());
        } catch (NumberFormatException ex) {
            Formularios.marcarError(txtSemilla, "La semilla debe ser un número entero.");
            notificador.error("La semilla debe ser un número entero (por ejemplo 2026).");
            return;
        }
        final Parametros n = norma;
        final double t = periodo;
        ejecutar(true, "Sintéticos compatibles con la E.030 (" + CONJUNTOS_SINTETICOS + " conjuntos, semilla " + semilla + ")",
                () -> GeneradorAcelerograma.generarConjuntos(n, CONJUNTOS_SINTETICOS, t, semilla));
    }

    @FXML
    private void onImportar() {
        if (resultado == null || ejecutando) {
            return;
        }
        Double dt = leerDt();
        if (dt != null && dt < 0) {
            return; // error ya mostrado
        }
        UnidadAceleracion unidad = cmbUnidad.getValue();
        try {
            RegistroSismico r;
            if (FORMATO_PAR.equals(cmbFormato.getValue())) {
                File fx = elegir("Seleccione el archivo de la componente X");
                if (fx == null) {
                    return;
                }
                File fy = elegir("Seleccione el archivo de la componente Y");
                if (fy == null) {
                    return;
                }
                r = ImportadorRegistro.importarPar(fx.toPath(), fy.toPath(), unidad, dt);
            } else {
                File f = elegir("Seleccione el acelerograma de 3 columnas (tiempo, ax, ay)");
                if (f == null) {
                    return;
                }
                r = ImportadorRegistro.importar(f.toPath(), unidad, dt);
            }
            importados.add(r);
            lstImportados.getItems().add(String.format(ES, "%s · %d pasos · dt %.4f s · PGA X %.3f g / Y %.3f g", r.nombre(), r.pasos(), r.dt(),
                    r.aceleracionMaxima(0) / EscaladoRegistros.G, r.aceleracionMaxima(1) / EscaladoRegistros.G));
            notificador.exito("Registro agregado: " + r.nombre());
        } catch (RuntimeException ex) {
            notificador.error(ex.getMessage() == null ? "No se pudo leer el archivo del acelerograma." : ex.getMessage());
        }
    }

    @FXML
    private void onQuitar() {
        int i = lstImportados.getSelectionModel().getSelectedIndex();
        if (i < 0) {
            notificador.advertencia("Seleccione en la lista el registro que desea quitar.");
            return;
        }
        lstImportados.getItems().remove(i);
        importados.remove(i);
    }

    @FXML
    private void onSimularImportados() {
        if (resultado == null || ejecutando) {
            return;
        }
        if (importados.isEmpty()) {
            notificador.advertencia("Primero importe al menos un acelerograma con «Importar acelerograma…».");
            return;
        }
        List<RegistroSismico> copia = List.copyOf(importados);
        ejecutar(false, "Importados: " + copia.size() + (copia.size() == 1 ? " conjunto" : " conjuntos") + " ("
                + String.join("; ", copia.stream().map(RegistroSismico::nombre).toList()) + ")", () -> copia);
    }

    @FXML
    private void onPlay() {
        if (actual == null) {
            return;
        }
        if (reloj != null) {
            pausar();
            return;
        }
        if (tiempo >= sldTiempo.getMax() - 1e-9) {
            tiempo = 0;
        }
        btnPlay.setText("Pausar");
        iconoPlay.setIconLiteral("fth-pause");
        reloj = new AnimationTimer() {
            private long previo;

            @Override
            public void handle(long ahora) {
                if (!enPantalla(raiz)) {
                    pausar();
                    return;
                }
                if (previo != 0) {
                    double v = cmbVelocidad.getValue() == null ? 1.0 : cmbVelocidad.getValue();
                    tiempo += (ahora - previo) * 1e-9 * v;
                }
                previo = ahora;
                boolean fin = tiempo >= sldTiempo.getMax();
                if (fin) {
                    tiempo = sldTiempo.getMax();
                }
                avanceDelReloj = true;
                try {
                    sldTiempo.setValue(tiempo);
                } finally {
                    avanceDelReloj = false;
                }
                if (fin) {
                    pausar();
                }
            }
        };
        reloj.start();
    }

    @FXML
    private void onReiniciar() {
        pausar();
        tiempo = 0;
        sldTiempo.setValue(0);
    }

    // ------------------------------------------------------------------ simulación en segundo plano

    private void ejecutar(boolean espectroCompatible, String origen, Supplier<List<RegistroSismico>> fuente) {
        final var modelo = resultado.modelo();
        final Parametros n = norma;
        final double t = periodo;
        final int gen = ++generacion;
        pausar();
        Task<ResumenTiempoHistoria> tarea = new Task<>() {
            @Override
            protected ResumenTiempoHistoria call() {
                updateMessage("Preparando los registros sísmicos…");
                updateProgress(-1, 1);
                List<RegistroSismico> registros = fuente.get();
                return ResumenSimulacion.simular(modelo, n, registros, espectroCompatible, t, origen, f -> {
                    updateProgress(f, 1.0);
                    updateMessage(f < 1.0 ? String.format(ES, "Escalando y simulando… %.0f %%", f * 100) : "Verificando el art. 49…");
                });
            }
        };
        ejecutando = true;
        habilitar(false);
        mostrar(barraProgreso, true);
        barraProgreso.progressProperty().bind(tarea.progressProperty());
        lblProgreso.textProperty().bind(tarea.messageProperty());
        tarea.setOnSucceeded(e -> {
            terminar();
            if (gen != generacion) {
                return;
            }
            lblProgreso.setText("Simulación terminada: " + tarea.getValue().resultados().size() + " registros.");
            mostrarResumen(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            terminar();
            Throwable ex = tarea.getException();
            String m = ex == null || ex.getMessage() == null ? "No se pudo completar la simulación." : ex.getMessage();
            lblProgreso.setText(m);
            notificador.error(m);
        });
        Thread hilo = new Thread(tarea, "tiempo-historia");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void terminar() {
        barraProgreso.progressProperty().unbind();
        lblProgreso.textProperty().unbind();
        mostrar(barraProgreso, false);
        ejecutando = false;
        habilitar(true);
    }

    private void habilitar(boolean si) {
        for (Node n : List.of(btnGenerar, btnImportar, btnQuitar, btnSimularImportados, txtSemilla, txtDt, cmbFormato, cmbUnidad, lstImportados)) {
            n.setDisable(!si);
        }
    }

    // ------------------------------------------------------------------ resultados

    private void mostrarResumen(ResumenTiempoHistoria r) {
        pausar();
        this.resumen = r;
        var ver = r.verificacion();
        mostrar(panelResultados, true);

        String veredicto = r.veredicto();
        String icono = switch (veredicto) {
            case "CUMPLE" -> "fth-check-circle";
            case "NO CUMPLE" -> "fth-x-circle";
            default -> "fth-alert-triangle";
        };
        String detalle = switch (veredicto) {
            case "CUMPLE" -> " · derivas de diseño (" + ver.criterio() + " de " + ver.conjuntos() + " conjuntos) dentro de 0,00875";
            case "NO CUMPLE" -> " · alguna deriva de diseño (" + ver.criterio() + ") supera 0,00875";
            default -> " · hay " + ver.conjuntos() + " conjuntos y se requieren al menos " + ver.conjuntosMinimos();
        };
        lblBanner.setText(veredicto + " (art. 49)" + detalle);
        lblBanner.setGraphic(new FontIcon(icono));
        lblBanner.getStyleClass().removeAll("sismo-banner", "sismo-banner-ok", "sismo-banner-error", "sismo-banner-alerta");
        lblBanner.getStyleClass().addAll("sismo-banner", switch (veredicto) {
            case "CUMPLE" -> "sismo-banner-ok";
            case "NO CUMPLE" -> "sismo-banner-error";
            default -> "sismo-banner-alerta";
        });

        cajaAvisos.getChildren().clear();
        boolean importado = r.origenRegistros().startsWith("Importados");
        if (importado && !ver.conjuntosSuficientes()) {
            cajaAvisos.getChildren().add(aviso("Se simularon " + ver.conjuntos() + " registros importados: no basta para la verificación formal (mínimo "
                    + ver.conjuntosMinimos() + " conjuntos). El resultado es solo orientativo.", "paso-alerta-roja"));
        }
        LinkedHashSet<String> avisos = new LinkedHashSet<>(ver.avisos());
        avisos.addAll(r.escalado().avisos());
        for (ResultadoTiempoHistoria x : r.resultados()) {
            avisos.addAll(x.avisos());
        }
        for (String a : avisos) {
            cajaAvisos.getChildren().add(aviso("• " + a, "paso-alerta"));
        }

        tablaResultados.getItems().setAll(r.filasTabla());
        espectros(r);

        cargandoRegistros = true;
        try {
            cmbRegistro.getItems().setAll(r.resultados().stream().map(ResultadoTiempoHistoria::nombreRegistro).toList());
            cmbRegistro.getSelectionModel().select(0);
        } finally {
            cargandoRegistros = false;
        }
        tabsTH.getSelectionModel().select(0);
        seleccionarRegistro(0);
    }

    private static Label aviso(String texto, String clase) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE); // sin esto el VBox encoge el texto largo a una línea con «…»
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add(clase);
        return l;
    }

    private void espectros(ResumenTiempoHistoria r) {
        var esc = r.escalado();
        XYChart.Series<Number, Number> diseno = new XYChart.Series<>();
        diseno.setName("Diseño (R = 1)");
        XYChart.Series<Number, Number> prom = new XYChart.Series<>();
        prom.setName("Promedio SRSS escalado");
        for (int i = 0; i < esc.periodos().length; i++) {
            diseno.getData().add(new XYChart.Data<>(esc.periodos()[i], esc.espectroDiseno()[i] / EscaladoRegistros.G));
            prom.getData().add(new XYChart.Data<>(esc.periodos()[i], esc.espectroPromedioSrss()[i] / EscaladoRegistros.G));
        }
        grafEspectros.getData().setAll(List.of(diseno, prom));
        cajaEscalado.getChildren().clear();
        cajaEscalado.getChildren().add(aviso(String.format(ES, "Razón mínima promedio SRSS / diseño = %.3f: %s el escalado del art. 47. T = %.3f s.",
                esc.razonMinima(), esc.cumple() ? "cumple" : "NO cumple", r.periodoFundamental()), esc.cumple() ? "paso-detalle" : "paso-alerta"));
        StringBuilder fac = new StringBuilder("Factores de escala: ");
        for (int i = 0; i < esc.factores().length; i++) {
            fac.append(i == 0 ? "" : " · ").append(i + 1).append(": ").append(String.format(ES, "%.3f", esc.factores()[i]));
        }
        cajaEscalado.getChildren().add(aviso(fac.toString(), "paso-detalle"));
        for (String a : esc.avisos()) {
            cajaEscalado.getChildren().add(aviso("• " + a, "paso-detalle"));
        }
    }

    // ------------------------------------------------------------------ animación y gráficas

    private int direccion() {
        return tbY.isSelected() ? 1 : 0;
    }

    private void seleccionarRegistro(int idx) {
        pausar();
        actual = resumen.resultados().get(idx);
        vista.mostrar(actual, resultado.modelo().cotas());
        vista.setDireccion(direccion());
        vista.setMultiplicador(sldEscala.getValue());
        lblExageracion.setText(vista.textoExageracion());
        tiempo = 0;
        sldTiempo.setMax(actual.tiempo()[actual.pasos() - 1]);
        sldTiempo.setBlockIncrement(actual.dt() * 10);
        reconstruirGraficas();
        sldTiempo.setValue(0);
        actualizar(0);
    }

    private int pasoDe(double t) {
        return Math.max(0, Math.min(actual.pasos() - 1, (int) Math.round(t / actual.dt())));
    }

    private void reconstruirGraficas() {
        if (actual == null) {
            return;
        }
        int dir = direccion();
        double[] t = actual.tiempo();
        double dur = t[t.length - 1];

        double[][] ag = ResumenSimulacion.decimar(t, ResumenSimulacion.aceleracionSuelo(actual, dir), ResumenSimulacion.PUNTOS_MAXIMOS);
        extremosAceleracion = ajustar(grafAceleracion, dur, minimo(ag[1]), maximo(ag[1]), false);
        cursorAceleracion = cursor("Instante", extremosAceleracion);
        grafAceleracion.getData().setAll(List.of(serie("ag (m/s²)", ag[0], ag[1], 1), cursorAceleracion));

        double[][] ug = ResumenSimulacion.decimar(t, ResumenSimulacion.suelo(actual, dir), ResumenSimulacion.PUNTOS_MAXIMOS);
        double[][] rel = ResumenSimulacion.decimar(t, ResumenSimulacion.techoRelativo(actual, dir), ResumenSimulacion.PUNTOS_MAXIMOS);
        double[][] abs = ResumenSimulacion.decimar(t, ResumenSimulacion.techoAbsoluto(actual, dir), ResumenSimulacion.PUNTOS_MAXIMOS);
        double mn = Math.min(minimo(ug[1]), Math.min(minimo(rel[1]), minimo(abs[1]))) * 100;
        double mx = Math.max(maximo(ug[1]), Math.max(maximo(rel[1]), maximo(abs[1]))) * 100;
        extremosDesplazamiento = ajustar(grafDesplazamiento, dur, mn, mx, false);
        cursorDesplazamiento = cursor("Instante", extremosDesplazamiento);
        grafDesplazamiento.getData().setAll(List.of(serie("Suelo ug(t)", ug[0], ug[1], 100), serie("Techo relativo", rel[0], rel[1], 100),
                serie("Techo absoluto", abs[0], abs[1], 100), cursorDesplazamiento));

        int critico = ResumenSimulacion.pisoCritico(actual, dir);
        double[][] dv = ResumenSimulacion.decimar(t, ResumenSimulacion.derivaPiso(actual, dir, critico), ResumenSimulacion.PUNTOS_MAXIMOS);
        extremosDeriva = ajustar(grafDeriva, dur, 0, Math.max(maximo(dv[1]), ResumenSimulacion.LIMITE_DERIVA) * 1.1, true);
        cursorDeriva = cursor("Instante", extremosDeriva);
        XYChart.Series<Number, Number> limite = serie("Límite 0,00875", new double[]{0, dur},
                new double[]{ResumenSimulacion.LIMITE_DERIVA, ResumenSimulacion.LIMITE_DERIVA}, 1);
        grafDeriva.getData().setAll(List.of(serie("Deriva |Δ/h|", dv[0], dv[1], 1), limite, cursorDeriva));
        lblTituloDeriva.setText("Deriva del entrepiso crítico (piso " + (critico + 1) + ", dirección " + (dir == 0 ? "X" : "Y") + ") y límite 0,00875");
    }

    private void actualizar(int paso) {
        if (actual == null) {
            return;
        }
        int dir = direccion();
        vista.setPaso(paso);
        double t = actual.tiempo()[paso];
        double ag = ResumenSimulacion.aceleracionSuelo(actual, dir)[paso];
        double ug = ResumenSimulacion.suelo(actual, dir)[paso] * 100;
        double techo = ResumenSimulacion.relativo(actual, dir, paso, actual.pisos() - 1) * 100;
        double deriva = ResumenSimulacion.derivaMaximaInstante(actual, dir, paso);
        lblT.setText(String.format(ES, "t = %.2f s", t));
        lblAg.setText(String.format(ES, "ag = %.3f m/s² (%.3f g)", ag, ag / EscaladoRegistros.G));
        lblUg.setText(String.format(ES, "ug = %.2f cm", ug));
        lblTecho.setText(String.format(ES, "Techo relativo = %.2f cm", techo));
        lblDeriva.setText(String.format(ES, "Deriva máx. del instante = %.5f (%.0f %% del límite)", deriva, 100 * deriva / ResumenSimulacion.LIMITE_DERIVA));
        mover(cursorAceleracion, t);
        mover(cursorDesplazamiento, t);
        mover(cursorDeriva, t);
    }

    private static void mover(XYChart.Series<Number, Number> cursor, double t) {
        if (cursor != null && cursor.getData().size() == 2) {
            cursor.getData().get(0).setXValue(t);
            cursor.getData().get(1).setXValue(t);
        }
    }

    private void pausar() {
        if (reloj != null) {
            reloj.stop();
            reloj = null;
        }
        if (btnPlay != null) {
            btnPlay.setText("Reproducir");
            iconoPlay.setIconLiteral("fth-play");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static XYChart.Series<Number, Number> serie(String nombre, double[] x, double[] y, double factorY) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(nombre);
        List<XYChart.Data<Number, Number>> datos = new ArrayList<>(x.length);
        for (int i = 0; i < x.length; i++) {
            datos.add(new XYChart.Data<>(x[i], y[i] * factorY));
        }
        s.getData().setAll(datos);
        return s;
    }

    private static XYChart.Series<Number, Number> cursor(String nombre, double[] extremos) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(nombre);
        s.getData().add(new XYChart.Data<>(0, extremos[0]));
        s.getData().add(new XYChart.Data<>(0, extremos[1]));
        return s;
    }

    /** Fija los ejes (sin autorango, para que el cursor vertical cubra todo el alto) y devuelve {mínimo, máximo} del eje Y. */
    private static double[] ajustar(LineChart<Number, Number> g, double duracion, double min, double max, boolean desdeCero) {
        NumberAxis x = (NumberAxis) g.getXAxis();
        NumberAxis y = (NumberAxis) g.getYAxis();
        x.setAutoRanging(false);
        x.setLowerBound(0);
        x.setUpperBound(duracion);
        x.setTickUnit(pasoLindo(duracion / 8));
        if (max - min < 1e-12) {
            min -= 1;
            max += 1;
        }
        double pad = (max - min) * 0.05;
        double lo = desdeCero ? 0 : min - pad, hi = max + (desdeCero ? 0 : pad);
        double tick = pasoLindo((hi - lo) / 6);
        lo = Math.floor(lo / tick) * tick;
        hi = Math.ceil(hi / tick) * tick;
        y.setAutoRanging(false);
        y.setLowerBound(lo);
        y.setUpperBound(hi);
        y.setTickUnit(tick);
        return new double[]{lo, hi};
    }

    static double pasoLindo(double aprox) {
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

    private static double maximo(double[] v) {
        double m = -Double.MAX_VALUE;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    /** Lee el dt opcional: {@code null} si está vacío, -1 si no es válido (ya marcado). */
    private Double leerDt() {
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
            Formularios.marcarError(txtDt, "Ingrese un paso de tiempo positivo (ej. 0,01).");
            notificador.error("El paso de tiempo dt debe ser un número positivo, por ejemplo 0,01 s.");
            return -1.0;
        }
    }

    private File elegir(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Acelerogramas (*.txt, *.dat, *.csv, *.at2, *.asc)", "*.txt", "*.dat", "*.csv", "*.at2", "*.asc"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        return fc.showOpenDialog(raiz.getScene() == null ? null : raiz.getScene().getWindow());
    }

    private static boolean enPantalla(Node n) {
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

    private static void mostrar(Node nodo, boolean visible) {
        if (nodo != null) {
            nodo.setVisible(visible);
            nodo.setManaged(visible);
        }
    }
}
