package com.concretolab.ui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Diálogo «Acerca de»: versión, autoría del equipo, normas aplicadas, componentes de terceros y avisos. */
public final class AcercaDe {

    private AcercaDe() { }

    public static final List<String> FERNANDO_HUANCA_AUTORES = List.of("Yefri Ronaldiño Lerma Pacompia", "Erick Mijael Delgado Lima", "Fernando André Huanca Huallpa", "Walter David Quispesayhua Quispe");

    static final List<String> FERNANDO_HUANCA_NORMAS = List.of(
            "Diseño sismorresistente: NTE E.030 (versión 2026, R.M. 183-2026-VIVIENDA, y versión 2018, R.M. 355-2018-VIVIENDA).",
            "Concreto armado: NTE E.060 (resistencia promedio requerida, criterio de aceptación, durabilidad).",
            "Dosificación: ACI 211.1. Agregados: NTP 400.037 / ASTM C33 (módulo de finura y husos).",
            "Cargas: valores de la NTE E.020 usados como referencia; revise los que la aplicación marca como «no verificado».");

    static final List<String> FERNANDO_HUANCA_TERCEROS = List.of(
            "JavaFX (GPL v2 + Classpath Exception) · AtlantaFX (MIT) · Ikonli y Feather Icons (Apache 2.0 / MIT)",
            "Spring Boot, Spring Data y Flyway Community (Apache 2.0) · Hibernate ORM (LGPL 2.1)",
            "SQLite y SQLite3MultipleCiphers mediante willena/sqlite-jdbc (SQLite en dominio público; cifrado y driver bajo licencias permisivas)",
            "Apache Commons Math (Apache 2.0) · OpenPDF (LGPL 2.1 / MPL 2.0)");

    static final String FERNANDO_HUANCA_AVISO = "Los resultados son una ayuda de cálculo y de organización: no sustituyen la revisión y la firma de un "
            + "profesional colegiado ni la verificación en laboratorio. Los valores de demostración son ficticios. "
            + "Los límites y supuestos de cada módulo se indican en su pantalla y en el README.";

    /** Texto plano del diálogo (útil para pruebas y para copiar). */
    public static String fernandoHuancaTexto(String version, boolean cifrada, String carpetaDatos) {
        var sb = new StringBuilder();
        sb.append("ConcretoLab Desktop ").append(version).append('\n');
        sb.append("Equipo ConcretoLab: ").append(String.join(", ", FERNANDO_HUANCA_AUTORES)).append("\n\n");
        sb.append("Normas aplicadas\n");
        FERNANDO_HUANCA_NORMAS.forEach(n -> sb.append("• ").append(n).append('\n'));
        sb.append("\nDatos\n• Carpeta: ").append(carpetaDatos).append('\n')
                .append("• Base de datos cifrada: ").append(cifrada ? "sí (AES-256)" : "no").append("\n\n");
        sb.append("Componentes de terceros\n");
        FERNANDO_HUANCA_TERCEROS.forEach(n -> sb.append("• ").append(n).append('\n'));
        sb.append('\n').append(FERNANDO_HUANCA_AVISO);
        return sb.toString();
    }

    public static void fernandoHuancaMostrar(Window ventana, String version, boolean cifrada, String carpetaDatos) {
        var dialogo = fernandoHuancaCrear(version, cifrada, carpetaDatos);
        dialogo.initOwner(ventana);
        dialogo.showAndWait();
    }

    /** Arma el diálogo sin mostrarlo. */
    public static Dialog<Void> fernandoHuancaCrear(String version, boolean cifrada, String carpetaDatos) {
        var dialogo = new Dialog<Void>();
        dialogo.setTitle("Acerca de ConcretoLab Desktop");
        dialogo.setHeaderText("ConcretoLab Desktop · versión " + version);
        var contenido = new VBox(10);
        contenido.setPadding(new Insets(8));
        contenido.getChildren().addAll(
                fernandoHuancaSeccion("Autoría", "Proyecto desarrollado por el equipo ConcretoLab:\n" + String.join("\n", FERNANDO_HUANCA_AUTORES.stream().map(a -> "• " + a).toList())),
                fernandoHuancaSeccion("Normas aplicadas", String.join("\n", FERNANDO_HUANCA_NORMAS.stream().map(n -> "• " + n).toList())),
                fernandoHuancaSeccion("Sus datos", "Carpeta: " + carpetaDatos + "\nBase de datos cifrada: " + (cifrada ? "sí (AES-256)" : "no")),
                fernandoHuancaSeccion("Componentes de terceros", String.join("\n", FERNANDO_HUANCA_TERCEROS.stream().map(n -> "• " + n).toList())),
                fernandoHuancaSeccion("Aviso", FERNANDO_HUANCA_AVISO));
        var scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(430);
        dialogo.getDialogPane().setContent(scroll);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        return dialogo;
    }

    private static Node fernandoHuancaSeccion(String titulo, String cuerpo) {
        var t = new Label(titulo.toUpperCase());
        t.getStyleClass().add("seccion-form");
        var c = new Label(cuerpo);
        c.setWrapText(true);
        c.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return new VBox(4, t, c);
    }
}
