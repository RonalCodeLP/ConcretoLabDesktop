package com.concretolab.ui;

import atlantafx.base.theme.Styles;
import com.concretolab.service.ManualPdf;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public final class VisorAyuda {
    public static final Map<String, String> FERNANDO_HUANCA_ANCLAS = Map.of(
            "inicio-view", "ayuda-inicio",
            "modelo-view", "ayuda-modelo",
            "sismo-view", "ayuda-sismo",
            "materiales-view", "ayuda-materiales",
            "granulometria-view", "ayuda-granulometria",
            "balance-view", "ayuda-balance",
            "mezclas-view", "ayuda-mezclas",
            "datos-view", "ayuda-datos");

    private static final double FERNANDO_HUANCA_SEPARACION = 14;
    private static final int FERNANDO_HUANCA_MARGEN_PAGINAS = 2;
    private static VisorAyuda fernandoHuancaAbierto;

    private final ManualPdf fernandoHuancaManual;
    private final Stage fernandoHuancaVentana = new Stage();
    private final VBox fernandoHuancaPaginas = new VBox(FERNANDO_HUANCA_SEPARACION);
    private final ScrollPane fernandoHuancaDesplazamiento = new ScrollPane(fernandoHuancaPaginas);
    private final List<StackPane> fernandoHuancaMarcos = new ArrayList<>();
    private final List<ImageView> fernandoHuancaImagenes = new ArrayList<>();
    private final List<double[]> fernandoHuancaTamanos = new ArrayList<>();
    private final Set<Integer> fernandoHuancaPedidas = new HashSet<>();
    private final ExecutorService fernandoHuancaHilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ayuda-pdf");
        t.setDaemon(true);
        return t;
    });
    private final TextField fernandoHuancaCampoPagina = new TextField();
    private final Label fernandoHuancaMensaje = new Label();
    private final Label fernandoHuancaZoomTexto = new Label();
    private double fernandoHuancaZoom = 1.25;
    private int fernandoHuancaGeneracion = 0;

    private VisorAyuda(ManualPdf manual, Window duena, Consumer<byte[]> abrirExterno) {
        this.fernandoHuancaManual = manual;
        fernandoHuancaVentana.setTitle("Ayuda de ConcretoLab · Manual de usuario");
        if (duena != null) {
            fernandoHuancaVentana.initOwner(duena);
            if (duena instanceof Stage s) fernandoHuancaVentana.getIcons().setAll(s.getIcons());
        }

        TreeView<ManualPdf.Entrada> indice = new TreeView<>();
        indice.setId("ayudaIndice");
        TreeItem<ManualPdf.Entrada> raiz = new TreeItem<>(new ManualPdf.Entrada("Manual", 0, List.of()));
        manual.fernandoHuancaIndice().forEach(e -> raiz.getChildren().add(fernandoHuancaRama(e, 0)));
        indice.setRoot(raiz);
        indice.setShowRoot(false);
        indice.setCellFactory(t -> new javafx.scene.control.TreeCell<>() {
            @Override protected void updateItem(ManualPdf.Entrada item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : item.titulo());
            }
        });
        indice.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (item != null && item.getValue().pagina() >= 0) fernandoHuancaIrA(new ManualPdf.Lugar(item.getValue().pagina(), 0));
        });
        Label tituloIndice = new Label("Contenido");
        tituloIndice.getStyleClass().add(Styles.TITLE_4);
        VBox panelIndice = new VBox(8, tituloIndice, indice);
        panelIndice.setPadding(new Insets(10));
        VBox.setVgrow(indice, Priority.ALWAYS);
        if (raiz.getChildren().isEmpty()) panelIndice.getChildren().add(1, new Label("Este manual no tiene índice."));

        fernandoHuancaPaginas.setAlignment(Pos.TOP_CENTER);
        fernandoHuancaPaginas.setPadding(new Insets(16));
        fernandoHuancaPaginas.getStyleClass().add("ayuda-paginas");
        fernandoHuancaDesplazamiento.setFitToWidth(true);
        fernandoHuancaDesplazamiento.getStyleClass().add("ayuda-desplazamiento");
        for (int i = 0; i < manual.fernandoHuancaPaginas(); i++) {
            ImageView vista = new ImageView();
            vista.setPreserveRatio(true);
            StackPane marco = new StackPane(vista);
            marco.getStyleClass().add("ayuda-pagina");
            marco.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 2);");
            fernandoHuancaMarcos.add(marco);
            fernandoHuancaImagenes.add(vista);
            fernandoHuancaTamanos.add(manual.fernandoHuancaTamano(i));
        }
        fernandoHuancaPaginas.getChildren().setAll(fernandoHuancaMarcos);
        fernandoHuancaDesplazamiento.vvalueProperty().addListener((o, a, b) -> fernandoHuancaActualizar());
        fernandoHuancaDesplazamiento.viewportBoundsProperty().addListener((o, a, b) -> fernandoHuancaActualizar());

        ToggleButton verIndice = new ToggleButton(null, new FontIcon(Feather.LIST));
        verIndice.setSelected(true);
        verIndice.setTooltip(new Tooltip("Mostrar u ocultar el contenido"));
        Button anterior = fernandoHuancaBoton(Feather.CHEVRON_UP, "Página anterior (Re Pág)", () -> fernandoHuancaSaltar(-1));
        Button siguiente = fernandoHuancaBoton(Feather.CHEVRON_DOWN, "Página siguiente (Av Pág)", () -> fernandoHuancaSaltar(1));
        fernandoHuancaCampoPagina.setPrefColumnCount(3);
        fernandoHuancaCampoPagina.setId("ayudaPagina");
        fernandoHuancaCampoPagina.setOnAction(e -> {
            try {
                int n = Integer.parseInt(fernandoHuancaCampoPagina.getText().strip());
                fernandoHuancaIrA(new ManualPdf.Lugar(Math.max(1, Math.min(n, fernandoHuancaMarcos.size())) - 1, 0));
            } catch (NumberFormatException ex) {
                fernandoHuancaMostrarPagina();
            }
        });
        Label total = new Label("de " + manual.fernandoHuancaPaginas());
        Button alejar = fernandoHuancaBoton(Feather.ZOOM_OUT, "Alejar (Ctrl -)", () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom / 1.2));
        Button acercar = fernandoHuancaBoton(Feather.ZOOM_IN, "Acercar (Ctrl +)", () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom * 1.2));
        TextField buscar = new TextField();
        buscar.setId("ayudaBuscar");
        buscar.setPromptText("Buscar en el manual (Enter)");
        buscar.setPrefColumnCount(18);
        buscar.setOnAction(e -> fernandoHuancaBuscar(buscar.getText()));
        Button abrir = new Button("Abrir en el lector de PDF", new FontIcon(Feather.EXTERNAL_LINK));
        abrir.setTooltip(new Tooltip("Abre el manual en el programa de PDF de la computadora (para imprimirlo o guardarlo)"));
        abrir.setOnAction(e -> abrirExterno.accept(manual.fernandoHuancaBytes()));
        abrir.setVisible(abrirExterno != null);
        abrir.setManaged(abrirExterno != null);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        fernandoHuancaMensaje.getStyleClass().add(Styles.TEXT_MUTED);
        ToolBar barra = new ToolBar(verIndice, anterior, siguiente, fernandoHuancaCampoPagina, total,
                alejar, fernandoHuancaZoomTexto, acercar, buscar, fernandoHuancaMensaje, espacio, abrir);

        SplitPane division = new SplitPane(panelIndice, fernandoHuancaDesplazamiento);
        division.setDividerPositions(0.27);
        SplitPane.setResizableWithParent(panelIndice, false);
        verIndice.selectedProperty().addListener((o, a, ver) -> {
            if (ver) { division.getItems().add(0, panelIndice); division.setDividerPositions(0.27); }
            else division.getItems().remove(panelIndice);
        });

        BorderPane contenido = new BorderPane(division);
        contenido.setTop(barra);
        Scene escena = new Scene(contenido, 1180, 820);
        if (duena != null && duena.getScene() != null) {
            escena.getStylesheets().setAll(duena.getScene().getStylesheets());
            if (duena.getScene().getRoot().getStyleClass().contains("dark")) contenido.getStyleClass().add("dark");
        }
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.PAGE_DOWN), () -> fernandoHuancaSaltar(1));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.PAGE_UP), () -> fernandoHuancaSaltar(-1));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.SHORTCUT_DOWN), () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom * 1.2));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom * 1.2));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom / 1.2));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN), () -> fernandoHuancaCambiarZoom(fernandoHuancaZoom / 1.2));
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), buscar::requestFocus);
        escena.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), fernandoHuancaVentana::close);
        fernandoHuancaVentana.setScene(escena);
        fernandoHuancaVentana.setOnHidden(e -> fernandoHuancaCerrar());
        fernandoHuancaCambiarZoom(fernandoHuancaZoom);
    }

    public static boolean fernandoHuancaMostrar(Window duena, String ancla, Consumer<byte[]> abrirExterno) {
        if (fernandoHuancaAbierto == null) {
            Optional<ManualPdf> manual;
            try {
                manual = ManualPdf.fernandoHuancaAbrirIncluido();
            } catch (IOException ex) {
                manual = Optional.empty();
            }
            if (manual.isEmpty()) return false;
            fernandoHuancaAbierto = new VisorAyuda(manual.get(), duena, abrirExterno);
        }
        VisorAyuda visor = fernandoHuancaAbierto;
        visor.fernandoHuancaVentana.show();
        visor.fernandoHuancaVentana.toFront();
        ManualPdf.Lugar lugar = ancla == null ? null : visor.fernandoHuancaManual.fernandoHuancaAncla(ancla).orElse(null);
        Platform.runLater(() -> visor.fernandoHuancaIrA(lugar != null ? lugar : new ManualPdf.Lugar(0, 0)));
        return true;
    }

    static VisorAyuda fernandoHuancaActual() {
        return fernandoHuancaAbierto;
    }

    Stage fernandoHuancaVentana() {
        return fernandoHuancaVentana;
    }

    private TreeItem<ManualPdf.Entrada> fernandoHuancaRama(ManualPdf.Entrada e, int nivel) {
        TreeItem<ManualPdf.Entrada> item = new TreeItem<>(e);
        item.setExpanded(nivel == 0);
        e.hijos().forEach(h -> item.getChildren().add(fernandoHuancaRama(h, nivel + 1)));
        return item;
    }

    void fernandoHuancaIrA(ManualPdf.Lugar lugar) {
        int i = Math.max(0, Math.min(lugar.pagina(), fernandoHuancaMarcos.size() - 1));
        fernandoHuancaPaginas.applyCss();
        fernandoHuancaPaginas.layout();
        StackPane marco = fernandoHuancaMarcos.get(i);
        double total = fernandoHuancaPaginas.getHeight() - fernandoHuancaDesplazamiento.getViewportBounds().getHeight();
        double y = marco.getLayoutY() + lugar.desdeArriba() * marco.getHeight() - (lugar.desdeArriba() > 0 ? 60 * fernandoHuancaZoom : 8);
        fernandoHuancaDesplazamiento.setVvalue(total <= 0 ? 0 : Math.max(0, Math.min(1, y / total)));
        fernandoHuancaActualizar();
    }

    int fernandoHuancaPaginaActual() {
        double arriba = fernandoHuancaArribaVisible();
        for (int i = 0; i < fernandoHuancaMarcos.size(); i++) {
            StackPane m = fernandoHuancaMarcos.get(i);
            if (m.getLayoutY() + m.getHeight() > arriba + 1) return i;
        }
        return fernandoHuancaMarcos.size() - 1;
    }

    private void fernandoHuancaSaltar(int paso) {
        fernandoHuancaIrA(new ManualPdf.Lugar(fernandoHuancaPaginaActual() + paso, 0));
    }

    private void fernandoHuancaBuscar(String texto) {
        if (texto == null || texto.isBlank()) return;
        int desde = fernandoHuancaPaginaActual() + 1;
        fernandoHuancaMensaje.setText("Buscando…");
        fernandoHuancaHilo.submit(() -> {
            int encontrada;
            try {
                encontrada = fernandoHuancaManual.fernandoHuancaBuscar(texto, desde);
            } catch (IOException ex) {
                encontrada = -1;
            }
            int pagina = encontrada;
            Platform.runLater(() -> {
                if (pagina < 0) {
                    fernandoHuancaMensaje.setText("No se encontró «" + texto.strip() + "».");
                } else {
                    fernandoHuancaMensaje.setText("Encontrado en la página " + (pagina + 1) + " (Enter: siguiente).");
                    fernandoHuancaIrA(new ManualPdf.Lugar(pagina, 0));
                }
            });
        });
    }

    private void fernandoHuancaCambiarZoom(double zoom) {
        fernandoHuancaZoom = Math.max(0.5, Math.min(3.0, zoom));
        fernandoHuancaZoomTexto.setText(Math.round(fernandoHuancaZoom * 100) + " %");
        int actual = fernandoHuancaMarcos.isEmpty() ? 0 : fernandoHuancaPaginaActual();
        fernandoHuancaGeneracion++;
        fernandoHuancaPedidas.clear();
        for (int i = 0; i < fernandoHuancaMarcos.size(); i++) {
            double[] tam = fernandoHuancaTamanos.get(i);
            StackPane marco = fernandoHuancaMarcos.get(i);
            double ancho = tam[0] * fernandoHuancaZoom, alto = tam[1] * fernandoHuancaZoom;
            marco.setMinSize(ancho, alto);
            marco.setPrefSize(ancho, alto);
            marco.setMaxSize(ancho, alto);
            ImageView vista = fernandoHuancaImagenes.get(i);
            vista.setFitWidth(ancho);
            vista.setImage(null);
        }
        Platform.runLater(() -> fernandoHuancaIrA(new ManualPdf.Lugar(actual, 0)));
    }

    private double fernandoHuancaArribaVisible() {
        double total = fernandoHuancaPaginas.getHeight() - fernandoHuancaDesplazamiento.getViewportBounds().getHeight();
        return Math.max(0, total) * fernandoHuancaDesplazamiento.getVvalue();
    }

    private void fernandoHuancaActualizar() {
        if (fernandoHuancaMarcos.isEmpty()) return;
        fernandoHuancaMostrarPagina();
        double arriba = fernandoHuancaArribaVisible();
        double abajo = arriba + fernandoHuancaDesplazamiento.getViewportBounds().getHeight();
        int primera = -1, ultima = -1;
        for (int i = 0; i < fernandoHuancaMarcos.size(); i++) {
            StackPane m = fernandoHuancaMarcos.get(i);
            if (m.getLayoutY() + m.getHeight() >= arriba && m.getLayoutY() <= abajo) {
                if (primera < 0) primera = i;
                ultima = i;
            }
        }
        if (primera < 0) return;
        int desde = Math.max(0, primera - FERNANDO_HUANCA_MARGEN_PAGINAS);
        int hasta = Math.min(fernandoHuancaMarcos.size() - 1, ultima + FERNANDO_HUANCA_MARGEN_PAGINAS);
        for (int i = 0; i < fernandoHuancaMarcos.size(); i++) {
            if (i < desde || i > hasta) {
                fernandoHuancaImagenes.get(i).setImage(null);
                fernandoHuancaPedidas.remove(i);
            } else if (fernandoHuancaPedidas.add(i)) {
                fernandoHuancaPedir(i);
            }
        }
    }

    private void fernandoHuancaPedir(int pagina) {
        int generacion = fernandoHuancaGeneracion;
        double salida = fernandoHuancaVentana.getOutputScaleX() > 0 ? fernandoHuancaVentana.getOutputScaleX() : 1;
        double escala = fernandoHuancaZoom * salida;
        fernandoHuancaHilo.submit(() -> {
            try {
                BufferedImage imagen = fernandoHuancaManual.fernandoHuancaDibujar(pagina, escala);
                WritableImage fx = fernandoHuancaConvertir(imagen);
                Platform.runLater(() -> {
                    if (generacion == fernandoHuancaGeneracion && fernandoHuancaPedidas.contains(pagina)) {
                        fernandoHuancaImagenes.get(pagina).setImage(fx);
                    }
                });
            } catch (IOException | RuntimeException ex) {
                Platform.runLater(() -> fernandoHuancaMensaje.setText("No se pudo mostrar la página " + (pagina + 1) + "."));
            }
        });
    }

    static WritableImage fernandoHuancaConvertir(BufferedImage imagen) {
        int ancho = imagen.getWidth(), alto = imagen.getHeight();
        int[] pixeles = imagen.getRaster().getDataBuffer() instanceof DataBufferInt d && imagen.getType() == BufferedImage.TYPE_INT_ARGB
                ? d.getData()
                : imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);
        WritableImage fx = new WritableImage(ancho, alto);
        fx.getPixelWriter().setPixels(0, 0, ancho, alto, PixelFormat.getIntArgbInstance(), pixeles, 0, ancho);
        return fx;
    }

    private void fernandoHuancaMostrarPagina() {
        if (!fernandoHuancaCampoPagina.isFocused()) {
            fernandoHuancaCampoPagina.setText(Integer.toString(fernandoHuancaPaginaActual() + 1));
        }
    }

    private static Button fernandoHuancaBoton(Feather icono, String ayuda, Runnable accion) {
        Button b = new Button(null, new FontIcon(icono));
        b.getStyleClass().add(Styles.BUTTON_ICON);
        b.setTooltip(new Tooltip(ayuda));
        b.setAccessibleText(ayuda);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void fernandoHuancaCerrar() {
        fernandoHuancaHilo.shutdownNow();
        try {
            fernandoHuancaManual.close();
        } catch (IOException ex) {
        }
        if (fernandoHuancaAbierto == this) fernandoHuancaAbierto = null;
    }

    @Override
    public String toString() {
        return "VisorAyuda[" + Objects.requireNonNullElse(fernandoHuancaVentana.getTitle(), "") + "]";
    }
}
