package com.concretolab.ui;

import atlantafx.base.theme.Styles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public final class Bienvenida {
    static final String FERNANDO_HUANCA_ARCHIVO = "preferencias.properties";
    static final String FERNANDO_HUANCA_CLAVE = "bienvenida.mostrar";
    public static final String FERNANDO_HUANCA_PROPIEDAD = "concretolab.bienvenida";

    public record Pagina(Ikon icono, String titulo, String texto, List<String> puntos) { }

    public static final List<Pagina> FERNANDO_HUANCA_PAGINAS = List.of(
            new Pagina(Feather.LAYERS, "Bienvenido a ConcretoLab",
                    "ConcretoLab le ayuda a modelar edificios de concreto armado, hacer un análisis sísmico preliminar "
                            + "según la Norma E.030 y llevar el control de materiales, mezclas y probetas.",
                    List.of("Funciona sin internet.",
                            "Todo se guarda en esta computadora.",
                            "Este recorrido dura un minuto; puede volver a verlo desde «Ayuda».")),
            new Pagina(Feather.BOX, "1. Cree un proyecto y su modelo 3D",
                    "En «Modelo 3D y metrado» cree un proyecto y agregue zapatas, columnas, vigas y losas: una por una, "
                            + "con una plantilla, con ejes y niveles o importando un plano DXF.",
                    List.of("Clic: seleccionar · Ctrl+clic: seleccionar varios · Doble clic: editar.",
                            "Puede editar varios elementos a la vez y deshacer con Ctrl+Z.",
                            "El metrado (concreto, encofrado y acero) se calcula solo.")),
            new Pagina(Feather.ACTIVITY, "2. Analice el sismo",
                    "En «Análisis sísmico E.030» responda cuatro preguntas (ubicación, uso, suelo y sistema) y pulse "
                            + "«Calcular». Verá si las derivas cumplen y, si no, «Cómo corregir» le dice cuánto agrandar las columnas.",
                    List.of("Si una viga no llega a las columnas, el resultado dice «MODELO INCOMPLETO».",
                            "El resultado es preliminar: no reemplaza el diseño de un ingeniero estructural.")),
            new Pagina(Feather.DROPLET, "3. Concreto y laboratorio",
                    "Registre sus agregados en «Materiales y ensayos», calcule el tamizado en «Granulometría» y diseñe "
                            + "o registre mezclas en «Mezclas y probetas», donde también anota las probetas ensayadas.",
                    List.of("El diseño de mezclas sigue el método ACI 211.1 con los requisitos de la E.060.",
                            "Toda dosificación debe validarse con mezclas de prueba.")),
            new Pagina(Feather.SHIELD, "4. Sus datos están protegidos",
                    "ConcretoLab guarda una copia de seguridad al abrir y otra al cerrar. En «Datos y copias» puede crear "
                            + "copias, restaurarlas y llevar proyectos a otra computadora.",
                    List.of("¿Tiene dudas? Pulse «Ayuda» en el menú de la izquierda para abrir el manual.",
                            "¿Quiere practicar? Agregue el proyecto de ejemplo: no modifica sus proyectos.")));

    private Bienvenida() { }

    public static boolean fernandoHuancaDebeMostrarse(Path carpetaDatos) {
        if ("no".equalsIgnoreCase(System.getProperty(FERNANDO_HUANCA_PROPIEDAD, "").strip())) return false;
        return !"false".equalsIgnoreCase(fernandoHuancaLeer(carpetaDatos).getProperty(FERNANDO_HUANCA_CLAVE, "true").strip());
    }

    public static void fernandoHuancaGuardar(Path carpetaDatos, boolean mostrar) {
        Properties p = fernandoHuancaLeer(carpetaDatos);
        p.setProperty(FERNANDO_HUANCA_CLAVE, Boolean.toString(mostrar));
        try {
            Files.createDirectories(carpetaDatos);
            try (OutputStream out = Files.newOutputStream(carpetaDatos.resolve(FERNANDO_HUANCA_ARCHIVO))) {
                p.store(out, "Preferencias de ConcretoLab");
            }
        } catch (IOException ex) {
            System.getLogger(Bienvenida.class.getName()).log(System.Logger.Level.WARNING,
                    "No se pudo guardar la preferencia de bienvenida", ex);
        }
    }

    private static Properties fernandoHuancaLeer(Path carpetaDatos) {
        Properties p = new Properties();
        Path archivo = carpetaDatos.resolve(FERNANDO_HUANCA_ARCHIVO);
        if (Files.isRegularFile(archivo)) {
            try (InputStream in = Files.newInputStream(archivo)) {
                p.load(in);
            } catch (IOException | IllegalArgumentException ex) {
            }
        }
        return p;
    }

    public static VBox fernandoHuancaCrear(Path carpetaDatos, Runnable alCerrar, Runnable alAgregarEjemplo) {
        FontIcon icono = new FontIcon();
        StackPane circulo = new StackPane(icono);
        circulo.getStyleClass().addAll("dialogo-icono", "bienvenida-icono");

        Label pasos = new Label();
        pasos.setId("bienvenidaPasos");
        pasos.getStyleClass().add(Styles.TEXT_MUTED);
        Button cerrar = new Button(null, new FontIcon(Feather.X));
        cerrar.setId("bienvenidaCerrar");
        cerrar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        cerrar.setAccessibleText("Cerrar la bienvenida");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox cabecera = new HBox(10, circulo, hueco, pasos, cerrar);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label();
        titulo.setId("bienvenidaTitulo");
        titulo.getStyleClass().add("dialogo-titulo");
        titulo.setWrapText(true);
        Label texto = new Label();
        texto.getStyleClass().add("dialogo-mensaje");
        texto.setWrapText(true);
        VBox puntos = new VBox(6);
        puntos.getStyleClass().add("bienvenida-puntos");

        HBox indicadores = new HBox(6);
        indicadores.setAlignment(Pos.CENTER);
        for (int i = 0; i < FERNANDO_HUANCA_PAGINAS.size(); i++) {
            Region punto = new Region();
            punto.getStyleClass().add("bienvenida-indicador");
            indicadores.getChildren().add(punto);
        }

        CheckBox noMostrar = new CheckBox("No volver a mostrar al abrir");
        noMostrar.setId("bienvenidaNoMostrar");
        noMostrar.setSelected(!fernandoHuancaDebeMostrarseSegunArchivo(carpetaDatos));
        Button anterior = new Button("Anterior");
        anterior.setId("bienvenidaAnterior");
        anterior.getStyleClass().add(Styles.BUTTON_OUTLINED);
        Button ejemplo = new Button("Agregar proyecto de ejemplo");
        ejemplo.setId("bienvenidaEjemplo");
        ejemplo.getStyleClass().add(Styles.BUTTON_OUTLINED);
        Button siguiente = new Button();
        siguiente.setId("bienvenidaSiguiente");
        siguiente.getStyleClass().add(Styles.ACCENT);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(10, noMostrar, espacio, anterior, ejemplo, siguiente);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        VBox dialogo = new VBox(14, cabecera, titulo, texto, puntos, indicadores, acciones);
        dialogo.getStyleClass().addAll("dialogo", "bienvenida");
        dialogo.setPrefWidth(640);
        dialogo.setMaxSize(640, Region.USE_PREF_SIZE);

        int[] actual = {0};
        Runnable pintar = () -> {
            Pagina p = FERNANDO_HUANCA_PAGINAS.get(actual[0]);
            boolean ultima = actual[0] == FERNANDO_HUANCA_PAGINAS.size() - 1;
            icono.setIconCode(p.icono());
            titulo.setText(p.titulo());
            texto.setText(p.texto());
            puntos.getChildren().setAll(p.puntos().stream().map(t -> {
                Label l = new Label("•  " + t);
                l.setWrapText(true);
                l.getStyleClass().add("dialogo-mensaje");
                return l;
            }).toList());
            pasos.setText((actual[0] + 1) + " de " + FERNANDO_HUANCA_PAGINAS.size());
            for (int i = 0; i < indicadores.getChildren().size(); i++) {
                indicadores.getChildren().get(i).getStyleClass().remove("activo");
                if (i == actual[0]) indicadores.getChildren().get(i).getStyleClass().add("activo");
            }
            anterior.setVisible(actual[0] > 0);
            anterior.setManaged(actual[0] > 0);
            ejemplo.setVisible(ultima && alAgregarEjemplo != null);
            ejemplo.setManaged(ultima && alAgregarEjemplo != null);
            siguiente.setText(ultima ? "Empezar" : "Siguiente");
        };
        Runnable terminar = () -> {
            fernandoHuancaGuardar(carpetaDatos, !noMostrar.isSelected());
            if (alCerrar != null) alCerrar.run();
        };
        anterior.setOnAction(e -> { if (actual[0] > 0) { actual[0]--; pintar.run(); } });
        siguiente.setOnAction(e -> {
            if (actual[0] < FERNANDO_HUANCA_PAGINAS.size() - 1) { actual[0]++; pintar.run(); }
            else terminar.run();
        });
        cerrar.setOnAction(e -> terminar.run());
        ejemplo.setOnAction(e -> { terminar.run(); if (alAgregarEjemplo != null) alAgregarEjemplo.run(); });
        dialogo.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) { e.consume(); terminar.run(); }
        });
        dialogo.sceneProperty().addListener((obs, antes, ahora) -> { if (ahora != null) siguiente.requestFocus(); });
        pintar.run();
        return dialogo;
    }

    private static boolean fernandoHuancaDebeMostrarseSegunArchivo(Path carpetaDatos) {
        return !"false".equalsIgnoreCase(fernandoHuancaLeer(carpetaDatos).getProperty(FERNANDO_HUANCA_CLAVE, "true").strip());
    }
}
