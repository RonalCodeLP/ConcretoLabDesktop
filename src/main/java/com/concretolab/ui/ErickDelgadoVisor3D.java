package com.concretolab.ui;

import com.concretolab.model.Elemento;
import java.util.List;
import java.util.function.Consumer;
import com.concretolab.service.MovimientoElementoService.Posicion;
import com.concretolab.service.MovimientoElementoService.Referencias;
import com.concretolab.service.MovimientoElementoService.Estado;
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
public class ErickDelgadoVisor3D {
    private static final double ERICK_DELGADO_ESCALA = 65;
    // Colores de los materiales 3D (no son CSS). Los de la leyenda 2D están en css/pendiente-modelo.css.
    private static final Color ERICK_DELGADO_EJE_X = Color.web("#ef4444"), ERICK_DELGADO_EJE_Y = Color.web("#22c55e"), ERICK_DELGADO_EJE_Z = Color.web("#3b82f6");
    private static final Color ERICK_DELGADO_COLOR_SELECCION = Color.web("#f59e0b");
    private static final Color ERICK_DELGADO_COLOR_ZAPATA = Color.web("#78716c");
    private static final Color ERICK_DELGADO_COLOR_COLUMNA = Color.web("#3b82f6");
    private static final Color ERICK_DELGADO_COLOR_VIGA = Color.web("#14b8a6");
    private static final Color ERICK_DELGADO_COLOR_LOSA = Color.web("#e2e8f0");
    private static final Color ERICK_DELGADO_COLOR_LINEA = Color.web("#94a3b8");

    private final Group contenido = new Group(), erickDelgadoSuelo = new Group(), ejes = new Group(), erickDelgadoReferencias = new Group(), elementos = new Group();
    private final Group erickDelgadoMundo = new Group(contenido);
    private final Rotate erickDelgadoRotacionX = new Rotate(25, Rotate.X_AXIS), erickDelgadoRotacionY = new Rotate(-35, Rotate.Y_AXIS);
    private final Translate erickDelgadoDesplazamiento = new Translate();
    private final PerspectiveCamera erickDelgadoCamara = new PerspectiveCamera(true);
    private final StackPane erickDelgadoNodo = new StackPane();
    private double erickDelgadoRadioModelo = 400, erickDelgadoDistancia = 1200, erickDelgadoInicioX, erickDelgadoInicioY, erickDelgadoAnguloX, erickDelgadoAnguloY, erickDelgadoPanX, erickDelgadoPanY;
    private Long erickDelgadoIdMovimiento;
    private Consumer<Double> erickDelgadoAlArrastrar;
    private double erickDelgadoInicioArrastre;

    public ErickDelgadoVisor3D() {
        contenido.getChildren().addAll(erickDelgadoSuelo, ejes, erickDelgadoReferencias, elementos);
        erickDelgadoMundo.getTransforms().addAll(erickDelgadoDesplazamiento, erickDelgadoRotacionX, erickDelgadoRotacionY);

        AmbientLight ambiente = new AmbientLight(Color.gray(0.42));
        PointLight principal = erickDelgadoLuz(Color.gray(0.85), -700, -1100, -900);
        PointLight relleno = erickDelgadoLuz(Color.gray(0.35), 900, -300, -500);
        PointLight contraluz = erickDelgadoLuz(Color.gray(0.22), 200, -600, 1000);
        Group raiz = new Group(erickDelgadoMundo, ambiente, principal, relleno, contraluz);

        SubScene escena = new SubScene(raiz, 600, 500, true, SceneAntialiasing.BALANCED);
        escena.setFill(Color.TRANSPARENT);
        erickDelgadoCamara.setNearClip(1);
        erickDelgadoCamara.setFarClip(30000);
        erickDelgadoCamara.setTranslateZ(-erickDelgadoDistancia);
        escena.setCamera(erickDelgadoCamara);

        escena.setOnMousePressed(e -> {
            erickDelgadoInicioX = e.getSceneX(); erickDelgadoInicioY = e.getSceneY();
            erickDelgadoAnguloX = erickDelgadoRotacionX.getAngle(); erickDelgadoAnguloY = erickDelgadoRotacionY.getAngle();
            erickDelgadoPanX = erickDelgadoDesplazamiento.getX(); erickDelgadoPanY = erickDelgadoDesplazamiento.getY();
        });
        escena.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - erickDelgadoInicioX, dy = e.getSceneY() - erickDelgadoInicioY;
            if (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE || e.isShiftDown()) {
                double k = erickDelgadoDistancia / 1200;
                erickDelgadoDesplazamiento.setX(erickDelgadoPanX + dx * k);
                erickDelgadoDesplazamiento.setY(erickDelgadoPanY + dy * k);
            } else {
                erickDelgadoRotacionY.setAngle(erickDelgadoAnguloY + dx * 0.5);
                erickDelgadoRotacionX.setAngle(Math.max(-10, Math.min(90, erickDelgadoAnguloX + dy * 0.5)));
            }
        });
        escena.setOnScroll(e -> {
            erickDelgadoDistancia = Math.max(150, Math.min(9000, erickDelgadoDistancia * (e.getDeltaY() > 0 ? 0.9 : 1 / 0.9)));
            erickDelgadoCamara.setTranslateZ(-erickDelgadoDistancia);
        });

        erickDelgadoNodo.getStyleClass().add("visor-fondo");
        erickDelgadoNodo.setMinSize(200, 200);
        escena.widthProperty().bind(erickDelgadoNodo.widthProperty());
        escena.heightProperty().bind(erickDelgadoNodo.heightProperty());
        erickDelgadoNodo.getChildren().addAll(escena, erickDelgadoBarra(), erickDelgadoLeyenda());
    }

    public StackPane erickDelgadoNodo() { return erickDelgadoNodo; }

    /** Redibuja los elementos. Si {@code reencuadrar} es true, ajusta el suelo y la cámara al modelo. */
    public void erickDelgadoMostrar(List<Elemento> lista, Elemento seleccionado, Consumer<Elemento> alSeleccionar, boolean reencuadrar) {
        elementos.getChildren().clear();
        for (Elemento e : lista) {
            Box caja = new Box(e.getLargo() * ERICK_DELGADO_ESCALA, e.getAltura() * ERICK_DELGADO_ESCALA, e.getAncho() * ERICK_DELGADO_ESCALA);
            caja.setTranslateX(e.getX() * ERICK_DELGADO_ESCALA);
            caja.setTranslateY(-e.getZ() * ERICK_DELGADO_ESCALA);
            caja.setTranslateZ(e.getY() * ERICK_DELGADO_ESCALA);
            caja.setMaterial(erickDelgadoMaterial(e == seleccionado ? ERICK_DELGADO_COLOR_SELECCION : erickDelgadoColorDe(e.getTipo()), e == seleccionado));
            caja.setUserData(e.getId());
            caja.setOnMouseClicked(ev -> { if (erickDelgadoIdMovimiento == null && ev.isStillSincePress() && ev.getButton() == MouseButton.PRIMARY) alSeleccionar.accept(e); });
            caja.setOnMousePressed(ev -> {
                if (erickDelgadoIdMovimiento != null && erickDelgadoIdMovimiento.equals(e.getId()) && ev.getButton() == MouseButton.PRIMARY) {
                    erickDelgadoInicioArrastre = ev.getSceneX();
                    ev.consume();
                }
            });
            caja.setOnMouseDragged(ev -> {
                if (erickDelgadoIdMovimiento != null && erickDelgadoIdMovimiento.equals(e.getId()) && ev.isPrimaryButtonDown()) {
                    double delta = (ev.getSceneX() - erickDelgadoInicioArrastre) * erickDelgadoDistancia / 1200 / ERICK_DELGADO_ESCALA;
                    erickDelgadoInicioArrastre = ev.getSceneX();
                    erickDelgadoAlArrastrar.accept(delta);
                    ev.consume();
                }
            });
            elementos.getChildren().add(caja);
        }
        if (reencuadrar) erickDelgadoConstruirReferencias(lista);
    }

    public void erickDelgadoIniciarMovimiento(Long id, Consumer<Double> alArrastrar) {
        this.erickDelgadoIdMovimiento = id;
        this.erickDelgadoAlArrastrar = alArrastrar;
    }

    public void erickDelgadoTerminarMovimiento() {
        erickDelgadoIdMovimiento = null;
        erickDelgadoAlArrastrar = null;
        erickDelgadoReferencias.getChildren().clear();
    }

    public void erickDelgadoPrevisualizar(Long id, Posicion posicion) {
        elementos.getChildren().stream().filter(n -> id.equals(n.getUserData())).findFirst().ifPresent(n -> {
            n.setTranslateX(posicion.x() * ERICK_DELGADO_ESCALA);
            n.setTranslateY(-posicion.z() * ERICK_DELGADO_ESCALA);
            n.setTranslateZ(posicion.y() * ERICK_DELGADO_ESCALA);
        });
    }

    public void erickDelgadoPrevisualizar(Estado estado) {
        elementos.getChildren().stream().filter(n -> estado.id().equals(n.getUserData())).findFirst().ifPresent(n -> {
            Box caja = (Box) n;
            caja.setWidth(estado.largo() * ERICK_DELGADO_ESCALA); caja.setDepth(estado.ancho() * ERICK_DELGADO_ESCALA); caja.setHeight(estado.altura() * ERICK_DELGADO_ESCALA);
        });
        erickDelgadoPrevisualizar(estado.id(), estado.posicion());
    }

    public void erickDelgadoMostrarReferencias(Referencias datos) {
        erickDelgadoReferencias.getChildren().clear();
        if (datos.ejesX().size() < 2 || datos.ejesY().size() < 2) return;
        double x0 = datos.ejesX().get(0), x1 = datos.ejesX().get(datos.ejesX().size() - 1);
        double y0 = datos.ejesY().get(0), y1 = datos.ejesY().get(datos.ejesY().size() - 1);
        for (double nivel : datos.niveles()) {
            for (double x : datos.ejesX()) erickDelgadoLineaReferencia(x, (y0 + y1) / 2, nivel, 0.025, y1 - y0, 0.025);
            for (double y : datos.ejesY()) erickDelgadoLineaReferencia((x0 + x1) / 2, y, nivel, x1 - x0, 0.025, 0.025);
        }
    }

    private void erickDelgadoLineaReferencia(double x, double y, double z, double largo, double ancho, double alto) {
        Box linea = new Box(largo * ERICK_DELGADO_ESCALA, alto * ERICK_DELGADO_ESCALA, ancho * ERICK_DELGADO_ESCALA);
        linea.setTranslateX(x * ERICK_DELGADO_ESCALA); linea.setTranslateY(-z * ERICK_DELGADO_ESCALA); linea.setTranslateZ(y * ERICK_DELGADO_ESCALA);
        linea.setMaterial(new PhongMaterial(Color.ORANGE));
        linea.setMouseTransparent(true);
        erickDelgadoReferencias.getChildren().add(linea);
    }

    Group erickDelgadoCapaElementos() { return elementos; }
    static double erickDelgadoUnidades() { return ERICK_DELGADO_ESCALA; }
    private void erickDelgadoConstruirReferencias(List<Elemento> lista) {
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

        double nivel = -base * ERICK_DELGADO_ESCALA, w = (x1 - x0) * ERICK_DELGADO_ESCALA, d = (y1 - y0) * ERICK_DELGADO_ESCALA;
        double cx = (x0 + x1) / 2.0 * ERICK_DELGADO_ESCALA, cy = (y0 + y1) / 2.0 * ERICK_DELGADO_ESCALA;

        erickDelgadoSuelo.getChildren().clear();
        Box losa = new Box(w, 2, d);
        losa.setTranslateX(cx); losa.setTranslateZ(cy); losa.setTranslateY(nivel + 1.5);
        losa.setMaterial(erickDelgadoMaterial(ERICK_DELGADO_COLOR_LOSA, false));
        erickDelgadoSuelo.getChildren().add(losa);
        PhongMaterial linea = new PhongMaterial(ERICK_DELGADO_COLOR_LINEA);
        for (int x = x0; x <= x1; x += paso) {
            Box l = new Box(x == 0 ? 1.6 : 0.8, 0.8, d);
            l.setTranslateX(x * ERICK_DELGADO_ESCALA); l.setTranslateZ(cy); l.setTranslateY(nivel + 0.3); l.setMaterial(linea);
            erickDelgadoSuelo.getChildren().add(l);
        }
        for (int y = y0; y <= y1; y += paso) {
            Box l = new Box(w, 0.8, y == 0 ? 1.6 : 0.8);
            l.setTranslateX(cx); l.setTranslateZ(y * ERICK_DELGADO_ESCALA); l.setTranslateY(nivel + 0.3); l.setMaterial(linea);
            erickDelgadoSuelo.getChildren().add(l);
        }

        ejes.getChildren().clear();
        double largo = Math.max(1.5, Math.min(4, (maxX - minX) / 2)) * ERICK_DELGADO_ESCALA, g = 3;
        double ox = x0 * ERICK_DELGADO_ESCALA, oy = y0 * ERICK_DELGADO_ESCALA;
        Box ex = new Box(largo, g, g); ex.setTranslateX(ox + largo / 2); ex.setTranslateZ(oy); ex.setTranslateY(nivel - 2); ex.setMaterial(erickDelgadoMaterial(ERICK_DELGADO_EJE_X, false));
        Box ey = new Box(g, g, largo); ey.setTranslateX(ox); ey.setTranslateZ(oy + largo / 2); ey.setTranslateY(nivel - 2); ey.setMaterial(erickDelgadoMaterial(ERICK_DELGADO_EJE_Y, false));
        Box ez = new Box(g, largo, g); ez.setTranslateX(ox); ez.setTranslateZ(oy); ez.setTranslateY(nivel - 2 - largo / 2); ez.setMaterial(erickDelgadoMaterial(ERICK_DELGADO_EJE_Z, false));
        ejes.getChildren().addAll(ex, ey, ez);

        // Ajusta el cuadro: centra el modelo y aleja la cámara según su tamaño.
        double radio = Math.hypot(Math.hypot(w, d), (alto - base) * ERICK_DELGADO_ESCALA) / 2;
        erickDelgadoRadioModelo = radio;
        erickDelgadoDesplazamiento.setX(0); erickDelgadoDesplazamiento.setY(0);
        contenido.getTransforms().setAll(new Translate(-cx, -(nivel - (alto - base) * ERICK_DELGADO_ESCALA / 2), -cy));
        erickDelgadoDistancia = erickDelgadoDistanciaParaEncuadrar(radio);
        erickDelgadoCamara.setTranslateZ(-erickDelgadoDistancia);
    }

    /** La cámara tiene un campo de visión vertical de 30°: una esfera de radio r cabe a r / sen(15°) ≈ 3,86·r, con un 10 % de margen. */
    private static double erickDelgadoDistanciaParaEncuadrar(double radio) {
        return Math.max(500, radio / Math.sin(Math.toRadians(15)) * 1.1);
    }

    private HBox erickDelgadoBarra() {
        Button iso = erickDelgadoBoton("Isométrica", () -> vista(25, -35));
        Button planta = erickDelgadoBoton("Planta", () -> vista(90, 0));
        Button frontal = erickDelgadoBoton("Frontal", () -> vista(0, 0));
        Button lateral = erickDelgadoBoton("Lateral", () -> vista(0, -90));
        Button ajustar = erickDelgadoBoton("Ajustar", this::erickDelgadoAjustar);
        ToggleButton cuadricula = new ToggleButton("Cuadrícula"), ejesBtn = new ToggleButton("Ejes");
        for (ToggleButton t : List.of(cuadricula, ejesBtn)) { t.setSelected(true); t.getStyleClass().add("visor-boton"); }
        cuadricula.setOnAction(e -> erickDelgadoSuelo.setVisible(cuadricula.isSelected()));
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

    private VBox erickDelgadoLeyenda() {
        VBox caja = new VBox(2, erickDelgadoTexto("X", "visor-leyenda-x"), erickDelgadoTexto("Y", "visor-leyenda-y"), erickDelgadoTexto("Z (vertical)", "visor-leyenda-z"));
        caja.setPadding(new Insets(6, 10, 6, 10));
        caja.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        caja.getStyleClass().add("visor-barra");
        caja.setMouseTransparent(true);
        StackPane.setAlignment(caja, Pos.BOTTOM_LEFT);
        StackPane.setMargin(caja, new Insets(10));
        return caja;
    }

    private Label erickDelgadoTexto(String t, String claseColor) {
        Label l = new Label("● " + t);
        l.getStyleClass().addAll("visor-leyenda", claseColor);
        return l;
    }

    private Button erickDelgadoBoton(String t, Runnable accion) {
        Button b = new Button(t);
        b.getStyleClass().add("visor-boton");
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void vista(double ax, double ay) {
        // Gira por el camino corto para que la animación no dé vueltas completas.
        double actual = erickDelgadoRotacionY.getAngle(), destino = ay + 360 * Math.round((actual - ay) / 360);
        new Timeline(new KeyFrame(Duration.millis(350),
                new KeyValue(erickDelgadoRotacionX.angleProperty(), ax), new KeyValue(erickDelgadoRotacionY.angleProperty(), destino),
                new KeyValue(erickDelgadoDesplazamiento.xProperty(), 0.0), new KeyValue(erickDelgadoDesplazamiento.yProperty(), 0.0))).play();
    }

    private void erickDelgadoAjustar() {
        vista(-25, -35);
        erickDelgadoDistancia = erickDelgadoDistanciaParaEncuadrar(erickDelgadoRadioModelo);
        new Timeline(new KeyFrame(Duration.millis(350), new KeyValue(erickDelgadoCamara.translateZProperty(), -erickDelgadoDistancia))).play();
    }

    private PointLight erickDelgadoLuz(Color color, double x, double y, double z) {
        PointLight l = new PointLight(color);
        l.setTranslateX(x); l.setTranslateY(y); l.setTranslateZ(z);
        return l;
    }

    private PhongMaterial erickDelgadoMaterial(Color color, boolean resaltado) {
        PhongMaterial m = new PhongMaterial(color);
        m.setSpecularColor(resaltado ? Color.gray(0.7) : Color.gray(0.28));
        m.setSpecularPower(resaltado ? 60 : 35);
        return m;
    }

    private Color erickDelgadoColorDe(String tipo) {
        return switch (tipo) {
            case "ZAPATA" -> ERICK_DELGADO_COLOR_ZAPATA;
            case "COLUMNA" -> ERICK_DELGADO_COLOR_COLUMNA;
            case "LOSA" -> Color.web("#94a3b8", 0.35); // translúcida: no tapa lo que hay debajo
            case "MURO" -> Color.web("#f59e0b");
            default -> ERICK_DELGADO_COLOR_VIGA;
        };
    }
}
