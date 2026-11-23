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
    static final String FERNANDO_HUANCA_ENTORNO_CLAVE = "CONCRETOLAB_DB_CLAVE";

    private ConfigurableApplicationContext fernandoHuancaContext;
    private boolean fernandoHuancaCancelado;
    private Path fernandoHuancaCopiaPlanaMigrada;
    private List<Path> fernandoHuancaCopiasPlanasAntiguas = List.of();

    @Override
    public void init() throws IOException {
        Files.createDirectories(RespaldoDatos.erickDelgadoCarpetaDatos());
        // Antes de que Spring y Flyway toquen la base: aplicar una restauración pedida por el usuario.
        RespaldoDatos.erickDelgadoAplicarRestauracionPendiente();
        char[] clave = null;
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        if (BaseCifrada.erickDelgadoInspeccionar(base) == BaseCifrada.Estado.CIFRADA) {
            var respuesta = DialogoClaveBase.erickDelgadoPedirActual(
                    "Ingresa tu contraseña actual una última vez para abrir sin contraseña en adelante.");
            if (respuesta.isEmpty()) { fernandoHuancaCancelado = true; return; }
            char[] actual = respuesta.get();
            try {
                BaseCifrada.erickDelgadoQuitarClave(base, actual);
            } catch (Exception e) {
                DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.ERROR, "No se pudo abrir la base", e.getMessage());
                fernandoHuancaCancelado = true;
                return;
            } finally {
                Arrays.fill(actual, '\0');
            }
        }
        // Copia de seguridad del estado actual (así una migración fallida nunca deja al usuario sin sus datos).
        RespaldoDatos.erickDelgadoRespaldarArchivo("inicio");
        this.fernandoHuancaContext = new SpringApplicationBuilder(ConcretoLabApplication.class)
                .web(WebApplicationType.NONE)
                .properties(BaseCifrada.erickDelgadoPropiedadesSpring(clave))
                .run();
        if (clave != null) {
            Arrays.fill(clave, '\0');
        }
        // La base ya abrió con la contraseña: la copia sin cifrar que dejó la migración ya no hace falta.
        if (fernandoHuancaCopiaPlanaMigrada != null) {
            Files.deleteIfExists(fernandoHuancaCopiaPlanaMigrada);
        }
    }

    /**
     * Obtiene la contraseña de la base: la pide al abrir (o la crea y cifra los datos existentes la primera vez).
     * Devuelve {@code null} y marca {@code cancelado} si la persona usuaria sale.
     */
    private char[] fernandoHuancaPrepararClave() {
        Path base = RespaldoDatos.erickDelgadoArchivoBaseDatos();
        String entorno = System.getenv(FERNANDO_HUANCA_ENTORNO_CLAVE);
        char[] deEntorno = entorno == null || entorno.isEmpty() ? null : entorno.toCharArray();
        switch (BaseCifrada.erickDelgadoInspeccionar(base)) {
            case NO_EXISTE -> {
                var clave = deEntorno != null ? java.util.Optional.of(deEntorno) : DialogoClaveBase.erickDelgadoPedirNueva(false);
                if (clave.isEmpty()) {
                    fernandoHuancaCancelado = true;
                    return null;
                }
                return clave.get();
            }
            case SIN_CIFRAR -> {
                var clave = deEntorno != null ? java.util.Optional.of(deEntorno) : DialogoClaveBase.erickDelgadoPedirNueva(true);
                if (clave.isEmpty()) {
                    fernandoHuancaCancelado = true;
                    return null;
                }
                // Copias de seguridad anteriores que quedaron sin cifrar: se ofrecerá borrarlas al abrir.
                fernandoHuancaCopiasPlanasAntiguas = fernandoHuancaCopiasSinCifrar();
                try {
                    fernandoHuancaCopiaPlanaMigrada = BaseCifrada.erickDelgadoCifrarExistente(base, clave.get());
                } catch (Exception e) {
                    DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.ERROR, "No se pudo cifrar la base",
                            "Sus datos no se modificaron. Detalle: " + e.getMessage());
                    fernandoHuancaCancelado = true;
                    return null;
                }
                return clave.get();
            }
            default -> {
                String aviso = null;
                for (int intento = 1; intento <= 3; intento++) {
                    var clave = deEntorno != null && intento == 1 ? java.util.Optional.of(deEntorno) : DialogoClaveBase.erickDelgadoPedirActual(aviso);
                    if (clave.isEmpty()) {
                        fernandoHuancaCancelado = true;
                        return null;
                    }
                    if (BaseCifrada.erickDelgadoClaveCorrecta(base, clave.get())) {
                        return clave.get();
                    }
                    aviso = "Contraseña incorrecta (" + intento + " de 3).";
                }
                DialogoClaveBase.erickDelgadoMostrar(Alert.AlertType.ERROR, "No se pudo abrir la base",
                        "Se agotaron los intentos. Sus datos siguen intactos.");
                fernandoHuancaCancelado = true;
                return null;
            }
        }
    }

    private static List<Path> fernandoHuancaCopiasSinCifrar() {
        try (Stream<Path> archivos = Files.list(RespaldoDatos.erickDelgadoCarpetaCopias())) {
            return archivos.filter(Files::isRegularFile)
                    .filter(p -> BaseCifrada.erickDelgadoInspeccionar(p) == BaseCifrada.Estado.SIN_CIFRAR)
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
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
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        stage.show();

        if (!fernandoHuancaCopiasPlanasAntiguas.isEmpty()) {
            Platform.runLater(this::fernandoHuancaOfrecerBorrarCopiasPlanas);
        }
    }

    private void fernandoHuancaOfrecerBorrarCopiasPlanas() {
        boolean borrar = DialogoClaveBase.erickDelgadoConfirmar("Copias de seguridad sin cifrar",
                "Sus datos ya están cifrados, pero hay " + fernandoHuancaCopiasPlanasAntiguas.size() + " copia(s) de seguridad anteriores que siguen SIN cifrar en:\n"
                        + RespaldoDatos.erickDelgadoCarpetaCopias() + "\n\n¿Desea eliminarlas ahora? (Las copias nuevas ya se guardan cifradas.)");
        if (borrar) {
            for (Path p : fernandoHuancaCopiasPlanasAntiguas) {
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
        if (fernandoHuancaContext != null) {
            fernandoHuancaContext.close();
            // Con la base ya cerrada, la copia de cierre es un archivo coherente.
            RespaldoDatos.erickDelgadoRespaldarArchivo("cierre");
        }
        Platform.exit();
    }
}
