package com.concretolab.ui;

import com.concretolab.model.Elemento;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

/** Visor 3D del modelo: solo presentación, no modifica datos. X e Y son horizontales y Z es vertical (en metros). */
public class MiajeldDelgadoVisor3D {
    private static final double ESCALA = 65;
    // Colores de los materiales 3D (no son CSS). Los de la leyenda 2D están en css/pendiente-modelo.css.
    private static final Color EJE_X = Color.web("#ef4444"), EJE_Y = Color.web("#22c55e"), EJE_Z = Color.web("#3b82f6");
    private static final Color COLOR_SELECCION = Color.web("#f59e0b");
    private static final Color COLOR_ZAPATA = Color.web("#78716c");
    private static final Color COLOR_COLUMNA = Color.web("#3b82f6");
    private static final Color COLOR_VIGA = Color.web("#14b8a6");
    private static final Color COLOR_LOSA = Color.web("#e2e8f0");
    private static final Color COLOR_LINEA = Color.web("#94a3b8");

    private final Group contenido = new Group(), suelo = new Group(), ejes = new Group(), elementos = new Group();
    private final Group mundo = new Group(contenido);
    private final Rotate rotacionX = new Rotate(-25, Rotate.X_AXIS), rotacionY = new Rotate(-35, Rotate.Y_AXIS);
    private final Translate desplazamiento = new Translate();
    private final PerspectiveCamera camara = new PerspectiveCamera(true);
    private final StackPane nodo = new StackPane();
    private double radioModelo = 400, distancia = 1200, inicioX, inicioY, anguloX, anguloY, panX, panY;

    public MiajeldDelgadoVisor3D() {
        contenido.getChildren().addAll(suelo, ejes, elementos);
        mundo.getTransforms().addAll(desplazamiento, rotacionX, rotacionY);

        AmbientLight ambiente = new AmbientLight(Color.gray(0.42));
        PointLight principal = luz(Color.gray(0.85), -700, -1100, -900);
        PointLight relleno = luz(Color.gray(0.35), 900, -300, -500);
        PointLight contraluz = luz(Color.gray(0.22), 200, -600, 1000);
        Group raiz = new Group(mundo, ambiente, principal, relleno, contraluz);

        SubScene escena = new SubScene(raiz, 600, 500, true, SceneAntialiasing.BALANCED);
        escena.setFill(Color.TRANSPARENT);
        camara.setNearClip(1);
        camara.setFarClip(30000);
        camara.setTranslateZ(-distancia);
        escena.setCamera(camara);

        escena.setOnMousePressed(e -> {
            inicioX = e.getSceneX(); inicioY = e.getSceneY();
            anguloX = rotacionX.getAngle(); anguloY = rotacionY.getAngle();
            panX = desplazamiento.getX(); panY = desplazamiento.getY();
        });
        escena.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - inicioX, dy = e.getSceneY() - inicioY;
            if (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE || e.isShiftDown()) {
                double k = distancia / 1200;
                desplazamiento.setX(panX + dx * k);
                desplazamiento.setY(panY + dy * k);
            } else {
                rotacionY.setAngle(anguloY + dx * 0.5);
                rotacionX.setAngle(Math.max(-90, Math.min(10, anguloX - dy * 0.5)));
            }
        });
        escena.setOnScroll(e -> {
            distancia = Math.max(150, Math.min(9000, distancia * (e.getDeltaY() > 0 ? 0.9 : 1 / 0.9)));
            camara.setTranslateZ(-distancia);
        });

        nodo.getStyleClass().add("visor-fondo");
        nodo.setMinSize(200, 200);
        escena.widthProperty().bind(nodo.widthProperty());
        escena.heightProperty().bind(nodo.heightProperty());
        nodo.getChildren().addAll(escena, barra(), leyenda());
    }

    public StackPane nodo() { return nodo; }

    /** Redibuja los elementos. Si {@code reencuadrar} es true, ajusta el suelo y la cámara al modelo. */
    public void mostrar(List<Elemento> lista, Elemento seleccionado, Consumer<Elemento> alSeleccionar, boolean reencuadrar) {
        elementos.getChildren().clear();
        for (Elemento e : lista) {
            Box caja = new Box(e.getLargo() * ESCALA, e.getAltura() * ESCALA, e.getAncho() * ESCALA);
            caja.setTranslateX(e.getX() * ESCALA);
            caja.setTranslateY(-e.getZ() * ESCALA);
            caja.setTranslateZ(e.getY() * ESCALA);
            caja.setMaterial(material(e == seleccionado ? COLOR_SELECCION : colorDe(e.getTipo()), e == seleccionado));
            caja.setOnMouseClicked(ev -> { if (ev.isStillSincePress() && ev.getButton() == MouseButton.PRIMARY) alSeleccionar.accept(e); });
            elementos.getChildren().add(caja);
        }
        if (reencuadrar) construirReferencias(lista);
    }

    Group capaElementos() { return elementos; }
    static double unidades() { return ESCALA; }
    private void construirReferencias(List<Elemento> lista) {
        double minX = -3, maxX = 3, minY = -3, maxY = 3, base = 0, alto = 3;
        if (!lista.isEmpty()) {
            minX = minY = base = Double.MAX_VALUE;
            maxX = maxY = alto = -Double.MAX_VALUE;
            for (Elemento e : lista) {
                minX = Math.min(minX, e.getX() - e.getLargo() / 2); maxX = Math.max(maxX, e.getX() + e.getLargo() / 2);
                minY = Math.min(minY, e.getY() - e.getAncho() / 2); maxY = Math.max(maxY, e.getY() + e.getAncho() / 2);
                base = Math.min(base, e.getZ() - e.getAltura() / 2); alto = Math.max(alto, e.getZ() + e.getAltura() / 2);
            }
        }
        int x0 = (int) Math.floor(minX) - 2, x1 = (int) Math.ceil(maxX) + 2, y0 = (int) Math.floor(minY) - 2, y1 = (int) Math.ceil(maxY) + 2;
        int paso = Math.max(1, (int) Math.ceil(Math.max(x1 - x0, y1 - y0) / 40.0)); // evita demasiadas líneas en modelos grandes
        x1 = x0 + (int) Math.ceil((x1 - x0) / (double) paso) * paso;
        y1 = y0 + (int) Math.ceil((y1 - y0) / (double) paso) * paso;

        double nivel = -base * ESCALA, w = (x1 - x0) * ESCALA, d = (y1 - y0) * ESCALA;
        double cx = (x0 + x1) / 2.0 * ESCALA, cy = (y0 + y1) / 2.0 * ESCALA;

        suelo.getChildren().clear();
        Box losa = new Box(w, 2, d);
        losa.setTranslateX(cx); losa.setTranslateZ(cy); losa.setTranslateY(nivel + 1.5);
        losa.setMaterial(material(COLOR_LOSA, false));
        suelo.getChildren().add(losa);
        PhongMaterial linea = new PhongMaterial(COLOR_LINEA);
        for (int x = x0; x <= x1; x += paso) {
            Box l = new Box(x == 0 ? 1.6 : 0.8, 0.8, d);
            l.setTranslateX(x * ESCALA); l.setTranslateZ(cy); l.setTranslateY(nivel + 0.3); l.setMaterial(linea);
            suelo.getChildren().add(l);
        }
        for (int y = y0; y <= y1; y += paso) {
            Box l = new Box(w, 0.8, y == 0 ? 1.6 : 0.8);
            l.setTranslateX(cx); l.setTranslateZ(y * ESCALA); l.setTranslateY(nivel + 0.3); l.setMaterial(linea);
            suelo.getChildren().add(l);
        }

        ejes.getChildren().clear();
        double largo = Math.max(1.5, Math.min(4, (maxX - minX) / 2)) * ESCALA, g = 3;
        double ox = x0 * ESCALA, oy = y0 * ESCALA;
        Box ex = new Box(largo, g, g); ex.setTranslateX(ox + largo / 2); ex.setTranslateZ(oy); ex.setTranslateY(nivel - 2); ex.setMaterial(material(EJE_X, false));
        Box ey = new Box(g, g, largo); ey.setTranslateX(ox); ey.setTranslateZ(oy + largo / 2); ey.setTranslateY(nivel - 2); ey.setMaterial(material(EJE_Y, false));
        Box ez = new Box(g, largo, g); ez.setTranslateX(ox); ez.setTranslateZ(oy); ez.setTranslateY(nivel - 2 - largo / 2); ez.setMaterial(material(EJE_Z, false));
        ejes.getChildren().addAll(ex, ey, ez);

        // Ajusta el cuadro: centra el modelo y aleja la cámara según su tamaño.
        double radio = Math.hypot(Math.hypot(w, d), (alto - base) * ESCALA) / 2;
        radioModelo = radio;
        desplazamiento.setX(0); desplazamiento.setY(0);
        contenido.getTransforms().setAll(new Translate(-cx, -(nivel - (alto - base) * ESCALA / 2), -cy));
        distancia = distanciaParaEncuadrar(radio);
        camara.setTranslateZ(-distancia);
    }

    /** La cámara tiene un campo de visión vertical de 30°: una esfera de radio r cabe a r / sen(15°) ≈ 3,86·r, con un 10 % de margen. */
    private static double distanciaParaEncuadrar(double radio) {
        return Math.max(500, radio / Math.sin(Math.toRadians(15)) * 1.1);
    }

    private HBox barra() {
        Button iso = boton("Isométrica", () -> vista(-25, -35));
        Button planta = boton("Planta", () -> vista(-90, 0));
        Button frontal = boton("Frontal", () -> vista(0, 0));
        Button lateral = boton("Lateral", () -> vista(0, -90));
        Button ajustar = boton("Ajustar", this::ajustar);
        ToggleButton cuadricula = new ToggleButton("Cuadrícula"), ejesBtn = new ToggleButton("Ejes");
        for (ToggleButton t : List.of(cuadricula, ejesBtn)) { t.setSelected(true); t.getStyleClass().add("visor-boton"); }
        cuadricula.setOnAction(e -> suelo.setVisible(cuadricula.isSelected()));
        ejesBtn.setOnAction(e -> ejes.setVisible(ejesBtn.isSelected()));
        HBox caja = new HBox(6, iso, planta, frontal, lateral, ajustar, cuadricula, ejesBtn);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setPadding(new Insets(6));
        caja.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
        caja.getStyleClass().add("visor-barra");
        StackPane.setAlignment(caja, Pos.TOP_LEFT);
        StackPane.setMargin(caja, new Insets(10));
        return caja;
    }

    private VBox leyenda() {
        VBox caja = new VBox(2, texto("X", "visor-leyenda-x"), texto("Y", "visor-leyenda-y"), texto("Z (vertical)", "visor-leyenda-z"));
        caja.setPadding(new Insets(6, 10, 6, 10));
        caja.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        caja.getStyleClass().add("visor-barra");
        caja.setMouseTransparent(true);
        StackPane.setAlignment(caja, Pos.BOTTOM_LEFT);
        StackPane.setMargin(caja, new Insets(10));
        return caja;
    }

    private Label texto(String t, String claseColor) {
        Label l = new Label("● " + t);
        l.getStyleClass().addAll("visor-leyenda", claseColor);
        return l;
    }

    private Button boton(String t, Runnable accion) {
        Button b = new Button(t);
        b.getStyleClass().add("visor-boton");
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void vista(double ax, double ay) {
        // Gira por el camino corto para que la animación no dé vueltas completas.
        double actual = rotacionY.getAngle(), destino = ay + 360 * Math.round((actual - ay) / 360);
        new Timeline(new KeyFrame(Duration.millis(350),
                new KeyValue(rotacionX.angleProperty(), ax), new KeyValue(rotacionY.angleProperty(), destino),
                new KeyValue(desplazamiento.xProperty(), 0.0), new KeyValue(desplazamiento.yProperty(), 0.0))).play();
    }

    private void ajustar() {
        vista(-25, -35);
        distancia = distanciaParaEncuadrar(radioModelo);
        new Timeline(new KeyFrame(Duration.millis(350), new KeyValue(camara.translateZProperty(), -distancia))).play();
    }

    private PointLight luz(Color color, double x, double y, double z) {
        PointLight l = new PointLight(color);
        l.setTranslateX(x); l.setTranslateY(y); l.setTranslateZ(z);
        return l;
    }

    private PhongMaterial material(Color color, boolean resaltado) {
        PhongMaterial m = new PhongMaterial(color);
        m.setSpecularColor(resaltado ? Color.gray(0.7) : Color.gray(0.28));
        m.setSpecularPower(resaltado ? 60 : 35);
        return m;
    }

    private Color colorDe(String tipo) {
        return switch (tipo) {
            case "ZAPATA" -> COLOR_ZAPATA;
            case "COLUMNA" -> COLOR_COLUMNA;
            default -> COLOR_VIGA;
        };
    }
}
