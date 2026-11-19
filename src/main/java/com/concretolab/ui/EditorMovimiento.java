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
    private final MovimientoElementoService servicio;
    private final MiajeldDelgadoVisor3D visor;
    private final VBox panel;
    private final Consumer<Operacion> alGuardar;
    private final Runnable alCerrar;
    private Estado original;
    private Posicion posicion;
    private TextField x;
    private TextField y;
    private TextField z;
    private ComboBox<String> eje;
    private Label estado;
    private VBox sugerencias;
    private boolean actualizando;
    private List<Candidato> candidatos;
    private final Map<Long, CheckBox> vinculos = new LinkedHashMap<>();

    public EditorMovimiento(MovimientoElementoService servicio, MiajeldDelgadoVisor3D visor, VBox panel,
                            Consumer<Operacion> alGuardar, Runnable alCerrar) {
        this.servicio = servicio;
        this.visor = visor;
        this.panel = panel;
        this.alGuardar = alGuardar;
        this.alCerrar = alCerrar;
    }

    public void iniciar(Elemento elemento) {
        original = servicio.estado(elemento);
        posicion = original.posicion();
        x = campo(posicion.x()); y = campo(posicion.y()); z = campo(posicion.z());
        eje = new ComboBox<>();
        eje.getItems().setAll("X", "Y", "Z"); eje.setValue("X");
        estado = new Label(); estado.setWrapText(true);
        sugerencias = new VBox(4);
        candidatos = servicio.candidatos(original);
        vinculos.clear();
        VBox panelVinculos = new VBox(4);
        if (!candidatos.isEmpty()) {
            Label explicacion = new Label("Vigas en contacto: marca solo las conexiones que deseas guardar y reajustar al mover la columna sobre el eje de cada viga.");
            explicacion.setWrapText(true);
            panelVinculos.getChildren().add(explicacion);
            for (Candidato candidato : candidatos) {
                CheckBox casilla = new CheckBox(candidato.viga().nombre() + (candidato.vinculado() ? " (vínculo guardado)" : " (posible conexión)"));
                casilla.setSelected(candidato.vinculado());
                casilla.selectedProperty().addListener((o, a, b) -> actualizar());
                vinculos.put(candidato.viga().id(), casilla);
                panelVinculos.getChildren().add(casilla);
            }
        }
        Label titulo = new Label("Mover " + original.tipo() + " " + original.nombre());
        titulo.getStyleClass().add("grafico-titulo");
        Label instruccion = new Label("Elige un eje y arrastra horizontalmente el elemento naranja, o escribe la posición exacta del centro en metros.");
        instruccion.setWrapText(true);
        HBox coordenadas = new HBox(6, new Label("X"), x, new Label("Y"), y, new Label("Z"), z, new Label("Eje"), eje);
        Button cancelar = new Button("Cancelar");
        Button guardar = new Button("Revisar y guardar");
        guardar.getStyleClass().add("accent");
        cancelar.setOnAction(e -> cerrar());
        guardar.setOnAction(e -> guardar());
        panel.getChildren().setAll(titulo, instruccion, coordenadas, panelVinculos, sugerencias, estado, new HBox(8, cancelar, guardar));
        panel.setSpacing(6); panel.setPadding(new Insets(8)); panel.setManaged(true); panel.setVisible(true);
        x.textProperty().addListener((o, a, b) -> actualizarDesdeCampos());
        y.textProperty().addListener((o, a, b) -> actualizarDesdeCampos());
        z.textProperty().addListener((o, a, b) -> actualizarDesdeCampos());
        visor.iniciarMovimiento(original.id(), this::arrastrar);
        visor.mostrarReferencias(servicio.referencias(original.proyectoId()));
        actualizar();
    }

    private void arrastrar(double delta) {
        if (!Double.isFinite(delta)) return;
        Posicion nueva = switch (eje.getValue()) {
            case "Y" -> new Posicion(posicion.x(), posicion.y() + delta, posicion.z());
            case "Z" -> new Posicion(posicion.x(), posicion.y(), posicion.z() + delta);
            default -> new Posicion(posicion.x() + delta, posicion.y(), posicion.z());
        };
        establecer(nueva);
    }

    private void establecer(Posicion nueva) {
        actualizando = true;
        try {
            x.setText(formato(nueva.x())); y.setText(formato(nueva.y())); z.setText(formato(nueva.z()));
            posicion = nueva;
        } finally { actualizando = false; }
        actualizar();
    }

    private void actualizarDesdeCampos() {
        if (actualizando) return;
        try {
            Posicion nueva = new Posicion(numero(x), numero(y), numero(z));
            if (!Double.isFinite(nueva.x()) || !Double.isFinite(nueva.y()) || !Double.isFinite(nueva.z()))
                throw new IllegalArgumentException("Ingrese posiciones finitas.");
            posicion = nueva;
            actualizar();
        } catch (IllegalArgumentException ex) {
            sugerencias.getChildren().clear();
            estado.setText(ex.getMessage());
        }
    }

    private void actualizar() {
        try {
            for (Candidato candidato : candidatos) visor.previsualizar(candidato.viga());
            visor.previsualizar(original.id(), posicion);
            Operacion operacion = servicio.preparar(original, posicion, seleccionados());
            for (Estado propuesto : operacion.propuestos()) visor.previsualizar(propuesto);
            VistaPrevia vista = operacion.vista();
            sugerencias.getChildren().clear();
            if (!vista.sugerencias().isEmpty()) {
                FlowPane botones = new FlowPane(6, 6);
                for (Sugerencia sugerencia : vista.sugerencias()) {
                    Button aceptar = new Button(sugerencia.descripcion());
                    aceptar.setOnAction(e -> establecer(sugerencia.posicion()));
                    botones.getChildren().add(aceptar);
                }
                sugerencias.getChildren().addAll(new Label("Sugerencias opcionales:"), botones);
            }
            estado.setText(vista.advertencias().isEmpty() ? "Sin superposiciones o separaciones detectadas."
                    : String.join("\n", vista.advertencias()));
        } catch (RuntimeException ex) {
            sugerencias.getChildren().clear();
            estado.setText(mensaje(ex));
        }
    }

    private void guardar() {
        try {
            Posicion introducida = new Posicion(numero(x), numero(y), numero(z));
            Operacion operacion = servicio.preparar(original, introducida, seleccionados());
            VistaPrevia vista = operacion.vista();
            if (introducida.equals(original.posicion())) { estado.setText("La posición no ha cambiado."); return; }
            String detalle = "Centro anterior: " + mostrar(original.posicion()) + " m\nCentro propuesto: " + mostrar(introducida)
                    + " m\nVolumen: " + formato(original.largo() * original.ancho() * original.altura()) + " m³";
            if (!vista.advertencias().isEmpty()) detalle += "\n\n" + String.join("\n", vista.advertencias());
            for (int i = 1; i < operacion.propuestos().size(); i++) {
                Estado antes = operacion.anteriores().get(i), despues = operacion.propuestos().get(i);
                detalle += "\n" + antes.nombre() + ": centro " + mostrar(antes.posicion()) + " → " + mostrar(despues.posicion())
                        + "; dimensiones " + formato(antes.largo()) + " × " + formato(antes.ancho()) + " → "
                        + formato(despues.largo()) + " × " + formato(despues.ancho()) + " m";
            }
            detalle += "\n\nSe guardarán los vínculos seleccionados. Los demás elementos no se moverán.";
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, detalle, ButtonType.CANCEL, ButtonType.OK);
            confirmacion.setTitle("Confirmar movimiento"); confirmacion.setHeaderText("Revisar cambio geométrico");
            if (confirmacion.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                Operacion guardada = servicio.guardar(operacion);
                cerrar();
                alGuardar.accept(guardada);
            }
        } catch (RuntimeException ex) { estado.setText(mensaje(ex)); }
    }

    public void cerrar() {
        visor.terminarMovimiento();
        panel.getChildren().clear(); panel.setManaged(false); panel.setVisible(false);
        alCerrar.run();
    }

    private TextField campo(double valor) { TextField campo = new TextField(formato(valor)); campo.setPrefColumnCount(7); return campo; }
    private Set<Long> seleccionados() {
        return vinculos.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    private double numero(TextField campo) {
        try { return Double.parseDouble(campo.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ingrese posiciones válidas en metros."); }
    }
    private String formato(double valor) { return java.math.BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString(); }
    private String mostrar(Posicion p) { return "(" + formato(p.x()) + ", " + formato(p.y()) + ", " + formato(p.z()) + ")"; }
    private String mensaje(Exception ex) { return ex.getMessage() == null ? "No se pudo completar el movimiento." : ex.getMessage(); }
}
