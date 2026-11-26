package com.concretolab.documentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import atlantafx.base.theme.PrimerLight;
import com.concretolab.ConcretoLabApplication;
import com.concretolab.controller.GranulometriaController;
import com.concretolab.controller.MainController;
import com.concretolab.controller.MezclasController;
import com.concretolab.controller.ModeloController;
import com.concretolab.model.Proyecto;
import com.concretolab.service.ProyectoEjemploService;
import com.concretolab.service.RespaldoService;
import com.concretolab.service.SistemaEstructural;
import com.concretolab.service.TipoSuelo;
import com.concretolab.service.UsoEdificacion;
import com.concretolab.ui.SesionProyecto;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CapturasManualTest {
    private static final String SALIDA = System.getProperty("capturas.dir", "");
    private static final Path DATOS;
    static {
        try {
            DATOS = Files.createTempDirectory("concretolab-capturas-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        if (!SALIDA.isBlank()) System.setProperty("concretolab.datos", DATOS.toString());
    }

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATOS.resolve("concretolab.db").toAbsolutePath());
    }

    @Autowired ApplicationContext context;
    @Autowired MainController principal;
    @Autowired SesionProyecto sesion;
    @Autowired ProyectoEjemploService ejemplo;
    @Autowired RespaldoService respaldos;
    @Autowired com.concretolab.service.YefriLermaProyectoService proyectos;

    private Path carpeta;
    private Stage ventana;
    private final StringBuilder coordenadas = new StringBuilder();
    private final List<String> hechas = new ArrayList<>();

    @Test
    void generarCapturas() throws Exception {
        assumeTrue(!SALIDA.isBlank(), "Solo se ejecuta con -Dcapturas.dir=<carpeta>");
        try { Platform.startup(() -> { }); } catch (IllegalStateException ya) { } catch (Throwable t) { assumeTrue(false, t.toString()); }
        Platform.setImplicitExit(false);
        carpeta = Path.of(SALIDA).toAbsolutePath();
        Files.createDirectories(carpeta);
        coordenadas.append("% Generado por CapturasManualTest (mvn -o test -Dtest=CapturasManualTest -Dcapturas.dir=...). No editar a mano.\n");

        fx(() -> {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
                loader.setControllerFactory(context::getBean);
                Parent raiz = loader.load();
                Scene escena = new Scene(raiz, 1440, 900);
                escena.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                ventana = new Stage();
                ventana.setTitle("ConcretoLab Desktop");
                ventana.setScene(escena);
                ventana.show();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        esperar(1200);

        fx(principal::fernandoHuancaMostrarBienvenida);
        esperar(900);
        capturar("01-bienvenida", Map.of(
                "BienvenidaNoMostrar", "#bienvenidaNoMostrar", "BienvenidaSiguiente", "#bienvenidaSiguiente",
                "BienvenidaCerrar", "#bienvenidaCerrar", "BienvenidaPasos", "#bienvenidaPasos"));
        fx(() -> boton("#bienvenidaCerrar").fire());
        esperar(700);
        capturar("02-inicio", mapa("InicioEjemplo", "#btnAgregarEjemplo", "BarraMenu", "#barraMenu", "Pestanas", ".tab-header-area",
                "AyudaPantalla", ".boton-ayuda-pantalla", "BarraEstado", ".barra-estado", "Mensaje", "#lblMensaje",
                "ProyectoActivo", "#lblProyectoActivo"));

        fx(() -> boton("#btnAgregarEjemplo").fire());
        esperar(900);
        capturar("02b-ejemplo-agregado", mapa("Aviso", "#lblMensaje", "ModeloProyecto", "#cmbProyecto"));
        Proyecto proyecto = sesion.getProyecto();
        assertThat(proyecto).isNotNull();
        capturar("03-modelo", mapa(
                "ModeloProyecto", "#cmbProyecto", "ModeloNuevoProyecto", "#btnNuevoProyecto", "ModeloEliminarProyecto", "#btnEliminarProyecto",
                "ModeloFiltro", "#cmbFiltroTipo", "ModeloTodos", "#btnSeleccionarTodo", "ModeloTabla", "#tabla", "ModeloVisor", "#contenedorVisor",
                "ModeloNuevoElemento", "#btnNuevoElemento", "ModeloEditar", "#btnEditar", "ModeloEliminar", "#btnEliminar",
                "ModeloPlantilla", "#btnPlantilla", "ModeloCuadricula", "#btnCuadricula", "ModeloMover", "#btnMover",
                "ModeloDeshacer", "#btnDeshacer", "ModeloMetrado", "#btnMetrado", "ModeloExportar", "#btnExportarDxf",
                "ModeloSeleccion", "#lblSeleccion"));

        ModeloController modelo = context.getBean(ModeloController.class);
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<String> filtro = (ComboBox<String>) nodo("#cmbFiltroTipo");
            filtro.getItems().stream().filter(t -> t.toUpperCase(Locale.ROOT).startsWith("COLUMNA")).findFirst().ifPresent(filtro::setValue);
        });
        esperar(500);
        fx(() -> boton("#btnSeleccionarTodo").fire());
        esperar(500);
        capturar("04-modelo-filtro", mapa("ModeloFiltro", "#cmbFiltroTipo", "ModeloTodos", "#btnSeleccionarTodo", "ModeloTabla", "#tabla",
                "ModeloSeleccion", "#lblSeleccion", "ModeloEditar", "#btnEditar"));
        fx(() -> invocar(modelo, "onEditarElemento"));
        esperar(900);
        capturar("05-modelo-editar-varios", mapa("LoteLargo", "#txtLoteLargo", "LoteAncho", "#txtLoteAncho", "LoteAltura", "#txtLoteAltura",
                "LoteAplicar", "#btnAplicarLote", "LoteTitulo", "#lblTituloLote", "LoteDetalle", "#lblDetalleLote"));
        fx(() -> invocar(modelo, "onCerrarPanel"));
        esperar(600);
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<String> filtro = (ComboBox<String>) nodo("#cmbFiltroTipo");
            filtro.setValue(filtro.getItems().get(0));
            @SuppressWarnings("unchecked") TableView<Object> tabla = (TableView<Object>) nodo("#tabla");
            tabla.getSelectionModel().clearSelection();
            tabla.getSelectionModel().select(9);
        });
        esperar(400);
        fx(() -> invocar(modelo, "onEditarElemento"));
        esperar(900);
        capturar("06-modelo-editar-uno", mapa("ElementoTipo", "#cmbTipo", "ElementoNombre", "#txtNombreElemento", "ElementoLargo", "#txtLargo",
                "ElementoX", "#txtX", "ElementoMover", "#btnMoverDesdeEditar"));
        fx(() -> invocar(modelo, "onCerrarPanel"));
        esperar(600);
        fx(() -> invocar(modelo, "onNuevoProyecto"));
        esperar(900);
        fx(() -> ((TextField) nodo("#txtNombreProyecto")).setText("Edificio de oficinas"));
        capturar("06b-modelo-nuevo-proyecto", mapa("ProyectoNombre", "#txtNombreProyecto", "ProyectoUbicacion", "#txtUbicacionProyecto",
                "ProyectoCrear", "texto:Crear"));
        fx(() -> invocar(modelo, "onCerrarPanel"));
        esperar(600);
        fx(() -> invocar(modelo, "onNuevoElemento"));
        esperar(900);
        capturar("06c-modelo-nuevo-elemento", mapa("ElementoTipo", "#cmbTipo", "ElementoNombre", "#txtNombreElemento",
                "ElementoLargo", "#txtLargo", "ElementoAncho", "#txtAncho", "ElementoAltura", "#txtAltura", "ElementoX", "#txtX",
                "ElementoGuardar", "texto:Guardar"));
        fx(() -> invocar(modelo, "onCerrarPanel"));
        esperar(600);
        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Object> tabla = (TableView<Object>) nodo("#tabla");
            tabla.getSelectionModel().clearSelection();
            for (int i = 0; i < tabla.getItems().size(); i++) {
                if (tabla.getItems().get(i) instanceof com.concretolab.model.Elemento el && "C-1-1-1".equals(el.getNombre())) {
                    tabla.getSelectionModel().select(i);
                    break;
                }
            }
        });
        esperar(400);
        fx(() -> invocar(modelo, "onMoverElemento"));
        esperar(1200);
        capturar("53-modelo-mover", mapa("Movimiento", "#panelMovimiento", "ModeloVisor", "#contenedorVisor"));
        fx(() -> principal.fernandoHuancaIrA("inicio-view"));
        esperar(500);
        fx(() -> principal.fernandoHuancaIrA("modelo-view"));
        esperar(1000);


        fx(() -> principal.fernandoHuancaIrA("sismo-view"));
        esperar(1200);
        capturar("07-sismo-vacio", mapa("SismoProyecto", "#cmbProyecto", "SismoBuscar", "#txtBuscarDistrito", "SismoDepartamento", "#cmbDepartamento",
                "SismoUso", "#cmbUso", "SismoSuelo", "#cmbTipoSuelo", "SismoSistema", "#cmbSistema", "SismoCalcular", "#btnCalcular",
                "SismoFalta", "#lblFalta"));
        fx(() -> elegirTexto("#cmbDepartamento", "lima"));
        esperar(500);
        fx(() -> elegirTexto("#cmbProvincia", "lima"));
        esperar(500);
        fx(() -> elegirTexto("#cmbDistrito", "miraflores"));
        esperar(400);
        fx(() -> {
            elegir("#cmbUso", UsoEdificacion.VIVIENDA);
            elegir("#cmbTipoSuelo", TipoSuelo.RIGIDO);
            elegir("#cmbSistema", SistemaEstructural.PORTICOS);
        });
        esperar(900);
        capturar("08-sismo-asistente", mapa("SismoProyecto", "#cmbProyecto", "SismoBuscar", "#txtBuscarDistrito", "SismoZona", "#lblZonaChip",
                "SismoUso", "#cmbUso", "SismoSuelo", "#cmbTipoSuelo", "SismoSistema", "#cmbSistema", "SismoAvanzado", "#panelAvanzado",
                "SismoPisos", "#tablaPisos", "SismoCalcular", "#btnCalcular"));
        fx(() -> desplazarHasta("#cmbSistema", 1));
        esperar(600);
        capturar("08b-sismo-pasos", mapa("SismoSuelo", "#cmbTipoSuelo", "SismoSistema", "#cmbSistema", "SismoAvanzado", "#panelAvanzado",
                "SismoPisos", "#tablaPisos", "SismoCalcular", "#btnCalcular"));
        fx(() -> ((TitledPane) nodo("#panelAvanzado")).setExpanded(true));
        esperar(700);
        fx(() -> desplazarHasta("#panelAvanzado", 0.55));
        esperar(500);
        capturar("09-sismo-avanzado", mapa("SismoAvanzado", "#panelAvanzado", "SismoVersion", "#cmbVersion", "SismoVs30", "#txtVs30",
                "SismoFc", "#txtFc", "SismoRigidez", "#txtRigidez", "SismoEspesor", "#txtEspesor", "SismoCt", "#txtCt"));
        fx(() -> ((TitledPane) nodo("#panelAvanzado")).setExpanded(false));
        fx(() -> desplazarHasta("#cmbDepartamento", 0));
        esperar(500);
        fx(() -> boton("#btnCalcular").fire());
        esperarHasta(() -> {
            Node estado = nodo("#lblEstado");
            return estado instanceof Labeled l && l.getText().startsWith("Análisis calculado");
        }, 60);
        esperar(1500);
        capturar("10-sismo-resultado", mapa("SismoEstado", "#lblEstado", "SismoPestanas", "#tabsResultados", "SismoExportar", "#btnExportar",
                "SismoCalcular", "#btnCalcular"));
        TabPane pestanas = fxValor(() -> (TabPane) nodo("#tabsResultados"));
        int numPestanas = fxValor(() -> pestanas.getTabs().size());
        for (int i = 0; i < numPestanas; i++) {
            int k = i;
            String nombre = fxValor(() -> pestanas.getTabs().get(k).getText());
            fx(() -> pestanas.getSelectionModel().select(k));
            esperar(1200);
            capturar(String.format("11-sismo-pestana-%02d-%s", i + 1, clave(nombre).toLowerCase(Locale.ROOT)),
                    mapa("SismoPestanas", "#tabsResultados"));
        }
        fx(() -> pestanas.getSelectionModel().select(0));
        fx(() -> {
            TabPane tabs = (TabPane) nodo("#tabsResultados");
            tabs.getTabs().stream().filter(tb -> tb.getText().startsWith("Simulación temporal")).findFirst()
                    .ifPresent(tb -> tabs.getSelectionModel().select(tb));
        });
        esperar(1000);
        capturar("60-temporal-inicio", mapa("TemporalSemilla", "#txtSemilla", "TemporalGenerar", "#btnGenerar",
                "TemporalImportar", "#btnImportar", "TemporalSimularImportados", "#btnSimularImportados"));
        fx(() -> boton("#btnGenerar").fire());
        esperarHasta(() -> nodo("#panelResultados").isVisible() && nodo("#lblBanner") instanceof Labeled l && !l.getText().isBlank(), 240);
        esperar(2500);
        capturar("61-temporal-resultado", mapa("TemporalBanner", "#lblBanner", "TemporalPestanas", "#tabsTH", "TemporalPlay", "#btnPlay",
                "TemporalVelocidad", "#cmbVelocidad", "TemporalEscala", "#sldEscala"));
        int nTH = fxValor(() -> ((TabPane) nodo("#tabsTH")).getTabs().size());
        for (int i = 1; i < nTH; i++) {
            int k = i;
            fx(() -> ((TabPane) nodo("#tabsTH")).getSelectionModel().select(k));
            esperar(1200);
            capturar(String.format("62-temporal-pestana-%d", i), mapa("TemporalPestanas", "#tabsTH"));
        }

        // Mezclas: registrar a mano y elegir elementos del modelo
        fx(() -> pestanas.getSelectionModel().select(0));
        esperar(800);

        fx(() -> ((TextField) nodo("#txtRigidez")).setText("0.7"));
        recalcular();
        capturar("12-sismo-no-cumple", mapa("SismoCorregir", "texto:Cómo corregir", "SismoPestanas", "#tabsResultados"));
        Platform.runLater(() -> ((Button) porTexto(ventana.getScene().getRoot(), "Cómo corregir")).fire());
        Stage dialogo = esperarVentana("Cómo corregir", 90);
        esperar(800);
        capturarEscena("12b-sismo-como-corregir", fxValor(dialogo::getScene), mapa("IrModelo", "texto:Ir al modelo 3D y precargar el cambio"));
        fx(() -> ((Button) porTexto(dialogo.getScene().getRoot(), "Ir al modelo 3D y precargar el cambio")).fire());
        esperar(1800);
        capturar("12c-modelo-correccion", mapa("LoteLargo", "#txtLoteLargo", "LoteAncho", "#txtLoteAncho", "LoteAplicar", "#btnAplicarLote",
                "LoteDetalle", "#lblDetalleLote", "ModeloSeleccion", "#lblSeleccion"));
        fx(() -> invocar(modelo, "onCerrarPanel"));
        esperar(600);

        var viga = proyectos.elementos(proyecto.getId()).stream().filter(e -> "VX-1-1-1".equals(e.getNombre())).findFirst().orElseThrow();
        double largoOriginal = viga.getLargo();
        viga.setLargo(2.0);
        proyectos.yefriLermaGuardar(viga);
        fx(() -> principal.fernandoHuancaIrA("sismo-view"));
        esperar(1000);
        fx(() -> ((TextField) nodo("#txtRigidez")).setText("1.0"));
        recalcular();
        capturar("12d-sismo-incompleto", mapa("SismoVerModelo", "texto:Ver en el modelo 3D", "SismoPestanas", "#tabsResultados"));
        fx(() -> ((Button) porTexto(ventana.getScene().getRoot(), "Ver en el modelo 3D")).fire());
        esperar(1800);
        capturar("12e-modelo-vigas-sueltas", mapa("ModeloSeleccion", "#lblSeleccion", "ModeloVisor", "#contenedorVisor", "ModeloTabla", "#tabla"));
        viga.setLargo(largoOriginal);
        proyectos.yefriLermaGuardar(viga);

        fx(() -> principal.fernandoHuancaIrA("materiales-view"));
        esperar(1100);
        capturar("20-materiales", mapa("MaterialesTabla", "#tabla", "MaterialesDetalle", "#lblDetalle", "MaterialesPaginador", "#paginador"));
        fx(() -> invocar(context.getBean(com.concretolab.controller.MaterialesController.class), "onNuevo"));
        esperar(900);
        capturar("21-materiales-nuevo", mapa("MaterialTipo", "#cmbTipo", "MaterialNombre", "#txtNombre", "MaterialDensidad", "#txtDensidadRelativa",
                "MaterialModuloFinura", "#txtModuloFinura", "MaterialTamano", "#txtTamanoMaximo"));
        fx(() -> invocar(context.getBean(com.concretolab.controller.MaterialesController.class), "onCerrarFormulario"));
        esperar(600);

        fx(() -> principal.fernandoHuancaIrA("granulometria-view"));
        esperar(1100);
        fx(() -> elegirTexto("#cmbMaterial", "ejemplo"));
        esperar(700);
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<Object> historial = (ComboBox<Object>) nodo("#cmbHistorial");
            if (!historial.getItems().isEmpty()) historial.setValue(historial.getItems().get(0));
        });
        esperar(700);
        fx(() -> invocar(context.getBean(GranulometriaController.class), "onCalcular"));
        esperar(1200);
        capturar("22-granulometria", mapa("GranMaterial", "#cmbMaterial", "GranHistorial", "#cmbHistorial", "GranIdentificacion", "#txtIdentificacion",
                "GranMasa", "#txtMasa", "GranFondo", "#txtFondo", "GranMediciones", "#txtMediciones", "GranCalcular", "#btnCalcular",
                "GranGuardar", "#btnGuardar", "GranResumen", "#lblResumen", "GranTabla", "#tabla", "GranGrafico", "#grafico"));

        fx(() -> principal.fernandoHuancaIrA("balance-view"));
        esperar(1100);
        fx(() -> invocar(context.getBean(com.concretolab.controller.BalanceController.class), "onCalcular"));
        esperar(800);
        capturar("23-balance", mapa("BalanceAgua", "#txtAgua", "BalanceAc", "#txtRelacionAc", "BalanceGrueso", "#txtGrueso",
                "BalanceAire", "#txtAire", "BalanceResultado", "#lblResultado", "BalanceUsar", "#btnUsarEnMezcla"));

        fx(() -> principal.fernandoHuancaIrA("mezclas-view"));
        esperar(1100);
        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Object> mezclas = (TableView<Object>) nodo("#tablaMezclas");
            if (!mezclas.getItems().isEmpty()) mezclas.getSelectionModel().select(0);
        });
        esperar(700);
        fx(() -> invocar(context.getBean(MezclasController.class), "onCalcular"));
        esperar(900);
        capturar("24-mezclas", mapa("MezclasProyecto", "#lblProyecto", "MezclasTabla", "#tablaMezclas", "MezclasVolumen", "#txtVolumen",
                "MezclasCalcular", "#btnCalcular", "MezclasElementos", "#btnElementos", "MezclasPdf", "#btnPdf", "MezclasResultado", "#lblResultado",
                "MezclasEnsayo", "#btnEnsayo", "MezclasEnsayos", "#tablaEnsayos"));
        fx(() -> invocar(context.getBean(MezclasController.class), "onDisenar"));
        esperar(900);
        capturar("25-mezclas-disenar", mapa("DisenoFc", "#txtFcDiseno", "DisenoAsentamiento", "#txtAsentDiseno", "DisenoDesviacion", "#txtDesviacion",
                "DisenoExposicion", "#cmbExposicion", "DisenoAire", "#chkAire", "DisenoFino", "#cmbFinoDiseno", "DisenoGrueso", "#cmbGruesoDiseno",
                "DisenoCemento", "#cmbCementoDiseno", "DisenoResultado", "#lblResultadoDiseno", "DisenoUsar", "#btnUsarDiseno"));
        fx(() -> invocar(context.getBean(MezclasController.class), "onCerrarPanel"));
        esperar(600);
        fx(() -> invocar(context.getBean(MezclasController.class), "onNuevoEnsayo"));
        esperar(900);
        capturar("26-mezclas-probeta", mapa("ProbetaId", "#txtIdentificacion", "ProbetaFecha", "#txtFecha", "ProbetaEdad", "#txtEdad",
                "ProbetaResistencia", "#txtResistenciaEnsayo", "ProbetaAsentamiento", "#txtAsentamiento"));
        fx(() -> invocar(context.getBean(MezclasController.class), "onCerrarPanel"));
        esperar(600);

        respaldos.erickDelgadoCrearCopia("manual");
        fx(() -> principal.fernandoHuancaIrA("datos-view"));
        esperar(1300);
        capturar("30-datos", mapa("DatosCrearCopia", "#btnCrearCopia", "DatosCopias", "#tablaCopias", "DatosRestaurar", "#btnRestaurar",
                "DatosEliminarCopia", "#btnEliminarCopia", "DatosProyecto", "#cmbProyecto", "DatosAvanzado", "#panelAvanzado"));
        fx(() -> ((TitledPane) nodo("#panelAvanzado")).setExpanded(true));
        esperar(800);
        capturar("31-datos-avanzado", mapa("DatosAvanzado", "#panelAvanzado", "DatosRuta", "#lblRutaBase", "DatosBitacora", "#tablaBitacora"));

        fx(() -> principal.fernandoHuancaIrA("modelo-view"));
        esperar(900);
        fx(principal::fernandoHuancaAyudaDeEstaPantalla);
        esperar(7000);
        Window ayuda = fxValor(() -> Window.getWindows().stream().filter(w -> w != ventana && w instanceof Stage s
                && s.getTitle() != null && s.getTitle().startsWith("Ayuda")).findFirst().orElse(null));
        if (ayuda != null) {
            capturarEscena("40-ayuda", ayuda.getScene(), mapa("AyudaIndice", "#ayudaIndice", "AyudaPagina", "#ayudaPagina", "AyudaBuscar", "#ayudaBuscar"));
            fx(() -> ((Stage) ayuda).close());
        }

        // 7. Funciones adicionales para el manual completo
        ModeloController modeloExtra = context.getBean(ModeloController.class);
        fx(() -> { Labeled m = (Labeled) nodo("#lblMensaje"); m.setText("Listo"); m.setGraphic(null); });
        fx(() -> principal.fernandoHuancaIrA("modelo-view"));
        esperar(1200);
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<String> filtro = (ComboBox<String>) nodo("#cmbFiltroTipo");
            filtro.setValue(filtro.getItems().get(0));
        });
        esperar(500);
        fx(() -> ((javafx.scene.control.MenuButton) nodo("#btnNuevoProyecto")).show());
        esperar(700);
        capturarPantalla("50-modelo-menu-proyecto", mapa("ModeloNuevoProyecto", "#btnNuevoProyecto"));
        fx(() -> ((javafx.scene.control.MenuButton) nodo("#btnNuevoProyecto")).hide());
        esperar(400);

        fx(() -> invocar(modeloExtra, "onPlantilla"));
        esperar(900);
        capturar("51-modelo-plantilla", mapa("PlantillaSeparacionX", "#txtSeparacionX", "PlantillaLadoColumna", "#txtLadoColumna",
                "PlantillaAnchoViga", "#txtAnchoViga", "PlantillaCrear", "texto:Crear"));
        fx(() -> invocar(modeloExtra, "onCerrarPanel"));
        esperar(600);

        capturarDialogo("52-modelo-ejes", () -> invocar(modeloExtra, "onCuadricula"), mapa());

        fx(() -> invocar(modeloExtra, "onVerMetrado"));
        esperar(1000);
        capturarPantalla("54-modelo-metrado", mapa("ModeloMetrado", "#btnMetrado"));
        fx(() -> Window.getWindows().stream().filter(w -> w instanceof javafx.stage.PopupWindow).toList().forEach(Window::hide));
        esperar(400);

        capturarDialogo("55-modelo-cuantias", () -> invocar(modeloExtra, "onEditarCuantias"), mapa());
        capturarDialogo("56-modelo-etabs", () -> invocar(modeloExtra, "onExportarEtabs"), mapa());

        java.io.File dxf = Path.of("doc", "planta-estructural-ejemplo.dxf").toAbsolutePath().toFile();
        if (dxf.isFile()) {
            capturarDialogo("57-modelo-importar-dxf", () -> new com.concretolab.ui.ErickDelgadoImportacionDxf(
                    context.getBean(com.concretolab.service.FernandoHuancaPlanoDxf.class), proyectos)
                    .erickDelgadoAbrirArchivo(ventana, null, dxf), mapa());
        }

        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Object> tabla = (TableView<Object>) nodo("#tabla");
            tabla.getSelectionModel().clearSelection();
            tabla.getSelectionModel().select(2);
        });
        esperar(300);
        fx(() -> invocar(modeloExtra, "onEliminarElemento"));
        esperar(900);
        capturar("58-modelo-eliminar", mapa("Cancelar", "texto:Cancelar"));
        fx(() -> { Node c = porTexto(ventana.getScene().getRoot(), "Cancelar"); if (c instanceof Button b) b.fire(); });
        esperar(500);
        capturar("59-modelo-visor", mapa("VisorBarra", ".visor-barra", "ModeloVisor", "#contenedorVisor"));

        // Simulación temporal (tiempo-historia)
        MezclasController mezclasExtra = context.getBean(MezclasController.class);
        fx(() -> principal.fernandoHuancaIrA("mezclas-view"));
        esperar(1000);
        fx(() -> invocar(mezclasExtra, "onNuevaMezcla"));
        esperar(900);
        capturar("63-mezclas-nueva", mapa("MezclaNombre", "#txtNombre", "MezclaResistencia", "#txtResistencia", "MezclaCemento", "#txtCemento",
                "MezclaFino", "#cmbFino", "MezclaGrueso", "#cmbGrueso"));
        fx(() -> invocar(mezclasExtra, "onCerrarPanel"));
        esperar(500);
        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Object> m = (TableView<Object>) nodo("#tablaMezclas");
            if (!m.getItems().isEmpty()) m.getSelectionModel().select(0);
        });
        esperar(400);
        fx(() -> invocar(mezclasExtra, "onElegirElementos"));
        esperar(900);
        capturar("64-mezclas-elementos", mapa("MezclaElementos", "#lstElementos"));
        fx(() -> invocar(mezclasExtra, "onCerrarPanel"));
        esperar(500);

        // Datos: exportar proyecto y eliminar copia
        com.concretolab.controller.DatosController datosExtra = context.getBean(com.concretolab.controller.DatosController.class);
        fx(() -> principal.fernandoHuancaIrA("datos-view"));
        esperar(1000);
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<Object> c = (ComboBox<Object>) nodo("#cmbProyecto");
            if (!c.getItems().isEmpty()) c.setValue(c.getItems().get(0));
        });
        esperar(300);
        capturar("65-datos-exportar-importar", mapa("DatosProyecto", "#cmbProyecto", "DatosExportar", "texto:Exportar…",
                "DatosImportar", "texto:Importar…"));
        fx(() -> invocar(datosExtra, "onExportar"));
        esperar(900);
        capturar("66-datos-exportar-clave", mapa("DatosProteger", "#chkProteger", "DatosContrasena", "#txtContrasena",
                "DatosConfirmar", "#btnConfirmarContrasena"));
        fx(() -> invocar(datosExtra, "onCancelarContrasena"));
        esperar(500);
        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Object> c = (TableView<Object>) nodo("#tablaCopias");
            if (!c.getItems().isEmpty()) c.getSelectionModel().select(0);
        });
        esperar(300);
        fx(() -> invocar(datosExtra, "onEliminarCopia"));
        esperar(900);
        capturar("67-datos-eliminar-copia", mapa("Cancelar", "texto:Cancelar"));
        fx(() -> { Node c = porTexto(ventana.getScene().getRoot(), "Cancelar"); if (c instanceof Button b) b.fire(); });
        esperar(500);

        // General: menú Ayuda, Acerca de y modo oscuro
        fx(() -> principal.fernandoHuancaIrA("inicio-view"));
        esperar(800);
        fx(() -> ((javafx.scene.control.MenuBar) nodo("#barraMenu")).getMenus().get(5).show());
        esperar(700);
        capturarPantalla("68-menu-ayuda", mapa("BarraMenu", "#barraMenu"));
        fx(() -> ((javafx.scene.control.MenuBar) nodo("#barraMenu")).getMenus().get(5).hide());
        esperar(400);
        capturarDialogo("69-acerca-de", () -> invocar(principal, "onAcercaDe"), mapa());
        fx(() -> { ((javafx.scene.control.CheckMenuItem) ((javafx.scene.control.MenuBar) nodo("#barraMenu")).getMenus().get(1)
                .getItems().get(2)).setSelected(true); invocar(principal, "onCambiarTema"); });
        esperar(900);
        capturar("70-modo-oscuro", mapa());
        fx(() -> { ((javafx.scene.control.CheckMenuItem) ((javafx.scene.control.MenuBar) nodo("#barraMenu")).getMenus().get(1)
                .getItems().get(2)).setSelected(false); invocar(principal, "onCambiarTema"); });
        esperar(600);

        fx(() -> ventana.close());
        Files.writeString(carpeta.resolve("coordenadas.tex"), coordenadas.toString(), StandardCharsets.UTF_8);
        System.out.println("Capturas generadas en " + carpeta + ": " + String.join(", ", hechas));
    }

    private void capturar(String nombre, Map<String, String> controles) throws Exception {
        capturarEscena(nombre, fxValor(() -> ventana.getScene()), controles);
    }

    private void capturarEscena(String nombre, Scene escena, Map<String, String> controles) throws Exception {
        capturarImagen(nombre, escena, fxValor(() -> escena.snapshot(null)), controles);
    }

    /** Captura de la pantalla real (incluye menús desplegables y globos, que no están en la escena). */
    private void capturarPantalla(String nombre, Map<String, String> controles) throws Exception {
        Scene escena = fxValor(() -> ventana.getScene());
        WritableImage imagen = fxValor(() -> {
            WritableImage base = escena.snapshot(null);
            double ox = ventana.getX() + escena.getX(), oy = ventana.getY() + escena.getY();
            for (Window w : Window.getWindows()) {
                if (!(w instanceof javafx.stage.PopupWindow) || !w.isShowing() || w.getScene() == null) continue;
                WritableImage capa = w.getScene().snapshot(null);
                int dx = (int) Math.round(w.getX() + w.getScene().getX() - ox), dy = (int) Math.round(w.getY() + w.getScene().getY() - oy);
                var lector = capa.getPixelReader();
                var escritor = base.getPixelWriter();
                for (int y = 0; y < (int) capa.getHeight(); y++) {
                    for (int x = 0; x < (int) capa.getWidth(); x++) {
                        int px = dx + x, py = dy + y;
                        if (px < 0 || py < 0 || px >= base.getWidth() || py >= base.getHeight()) continue;
                        int argb = lector.getArgb(x, y);
                        if ((argb >>> 24) > 200) escritor.setArgb(px, py, argb);
                    }
                }
            }
            return base;
        });
        capturarImagen(nombre, escena, imagen, controles);
    }

    private void capturarImagen(String nombre, Scene escena, WritableImage imagen, Map<String, String> controles) throws Exception {
        int ancho = (int) imagen.getWidth(), alto = (int) imagen.getHeight();
        int[] pixeles = new int[ancho * alto];
        imagen.getPixelReader().getPixels(0, 0, ancho, alto, PixelFormat.getIntArgbInstance(), pixeles, 0, ancho);
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        salida.setRGB(0, 0, ancho, alto, pixeles, 0, ancho);
        ImageIO.write(salida, "png", carpeta.resolve(nombre + ".png").toFile());
        hechas.add(nombre);
        coordenadas.append("% ").append(nombre).append(".png\n");
        for (var e : controles.entrySet()) {
            Bounds b = fxValor(() -> {
                Node n = e.getValue().startsWith("texto:") ? porTexto(escena.getRoot(), e.getValue().substring(6))
                        : escena.getRoot().lookup(e.getValue());
                return n == null || !n.isVisible() || n.getScene() == null ? null : n.localToScene(n.getBoundsInLocal());
            });
            if (b == null) {
                coordenadas.append("%   (no visible: ").append(e.getKey()).append(")\n");
                continue;
            }
            String k = clave(nombre) + e.getKey();
            double izq = b.getMinX() / ancho, der = b.getMaxX() / ancho;
            double arr = 1 - b.getMinY() / alto, aba = 1 - b.getMaxY() / alto;
            coordenadas.append(String.format(Locale.ROOT,
                    "\\def\\capizq%1$s{%2$.3f}\\def\\capder%1$s{%3$.3f}\\def\\capaba%1$s{%4$.3f}\\def\\caparr%1$s{%5$.3f}\\def\\capx%1$s{%6$.3f}\\def\\capy%1$s{%7$.3f}%n",
                    k, clamp(izq), clamp(der), clamp(aba), clamp(arr), clamp((izq + der) / 2), clamp((aba + arr) / 2)));
        }
    }

    private static String clave(String texto) {
        String sin = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder();
        boolean mayus = true;
        for (char c : sin.toCharArray()) {
            if (Character.isLetter(c) && c < 128) {
                sb.append(mayus ? Character.toUpperCase(c) : c);
                mayus = false;
            } else {
                mayus = true;
            }
        }
        return sb.toString();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void recalcular() throws Exception {
        fx(() -> boton("#btnCalcular").fire());
        esperarHasta(() -> nodo("#lblEstado") instanceof Labeled l && l.getText().startsWith("Análisis calculado"), 60);
        esperar(1500);
        fx(() -> ((TabPane) nodo("#tabsResultados")).getSelectionModel().select(0));
        esperar(800);
    }

    private void desplazarHasta(String selector, double fraccion) {
        for (Node n = nodo(selector); n != null; n = n.getParent()) {
            if (n instanceof ScrollPane sp) { sp.setVvalue(fraccion); return; }
        }
    }

    private static Node porTexto(Node raiz, String texto) {
        if (raiz instanceof Labeled l && texto.equals(l.getText()) && raiz.isVisible()) return raiz;
        if (raiz instanceof Parent p) {
            for (Node hijo : p.getChildrenUnmodifiable()) {
                Node r = porTexto(hijo, texto);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Abre un diálogo modal sin esperar, captura la ventana nueva y la cierra. */
    private void capturarDialogo(String nombre, Runnable abrir, Map<String, String> controles) throws Exception {
        java.util.Set<Window> antes = new java.util.HashSet<>(fxValor(() -> new ArrayList<>(Window.getWindows())));
        Platform.runLater(abrir);
        Window nueva = null;
        long limite = System.currentTimeMillis() + 20000;
        while (nueva == null && System.currentTimeMillis() < limite) {
            Thread.sleep(300);
            nueva = fxValor(() -> Window.getWindows().stream().filter(w -> !antes.contains(w) && w instanceof Stage && w.isShowing())
                    .findFirst().orElse(null));
        }
        if (nueva == null) {
            System.out.println("Sin ventana para " + nombre);
            return;
        }
        esperar(900);
        Window w = nueva;
        capturarEscena(nombre, fxValor(w::getScene), controles);
        fx(() -> ((Stage) w).close());
        esperar(600);
    }

    private Stage esperarVentana(String inicioTitulo, int segundos) throws Exception {
        long limite = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            Stage s = fxValor(() -> (Stage) Window.getWindows().stream().filter(w -> w != ventana && w instanceof Stage st
                    && st.getTitle() != null && st.getTitle().startsWith(inicioTitulo)).findFirst().orElse(null));
            if (s != null) return s;
            Thread.sleep(300);
        }
        throw new IllegalStateException("No se abrió la ventana «" + inicioTitulo + "»");
    }

    private static Map<String, String> mapa(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) m.put(pares[i], pares[i + 1]);
        return m;
    }

    private Node nodo(String selector) {
        Node n = ventana.getScene().getRoot().lookup(selector);
        if (n == null) throw new IllegalStateException("No se encontró " + selector);
        return n;
    }

    private Button boton(String selector) {
        return (Button) nodo(selector);
    }

    @SuppressWarnings("unchecked")
    private <T> void elegir(String selector, T valor) {
        ((ComboBox<T>) nodo(selector)).setValue(valor);
    }

    @SuppressWarnings("unchecked")
    private void elegirTexto(String selector, String buscado) {
        ComboBox<Object> combo = (ComboBox<Object>) nodo(selector);
        String b = normal(buscado);
        combo.getItems().stream().filter(x -> normal(String.valueOf(x)).equals(b)).findFirst()
                .or(() -> combo.getItems().stream().filter(x -> normal(String.valueOf(x)).contains(b)).findFirst())
                .ifPresent(combo::setValue);
    }

    private static String normal(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).strip();
    }

    private static void invocar(Object controlador, String metodo) {
        try {
            Method m = controlador.getClass().getDeclaredMethod(metodo);
            m.setAccessible(true);
            m.invoke(controlador);
        } catch (NoSuchMethodException e) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(metodo, e);
        }
    }

    private static void esperar(long ms) throws Exception {
        Thread.sleep(ms);
        fx(() -> { });
    }

    private static void esperarHasta(Supplier<Boolean> condicion, int segundos) throws Exception {
        long limite = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            if (Boolean.TRUE.equals(fxValor(condicion))) return;
            Thread.sleep(300);
        }
    }

    private static <T> T fxValor(Supplier<T> s) throws Exception {
        CompletableFuture<T> r = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { r.complete(s.get()); } catch (Throwable t) { r.completeExceptionally(t); }
        });
        return r.get(60, TimeUnit.SECONDS);
    }

    private static void fx(Runnable tarea) throws Exception {
        fxValor(() -> { tarea.run(); return null; });
    }
}
