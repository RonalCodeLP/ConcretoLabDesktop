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
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class JavaFxApplication extends Application {

    /** Variable de entorno para abrir la base sin diálogo (automatización). No se recomienda para uso normal. */
    static final String ENTORNO_CLAVE = "CONCRETOLAB_DB_CLAVE";

    private ConfigurableApplicationContext context;
    private boolean cancelado;
    private Path copiaPlanaMigrada;
    private List<Path> copiasPlanasAntiguas = List.of();

    @Override
    public void init() throws IOException {
        Files.createDirectories(RespaldoDatos.carpetaDatos());
        // Antes de que Spring y Flyway toquen la base: aplicar una restauración pedida por el usuario.
        RespaldoDatos.aplicarRestauracionPendiente();
        char[] clave = prepararClave();
        if (cancelado) {
            return;
        }
        // Copia de seguridad del estado actual (así una migración fallida nunca deja al usuario sin sus datos).
        RespaldoDatos.respaldarArchivo("inicio");
        this.context = new SpringApplicationBuilder(ConcretoLabApplication.class)
                .web(WebApplicationType.NONE)
                .properties(BaseCifrada.propiedadesSpring(clave))
                .run();
        if (clave != null) {
            Arrays.fill(clave, '\0');
        }
        // La base ya abrió con la contraseña: la copia sin cifrar que dejó la migración ya no hace falta.
        if (copiaPlanaMigrada != null) {
            Files.deleteIfExists(copiaPlanaMigrada);
        }
    }

    /**
     * Obtiene la contraseña de la base: la pide al abrir (o la crea y cifra los datos existentes la primera vez).
     * Devuelve {@code null} y marca {@code cancelado} si la persona usuaria sale.
     */
    private char[] prepararClave() {
        Path base = RespaldoDatos.archivoBaseDatos();
        String entorno = System.getenv(ENTORNO_CLAVE);
        char[] deEntorno = entorno == null || entorno.isEmpty() ? null : entorno.toCharArray();
        switch (BaseCifrada.inspeccionar(base)) {
            case NO_EXISTE -> {
                var clave = deEntorno != null ? java.util.Optional.of(deEntorno) : DialogoClaveBase.pedirNueva(false);
                if (clave.isEmpty()) {
                    cancelado = true;
                    return null;
                }
                return clave.get();
            }
            case SIN_CIFRAR -> {
                var clave = deEntorno != null ? java.util.Optional.of(deEntorno) : DialogoClaveBase.pedirNueva(true);
                if (clave.isEmpty()) {
                    cancelado = true;
                    return null;
                }
                // Copias de seguridad anteriores que quedaron sin cifrar: se ofrecerá borrarlas al abrir.
                copiasPlanasAntiguas = copiasSinCifrar();
                try {
                    copiaPlanaMigrada = BaseCifrada.cifrarExistente(base, clave.get());
                } catch (Exception e) {
                    DialogoClaveBase.mostrar(Alert.AlertType.ERROR, "No se pudo cifrar la base",
                            "Sus datos no se modificaron. Detalle: " + e.getMessage());
                    cancelado = true;
                    return null;
                }
                return clave.get();
            }
            default -> {
                String aviso = null;
                for (int intento = 1; intento <= 3; intento++) {
                    var clave = deEntorno != null && intento == 1 ? java.util.Optional.of(deEntorno) : DialogoClaveBase.pedirActual(aviso);
                    if (clave.isEmpty()) {
                        cancelado = true;
                        return null;
                    }
                    if (BaseCifrada.claveCorrecta(base, clave.get())) {
                        return clave.get();
                    }
                    aviso = "Contraseña incorrecta (" + intento + " de 3).";
                }
                DialogoClaveBase.mostrar(Alert.AlertType.ERROR, "No se pudo abrir la base",
                        "Se agotaron los intentos. Sus datos siguen intactos.");
                cancelado = true;
                return null;
            }
        }
    }

    private static List<Path> copiasSinCifrar() {
        try (Stream<Path> archivos = Files.list(RespaldoDatos.carpetaCopias())) {
            return archivos.filter(Files::isRegularFile)
                    .filter(p -> BaseCifrada.inspeccionar(p) == BaseCifrada.Estado.SIN_CIFRAR)
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        if (cancelado || context == null) {
            Platform.exit();
            return;
        }
        // Tema visual: hay PrimerDark, NordLight, NordDark, Dracula, etc.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        // Permite abrir los PDF y Excel generados con la aplicacion del sistema.
        context.getBean(Exportador.class).setHostServices(getHostServices());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));

        // LINEA CLAVE: Spring entrega los controladores ya inyectados
        loader.setControllerFactory(context::getBean);

        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        stage.setTitle("ConcretoLab Desktop");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        stage.show();

        if (!copiasPlanasAntiguas.isEmpty()) {
            Platform.runLater(this::ofrecerBorrarCopiasPlanas);
        }
    }

    private void ofrecerBorrarCopiasPlanas() {
        boolean borrar = DialogoClaveBase.confirmar("Copias de seguridad sin cifrar",
                "Sus datos ya están cifrados, pero hay " + copiasPlanasAntiguas.size() + " copia(s) de seguridad anteriores que siguen SIN cifrar en:\n"
                        + RespaldoDatos.carpetaCopias() + "\n\n¿Desea eliminarlas ahora? (Las copias nuevas ya se guardan cifradas.)");
        if (borrar) {
            for (Path p : copiasPlanasAntiguas) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignorada) {
                    // se podrá borrar a mano
                }
            }
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
            // Con la base ya cerrada, la copia de cierre es un archivo coherente.
            RespaldoDatos.respaldarArchivo("cierre");
        }
        Platform.exit();
    }
}
