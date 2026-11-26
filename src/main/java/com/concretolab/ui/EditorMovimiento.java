package com.concretolab.ui;

import com.concretolab.model.Elemento;
import com.concretolab.service.MovimientoElementoService;
import com.concretolab.service.MovimientoElementoService.Estado;
import com.concretolab.service.MovimientoElementoService.Posicion;
import com.concretolab.service.MovimientoElementoService.Sugerencia;
import com.concretolab.service.MovimientoElementoService.VistaPrevia;
import com.concretolab.service.MovimientoElementoService.Candidato;
import com.concretolab.service.MovimientoElementoService.Operacion;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class EditorMovimiento {
    private final MovimientoElementoService walterQuispesayhuaServicio;
    private final ErickDelgadoVisor3D walterQuispesayhuaVisor;
    private final VBox walterQuispesayhuaPanel;
    private final Consumer<Operacion> walterQuispesayhuaAlGuardar;
    private final Runnable walterQuispesayhuaAlCerrar;
    private Estado walterQuispesayhuaOriginal;
    private Posicion walterQuispesayhuaPosicion;
    private TextField x;
    private TextField y;
    private TextField walterQuispesayhuaZ;
    private ComboBox<String> walterQuispesayhuaEje;
    private Label walterQuispesayhuaEstado;
    private VBox walterQuispesayhuaSugerencias;
    private boolean walterQuispesayhuaActualizando;
    private List<Candidato> walterQuispesayhuaCandidatos;
    private final Map<Long, CheckBox> vinculos = new LinkedHashMap<>();

    public EditorMovimiento(MovimientoElementoService servicio, ErickDelgadoVisor3D visor, VBox panel,
                            Consumer<Operacion> alGuardar, Runnable alCerrar) {
        this.walterQuispesayhuaServicio = servicio;
        this.walterQuispesayhuaVisor = visor;
        this.walterQuispesayhuaPanel = panel;
        this.walterQuispesayhuaAlGuardar = alGuardar;
        this.walterQuispesayhuaAlCerrar = alCerrar;
    }

    public void walterQuispesayhuaIniciar(Elemento elemento) {
        walterQuispesayhuaOriginal = walterQuispesayhuaServicio.walterQuispesayhuaEstado(elemento);
        walterQuispesayhuaPosicion = walterQuispesayhuaOriginal.posicion();
        x = campo(walterQuispesayhuaPosicion.x()); y = campo(walterQuispesayhuaPosicion.y()); walterQuispesayhuaZ = campo(walterQuispesayhuaPosicion.z());
        walterQuispesayhuaEje = new ComboBox<>();
        walterQuispesayhuaEje.getItems().setAll("X", "Y", "Z"); walterQuispesayhuaEje.setValue("X");
        walterQuispesayhuaEstado = new Label(); walterQuispesayhuaEstado.setWrapText(true);
        walterQuispesayhuaSugerencias = new VBox(4);
        walterQuispesayhuaCandidatos = walterQuispesayhuaServicio.walterQuispesayhuaCandidatos(walterQuispesayhuaOriginal);
        vinculos.clear();
        VBox panelVinculos = new VBox(4);
        if (!walterQuispesayhuaCandidatos.isEmpty()) {
            Label explicacion = new Label("Vigas en contacto: marque solo las conexiones que desea guardar y reajustar al mover la columna sobre el eje de cada viga.");
            explicacion.setWrapText(true);
            panelVinculos.getChildren().add(explicacion);
            for (Candidato candidato : walterQuispesayhuaCandidatos) {
                CheckBox casilla = new CheckBox(candidato.viga().nombre() + (candidato.vinculado() ? " (vínculo guardado)" : " (posible conexión)"));
                casilla.setSelected(candidato.vinculado());
                casilla.selectedProperty().addListener((o, a, b) -> walterQuispesayhuaActualizar());
                vinculos.put(candidato.viga().id(), casilla);
                panelVinculos.getChildren().add(casilla);
            }
        }
        Label titulo = new Label("Mover " + walterQuispesayhuaOriginal.tipo() + " " + walterQuispesayhuaOriginal.nombre());
        titulo.getStyleClass().add("grafico-titulo");
        Label instruccion = new Label("Elija un eje y arrastre horizontalmente el elemento naranja, o escriba la posición exacta del centro en metros.");
        instruccion.setWrapText(true);
        HBox coordenadas = new HBox(6, new Label("X"), x, new Label("Y"), y, new Label("Z"), walterQuispesayhuaZ, new Label("Eje"), walterQuispesayhuaEje);
        Button cancelar = new Button("Cancelar");
        Button guardar = new Button("Revisar y guardar");
        guardar.getStyleClass().add("accent");
        cancelar.setOnAction(e -> walterQuispesayhuaCerrar());
        guardar.setOnAction(e -> walterQuispesayhuaGuardar());
        walterQuispesayhuaPanel.getChildren().setAll(titulo, instruccion, coordenadas, panelVinculos, walterQuispesayhuaSugerencias, walterQuispesayhuaEstado, new HBox(8, cancelar, guardar));
        walterQuispesayhuaPanel.setSpacing(6); walterQuispesayhuaPanel.setPadding(new Insets(8)); walterQuispesayhuaPanel.setManaged(true); walterQuispesayhuaPanel.setVisible(true);
        x.textProperty().addListener((o, a, b) -> walterQuispesayhuaActualizarDesdeCampos());
        y.textProperty().addListener((o, a, b) -> walterQuispesayhuaActualizarDesdeCampos());
        walterQuispesayhuaZ.textProperty().addListener((o, a, b) -> walterQuispesayhuaActualizarDesdeCampos());
        walterQuispesayhuaVisor.erickDelgadoIniciarMovimiento(walterQuispesayhuaOriginal.id(), this::walterQuispesayhuaArrastrar);
        walterQuispesayhuaVisor.erickDelgadoMostrarReferencias(walterQuispesayhuaServicio.walterQuispesayhuaReferencias(walterQuispesayhuaOriginal.proyectoId()));
        walterQuispesayhuaActualizar();
    }

    private void walterQuispesayhuaArrastrar(double delta) {
        if (!Double.isFinite(delta)) return;
        Posicion nueva = switch (walterQuispesayhuaEje.getValue()) {
            case "Y" -> new Posicion(walterQuispesayhuaPosicion.x(), walterQuispesayhuaPosicion.y() + delta, walterQuispesayhuaPosicion.z());
            case "Z" -> new Posicion(walterQuispesayhuaPosicion.x(), walterQuispesayhuaPosicion.y(), walterQuispesayhuaPosicion.z() + delta);
            default -> new Posicion(walterQuispesayhuaPosicion.x() + delta, walterQuispesayhuaPosicion.y(), walterQuispesayhuaPosicion.z());
        };
        walterQuispesayhuaEstablecer(nueva);
    }

    private void walterQuispesayhuaEstablecer(Posicion nueva) {
        walterQuispesayhuaActualizando = true;
        try {
            x.setText(walterQuispesayhuaFormato(nueva.x())); y.setText(walterQuispesayhuaFormato(nueva.y())); walterQuispesayhuaZ.setText(walterQuispesayhuaFormato(nueva.z()));
            walterQuispesayhuaPosicion = nueva;
        } finally { walterQuispesayhuaActualizando = false; }
        walterQuispesayhuaActualizar();
    }

    private void walterQuispesayhuaActualizarDesdeCampos() {
        if (walterQuispesayhuaActualizando) return;
        try {
            Posicion nueva = new Posicion(walterQuispesayhuaNumero(x), walterQuispesayhuaNumero(y), walterQuispesayhuaNumero(walterQuispesayhuaZ));
            if (!Double.isFinite(nueva.x()) || !Double.isFinite(nueva.y()) || !Double.isFinite(nueva.z()))
                throw new IllegalArgumentException("Ingrese posiciones finitas.");
            walterQuispesayhuaPosicion = nueva;
            walterQuispesayhuaActualizar();
        } catch (IllegalArgumentException ex) {
            walterQuispesayhuaSugerencias.getChildren().clear();
            walterQuispesayhuaEstado.setText(ex.getMessage());
        }
    }

    private void walterQuispesayhuaActualizar() {
        try {
            for (Candidato candidato : walterQuispesayhuaCandidatos) walterQuispesayhuaVisor.erickDelgadoPrevisualizar(candidato.viga());
            walterQuispesayhuaVisor.erickDelgadoPrevisualizar(walterQuispesayhuaOriginal.id(), walterQuispesayhuaPosicion);
            Operacion operacion = walterQuispesayhuaServicio.walterQuispesayhuaPreparar(walterQuispesayhuaOriginal, walterQuispesayhuaPosicion, walterQuispesayhuaSeleccionados());
            for (Estado propuesto : operacion.propuestos()) walterQuispesayhuaVisor.erickDelgadoPrevisualizar(propuesto);
            VistaPrevia vista = operacion.vista();
            walterQuispesayhuaSugerencias.getChildren().clear();
            if (!vista.sugerencias().isEmpty()) {
                FlowPane botones = new FlowPane(6, 6);
                for (Sugerencia sugerencia : vista.sugerencias()) {
                    Button aceptar = new Button(sugerencia.descripcion());
                    aceptar.setOnAction(e -> walterQuispesayhuaEstablecer(sugerencia.posicion()));
                    botones.getChildren().add(aceptar);
                }
                walterQuispesayhuaSugerencias.getChildren().addAll(new Label("Sugerencias opcionales:"), botones);
            }
            walterQuispesayhuaEstado.setText(vista.advertencias().isEmpty() ? "Sin superposiciones o separaciones detectadas."
                    : String.join("\n", vista.advertencias()));
        } catch (RuntimeException ex) {
            walterQuispesayhuaSugerencias.getChildren().clear();
            walterQuispesayhuaEstado.setText(walterQuispesayhuaMensaje(ex));
        }
    }

    private void walterQuispesayhuaGuardar() {
        try {
            Posicion introducida = new Posicion(walterQuispesayhuaNumero(x), walterQuispesayhuaNumero(y), walterQuispesayhuaNumero(walterQuispesayhuaZ));
            Operacion operacion = walterQuispesayhuaServicio.walterQuispesayhuaPreparar(walterQuispesayhuaOriginal, introducida, walterQuispesayhuaSeleccionados());
            VistaPrevia vista = operacion.vista();
            if (introducida.equals(walterQuispesayhuaOriginal.posicion())) { walterQuispesayhuaEstado.setText("La posición no ha cambiado."); return; }
            String detalle = "Centro anterior: " + walterQuispesayhuaMostrar(walterQuispesayhuaOriginal.posicion()) + " m\nCentro propuesto: " + walterQuispesayhuaMostrar(introducida)
                    + " m\nVolumen: " + walterQuispesayhuaFormato(walterQuispesayhuaOriginal.largo() * walterQuispesayhuaOriginal.ancho() * walterQuispesayhuaOriginal.altura()) + " m³";
            if (!vista.advertencias().isEmpty()) detalle += "\n\n" + String.join("\n", vista.advertencias());
            for (int i = 1; i < operacion.propuestos().size(); i++) {
                Estado antes = operacion.anteriores().get(i), despues = operacion.propuestos().get(i);
                detalle += "\n" + antes.nombre() + ": centro " + walterQuispesayhuaMostrar(antes.posicion()) + " → " + walterQuispesayhuaMostrar(despues.posicion())
                        + "; dimensiones " + walterQuispesayhuaFormato(antes.largo()) + " × " + walterQuispesayhuaFormato(antes.ancho()) + " → "
                        + walterQuispesayhuaFormato(despues.largo()) + " × " + walterQuispesayhuaFormato(despues.ancho()) + " m";
            }
            detalle += "\n\nSe guardarán los vínculos seleccionados. Los demás elementos no se moverán.";
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, detalle, ButtonType.CANCEL, ButtonType.OK);
            confirmacion.setTitle("Confirmar movimiento"); confirmacion.setHeaderText("Revisar cambio geométrico");
            if (confirmacion.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                Operacion guardada = walterQuispesayhuaServicio.walterQuispesayhuaGuardar(operacion);
                walterQuispesayhuaCerrar();
                walterQuispesayhuaAlGuardar.accept(guardada);
            }
        } catch (RuntimeException ex) { walterQuispesayhuaEstado.setText(walterQuispesayhuaMensaje(ex)); }
    }

    public void walterQuispesayhuaCerrar() {
        walterQuispesayhuaVisor.erickDelgadoTerminarMovimiento();
        walterQuispesayhuaPanel.getChildren().clear(); walterQuispesayhuaPanel.setManaged(false); walterQuispesayhuaPanel.setVisible(false);
        walterQuispesayhuaAlCerrar.run();
    }

    private TextField campo(double valor) { TextField campo = new TextField(walterQuispesayhuaFormato(valor)); campo.setPrefColumnCount(7); return campo; }
    private Set<Long> walterQuispesayhuaSeleccionados() {
        return vinculos.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    private double walterQuispesayhuaNumero(TextField campo) {
        try { return Double.parseDouble(campo.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ingrese posiciones válidas en metros."); }
    }
    private String walterQuispesayhuaFormato(double valor) { return java.math.BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString(); }
    private String walterQuispesayhuaMostrar(Posicion p) { return "(" + walterQuispesayhuaFormato(p.x()) + ", " + walterQuispesayhuaFormato(p.y()) + ", " + walterQuispesayhuaFormato(p.z()) + ")"; }
    private String walterQuispesayhuaMensaje(Exception ex) { return ex.getMessage() == null ? "No se pudo completar el movimiento." : ex.getMessage(); }
}
