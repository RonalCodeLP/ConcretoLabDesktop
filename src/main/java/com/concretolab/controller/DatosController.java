package com.concretolab.controller;

import com.concretolab.model.Proyecto;
import com.concretolab.model.RegistroBitacora;
import com.concretolab.service.BitacoraService;
import com.concretolab.service.IntercambioProyectoService;
import com.concretolab.service.RespaldoService;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.ui.Exportador;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import com.concretolab.ui.Tablas;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Datos y copias de seguridad, pensada para el cliente: copias (crear, restaurar, eliminar) y exportación e importación
 * de proyectos (el archivo exportado puede protegerse con contraseña de forma opcional). Las rutas, los registros y el
 * historial de cambios están en «Opciones avanzadas».
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatosController implements Refrescable {

    private static final DateTimeFormatter ERICK_DELGADO_FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int ERICK_DELGADO_MINIMO_CONTRASENA = 8;

    private final RespaldoService erickDelgadoRespaldos;
    private final IntercambioProyectoService erickDelgadoIntercambio;
    private final BitacoraService bitacora;
    private final YefriLermaProyectoService proyectos;
    private final SesionProyecto erickDelgadoSesion;
    private final Notificador erickDelgadoNotificador;
    private final Exportador exportador;

    // ---- opciones avanzadas
    @FXML private Label lblRutaBase;
    @FXML private Label lblTamanoBase;

    // ---- copias
    @FXML private Label lblRestauracionPendiente;
    @FXML private TableView<RespaldoService.Copia> tablaCopias;
    @FXML private TableColumn<RespaldoService.Copia, String> colFechaCopia;
    @FXML private TableColumn<RespaldoService.Copia, String> colTipoCopia;
    @FXML private TableColumn<RespaldoService.Copia, String> colTamanoCopia;
    /** Cierra la aplicación tras programar una restauración (se sustituye en las pruebas). */
    private Runnable erickDelgadoCerrarAplicacion = javafx.application.Platform::exit;
    @FXML private Button btnRestaurar;
    @FXML private Button btnEliminarCopia;
    @FXML private ComboBox<Proyecto> cmbProyecto;
    @FXML private TableView<RegistroBitacora> tablaBitacora;
    @FXML private TableColumn<RegistroBitacora, String> colFechaBitacora;
    @FXML private TableColumn<RegistroBitacora, String> colAccionBitacora;
    @FXML private TableColumn<RegistroBitacora, String> colDetalleBitacora;

    @FXML private VBox panelContrasena;
    @FXML private Label lblTituloContrasena;
    @FXML private Label lblSubtituloContrasena;
    @FXML private Label lblCampoContrasena;
    @FXML private Label lblAvisoContrasena;
    @FXML private CheckBox chkProteger;
    @FXML private PasswordField txtContrasena;
    @FXML private PasswordField txtConfirmacion;
    @FXML private VBox cajaConfirmacion;
    @FXML private Button btnConfirmarContrasena;

    private enum Modo { EXPORTAR, IMPORTAR }

    private Modo erickDelgadoModo;
    private Proyecto erickDelgadoProyectoAExportar;
    private File erickDelgadoArchivoAImportar;
    private File erickDelgadoUltimaCarpeta = new File(System.getProperty("user.home"));

    @FXML
    public void initialize() {
        colFechaCopia.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha().format(ERICK_DELGADO_FORMATO_FECHA)));
        colTipoCopia.setCellValueFactory(c -> new SimpleStringProperty(erickDelgadoNombreTipo(c.getValue().tipo())));
        colTamanoCopia.setCellValueFactory(c -> new SimpleStringProperty(erickDelgadoTamano(c.getValue().bytes())));
        Tablas.fernandoHuancaConfigurar(tablaCopias, "Todavía no hay copias de seguridad.", copia -> { });
        btnRestaurar.disableProperty().bind(tablaCopias.getSelectionModel().selectedItemProperty().isNull());
        btnEliminarCopia.disableProperty().bind(tablaCopias.getSelectionModel().selectedItemProperty().isNull());

        colFechaBitacora.setCellValueFactory(c -> new SimpleStringProperty(erickDelgadoFormatoBitacora(c.getValue().getFecha())));
        colAccionBitacora.setCellValueFactory(c -> new SimpleStringProperty(erickDelgadoNombreAccion(c.getValue().getAccion())));
        colDetalleBitacora.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDetalle()));
        Tablas.fernandoHuancaConfigurar(tablaBitacora, "Todavía no hay cambios registrados.", registro -> { });

        cmbProyecto.setConverter(new StringConverter<>() {
            @Override public String toString(Proyecto p) { return p == null ? "" : p.getNombre(); }
            @Override public Proyecto fromString(String s) { return null; }
        });
        chkProteger.selectedProperty().addListener((obs, antes, ahora) -> erickDelgadoActualizarCamposContrasena());
        Formularios.fernandoHuancaLimpiarAlEditar(txtContrasena, txtConfirmacion);
    }

    @Override
    public void refrescar() {
        lblRutaBase.setText("Base de datos: " + erickDelgadoRespaldos.erickDelgadoArchivoBaseDatos()
                + "\nCopias de seguridad: " + erickDelgadoRespaldos.erickDelgadoCarpetaCopias()
                + "\nRegistros de la aplicación: " + erickDelgadoRespaldos.erickDelgadoCarpetaRegistros());
        lblTamanoBase.setText("Tamaño de la base de datos: " + erickDelgadoTamano(erickDelgadoRespaldos.erickDelgadoTamanoBaseDatos()));
        var pendiente = erickDelgadoRespaldos.erickDelgadoRestauracionPendiente();
        lblRestauracionPendiente.setText(pendiente.map(p -> "Restauración pendiente: la copia «" + p.getFileName()
                + "» se aplicará la próxima vez que abras ConcretoLab.").orElse(""));
        lblRestauracionPendiente.setVisible(pendiente.isPresent());
        lblRestauracionPendiente.setManaged(pendiente.isPresent());
        tablaCopias.getItems().setAll(erickDelgadoRespaldos.erickDelgadoListar());
        Proyecto activo = erickDelgadoSesion.getProyecto();
        cmbProyecto.getItems().setAll(proyectos.proyectos());
        cmbProyecto.getItems().stream().filter(p -> activo != null && p.getId().equals(activo.getId())).findFirst().ifPresent(cmbProyecto::setValue);
        tablaBitacora.getItems().setAll(bitacora.erickDelgadoRecientes());
    }

    // ------------------------------------------------------------ carpetas

    @FXML private void onAbrirCarpetaDatos() { exportador.fernandoHuancaAbrirCarpeta(erickDelgadoRespaldos.erickDelgadoArchivoBaseDatos().getParent().toFile()); }

    @FXML private void onAbrirCarpetaRegistros() { exportador.fernandoHuancaAbrirCarpeta(erickDelgadoRespaldos.erickDelgadoCarpetaRegistros().toFile()); }

    // ------------------------------------------------------------ copias

    @FXML
    private void onCrearCopia() {
        erickDelgadoEnSegundoPlano("Creando la copia de seguridad…", () -> erickDelgadoRespaldos.erickDelgadoCrearCopia("manual"),
                copia -> { erickDelgadoNotificador.exito("Copia creada: " + copia.nombre()); refrescar(); });
    }

    @FXML
    private void onRestaurar() {
        var copia = tablaCopias.getSelectionModel().getSelectedItem();
        if (copia == null) return;
        erickDelgadoNotificador.fernandoHuancaConfirmar("Restaurar copia",
                "Tus datos actuales se reemplazarán por los de la copia del " + copia.fecha().format(ERICK_DELGADO_FORMATO_FECHA)
                        + ". Antes se guarda automáticamente una copia de lo que tienes ahora.\n\n"
                        + "ConcretoLab se cerrará para aplicarla: vuelve a abrirlo y verás los datos de esa copia.",
                "Restaurar y cerrar", () -> {
                    try {
                        erickDelgadoRespaldos.erickDelgadoProgramarRestauracion(copia.archivo());
                        refrescar();
                        erickDelgadoCerrarAplicacion.run();
                    } catch (IllegalArgumentException | java.io.UncheckedIOException ex) {
                        erickDelgadoNotificador.fernandoHuancaError(erickDelgadoMensaje(ex));
                    }
                });
    }

    @FXML
    private void onEliminarCopia() {
        var copia = tablaCopias.getSelectionModel().getSelectedItem();
        if (copia == null) return;
        erickDelgadoNotificador.fernandoHuancaConfirmar("Eliminar copia", "¿Eliminar «" + copia.nombre() + "»? No se puede deshacer.", "Eliminar", () -> {
            try {
                erickDelgadoRespaldos.erickDelgadoEliminarCopia(copia.archivo());
                erickDelgadoNotificador.exito("Copia eliminada.");
                refrescar();
            } catch (IllegalArgumentException ex) {
                erickDelgadoNotificador.fernandoHuancaError(ex.getMessage());
            }
        });
    }

    // ------------------------------------------------------------ exportar / importar

    @FXML
    private void onExportar() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            erickDelgadoNotificador.fernandoHuancaAdvertencia("Elige primero el proyecto que quieres exportar.");
            return;
        }
        erickDelgadoModo = Modo.EXPORTAR;
        erickDelgadoProyectoAExportar = p;
        lblTituloContrasena.setText("Exportar «" + p.getNombre() + "»");
        lblSubtituloContrasena.setText("Se guardará como archivo .concretolab");
        erickDelgadoAbrirPanelContrasena(false);
    }

    @FXML
    private void onImportar() {
        var selector = new FileChooser();
        selector.setTitle("Importar proyecto");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proyecto de ConcretoLab", "*.concretolab"));
        if (erickDelgadoUltimaCarpeta.isDirectory()) selector.setInitialDirectory(erickDelgadoUltimaCarpeta);
        File elegido = selector.showOpenDialog(tablaCopias.getScene().getWindow());
        if (elegido == null) return;
        erickDelgadoUltimaCarpeta = elegido.getParentFile();
        erickDelgadoArchivoAImportar = elegido;
        boolean cifrado;
        try {
            cifrado = erickDelgadoIntercambio.erickDelgadoEstaCifrado(elegido.toPath());
        } catch (RuntimeException ex) {
            erickDelgadoNotificador.fernandoHuancaError(erickDelgadoMensaje(ex));
            return;
        }
        if (cifrado) {
            erickDelgadoModo = Modo.IMPORTAR;
            lblTituloContrasena.setText("Importar proyecto protegido");
            lblSubtituloContrasena.setText(elegido.getName());
            erickDelgadoAbrirPanelContrasena(true);
        } else {
            erickDelgadoImportar(elegido, null);
        }
    }

    private void erickDelgadoAbrirPanelContrasena(boolean soloIntroducir) {
        txtContrasena.clear();
        txtConfirmacion.clear();
        Formularios.fernandoHuancaLimpiarErrores(txtContrasena, txtConfirmacion);
        chkProteger.setVisible(!soloIntroducir);
        chkProteger.setManaged(!soloIntroducir);
        chkProteger.setSelected(soloIntroducir);
        lblCampoContrasena.setText(soloIntroducir ? "Contraseña del archivo" : "Contraseña");
        lblAvisoContrasena.setVisible(!soloIntroducir);
        lblAvisoContrasena.setManaged(!soloIntroducir);
        btnConfirmarContrasena.setText(soloIntroducir ? "Importar" : "Elegir destino");
        erickDelgadoActualizarCamposContrasena();
        erickDelgadoNotificador.fernandoHuancaAbrirPanel(panelContrasena);
    }

    private void erickDelgadoActualizarCamposContrasena() {
        boolean pedir = chkProteger.isSelected();
        boolean importando = erickDelgadoModo == Modo.IMPORTAR;
        txtContrasena.getParent().setVisible(pedir);
        txtContrasena.getParent().setManaged(pedir);
        cajaConfirmacion.setVisible(pedir && !importando);
        cajaConfirmacion.setManaged(pedir && !importando);
    }

    @FXML private void onCancelarContrasena() { erickDelgadoLimpiarContrasenas(); erickDelgadoNotificador.fernandoHuancaCerrarPanel(); }

    @FXML
    private void onConfirmarContrasena() {
        Formularios.fernandoHuancaLimpiarErrores(txtContrasena, txtConfirmacion);
        char[] contrasena = null;
        if (chkProteger.isSelected()) {
            String texto = txtContrasena.getText();
            if (erickDelgadoModo == Modo.EXPORTAR) {
                if (texto.length() < ERICK_DELGADO_MINIMO_CONTRASENA) {
                    Formularios.fernandoHuancaMarcarError(txtContrasena, "Usa al menos " + ERICK_DELGADO_MINIMO_CONTRASENA + " caracteres.");
                    return;
                }
                if (!texto.equals(txtConfirmacion.getText())) {
                    Formularios.fernandoHuancaMarcarError(txtConfirmacion, "Las contraseñas no coinciden.");
                    return;
                }
            } else if (texto.isEmpty()) {
                Formularios.fernandoHuancaMarcarError(txtContrasena, "Escribe la contraseña del archivo.");
                return;
            }
            contrasena = texto.toCharArray();
        }
        Modo actual = erickDelgadoModo;
        erickDelgadoLimpiarContrasenas();
        erickDelgadoNotificador.fernandoHuancaCerrarPanel();
        if (actual == Modo.EXPORTAR) erickDelgadoExportar(erickDelgadoProyectoAExportar, contrasena);
        else erickDelgadoImportar(erickDelgadoArchivoAImportar, contrasena);
    }

    private void erickDelgadoLimpiarContrasenas() {
        txtContrasena.clear();
        txtConfirmacion.clear();
    }

    private void erickDelgadoExportar(Proyecto p, char[] contrasena) {
        var selector = new FileChooser();
        selector.setTitle("Exportar proyecto");
        selector.setInitialFileName(p.getNombre().replaceAll("[\\\\/:*?\"<>|]", "_") + ".concretolab");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proyecto de ConcretoLab", "*.concretolab"));
        if (erickDelgadoUltimaCarpeta.isDirectory()) selector.setInitialDirectory(erickDelgadoUltimaCarpeta);
        File elegido = selector.showSaveDialog(tablaCopias.getScene().getWindow());
        if (elegido == null) {
            erickDelgadoBorrar(contrasena);
            return;
        }
        File destino = elegido.getName().toLowerCase(Locale.ROOT).endsWith(".concretolab") ? elegido : new File(elegido.getParentFile(), elegido.getName() + ".concretolab");
        erickDelgadoUltimaCarpeta = destino.getParentFile();
        erickDelgadoEnSegundoPlano("Exportando el proyecto…", () -> erickDelgadoIntercambio.erickDelgadoExportar(p.getId(), destino.toPath(), contrasena),
                ruta -> {
                    erickDelgadoNotificador.exito("Proyecto exportado" + (contrasena != null && contrasena.length > 0 ? " y protegido con contraseña" : "") + ": " + destino.getName());
                    refrescar();
                }, () -> erickDelgadoBorrar(contrasena));
    }

    private void erickDelgadoImportar(File archivo, char[] contrasena) {
        erickDelgadoEnSegundoPlano("Importando el proyecto…", () -> erickDelgadoIntercambio.erickDelgadoImportar(archivo.toPath(), contrasena),
                resultado -> {
                    erickDelgadoSesion.setProyecto(resultado.proyecto());
                    erickDelgadoNotificador.exito("Proyecto importado: «" + resultado.proyecto().getNombre() + "» · " + resultado.elementos() + " elementos, "
                            + resultado.mezclas() + " mezclas, " + resultado.ensayos() + " ensayos.");
                    resultado.avisos().forEach(erickDelgadoNotificador::fernandoHuancaAdvertencia);
                    refrescar();
                }, () -> erickDelgadoBorrar(contrasena));
    }

    // ------------------------------------------------------------ bitácora

    @FXML private void onActualizarBitacora() { tablaBitacora.getItems().setAll(bitacora.erickDelgadoRecientes()); }

    // ------------------------------------------------------------ utilidades

    /** Ejecuta la operación fuera del hilo de JavaFX y avisa del resultado; los errores del usuario salen como mensaje claro. */
    private <T> void erickDelgadoEnSegundoPlano(String mensajeInicio, java.util.concurrent.Callable<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        erickDelgadoEnSegundoPlano(mensajeInicio, trabajo, alTerminar, () -> { });
    }

    private <T> void erickDelgadoEnSegundoPlano(String mensajeInicio, java.util.concurrent.Callable<T> trabajo,
                                    java.util.function.Consumer<T> alTerminar, Runnable alFinalizar) {
        erickDelgadoNotificador.fernandoHuancaInfo(mensajeInicio);
        Task<T> tarea = new Task<>() {
            @Override protected T call() throws Exception { return trabajo.call(); }
        };
        tarea.setOnSucceeded(e -> { alFinalizar.run(); alTerminar.accept(tarea.getValue()); });
        tarea.setOnFailed(e -> {
            alFinalizar.run();
            Throwable causa = tarea.getException();
            log.warn("Operación de datos fallida", causa);
            erickDelgadoNotificador.fernandoHuancaError(erickDelgadoMensaje(causa));
        });
        Thread hilo = new Thread(tarea, "datos");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static String erickDelgadoMensaje(Throwable ex) {
        return ex instanceof IllegalArgumentException && ex.getMessage() != null ? ex.getMessage()
                : "No se pudo completar la operación: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    private static void erickDelgadoBorrar(char[] contrasena) {
        if (contrasena != null) Arrays.fill(contrasena, '\0');
    }

    private static String erickDelgadoTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String erickDelgadoNombreTipo(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "inicio" -> "Al abrir la aplicación";
            case "cierre" -> "Al cerrarla";
            case "manual" -> "Manual";
            case "previa-restauracion" -> "Antes de restaurar";
            case "antes-de-quitar-clave" -> "Base anterior con contraseña";
            default -> tipo;
        };
    }

    private static String erickDelgadoNombreAccion(String accion) {
        return switch (accion == null ? "" : accion) {
            case "PROYECTO_CREADO" -> "Proyecto creado";
            case "PROYECTO_ELIMINADO" -> "Proyecto eliminado";
            case "PROYECTO_EXPORTADO" -> "Proyecto exportado";
            case "PROYECTO_IMPORTADO" -> "Proyecto importado";
            case "COPIA_CREADA" -> "Copia creada";
            case "RESTAURACION_PROGRAMADA" -> "Restauración programada";
            default -> accion;
        };
    }

    private static String erickDelgadoFormatoBitacora(String iso) {
        try {
            return LocalDateTime.parse(iso).format(ERICK_DELGADO_FORMATO_FECHA);
        } catch (RuntimeException e) {
            return iso;
        }
    }
}
