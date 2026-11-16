package com.concretolab.ui;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.AnalisisSismico;
import com.concretolab.service.AnalisisSismico.*;
import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.*;
import com.concretolab.service.ProyectoService;
import com.concretolab.service.ReporteSismo;
import java.nio.file.Files;
import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Pantalla del análisis sísmico E.030. Los datos de entrada no se guardan en la base: se ingresan en cada sesión. */
public class PantallaSismo {
    private final ProyectoService servicio;
    private final ComboBox<Version> version = new ComboBox<>(); private final ComboBox<Zona> zona = new ComboBox<>(); private final ComboBox<Categoria> categoria = new ComboBox<>(); private final ComboBox<Suelo> suelo = new ComboBox<>();
    private final ComboBox<Double> ia = new ComboBox<>(), ip = new ComboBox<>(), ct = new ComboBox<>();
    private final TextField vs30 = new TextField(), r0 = new TextField("8"), fc = new TextField("21"), poisson = new TextField("0.20"), rigidez = new TextField("1.0"), espesor = new TextField("0.20");
    private final TableView<FilaPiso> pisos = new TableView<>();
    private final TabPane resultados = new TabPane(); private final Label estado = new Label("Defina los datos y pulse «Calcular».");
    private final Button exportar = new Button("Exportar PDF");
    private Proyecto proyecto; private Entrada entrada; private Resultado resultado;

    public PantallaSismo(ProyectoService servicio) { this.servicio = servicio; }

    public static final class FilaPiso {
        final int numero; final double cota; final StringProperty area, cm, cv;
        FilaPiso(int n, double cota, double area) { numero = n; this.cota = cota; this.area = new SimpleStringProperty(f(area, 2)); cm = new SimpleStringProperty("1.0"); cv = new SimpleStringProperty("2.0"); }
    }

    public Parent crear(Proyecto p) {
        proyecto = p;
        Label titulo = new Label("Análisis sísmico · E.030"); titulo.getStyleClass().add("titulo");
        Label sub = new Label("Pórtico de concreto armado · estático, modal espectral (CQC) y derivas"); sub.getStyleClass().add("subtitulo");
        if (p == null) { VBox v = new VBox(12, titulo, new Label("Seleccione un proyecto en «Modelo 3D y metrado» para analizarlo.")); v.setPadding(new Insets(24)); return v; }

        zona.getItems().addAll(Zona.values()); zona.setValue(Zona.Z4);
        categoria.getItems().addAll(Categoria.values()); categoria.setValue(Categoria.C);
        version.getItems().addAll(Version.values()); version.setValue(Version.V2026);
        version.setOnAction(e -> cambiarVersion()); cambiarVersion();
        ia.getItems().addAll(1.0, 0.90, 0.80, 0.75, 0.60, 0.50); ia.setValue(1.0); ip.getItems().addAll(1.0, 0.90, 0.85, 0.75, 0.60); ip.setValue(1.0); ct.getItems().addAll(35.0, 45.0, 60.0); ct.setValue(35.0);
        for (ComboBox<?> c : List.of(version, zona, categoria, suelo, ia, ip, ct)) c.setMaxWidth(Double.MAX_VALUE);

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8);
        vs30.setPromptText("vacío = valor más desfavorable"); vs30.setTooltip(new Tooltip("Solo para S2 (350–550 m/s) y S3 (200–350 m/s): interpola S, Tp y TL (Tablas 4 y 5)."));
        String[] et = {"Versión de la E.030", "Zona (Z)", "Categoría (U)", "Suelo (S, Tp, TL)", "Vs30 en m/s (opcional, S2 y S3)", "R0 (pórticos de C.A. = 8)", "Ia (Tabla 8, menor valor aplicable)", "Ip (Tabla 9, menor valor aplicable)", "CT (35: solo pórticos)", "f'c (MPa)", "Poisson", "Fracción de rigidez EI (≤ 1)", "Espesor de losa (m)"};
        Region[] campos = {version, zona, categoria, suelo, vs30, r0, ia, ip, ct, fc, poisson, rigidez, espesor};
        for (int i = 0; i < et.length; i++) { g.add(new Label(et[i]), 0, i); g.add(campos[i], 1, i); GridPane.setHgrow(campos[i], Priority.ALWAYS); }

        columna("Piso", c -> new SimpleStringProperty("" + c.numero)); columna("Cota (m)", c -> new SimpleStringProperty(f(c.cota, 2)));
        editable("Área losa (m²)", c -> c.area); editable("CM adic. (kN/m²)", c -> c.cm); editable("CV (kN/m²)", c -> c.cv);
        pisos.setEditable(true); pisos.setPrefHeight(190); pisos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        try { for (var b : AnalisisSismico.pisos(servicio.elementos(p.getId()))) pisos.getItems().add(new FilaPiso(pisos.getItems().size() + 1, b.cota(), b.areaPorDefecto())); }
        catch (IllegalArgumentException ex) { estado.setText(ex.getMessage()); }

        Label seccion = new Label("Pisos detectados (editable)"); seccion.getStyleClass().add("seccion");
        Label nota = new Label("Los pisos se agrupan por la cota superior de las vigas. CM y CV se proponen como 1,0 y 2,0 kN/m²: ajústelos a su proyecto (E.020). Nada de esto se guarda en la base de datos."); nota.setWrapText(true); nota.getStyleClass().add("subtitulo");
        Button calcular = new Button("Calcular análisis sísmico"); calcular.getStyleClass().add("principal"); calcular.setMaxWidth(Double.MAX_VALUE); calcular.setOnAction(e -> calcular());
        exportar.setMaxWidth(Double.MAX_VALUE); exportar.setDisable(true); exportar.setOnAction(e -> exportarPdf());
        estado.setWrapText(true); estado.getStyleClass().add("estado");
        VBox izquierda = new VBox(10, g, seccion, pisos, nota, calcular, exportar, estado); izquierda.getStyleClass().add("tarjeta"); izquierda.setPrefWidth(430);
        ScrollPane sp = new ScrollPane(izquierda); sp.setFitToWidth(true); sp.setPrefWidth(460);

        Label aviso = new Label("Resultado educativo y preliminar: base empotrada, losa como diafragma rígido, sin muros ni zonas rígidas. Se ofrecen el texto 2026 (R.M. 183-2026-VIVIENDA) y la versión 2018 (R.M. 355-2018-VIVIENDA); verifique cuál rige para su proyecto, pues los trámites iniciados pueden seguir con la anterior. Haga validar el análisis por un ingeniero civil colegiado.");
        aviso.setWrapText(true); aviso.getStyleClass().add("aviso");
        resultados.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox derecha = new VBox(10, resultados, aviso); derecha.getStyleClass().add("tarjeta"); VBox.setVgrow(resultados, Priority.ALWAYS); HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox centro = new HBox(16, sp, derecha); centro.setPadding(new Insets(20)); VBox.setVgrow(centro, Priority.ALWAYS);
        VBox cab = new VBox(3, titulo, sub); cab.getStyleClass().add("cabecera");
        VBox raiz = new VBox(cab, centro); raiz.getStyleClass().add("raiz"); return raiz;
    }

    /** Cambia la lista de suelos y habilita Vs30 según la versión de la norma. */
    private void cambiarVersion() {
        var v = version.getValue(); var anterior = suelo.getValue(); int idx = anterior == null ? 2 : Math.max(0, suelo.getItems().indexOf(anterior));
        suelo.getItems().setAll(Suelo.de(v)); suelo.setValue(suelo.getItems().get(Math.min(idx, suelo.getItems().size() - 1)));
        vs30.setDisable(v != Version.V2026); if (v != Version.V2026) vs30.clear();
    }

    private void columna(String t, java.util.function.Function<FilaPiso, StringProperty> v) { TableColumn<FilaPiso, String> c = new TableColumn<>(t); c.setCellValueFactory(x -> v.apply(x.getValue())); c.setEditable(false); pisos.getColumns().add(c); }
    private void editable(String t, java.util.function.Function<FilaPiso, StringProperty> v) { TableColumn<FilaPiso, String> c = new TableColumn<>(t); c.setCellValueFactory(x -> v.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn()); c.setOnEditCommit(e -> v.apply(e.getRowValue()).set(e.getNewValue())); pisos.getColumns().add(c); }

    private static double num(String s) { return Double.parseDouble(s.trim().replace(',', '.')); }
    private static String f(double v, int d) { return String.format(Locale.ROOT, "%." + d + "f", v); }

    private void calcular() {
        try {
            List<Elemento> el = servicio.elementos(proyecto.getId());
            List<CargaPiso> cargas = new ArrayList<>(); for (FilaPiso fp : pisos.getItems()) cargas.add(new CargaPiso(num(fp.area.get()), num(fp.cm.get()), num(fp.cv.get())));
            var norma = new Parametros(version.getValue(), zona.getValue(), categoria.getValue(), suelo.getValue(), num(r0.getText()), ia.getValue(), ip.getValue(), ct.getValue(), version.getValue() == Version.V2026 && !vs30.getText().isBlank() ? num(vs30.getText()) : Double.NaN);
            entrada = new Entrada(el, num(fc.getText()), num(poisson.getText()), num(rigidez.getText()), num(espesor.getText()), cargas, norma);
            resultado = AnalisisSismico.analizar(entrada);
            mostrar(); exportar.setDisable(false); estado.setText("Análisis calculado: " + resultado.pisos().size() + " pisos, " + resultado.modos().size() + " modos.");
        } catch (NumberFormatException ex) { error("Revise que los campos numéricos contengan solo números."); }
        catch (IllegalArgumentException ex) { error(ex.getMessage()); }
    }

    private void error(String m) { estado.setText(m); new Alert(Alert.AlertType.ERROR, m, ButtonType.OK).showAndWait(); }

    private static Tab tab(String t, javafx.scene.Node n) { Tab x = new Tab(t, n); return x; }

    private static TableView<String[]> tabla(String[] cab, List<String[]> filas) {
        TableView<String[]> t = new TableView<>(); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int i = 0; i < cab.length; i++) { final int k = i; TableColumn<String[], String> c = new TableColumn<>(cab[i]); c.setCellValueFactory(x -> new SimpleStringProperty(x.getValue()[k])); t.getColumns().add(c); }
        t.getItems().setAll(filas); return t;
    }

    private void mostrar() {
        var r = resultado; var n = entrada.norma(); resultados.getTabs().clear();
        // Resumen
        List<String[]> res = new ArrayList<>(List.of(
                new String[]{"Versión de la norma", n.version().etiqueta},
                new String[]{"Z · U · S", n.z() + " · " + n.u() + " · " + n.s()}, new String[]{"Tp · TL (s)", n.tp() + " · " + n.tl()},
                new String[]{"R = R0·Ia·Ip", f(n.r(), 3) + (n.regular() ? "  (regular)" : "  (irregular)")},
                new String[]{"Ec (MPa) · E.060", f(r.ecMpa(), 0)}, new String[]{"Peso sísmico total (kN)", f(r.pesoTotal(), 1)},
                new String[]{"T fundamental X · Y (s)", f(r.espectralX().periodo(), 3) + " · " + f(r.espectralY().periodo(), 3)},
                new String[]{"T aproximado hn/CT (s)", f(r.estaticoX().periodo(), 3)},
                new String[]{"Masa efectiva acumulada X · Y", f(r.masaAcumuladaX() * 100, 1) + " % · " + f(r.masaAcumuladaY() * 100, 1) + " %"},
                new String[]{"Cortante basal estático X · Y (kN)", f(r.estaticoX().cortanteBase(), 1) + " · " + f(r.estaticoY().cortanteBase(), 1)},
                new String[]{"Cortante basal espectral X · Y (kN)", f(r.espectralX().cortanteBase(), 1) + " · " + f(r.espectralY().cortanteBase(), 1)},
                new String[]{"Deriva máxima (límite " + NormaE030.DERIVA_LIMITE_CONCRETO + ")", "X: " + f(Math.max(r.estaticoX().derivaMaxima(), r.espectralX().derivaMaxima()), 5) + " · Y: " + f(Math.max(r.estaticoY().derivaMaxima(), r.espectralY().derivaMaxima()), 5)},
                new String[]{"Verificación de deriva", r.estaticoX().cumple() && r.estaticoY().cumple() && r.espectralX().cumple() && r.espectralY().cumple() ? "CUMPLE (preliminar)" : "NO CUMPLE"}));
        var avisos = new VBox(4); for (String a : r.advertencias()) { Label l = new Label("• " + a); l.setWrapText(true); avisos.getChildren().add(l); }
        VBox resumen = new VBox(10, tabla(new String[]{"Concepto", "Valor"}, res), avisos); VBox.setVgrow(resumen.getChildren().get(0), Priority.ALWAYS);
        resultados.getTabs().add(tab("Resumen", resumen));
        // Pisos
        resultados.getTabs().add(tab("Pisos", tabla(new String[]{"Piso", "Cota (m)", "h (m)", "Área (m²)", "Peso (kN)", "X cm", "Y cm", "I polar"},
                r.pisos().stream().map(s -> new String[]{"" + s.numero(), f(s.cota(), 2), f(s.altura(), 2), f(s.area(), 2), f(s.peso(), 1), f(s.xcm(), 3), f(s.ycm(), 3), f(s.inercia(), 0)}).toList())));
        // Modos
        resultados.getTabs().add(tab("Modos", tabla(new String[]{"Modo", "T (s)", "f (Hz)", "Masa X (%)", "Masa Y (%)"},
                r.modos().stream().map(m -> new String[]{"" + m.numero(), f(m.periodo(), 4), f(1 / m.periodo(), 3), f(m.masaEfectivaX() * 100, 1), f(m.masaEfectivaY() * 100, 1)}).toList())));
        resultados.getTabs().add(tab("Estático", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.estaticoX(), r.estaticoY()))));
        resultados.getTabs().add(tab("Espectral", tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fuerzas(r.espectralX(), r.espectralY()))));
        // Derivas
        List<String[]> der = new ArrayList<>();
        for (int i = r.pisos().size() - 1; i >= 0; i--) {
            double max = Math.max(Math.max(r.estaticoX().derivasMaximas()[i], r.estaticoY().derivasMaximas()[i]), Math.max(r.espectralX().derivasMaximas()[i], r.espectralY().derivasMaximas()[i]));
            der.add(new String[]{"" + (i + 1), f(r.estaticoX().derivasMaximas()[i], 5), f(r.estaticoY().derivasMaximas()[i], 5), f(r.espectralX().derivasMaximas()[i], 5), f(r.espectralY().derivasMaximas()[i], 5),
                    f(r.espectralX().desplazamientosCm()[i] * 1000, 1), f(r.espectralY().desplazamientosCm()[i] * 1000, 1), max <= NormaE030.DERIVA_LIMITE_CONCRETO ? "Cumple" : "NO CUMPLE"});
        }
        resultados.getTabs().add(tab("Derivas", tabla(new String[]{"Piso", "Est. X", "Est. Y", "Esp. X", "Esp. Y", "Δ esp. X (mm)", "Δ esp. Y (mm)", "Estado"}, der)));
        resultados.getTabs().add(tab("Espectro", espectro(r, n)));
    }

    private static List<String[]> fuerzas(Direccion x, Direccion y) {
        List<String[]> l = new ArrayList<>();
        for (int i = x.fuerzas().length - 1; i >= 0; i--) l.add(new String[]{"" + (i + 1), f(x.fuerzas()[i], 2), f(x.cortantes()[i], 2), f(y.fuerzas()[i], 2), f(y.cortantes()[i], 2)});
        return l;
    }

    private javafx.scene.Node espectro(Resultado r, Parametros n) {
        NumberAxis ejeX = new NumberAxis(0, 4, 0.5), ejeY = new NumberAxis(); ejeX.setLabel("T (s)"); ejeY.setLabel("Sa / g");
        LineChart<Number, Number> grafico = new LineChart<>(ejeX, ejeY); grafico.setCreateSymbols(false); grafico.setAnimated(false);
        XYChart.Series<Number, Number> curva = new XYChart.Series<>(); curva.setName("Espectro de diseño E.030");
        for (double t = 0.02; t <= 4.0001; t += 0.02) curva.getData().add(new XYChart.Data<>(t, n.saSobreG(t)));
        XYChart.Series<Number, Number> puntos = new XYChart.Series<>(); puntos.setName("Periodos modales");
        for (Modo m : r.modos()) if (m.periodo() <= 4) puntos.getData().add(new XYChart.Data<>(m.periodo(), n.saSobreG(m.periodo())));
        grafico.getData().addAll(curva, puntos);
        if (puntos.getNode() != null) puntos.getNode().setStyle("-fx-stroke: transparent;"); // solo marcadores
        return grafico;
    }

    private void exportarPdf() {
        try {
            byte[] pdf = ReporteSismo.generar(proyecto, entrada, resultado);
            FileChooser s = new FileChooser(); s.setTitle("Guardar reporte sísmico"); s.setInitialFileName("analisis-sismico.pdf");
            s.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
            var archivo = s.showSaveDialog(exportar.getScene().getWindow());
            if (archivo != null) Files.write(archivo.toPath(), pdf);
        } catch (Exception ex) { error("No se pudo guardar el PDF: " + ex.getMessage()); }
    }
}
