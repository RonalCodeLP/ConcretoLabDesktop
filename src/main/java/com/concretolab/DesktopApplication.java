package com.concretolab;
import com.concretolab.ui.PantallaPrincipal;
import java.nio.file.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class DesktopApplication extends Application {
    private ConfigurableApplicationContext contexto;
    @Override public void init() throws Exception {
        Files.createDirectories(Path.of(System.getProperty("user.home"),".concretolab"));
        contexto=new SpringApplicationBuilder(ConcretoLabConfig.class).headless(false).run();
    }
    @Override public void start(Stage stage) {
        PantallaPrincipal pantalla=contexto.getBean(PantallaPrincipal.class);
        Scene scene=new Scene(pantalla.crear(),1200,760);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setTitle("ConcretoLab Desktop"); stage.setMinWidth(950); stage.setMinHeight(650); stage.setScene(scene); stage.show();
    }
    @Override public void stop() { if(contexto!=null) contexto.close(); }
}
