package com.concretolab.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MainController {

    private static final double FERNANDO_HUANCA_ANCHO_EXPANDIDO = 248;
    private static final double FERNANDO_HUANCA_ANCHO_CONTRAIDO = 76;
    private static final Duration FERNANDO_HUANCA_DURACION_MENU = Duration.millis(260);

    private final ApplicationContext fernandoHuancaContext;
    private final Notificador fernandoHuancaNotificador;

    @FXML private StackPane raiz;
    @FXML private VBox sidebar;
    @FXML private StackPane contenido;
    @FXML private ModalPane modalPanel;
    @FXML private ModalPane modalDialogo;
    @FXML private VBox zonaNotificaciones;

    @FXML private ToggleGroup menu;
    @FXML private ToggleButton navInicio;
    @FXML private ToggleButton navModelo;
    @FXML private ToggleButton navSismo;
    @FXML private ToggleButton navMateriales;
    @FXML private ToggleButton navGranulometria;
    @FXML private ToggleButton navBalance;
    @FXML private ToggleButton navMezclas;
    @FXML private ToggleButton navDatos;
    @FXML private Region indicador;

    @FXML private VBox marcaTextos;
    @FXML private Button btnTema;
    @FXML private Button btnContraer;
    @FXML private Button btnAcerca;
    @org.springframework.beans.factory.annotation.Value("${app.version:desconocida}")
    private String fernandoHuancaVersion;

    /** Vistas ya cargadas: se construyen una sola vez y se reutilizan. */
    private final Map<String, Vista> fernandoHuancaVistas = new HashMap<>();
    private boolean fernandoHuancaOscuro = false;
    private boolean fernandoHuancaContraido = false;

    private record Vista(Parent nodo, Object controlador) {}

    @FXML
    public void initialize() {
        fernandoHuancaNotificador.fernandoHuancaRegistrar(modalPanel, modalDialogo, zonaNotificaciones);
        modalPanel.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        modalDialogo.setAlignment(javafx.geometry.Pos.CENTER);

        navInicio.setUserData("inicio-view");
        navModelo.setUserData("modelo-view");
        navSismo.setUserData("sismo-view");
        navMateriales.setUserData("materiales-view");
        navGranulometria.setUserData("granulometria-view");
        navBalance.setUserData("balance-view");
        navMezclas.setUserData("mezclas-view");
        navDatos.setUserData("datos-view");
        for (ToggleButton b : fernandoHuancaBotonesMenu()) {
            Tooltip t = new Tooltip(b.getText());
            t.setShowDelay(Duration.millis(300));
            b.setTooltip(t);
        }

        // Siempre debe haber una opcion seleccionada.
        menu.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                menu.selectToggle(anterior);
                return;
            }
            fernandoHuancaMostrar((String) actual.getUserData());
            fernandoHuancaMoverIndicador(actual);
        });

        menu.selectToggle(navInicio);
        Platform.runLater(() -> fernandoHuancaMoverIndicador(navInicio));
    }

    // ----------------------------------------------------------- navegacion

    /** Cambia a otra vista como si se hubiera pulsado su opcion en el menu lateral. */
    public void fernandoHuancaIrA(String vista) {
        fernandoHuancaBotonesMenu().stream()
                .filter(b -> vista.equals(b.getUserData()))
                .findFirst()
                .ifPresent(menu::selectToggle);
    }

    private void fernandoHuancaMostrar(String nombre) {
        Vista vista = fernandoHuancaVistas.computeIfAbsent(nombre, this::fernandoHuancaCargar);
        contenido.getChildren().setAll(vista.nodo());
        if (vista.controlador() instanceof Refrescable r) {
            r.refrescar();
        }
        Animations.fadeInUp(vista.nodo(), Duration.millis(380)).play();
    }

    private Vista fernandoHuancaCargar(String nombre) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + nombre + ".fxml"));
        loader.setControllerFactory(fernandoHuancaContext::getBean);
        // Explicito: en macOS el hilo de eventos puede no tener class loader de contexto.
        loader.setClassLoader(getClass().getClassLoader());
        try {
            Parent nodo = loader.load();
            return new Vista(nodo, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + nombre, e);
        }
    }

    /** Desliza el fondo resaltado hasta la opcion seleccionada. */
    private void fernandoHuancaMoverIndicador(Toggle toggle) {
        Node boton = (Node) toggle;
        double destino = boton.getBoundsInParent().getMinY();
        indicador.setPrefHeight(boton.getBoundsInParent().getHeight());
        new Timeline(new KeyFrame(Duration.millis(280),
                new KeyValue(indicador.translateYProperty(), destino, Interpolator.SPLINE(0.4, 0, 0.2, 1))))
                .play();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onCambiarTema() {
        fernandoHuancaOscuro = !fernandoHuancaOscuro;
        Application.setUserAgentStylesheet(fernandoHuancaOscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        if (fernandoHuancaOscuro) {
            raiz.getStyleClass().add("dark");
        } else {
            raiz.getStyleClass().remove("dark");
        }
        ((FontIcon) btnTema.getGraphic()).setIconCode(fernandoHuancaOscuro ? Feather.SUN : Feather.MOON);
        btnTema.setText(fernandoHuancaOscuro ? "Modo claro" : "Modo oscuro");
        Animations.fadeIn(contenido, Duration.millis(300)).play();
    }

    @FXML
    private void onAcercaDe() {
        com.concretolab.ui.AcercaDe.fernandoHuancaMostrar(raiz.getScene().getWindow(), fernandoHuancaVersion,
                com.concretolab.service.RespaldoDatos.erickDelgadoCarpetaDatos().toString());
    }

    @FXML
    private void onContraerMenu() {
        fernandoHuancaContraido = !fernandoHuancaContraido;
        double ancho = fernandoHuancaContraido ? FERNANDO_HUANCA_ANCHO_CONTRAIDO : FERNANDO_HUANCA_ANCHO_EXPANDIDO;

        Timeline ajuste = new Timeline(new KeyFrame(FERNANDO_HUANCA_DURACION_MENU,
                new KeyValue(sidebar.prefWidthProperty(), ancho, Interpolator.SPLINE(0.4, 0, 0.2, 1)),
                new KeyValue(sidebar.minWidthProperty(), ancho, Interpolator.SPLINE(0.4, 0, 0.2, 1))));

        ContentDisplay modo = fernandoHuancaContraido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        List<Node> textos = new ArrayList<>(sidebar.lookupAll(".nav-seccion"));
        textos.add(marcaTextos);

        if (fernandoHuancaContraido) {
            textos.forEach(n -> { n.setVisible(false); n.setManaged(false); });
            fernandoHuancaTodosLosBotones().forEach(b -> b.setContentDisplay(modo));
            ajuste.play();
        } else {
            ajuste.setOnFinished(e -> {
                textos.forEach(n -> { n.setVisible(true); n.setManaged(true); });
                fernandoHuancaTodosLosBotones().forEach(b -> b.setContentDisplay(modo));
                ParallelTransition aparicion = new ParallelTransition();
                textos.forEach(n -> aparicion.getChildren().add(Animations.fadeIn(n, Duration.millis(200))));
                aparicion.play();
                Platform.runLater(() -> fernandoHuancaMoverIndicador(menu.getSelectedToggle()));
            });
            ajuste.play();
        }
        ((FontIcon) btnContraer.getGraphic()).setIconCode(
                fernandoHuancaContraido ? Feather.CHEVRONS_RIGHT : Feather.CHEVRONS_LEFT);
        Platform.runLater(() -> fernandoHuancaMoverIndicador(menu.getSelectedToggle()));
    }

    private List<ToggleButton> fernandoHuancaBotonesMenu() {
        return List.of(navInicio, navModelo, navSismo, navMateriales, navGranulometria, navBalance, navMezclas, navDatos);
    }

    private List<ButtonBase> fernandoHuancaTodosLosBotones() {
        List<ButtonBase> todos = new ArrayList<>(fernandoHuancaBotonesMenu());
        todos.add(btnTema);
        todos.add(btnAcerca);
        todos.add(btnContraer);
        return todos;
    }
}
