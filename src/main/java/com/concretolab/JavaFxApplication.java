package com.concretolab;

import atlantafx.base.theme.PrimerLight;
import com.concretolab.service.BaseCifrada;
import com.concretolab.service.RespaldoDatos;
import com.concretolab.ui.DialogoClaveBase;
import com.concretolab.ui.Exportador;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Arranque de la aplicación de escritorio. La base se abre directamente, sin contraseña. Si se encuentra una base de una
 * versión anterior que estaba protegida con contraseña, se pide esa contraseña una sola vez y se convierte, conservando
 * antes una copia de la base protegida en la carpeta de copias de seguridad.
 */
public class JavaFxApplication extends Application {

    /** Variable de entorno con la contraseña de una base antigua protegida (automatización). No es necesaria para uso normal. */
    static final String FERNANDO_HUANCA_ENTORNO_CLAVE = "CONCRETOLAB_DB_CLAVE";
    static final int FERNANDO_HUANCA_INTENTOS = 3;

    private ConfigurableApplicationContext fernandoHuancaContext;
    private boolean fernandoHuancaCancelado;

    @Override
    public void init() throws IOException {
        Files.createDirectories(RespaldoDatos.erickDelgadoCarpetaDatos());
        // Antes de que Spring y Flyway toquen la base: aplicar una restauración pedida por el usuario.
        RespaldoDatos.erickDelgadoAplicarRestauracionPendiente();
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        if (BaseCifrada.erickDelgadoInspeccionar(base) == BaseCifrada.Estado.CIFRADA && !fernandoHuancaConvertirBaseProtegida(base)) {
            fernandoHuancaCancelado = true;
            return;
        }
        // Copia de seguridad del estado actual (así una migración fallida nunca deja al usuario sin sus datos).
        RespaldoDatos.erickDelgadoRespaldarArchivo(RespaldoDatos.ERICK_DELGADO_MOTIVO_INICIO);
        this.fernandoHuancaContext = new SpringApplicationBuilder(ConcretoLabApplication.class)
                .web(WebApplicationType.NONE)
                .properties(BaseCifrada.erickDelgadoPropiedadesSpring(null))
                .run();
    }

    /**
     * Base protegida de una versión anterior: pide la contraseña actual (hasta 3 intentos) y la convierte en una base sin
     * contraseña. Si la persona sale o algo falla, la base no se modifica y la aplicación se cierra.
     *
     * @return true si la base quedó convertida y se puede abrir
     */
    private boolean fernandoHuancaConvertirBaseProtegida(Path base) {
        String entorno = System.getenv(FERNANDO_HUANCA_ENTORNO_CLAVE);
        String aviso = "Esta base de datos se creó con una versión anterior que pedía contraseña. Escríbala una última vez: "
                + "sus proyectos se conservan y desde ahora ConcretoLab abrirá directamente. Antes se guarda una copia de la base tal como está.";
        for (int intento = 1; intento <= FERNANDO_HUANCA_INTENTOS; intento++) {
            Optional<char[]> respuesta = entorno != null && !entorno.isEmpty() && intento == 1
                    ? Optional.of(entorno.toCharArray()) : DialogoClaveBase.erickDelgadoPedirActual(aviso);
            if (respuesta.isEmpty()) {
                return false;
            }
            char[] clave = respuesta.get();
            try {
                if (!BaseCifrada.erickDelgadoClaveCorrecta(base, clave)) {
                    aviso = "Contraseña incorrecta (intento " + intento + " de " + FERNANDO_HUANCA_INTENTOS + "). Sus datos no se modificaron.";
                    continue;
                }
                Path copia = BaseCifrada.erickDelgadoQuitarClave(base, clave, RespaldoDatos.erickDelgadoCarpetaCopias());
                DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.INFORMATION, "Datos listos",
                        "Sus proyectos se conservaron y ConcretoLab ya no pedirá contraseña.\n\nLa base anterior (con contraseña) quedó guardada "
                                + "como copia de seguridad: " + copia.getFileName());
                return true;
            } catch (Exception e) {
                DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.ERROR, "No se pudo convertir la base",
                        "Sus datos no se modificaron. Detalle: " + e.getMessage());
                return false;
            } finally {
                Arrays.fill(clave, '\0');
            }
        }
        DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.ERROR, "No se pudo abrir la base",
                "Se agotaron los intentos. Sus datos siguen intactos; vuelva a abrir ConcretoLab para intentarlo de nuevo.");
        return false;
    }

    @Override
    public void start(Stage stage) throws Exception {
        if (fernandoHuancaCancelado || fernandoHuancaContext == null) {
            Platform.exit();
            return;
        }
        // Tema visual: hay PrimerDark, NordLight, NordDark, Dracula, etc.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        // Permite abrir los PDF y Excel generados con la aplicacion del sistema.
        fernandoHuancaContext.getBean(Exportador.class).setHostServices(getHostServices());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));

        // LINEA CLAVE: Spring entrega los controladores ya inyectados
        loader.setControllerFactory(fernandoHuancaContext::getBean);

        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        stage.setTitle("ConcretoLab Desktop");
        for (String icono : new String[]{"/img/concretolab-32.png", "/img/concretolab-256.png"}) {
            try (InputStream in = getClass().getResourceAsStream(icono)) {
                if (in != null) stage.getIcons().add(new Image(in));
            }
        }
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        stage.show();
    }

    @Override
    public void stop() {
        if (fernandoHuancaContext != null) {
            fernandoHuancaContext.close();
            // Con la base ya cerrada, la copia de cierre es un archivo coherente.
            RespaldoDatos.erickDelgadoRespaldarArchivo(RespaldoDatos.ERICK_DELGADO_MOTIVO_CIERRE);
        }
        Platform.exit();
    }
}
