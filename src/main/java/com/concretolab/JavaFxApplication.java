package com.concretolab;



import atlantafx.base.theme.PrimerLight;
import com.concretolab.ui.Exportador;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() throws java.io.IOException {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(System.getProperty("user.home"), ".concretolab"));
        this.context = new SpringApplicationBuilder(ConcretoLabApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public void start(Stage stage) throws Exception {
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
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}

