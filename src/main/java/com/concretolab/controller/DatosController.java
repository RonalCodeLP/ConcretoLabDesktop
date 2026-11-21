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

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MINIMO_CONTRASENA = 8;

    private final RespaldoService respaldos;
    private final IntercambioProyectoService intercambio;
    private final BitacoraService bitacora;
    private final YefriLermaProyectoService proyectos;
    private final SesionProyecto sesion;
    private final Notificador notificador;
    private final Exportador exportador;

    @FXML private Label lblRutaBase;
    @FXML private Label lblTamanoBase;
    @FXML private Label lblCifrado;
    @FXML private Button btnCambiarClave;
    private final com.concretolab.service.ClaveBaseService claveBase;

    @FXML
    private void onCambiarClaveBase() {
        var respuesta = com.concretolab.ui.DialogoClaveBase.pedirCambio();
        if (respuesta.isEmpty()) {
            return;
        }
        try {
            claveBase.cambiar(respuesta.get()[0], respuesta.get()[1]);
        } catch (IllegalArgumentException e) {
            notificador.advertencia(e.getMessage());
            return;
        } catch (Exception e) {
            notificador.error("No se pudo cambiar la contraseña: " + e.getMessage());
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

    private Modo modo;
    private Proyecto proyectoAExportar;
    private File archivoAImportar;
    private File ultimaCarpeta = new File(System.getProperty("user.home"));

    @FXML
    public void initialize() {
        colFechaCopia.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha().format(FORMATO_FECHA)));
        colTipoCopia.setCellValueFactory(c -> new SimpleStringProperty(nombreTipo(c.getValue().tipo())));
        colTamanoCopia.setCellValueFactory(c -> new SimpleStringProperty(tamano(c.getValue().bytes())));
        colNombreCopia.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        Tablas.configurar(tablaCopias, "Todavía no hay copias de seguridad.", copia -> { });
        btnRestaurar.disableProperty().bind(tablaCopias.getSelectionModel().selectedItemProperty().isNull());
        btnEliminarCopia.disableProperty().bind(tablaCopias.getSelectionModel().selectedItemProperty().isNull());

        colFechaBitacora.setCellValueFactory(c -> new SimpleStringProperty(formatoBitacora(c.getValue().getFecha())));
        colAccionBitacora.setCellValueFactory(c -> new SimpleStringProperty(nombreAccion(c.getValue().getAccion())));
        colDetalleBitacora.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDetalle()));
        Tablas.configurar(tablaBitacora, "Todavía no hay cambios registrados.", registro -> { });

        cmbProyecto.setConverter(new StringConverter<>() {
            @Override public String toString(Proyecto p) { return p == null ? "" : p.getNombre(); }
            @Override public Proyecto fromString(String s) { return null; }
        });
        chkProteger.selectedProperty().addListener((obs, antes, ahora) -> actualizarCamposContrasena());
        Formularios.limpiarAlEditar(txtContrasena, txtConfirmacion);
    }

    @Override
    public void refrescar() {
        boolean cifrada = claveBase.estaCifrada();
        lblCifrado.setText(cifrada ? "Cifrado: SÍ (AES-256). La contraseña se pide al abrir la aplicación."
                : "Cifrado: NO. Esta base no está cifrada (modo de pruebas o abierta con otro método).");
        btnCambiarClave.setDisable(!cifrada);
        lblRutaBase.setText("Base de datos: " + respaldos.archivoBaseDatos() + "\nRegistros: " + respaldos.carpetaRegistros());
        lblTamanoBase.setText("Tamaño de la base: " + tamano(respaldos.tamanoBaseDatos()) + " · Copias en: " + respaldos.carpetaCopias());
        tablaCopias.getItems().setAll(respaldos.listar());
        Proyecto activo = sesion.getProyecto();
        cmbProyecto.getItems().setAll(proyectos.proyectos());
        cmbProyecto.getItems().stream().filter(p -> activo != null && p.getId().equals(activo.getId())).findFirst().ifPresent(cmbProyecto::setValue);
        tablaBitacora.getItems().setAll(bitacora.recientes());
    }

    // ------------------------------------------------------------ carpetas

    @FXML private void onAbrirCarpetaDatos() { exportador.abrirCarpeta(respaldos.archivoBaseDatos().getParent().toFile()); }

    @FXML private void onAbrirCarpetaRegistros() { exportador.abrirCarpeta(respaldos.carpetaRegistros().toFile()); }

    // ------------------------------------------------------------ copias

    @FXML
    private void onCrearCopia() {
        enSegundoPlano("Creando la copia de seguridad…", () -> respaldos.crearCopia("manual"),
                copia -> { notificador.exito("Copia creada: " + copia.nombre()); refrescar(); });
    }

    @FXML
    private void onRestaurar() {
        var copia = tablaCopias.getSelectionModel().getSelectedItem();
        if (copia == null) return;
        notificador.confirmar("Restaurar copia",
                "Se reemplazarán todos tus datos actuales por los de la copia del " + copia.fecha().format(FORMATO_FECHA)
                        + ". Antes se guardará una copia de lo actual. El cambio se aplica al volver a abrir la aplicación.",
                "Restaurar", () -> {
                    try {
                        respaldos.programarRestauracion(copia.archivo());
                        notificador.advertencia("Restauración programada. Cierra ConcretoLab y vuelve a abrirlo para aplicarla.");
                        refrescar();
                    } catch (IllegalArgumentException ex) {
                        notificador.error(ex.getMessage());
                    }
                });
    }

    @FXML
    private void onEliminarCopia() {
        var copia = tablaCopias.getSelectionModel().getSelectedItem();
        if (copia == null) return;
        notificador.confirmar("Eliminar copia", "¿Eliminar «" + copia.nombre() + "»? No se puede deshacer.", "Eliminar", () -> {
            try {
                respaldos.eliminarCopia(copia.archivo());
                notificador.exito("Copia eliminada.");
                refrescar();
            } catch (IllegalArgumentException ex) {
                notificador.error(ex.getMessage());
            }
        });
    }

    // ------------------------------------------------------------ exportar / importar

    @FXML
    private void onExportar() {
        Proyecto p = cmbProyecto.getValue();
        if (p == null) {
            notificador.advertencia("Elige primero el proyecto que quieres exportar.");
            return;
        }
        modo = Modo.EXPORTAR;
        proyectoAExportar = p;
        lblTituloContrasena.setText("Exportar «" + p.getNombre() + "»");
        lblSubtituloContrasena.setText("Puedes protegerlo con una contraseña");
        abrirPanelContrasena(false);
    }

    @FXML
    private void onImportar() {
        var selector = new FileChooser();
        selector.setTitle("Importar proyecto");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proyecto de ConcretoLab", "*.concretolab"));
        if (ultimaCarpeta.isDirectory()) selector.setInitialDirectory(ultimaCarpeta);
        File elegido = selector.showOpenDialog(tablaCopias.getScene().getWindow());
        if (elegido == null) return;
        ultimaCarpeta = elegido.getParentFile();
        archivoAImportar = elegido;
        boolean cifrado;
        try {
            cifrado = intercambio.estaCifrado(elegido.toPath());
        } catch (RuntimeException ex) {
            notificador.error(mensaje(ex));
            return;
        }
        if (cifrado) {
            modo = Modo.IMPORTAR;
            lblTituloContrasena.setText("Importar proyecto protegido");
            lblSubtituloContrasena.setText(elegido.getName());
            abrirPanelContrasena(true);
        } else {
            importar(elegido, null);
        }
    }

    private void abrirPanelContrasena(boolean soloIntroducir) {
        txtContrasena.clear();
        txtConfirmacion.clear();
        Formularios.limpiarErrores(txtContrasena, txtConfirmacion);
        chkProteger.setVisible(!soloIntroducir);
        chkProteger.setManaged(!soloIntroducir);
        chkProteger.setSelected(soloIntroducir);
        lblCampoContrasena.setText(soloIntroducir ? "Contraseña del archivo" : "Contraseña");
        lblAvisoContrasena.setVisible(!soloIntroducir);
        lblAvisoContrasena.setManaged(!soloIntroducir);
        btnConfirmarContrasena.setText(soloIntroducir ? "Importar" : "Elegir destino");
        actualizarCamposContrasena();
        notificador.abrirPanel(panelContrasena);
    }

    private void actualizarCamposContrasena() {
        boolean pedir = chkProteger.isSelected();
        boolean importando = modo == Modo.IMPORTAR;
        txtContrasena.getParent().setVisible(pedir);
        txtContrasena.getParent().setManaged(pedir);
        cajaConfirmacion.setVisible(pedir && !importando);
        cajaConfirmacion.setManaged(pedir && !importando);
    }

    @FXML private void onCancelarContrasena() { limpiarContrasenas(); notificador.cerrarPanel(); }

    @FXML
    private void onConfirmarContrasena() {
        Formularios.limpiarErrores(txtContrasena, txtConfirmacion);
        char[] contrasena = null;
        if (chkProteger.isSelected()) {
            String texto = txtContrasena.getText();
            if (modo == Modo.EXPORTAR) {
                if (texto.length() < MINIMO_CONTRASENA) {
                    Formularios.marcarError(txtContrasena, "Usa al menos " + MINIMO_CONTRASENA + " caracteres.");
                    return;
                }
                if (!texto.equals(txtConfirmacion.getText())) {
                    Formularios.marcarError(txtConfirmacion, "Las contraseñas no coinciden.");
                    return;
                }
            } else if (texto.isEmpty()) {
                Formularios.marcarError(txtContrasena, "Escribe la contraseña del archivo.");
                return;
            }
            contrasena = texto.toCharArray();
        }
        Modo actual = modo;
        limpiarContrasenas();
        notificador.cerrarPanel();
        if (actual == Modo.EXPORTAR) exportar(proyectoAExportar, contrasena);
        else importar(archivoAImportar, contrasena);
    }

    private void limpiarContrasenas() {
        txtContrasena.clear();
        txtConfirmacion.clear();
    }

    private void exportar(Proyecto p, char[] contrasena) {
        var selector = new FileChooser();
        selector.setTitle("Exportar proyecto");
        selector.setInitialFileName(p.getNombre().replaceAll("[\\\\/:*?\"<>|]", "_") + ".concretolab");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proyecto de ConcretoLab", "*.concretolab"));
        if (ultimaCarpeta.isDirectory()) selector.setInitialDirectory(ultimaCarpeta);
        File elegido = selector.showSaveDialog(tablaCopias.getScene().getWindow());
        if (elegido == null) {
            borrar(contrasena);
            return;
        }
        File destino = elegido.getName().toLowerCase(Locale.ROOT).endsWith(".concretolab") ? elegido : new File(elegido.getParentFile(), elegido.getName() + ".concretolab");
        ultimaCarpeta = destino.getParentFile();
        enSegundoPlano("Exportando el proyecto…", () -> intercambio.exportar(p.getId(), destino.toPath(), contrasena),
                ruta -> {
                    notificador.exito("Proyecto exportado" + (contrasena != null && contrasena.length > 0 ? " y protegido con contraseña" : "") + ": " + destino.getName());
                    refrescar();
                }, () -> borrar(contrasena));
    }

    private void importar(File archivo, char[] contrasena) {
        enSegundoPlano("Importando el proyecto…", () -> intercambio.importar(archivo.toPath(), contrasena),
                resultado -> {
                    sesion.setProyecto(resultado.proyecto());
                    notificador.exito("Proyecto importado: «" + resultado.proyecto().getNombre() + "» · " + resultado.elementos() + " elementos, "
                            + resultado.mezclas() + " mezclas, " + resultado.ensayos() + " ensayos.");
                    resultado.avisos().forEach(notificador::advertencia);
                    refrescar();
                }, () -> borrar(contrasena));
    }

    // ------------------------------------------------------------ bitácora

    @FXML private void onActualizarBitacora() { tablaBitacora.getItems().setAll(bitacora.recientes()); }

    // ------------------------------------------------------------ utilidades

    /** Ejecuta la operación fuera del hilo de JavaFX y avisa del resultado; los errores del usuario salen como mensaje claro. */
    private <T> void enSegundoPlano(String mensajeInicio, java.util.concurrent.Callable<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        enSegundoPlano(mensajeInicio, trabajo, alTerminar, () -> { });
    }

    private <T> void enSegundoPlano(String mensajeInicio, java.util.concurrent.Callable<T> trabajo,
                                    java.util.function.Consumer<T> alTerminar, Runnable alFinalizar) {
        notificador.info(mensajeInicio);
        Task<T> tarea = new Task<>() {
            @Override protected T call() throws Exception { return trabajo.call(); }
        };
        tarea.setOnSucceeded(e -> { alFinalizar.run(); alTerminar.accept(tarea.getValue()); });
        tarea.setOnFailed(e -> {
            alFinalizar.run();
            Throwable causa = tarea.getException();
            log.warn("Operación de datos fallida", causa);
            notificador.error(mensaje(causa));
        });
        Thread hilo = new Thread(tarea, "datos");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static String mensaje(Throwable ex) {
        return ex instanceof IllegalArgumentException && ex.getMessage() != null ? ex.getMessage()
                : "No se pudo completar la operación: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    private static void borrar(char[] contrasena) {
        if (contrasena != null) Arrays.fill(contrasena, '\0');
    }

    private static String tamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String nombreTipo(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "inicio" -> "Al abrir la aplicación";
            case "cierre" -> "Al cerrarla";
            case "manual" -> "Manual";
            case "previa-restauracion" -> "Antes de restaurar";
            default -> tipo;
        };
    }

    private static String nombreAccion(String accion) {
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

    private static String formatoBitacora(String iso) {
        try {
            return LocalDateTime.parse(iso).format(FORMATO_FECHA);
        } catch (RuntimeException e) {
            return iso;
        }
    }
}
