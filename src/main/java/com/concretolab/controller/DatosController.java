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
 * Datos y copias de seguridad: ubicación de la base y de los registros, copias manuales y restauración,
 * exportación e importación de proyectos (con cifrado opcional) e historial de cambios.
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

    @FXML private Label lblRutaBase;
    @FXML private Label lblTamanoBase;
    @FXML private Label lblCifrado;
    @FXML private Button btnCambiarClave;
    private final com.concretolab.service.ClaveBaseService erickDelgadoClaveBase;

    @FXML
    private void onCambiarClaveBase() {
        var respuesta = com.concretolab.ui.DialogoClaveBase.erickDelgadoPedirCambio();
        if (respuesta.isEmpty()) {
            return;
        }
        try {
            erickDelgadoClaveBase.erickDelgadoCambiar(respuesta.get()[0], respuesta.get()[1]);
        } catch (IllegalArgumentException e) {
            erickDelgadoNotificador.fernandoHuancaAdvertencia(e.getMessage());
            return;
        } catch (Exception e) {
            erickDelgadoNotificador.fernandoHuancaError("No se pudo cambiar la contraseña: " + e.getMessage());
            return;
        }
        var aviso = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                "La contraseña se cambió. La aplicación se cerrará ahora; vuelva a abrirla con la nueva contraseña.");
        aviso.setHeaderText("Contraseña cambiada");
        aviso.showAndWait();
        javafx.application.Platform.exit();
    }
    @FXML private TableView<RespaldoService.Copia> tablaCopias;
    @FXML private TableColumn<RespaldoService.Copia, String> colFechaCopia;
    @FXML private TableColumn<RespaldoService.Copia, String> colTipoCopia;
    @FXML private TableColumn<RespaldoService.Copia, String> colTamanoCopia;
    @FXML private TableColumn<RespaldoService.Copia, String> colNombreCopia;
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
        colNombreCopia.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
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
        boolean cifrada = erickDelgadoClaveBase.erickDelgadoEstaCifrada();
        lblCifrado.setText(cifrada ? "Cifrado: SÍ (AES-256). La contraseña se pide al abrir la aplicación."
                : "Cifrado: NO. Esta base no está cifrada (modo de pruebas o abierta con otro método).");
        btnCambiarClave.setVisible(false);
        btnCambiarClave.setManaged(false);
        lblRutaBase.setText("Base de datos: " + erickDelgadoRespaldos.erickDelgadoArchivoBaseDatos() + "\nRegistros: " + erickDelgadoRespaldos.erickDelgadoCarpetaRegistros());
        lblTamanoBase.setText("Tamaño de la base: " + erickDelgadoTamano(erickDelgadoRespaldos.erickDelgadoTamanoBaseDatos()) + " · Copias en: " + erickDelgadoRespaldos.erickDelgadoCarpetaCopias());
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
                "Se reemplazarán todos tus datos actuales por los de la copia del " + copia.fecha().format(ERICK_DELGADO_FORMATO_FECHA)
                        + ". Antes se guardará una copia de lo actual. El cambio se aplica al volver a abrir la aplicación.",
                "Restaurar", () -> {
                    try {
                        erickDelgadoRespaldos.erickDelgadoProgramarRestauracion(copia.archivo());
                        erickDelgadoNotificador.fernandoHuancaAdvertencia("Restauración programada. Cierra ConcretoLab y vuelve a abrirlo para aplicarla.");
                        refrescar();
                    } catch (IllegalArgumentException ex) {
                        erickDelgadoNotificador.fernandoHuancaError(ex.getMessage());
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
        lblSubtituloContrasena.setText("Puedes protegerlo con una contraseña");
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
