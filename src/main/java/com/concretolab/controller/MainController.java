package com.concretolab.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.concretolab.service.RespaldoDatos;
import com.concretolab.ui.Bienvenida;
import com.concretolab.ui.Exportador;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import com.concretolab.ui.VisorAyuda;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MainController {
    private final ApplicationContext fernandoHuancaContext;
    private final Notificador fernandoHuancaNotificador;
    private final SesionProyecto fernandoHuancaSesion;

    @FXML private StackPane raiz;
    @FXML private ModalPane modalPanel;
    @FXML private ModalPane modalDialogo;
    @FXML private TabPane pestanas;
    @FXML private Tab tabInicio;
    @FXML private Tab tabModelo;
    @FXML private Tab tabSismo;
    @FXML private Tab tabMateriales;
    @FXML private Tab tabGranulometria;
    @FXML private Tab tabBalance;
    @FXML private Tab tabMezclas;
    @FXML private Tab tabDatos;
    @FXML private CheckMenuItem mnuOscuro;
    @FXML private Label lblMensaje;
    @FXML private Label lblProyectoActivo;
    @FXML private Label lblVersion;
    @org.springframework.beans.factory.annotation.Value("${app.version:desconocida}")
    private String fernandoHuancaVersion;

    private final Map<String, Vista> fernandoHuancaVistas = new HashMap<>();
    private String fernandoHuancaVistaActual = "inicio-view";

    private record Vista(Parent nodo, Object controlador) {}

    @FXML
    public void initialize() {
        fernandoHuancaNotificador.fernandoHuancaRegistrar(modalPanel, modalDialogo, lblMensaje);
        modalPanel.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        modalDialogo.setAlignment(javafx.geometry.Pos.CENTER);
        lblVersion.setText("Versión " + fernandoHuancaVersion);

        tabInicio.setUserData("inicio-view");
        tabModelo.setUserData("modelo-view");
        tabSismo.setUserData("sismo-view");
        tabMateriales.setUserData("materiales-view");
        tabGranulometria.setUserData("granulometria-view");
        tabBalance.setUserData("balance-view");
        tabMezclas.setUserData("mezclas-view");
        tabDatos.setUserData("datos-view");

        pestanas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> {
            if (actual != null) fernandoHuancaMostrar(actual);
        });
        fernandoHuancaMostrar(pestanas.getSelectionModel().getSelectedItem());

        fernandoHuancaSesion.proyectoProperty().addListener((obs, a, p) ->
                lblProyectoActivo.setText(p == null ? "Sin proyecto" : "Proyecto: " + p.getNombre()));
    }

    public void fernandoHuancaIrA(String vista) {
        fernandoHuancaPestanas().stream()
                .filter(t -> vista.equals(t.getUserData()))
                .findFirst()
                .ifPresent(t -> pestanas.getSelectionModel().select(t));
    }

    private List<Tab> fernandoHuancaPestanas() {
        return List.of(tabInicio, tabModelo, tabSismo, tabMateriales, tabGranulometria, tabBalance, tabMezclas, tabDatos);
    }

    private void fernandoHuancaMostrar(Tab pestana) {
        String nombre = (String) pestana.getUserData();
        Vista vista = fernandoHuancaVistas.computeIfAbsent(nombre, this::fernandoHuancaCargar);
        fernandoHuancaVistaActual = nombre;
        if (pestana.getContent() != vista.nodo()) pestana.setContent(vista.nodo());
        if (vista.controlador() instanceof Refrescable r) {
            r.refrescar();
        }
    }

    private Vista fernandoHuancaCargar(String nombre) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + nombre + ".fxml"));
        loader.setControllerFactory(fernandoHuancaContext::getBean);
        loader.setClassLoader(getClass().getClassLoader());
        try {
            Parent nodo = loader.load();
            fernandoHuancaAgregarBotonAyuda(nodo, nombre);
            return new Vista(nodo, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + nombre, e);
        }
    }

    private void fernandoHuancaAgregarBotonAyuda(Parent nodo, String nombre) {
        Node base = nodo instanceof ScrollPane sp && sp.getContent() != null ? sp.getContent() : nodo;
        if (!(base.lookup(".pagina-cabecera") instanceof HBox cabecera)) return;
        Button ayuda = new Button(null, new FontIcon(Feather.HELP_CIRCLE));
        ayuda.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "boton-ayuda-pantalla");
        ayuda.setTooltip(new Tooltip("Ayuda de esta pantalla (F1)"));
        ayuda.setAccessibleText("Ayuda de esta pantalla");
        ayuda.setOnAction(e -> fernandoHuancaAbrirAyuda(VisorAyuda.FERNANDO_HUANCA_ANCLAS.get(nombre)));
        cabecera.getChildren().add(ayuda);
    }

    @FXML private void onMenuInicio() { fernandoHuancaIrA("inicio-view"); }
    @FXML private void onMenuModelo() { fernandoHuancaIrA("modelo-view"); }
    @FXML private void onMenuSismo() { fernandoHuancaIrA("sismo-view"); }
    @FXML private void onMenuMateriales() { fernandoHuancaIrA("materiales-view"); }
    @FXML private void onMenuGranulometria() { fernandoHuancaIrA("granulometria-view"); }
    @FXML private void onMenuBalance() { fernandoHuancaIrA("balance-view"); }
    @FXML private void onMenuMezclas() { fernandoHuancaIrA("mezclas-view"); }
    @FXML private void onMenuDatos() { fernandoHuancaIrA("datos-view"); }
    @FXML private void onSalir() { if (raiz.getScene() != null) raiz.getScene().getWindow().hide(); Platform.exit(); }
    @FXML private void onManual() { fernandoHuancaAbrirAyuda(null); }
    @FXML private void onAyudaPantalla() { fernandoHuancaAyudaDeEstaPantalla(); }
    @FXML private void onBienvenida() { fernandoHuancaMostrarBienvenida(); }
    @FXML private void onEjemplo() { fernandoHuancaContext.getBean(InicioController.class).fernandoHuancaAgregarEjemplo(); }

    @FXML
    private void onCambiarTema() {
        boolean oscuro = mnuOscuro.isSelected();
        Application.setUserAgentStylesheet(oscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        if (oscuro) {
            if (!raiz.getStyleClass().contains("dark")) raiz.getStyleClass().add("dark");
        } else {
            raiz.getStyleClass().remove("dark");
        }
    }

    public void fernandoHuancaAyudaDeEstaPantalla() {
        fernandoHuancaAbrirAyuda(VisorAyuda.FERNANDO_HUANCA_ANCLAS.get(fernandoHuancaVistaActual));
    }

    public void fernandoHuancaAbrirAyuda(String ancla) {
        Exportador exportador = fernandoHuancaContext.getBean(Exportador.class);
        boolean abierto = VisorAyuda.fernandoHuancaMostrar(raiz.getScene() == null ? null : raiz.getScene().getWindow(), ancla,
                bytes -> exportador.fernandoHuancaAbrirTemporal("Manual_de_usuario_ConcretoLab", Exportador.Formato.PDF,
                        destino -> java.nio.file.Files.write(destino.toPath(), bytes)));
        if (!abierto) {
            fernandoHuancaNotificador.fernandoHuancaAdvertencia("Esta versión no incluye el manual. Búsquelo en la carpeta «doc» "
                    + "del proyecto (Manual_de_usuario_ConcretoLab.pdf).");
        }
    }

    public void fernandoHuancaMostrarBienvenida() {
        Node recorrido = Bienvenida.fernandoHuancaCrear(RespaldoDatos.erickDelgadoCarpetaDatos(),
                fernandoHuancaNotificador::fernandoHuancaCerrarDialogo,
                () -> fernandoHuancaContext.getBean(InicioController.class).fernandoHuancaAgregarEjemplo());
        fernandoHuancaNotificador.fernandoHuancaMostrarDialogo(recorrido);
    }

    public void fernandoHuancaBienvenidaInicial() {
        if (Bienvenida.fernandoHuancaDebeMostrarse(RespaldoDatos.erickDelgadoCarpetaDatos())) {
            fernandoHuancaMostrarBienvenida();
        }
    }

    @FXML
    private void onAcercaDe() {
        com.concretolab.ui.AcercaDe.fernandoHuancaMostrar(raiz.getScene().getWindow(), fernandoHuancaVersion,
                RespaldoDatos.erickDelgadoCarpetaDatos().toString());
    }
}
