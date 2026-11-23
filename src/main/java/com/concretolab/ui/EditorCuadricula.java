package com.concretolab.ui;

import com.concretolab.model.CuadriculaProyecto;
import com.concretolab.model.Elemento;
import com.concretolab.service.CuadriculaService;
import com.concretolab.service.CuadriculaService.Definicion;
import com.concretolab.service.CuadriculaService.Dimensiones;
import com.concretolab.service.CuadriculaService.Punto;
import com.concretolab.service.CuadriculaService.Solicitud;
import com.concretolab.service.CuadriculaService.Tramo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.springframework.stereotype.Component;

@Component
public class EditorCuadricula {
    private final CuadriculaService walterQuispesayhuaServicio;

    public EditorCuadricula(CuadriculaService servicio) { this.walterQuispesayhuaServicio = servicio; }

    public boolean walterQuispesayhuaAbrir(Long proyectoId, List<Elemento> existentes) {
        CuadriculaProyecto guardada = walterQuispesayhuaServicio.walterQuispesayhuaObtener(proyectoId).orElse(null);
        TextField origenX = campo(guardada == null ? "0" : walterQuispesayhuaTexto(guardada.getOrigenX()));
        TextField origenY = campo(guardada == null ? "0" : walterQuispesayhuaTexto(guardada.getOrigenY()));
        TextField separacionesX = campo(guardada == null ? "5; 5" : guardada.getSeparacionesX());
        TextField separacionesY = campo(guardada == null ? "4" : guardada.getSeparacionesY());
        TextField niveles = campo(guardada == null ? "0; 3" : guardada.getNiveles());
        TextField largoZapata = campo("1.5"), anchoZapata = campo("1.5"), alturaZapata = campo("0.5");
        TextField ladoColumna = campo("0.4"), anchoViga = campo("0.3"), alturaViga = campo("0.5");
        CheckBox zapatas = walterQuispesayhuaOpcion("Zapatas"), columnas = walterQuispesayhuaOpcion("Columnas");
        CheckBox vigasX = walterQuispesayhuaOpcion("Vigas X"), vigasY = walterQuispesayhuaOpcion("Vigas Y");
        FlowPane puntosPanel = new FlowPane(8, 6);
        FlowPane tramosPanel = new FlowPane(8, 6);
        Canvas plano = new Canvas(560, 300);
        Label estado = new Label();
        estado.setWrapText(true);
        List<CheckBox> selectores = new ArrayList<>();
        List<Punto> puntos = new ArrayList<>();
        List<CheckBox> selectoresTramos = new ArrayList<>();
        List<Tramo> tramos = new ArrayList<>();
        Definicion[] dibujada = new Definicion[1];
        boolean[] creado = {false};
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle("Ejes y niveles");
        dialogo.setHeaderText("Definir cuadrícula y generar elementos");
        dialogo.setResizable(true);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        GridPane datos = new GridPane();
        datos.setHgap(10); datos.setVgap(7);
        walterQuispesayhuaFila(datos, 0, "Origen X / Y (m)", new HBox(8, origenX, origenY));
        walterQuispesayhuaFila(datos, 1, "Separaciones X (m)", separacionesX);
        walterQuispesayhuaFila(datos, 2, "Separaciones Y (m)", separacionesY);
        walterQuispesayhuaFila(datos, 3, "Niveles Z (m)", niveles);
        Label indicaciones = new Label("Separa los valores con punto y coma. Los niveles son cotas ascendentes; el primero es la parte superior de las zapatas.");
        indicaciones.setWrapText(true);
        Button actualizar = new Button("Actualizar cuadrícula");
        Button seleccionar = new Button("Seleccionar todas");
        Button limpiar = new Button("Limpiar selección");
        seleccionar.setOnAction(e -> selectores.forEach(c -> c.setSelected(true)));
        limpiar.setOnAction(e -> selectores.forEach(c -> c.setSelected(false)));
        actualizar.setOnAction(e -> {
            try {
                Definicion d = walterQuispesayhuaDefinicion(origenX, origenY, separacionesX, separacionesY, niveles);
                walterQuispesayhuaServicio.walterQuispesayhuaValidarDefinicion(d);
                dibujada[0] = d;
                puntos.clear(); selectores.clear(); puntosPanel.getChildren().clear();
                tramos.clear(); selectoresTramos.clear(); tramosPanel.getChildren().clear();
                for (int y = 0; y <= d.separacionesY().size(); y++) {
                    for (int x = 0; x <= d.separacionesX().size(); x++) {
                        Punto punto = new Punto(x, y);
                        CheckBox selector = walterQuispesayhuaOpcion((x + 1) + "-" + (y + 1));
                        puntos.add(punto); selectores.add(selector); puntosPanel.getChildren().add(selector);
                        if (x < d.separacionesX().size()) walterQuispesayhuaAgregarTramo(tramos, selectoresTramos, tramosPanel, punto, new Punto(x + 1, y));
                        if (y < d.separacionesY().size()) walterQuispesayhuaAgregarTramo(tramos, selectoresTramos, tramosPanel, punto, new Punto(x, y + 1));
                    }
                }
                walterQuispesayhuaDibujar(plano, d, existentes, List.of());
                estado.setText("Cuadrícula actualizada. Los rectángulos grises representan elementos existentes en planta.");
            } catch (IllegalArgumentException ex) { estado.setText(ex.getMessage()); }
        });
        GridPane medidas = new GridPane();
        medidas.setHgap(10); medidas.setVgap(7);
        walterQuispesayhuaFila(medidas, 0, "Zapata: largo / ancho / altura (m)", new HBox(8, largoZapata, anchoZapata, alturaZapata));
        walterQuispesayhuaFila(medidas, 1, "Columna: lado (m)", ladoColumna);
        walterQuispesayhuaFila(medidas, 2, "Viga: ancho / altura (m)", new HBox(8, anchoViga, alturaViga));
        Button guardar = new Button("Guardar ejes y niveles");
        Button vista = new Button("Revisar elementos");
        vista.getStyleClass().add("accent");
        guardar.setOnAction(e -> {
            try {
                Definicion d = walterQuispesayhuaDefinicion(origenX, origenY, separacionesX, separacionesY, niveles);
                walterQuispesayhuaServicio.walterQuispesayhuaGuardarCuadricula(proyectoId, d);
                estado.setText("Ejes y niveles guardados en el proyecto.");
            } catch (IllegalArgumentException ex) { estado.setText(ex.getMessage()); }
        });
        vista.setOnAction(e -> {
            try {
                Definicion d = walterQuispesayhuaDefinicion(origenX, origenY, separacionesX, separacionesY, niveles);
                if (!d.equals(dibujada[0])) throw new IllegalArgumentException("Actualice la cuadrícula antes de revisar la selección.");
                Set<Punto> seleccion = new HashSet<>();
                for (int i = 0; i < puntos.size(); i++) if (selectores.get(i).isSelected()) seleccion.add(puntos.get(i));
                Set<Tramo> seleccionTramos = new HashSet<>();
                for (int i = 0; i < tramos.size(); i++) if (selectoresTramos.get(i).isSelected()) seleccionTramos.add(tramos.get(i));
                Dimensiones dimensiones = new Dimensiones(walterQuispesayhuaNumero(largoZapata), walterQuispesayhuaNumero(anchoZapata), walterQuispesayhuaNumero(alturaZapata),
                        walterQuispesayhuaNumero(ladoColumna), walterQuispesayhuaNumero(anchoViga), walterQuispesayhuaNumero(alturaViga));
                Solicitud solicitud = new Solicitud(d, Set.copyOf(seleccion), Set.copyOf(seleccionTramos), zapatas.isSelected(), columnas.isSelected(),
                        vigasX.isSelected(), vigasY.isSelected(), dimensiones);
                CuadriculaService.VistaPrevia previa = walterQuispesayhuaServicio.walterQuispesayhuaPrevisualizar(proyectoId, solicitud);
                if (previa.elementos().isEmpty()) throw new IllegalArgumentException("La selección no genera elementos.");
                if (walterQuispesayhuaConfirmar(d, existentes, previa)) {
                    walterQuispesayhuaServicio.walterQuispesayhuaCrear(proyectoId, solicitud);
                    creado[0] = true;
                    dialogo.close();
                }
            } catch (IllegalArgumentException ex) { estado.setText(ex.getMessage()); }
        });
        Label notaVigas = new Label("Seleccione los tramos y sus dos intersecciones. Se generan vigas en cada nivel superior usando el lado indicado para los apoyos.");
        notaVigas.setWrapText(true);
        VBox contenido = new VBox(10, indicaciones, datos, actualizar, plano,
                new Label("Intersecciones X-Y"), new HBox(8, seleccionar, limpiar), puntosPanel,
                new Label("Elementos a generar"), new HBox(12, zapatas, columnas, vigasX, vigasY),
                notaVigas, tramosPanel, medidas, estado, new HBox(10, guardar, vista));
        contenido.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true); scroll.setPrefViewportWidth(650); scroll.setPrefViewportHeight(650);
        dialogo.getDialogPane().setContent(scroll);
        actualizar.fire();
        dialogo.showAndWait();
        return creado[0];
    }

    private boolean walterQuispesayhuaConfirmar(Definicion d, List<Elemento> existentes, CuadriculaService.VistaPrevia previa) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Vista previa de elementos");
        dialogo.setHeaderText("Revise la geometría antes de crearla");
        ButtonType crear = new ButtonType("Crear elementos", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(crear, ButtonType.CANCEL);
        Canvas plano = new Canvas(560, 300);
        walterQuispesayhuaDibujar(plano, d, existentes, previa.elementos());
        StringBuilder detalle = new StringBuilder();
        for (Elemento e : previa.elementos()) {
            detalle.append(String.format(java.util.Locale.ROOT, "%s %s | %.3f × %.3f × %.3f m | centro (%.3f, %.3f, %.3f)%n",
                    e.getTipo(), e.getNombre(), e.getLargo(), e.getAncho(), e.getAltura(), e.getX(), e.getY(), e.getZ()));
        }
        if (!previa.superposiciones().isEmpty()) {
            detalle.append("\nPosibles superposiciones:\n");
            previa.superposiciones().forEach(c -> detalle.append("• ").append(c).append('\n'));
        }
        TextArea texto = new TextArea(detalle.toString());
        texto.setEditable(false); texto.setWrapText(false); texto.setPrefRowCount(12);
        Label resumen = new Label(previa.elementos().size() + " elementos nuevos. "
                + (previa.superposiciones().isEmpty() ? "Sin superposiciones detectadas."
                : previa.superposiciones().size() + " posibles superposiciones; revise la lista."));
        resumen.setWrapText(true);
        dialogo.getDialogPane().setContent(new VBox(10, new Label("Gris: existentes · Azul: nuevos (vista en planta)"), plano, resumen, texto));
        dialogo.setResizable(true);
        return dialogo.showAndWait().filter(crear::equals).isPresent();
    }

    private void walterQuispesayhuaDibujar(Canvas plano, Definicion d, List<Elemento> existentes, List<Elemento> nuevos) {
        List<Double> xs = walterQuispesayhuaServicio.walterQuispesayhuaCoordenadas(d.origenX(), d.separacionesX());
        List<Double> ys = walterQuispesayhuaServicio.walterQuispesayhuaCoordenadas(d.origenY(), d.separacionesY());
        double minX = xs.get(0), maxX = xs.get(xs.size() - 1), minY = ys.get(0), maxY = ys.get(ys.size() - 1);
        for (Elemento e : existentes) {
            minX = Math.min(minX, e.getX() - e.getLargo() / 2); maxX = Math.max(maxX, e.getX() + e.getLargo() / 2);
            minY = Math.min(minY, e.getY() - e.getAncho() / 2); maxY = Math.max(maxY, e.getY() + e.getAncho() / 2);
        }
        for (Elemento e : nuevos) {
            minX = Math.min(minX, e.getX() - e.getLargo() / 2); maxX = Math.max(maxX, e.getX() + e.getLargo() / 2);
            minY = Math.min(minY, e.getY() - e.getAncho() / 2); maxY = Math.max(maxY, e.getY() + e.getAncho() / 2);
        }
        double escala = Math.min((plano.getWidth() - 60) / Math.max(1, maxX - minX), (plano.getHeight() - 60) / Math.max(1, maxY - minY));
        GraphicsContext g = plano.getGraphicsContext2D();
        g.setFill(Color.WHITE); g.fillRect(0, 0, plano.getWidth(), plano.getHeight());
        walterQuispesayhuaPintarElementos(g, existentes, minX, minY, escala, Color.rgb(100, 116, 139, 0.45));
        walterQuispesayhuaPintarElementos(g, nuevos, minX, minY, escala, Color.rgb(37, 99, 235, 0.55));
        g.setStroke(Color.DARKORANGE); g.setFill(Color.DARKORANGE); g.setLineWidth(1);
        for (int i = 0; i < xs.size(); i++) {
            double x = 30 + (xs.get(i) - minX) * escala;
            g.strokeLine(x, 20, x, plano.getHeight() - 20); g.fillText("X" + (i + 1), x + 2, 14);
        }
        for (int i = 0; i < ys.size(); i++) {
            double y = plano.getHeight() - 30 - (ys.get(i) - minY) * escala;
            g.strokeLine(20, y, plano.getWidth() - 20, y); g.fillText("Y" + (i + 1), 2, y - 2);
        }
    }

    private void walterQuispesayhuaPintarElementos(GraphicsContext g, List<Elemento> elementos, double minX, double minY, double escala, Color color) {
        g.setFill(color);
        for (Elemento e : elementos) {
            double x = 30 + (e.getX() - e.getLargo() / 2 - minX) * escala;
            double y = g.getCanvas().getHeight() - 30 - (e.getY() + e.getAncho() / 2 - minY) * escala;
            g.fillRect(x, y, Math.max(1, e.getLargo() * escala), Math.max(1, e.getAncho() * escala));
        }
    }

    private Definicion walterQuispesayhuaDefinicion(TextField x, TextField y, TextField sx, TextField sy, TextField niveles) {
        return new Definicion(walterQuispesayhuaNumero(x), walterQuispesayhuaNumero(y), walterQuispesayhuaLista(sx), walterQuispesayhuaLista(sy), walterQuispesayhuaLista(niveles));
    }
    private List<Double> walterQuispesayhuaLista(TextField campo) {
        List<Double> valores = new ArrayList<>();
        for (String parte : campo.getText().split(";", -1)) valores.add(walterQuispesayhuaNumero(parte));
        return List.copyOf(valores);
    }
    private double walterQuispesayhuaNumero(TextField campo) { return walterQuispesayhuaNumero(campo.getText()); }
    private double walterQuispesayhuaNumero(String texto) {
        try { return Double.parseDouble(texto.trim().replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ingrese números válidos separados por punto y coma."); }
    }
    private TextField campo(String valor) { TextField campo = new TextField(valor); campo.setPrefColumnCount(8); return campo; }
    private void walterQuispesayhuaAgregarTramo(List<Tramo> tramos, List<CheckBox> selectores, FlowPane panel, Punto inicio, Punto fin) {
        tramos.add(new Tramo(inicio, fin));
        CheckBox selector = walterQuispesayhuaOpcion((inicio.x() + 1) + "-" + (inicio.y() + 1) + " → " + (fin.x() + 1) + "-" + (fin.y() + 1));
        selectores.add(selector);
        panel.getChildren().add(selector);
    }
    private CheckBox walterQuispesayhuaOpcion(String nombre) { CheckBox opcion = new CheckBox(nombre); opcion.setSelected(true); return opcion; }
    private void walterQuispesayhuaFila(GridPane panel, int fila, String etiqueta, javafx.scene.Node campo) {
        panel.add(new Label(etiqueta), 0, fila); panel.add(campo, 1, fila);
    }
    private String walterQuispesayhuaTexto(double valor) { return Double.toString(valor); }
}
