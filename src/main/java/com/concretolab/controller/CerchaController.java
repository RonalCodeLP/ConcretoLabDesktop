package com.concretolab.controller;

import com.concretolab.model.ModeloCercha;
import com.concretolab.model.Proyecto;
import com.concretolab.service.AnalisisCercha;
import com.concretolab.service.ModeloCerchaService;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Analisis de cerchas 2D: entrada de nudos y barras, dibujo del esquema y resultados. */
@Component
@RequiredArgsConstructor
public class CerchaController implements Refrescable {

    // Colores del dibujo (el lienzo no hereda el tema, por eso son constantes con nombre).
    private static final Color COLOR_FONDO_LIENZO = Color.web("#f2f6fc");
    private static final Color COLOR_BARRA = Color.web("#2263bd");
    private static final Color COLOR_NUDO = Color.web("#0f2740");
    private static final Color COLOR_CARGA = Color.FIREBRICK;

    private final ModeloCerchaService modelos;
    private final SesionProyecto sesion;
    private final Notificador notificador;

    @FXML private Label lblProyecto;
    @FXML private ComboBox<ModeloCercha> cmbGuardados;
    @FXML private TextField txtNombre;
    @FXML private TextArea txtNudos;
    @FXML private TextArea txtBarras;
    @FXML private TextArea txtResultado;
    @FXML private Canvas cnvLienzo;

    // Panel lateral: nudo
    @FXML private VBox panelNudo;
    @FXML private TextField txtNudoNombre;
    @FXML private TextField txtNudoX;
    @FXML private TextField txtNudoY;
    @FXML private TextField txtNudoFx;
    @FXML private TextField txtNudoFy;
    @FXML private CheckBox chkApoyoX;
    @FXML private CheckBox chkApoyoY;

    // Panel lateral: barra
    @FXML private VBox panelBarra;
    @FXML private TextField txtBarraNombre;
    @FXML private TextField txtBarraModulo;
    @FXML private TextField txtBarraArea;
    @FXML private ComboBox<String> cmbInicio;
    @FXML private ComboBox<String> cmbFin;

    private ModeloCercha activo;
    /** Proyecto cuyos modelos estan cargados; sirve para detectar un cambio de proyecto en {@link #refrescar()}. */
    private Long proyectoCargadoId;

    @FXML
    public void initialize() {
        txtResultado.setEditable(false);
        txtResultado.setWrapText(false);

        // Cualquier cambio en los datos invalida el dibujo y los resultados anteriores.
        Runnable invalida = () -> {
            txtResultado.setText("Los datos cambiaron. Vuelve a dibujar y calcular.");
            limpiarLienzo();
        };
        txtNudos.textProperty().addListener((o, a, b) -> invalida.run());
        txtBarras.textProperty().addListener((o, a, b) -> invalida.run());

        Formularios.limpiarAlEditar(txtNudoNombre, txtNudoX, txtNudoY, txtNudoFx, txtNudoFy,
                txtBarraNombre, txtBarraModulo, txtBarraArea);
        cmbInicio.valueProperty().addListener((o, a, b) -> Formularios.limpiarError(cmbInicio));
        cmbFin.valueProperty().addListener((o, a, b) -> Formularios.limpiarError(cmbFin));
    }

    @Override
    public void refrescar() {
        Proyecto proyecto = sesion.getProyecto();
        Long id = proyecto == null ? null : proyecto.getId();
        lblProyecto.setText(proyecto == null ? "Selecciona un proyecto" : proyecto.getNombre());

        if (!Objects.equals(id, proyectoCargadoId)) {
            proyectoCargadoId = id;
            activo = null;
            cmbGuardados.getSelectionModel().clearSelection();
            cmbGuardados.setItems(FXCollections.observableArrayList(
                    id == null ? List.<ModeloCercha>of() : modelos.listar(id)));
            limpiar();
        }
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onGuardadoSeleccionado() {
        ModeloCercha m = cmbGuardados.getValue();
        if (m != null) {
            activo = m;
            txtNombre.setText(m.getNombre());
            txtNudos.setText(m.getNudos());
            txtBarras.setText(m.getBarras());
        }
    }

    @FXML
    private void onNuevoModelo() {
        activo = null;
        cmbGuardados.getSelectionModel().clearSelection();
        limpiar();
    }

    @FXML
    private void onCargarEjemplo() {
        activo = null;
        cmbGuardados.getSelectionModel().clearSelection();
        txtNombre.setText("Cercha triangular de ejemplo");
        txtNudos.setText("A; 0; 0; SI; SI; 0; 0\nB; 4; 0; NO; SI; 0; 0\nC; 2; 3; NO; NO; 0; -30");
        txtBarras.setText("AB; A; B; 200000; 20\nAC; A; C; 200000; 20\nBC; B; C; 200000; 20");
    }

    @FXML
    private void onGuardar() {
        try {
            Proyecto proyecto = sesion.getProyecto();
            if (proyecto == null) {
                throw new IllegalArgumentException("Selecciona un proyecto en «Modelo 3D y metrado».");
            }
            ModeloCercha m = new ModeloCercha();
            if (activo != null) {
                m.setId(activo.getId());
            }
            m.setProyectoId(proyecto.getId());
            m.setNombre(txtNombre.getText());
            m.setNudos(txtNudos.getText());
            m.setBarras(txtBarras.getText());
            activo = modelos.guardar(m);
            cmbGuardados.setItems(FXCollections.observableArrayList(modelos.listar(proyecto.getId())));
            txtResultado.setText("Modelo guardado localmente. Pulsa Calcular para obtener resultados.");
        } catch (Exception ex) {
            error(ex);
        }
    }

    @FXML
    private void onDibujar() {
        try {
            var datos = ModeloCerchaService.interpretar(txtNudos.getText(), txtBarras.getText());
            dibujar(datos);
        } catch (Exception ex) {
            error(ex);
        }
    }

    @FXML
    private void onCalcular() {
        try {
            var datos = ModeloCerchaService.interpretar(txtNudos.getText(), txtBarras.getText());
            var resultado = AnalisisCercha.resolver(datos.nudos(), datos.barras());
            dibujar(datos);
            txtResultado.setText(formatear(resultado));
        } catch (Exception ex) {
            txtResultado.clear();
            error(ex);
        }
    }

    // ------------------------------------------------------- panel: nuevo nudo

    @FXML
    private void onAgregarNudo() {
        Formularios.limpiarErrores(txtNudoNombre, txtNudoX, txtNudoY, txtNudoFx, txtNudoFy);
        txtNudoNombre.clear();
        txtNudoX.clear();
        txtNudoY.clear();
        txtNudoFx.setText("0");
        txtNudoFy.setText("0");
        chkApoyoX.setSelected(false);
        chkApoyoY.setSelected(false);
        notificador.abrirPanel(panelNudo);
        Platform.runLater(txtNudoNombre::requestFocus);
    }

    @FXML
    private void onConfirmarNudo() {
        Formularios.limpiarErrores(txtNudoNombre, txtNudoX, txtNudoY, txtNudoFx, txtNudoFy);
        String clave = leerIdentificador(txtNudoNombre);
        Double px = leerNumero(txtNudoX);
        Double py = leerNumero(txtNudoY);
        Double cx = leerNumero(txtNudoFx);
        Double cy = leerNumero(txtNudoFy);
        if (clave == null || px == null || py == null || cx == null || cy == null) {
            return;
        }
        try {
            var actual = ModeloCerchaService.interpretar(txtNudos.getText(), txtBarras.getText());
            if (actual.nudos().stream().anyMatch(n -> n.nombre().equals(clave))) {
                Formularios.marcarError(txtNudoNombre, "Ya existe un nudo con ese nombre.");
                return;
            }
            agregarLinea(txtNudos, String.join("; ", clave, Double.toString(px), Double.toString(py),
                    chkApoyoX.isSelected() ? "SI" : "NO", chkApoyoY.isSelected() ? "SI" : "NO",
                    Double.toString(cx), Double.toString(cy)));
            notificador.cerrarPanel();
        } catch (Exception ex) {
            error(ex);
        }
    }

    // ------------------------------------------------------ panel: nueva barra

    @FXML
    private void onAgregarBarra() {
        try {
            var datos = ModeloCerchaService.interpretar(txtNudos.getText(), txtBarras.getText());
            if (datos.nudos().size() < 2) {
                throw new IllegalArgumentException("Agrega al menos dos nudos antes de crear una barra.");
            }
            Formularios.limpiarErrores(txtBarraNombre, txtBarraModulo, txtBarraArea, cmbInicio, cmbFin);
            txtBarraNombre.clear();
            txtBarraModulo.clear();
            txtBarraArea.clear();
            List<String> nombres = datos.nudos().stream().map(AnalisisCercha.Nudo::nombre).toList();
            cmbInicio.setItems(FXCollections.observableArrayList(nombres));
            cmbFin.setItems(FXCollections.observableArrayList(nombres));
            cmbInicio.setValue(null);
            cmbFin.setValue(null);
            notificador.abrirPanel(panelBarra);
            Platform.runLater(txtBarraNombre::requestFocus);
        } catch (Exception ex) {
            error(ex);
        }
    }

    @FXML
    private void onConfirmarBarra() {
        Formularios.limpiarErrores(txtBarraNombre, txtBarraModulo, txtBarraArea, cmbInicio, cmbFin);
        String clave = leerIdentificador(txtBarraNombre);
        Double modulo = leerNumero(txtBarraModulo);
        Double area = leerNumero(txtBarraArea);
        if (clave == null || modulo == null || area == null) {
            return;
        }
        if (modulo <= 0 || area <= 0) {
            if (modulo <= 0) {
                Formularios.marcarError(txtBarraModulo, "El módulo y el área deben ser positivos.");
            }
            if (area <= 0) {
                Formularios.marcarError(txtBarraArea, "El módulo y el área deben ser positivos.");
            }
            return;
        }
        String inicio = cmbInicio.getValue();
        String fin = cmbFin.getValue();
        if (inicio == null || fin == null || inicio.equals(fin)) {
            if (inicio == null) {
                Formularios.marcarError(cmbInicio, "Elige dos nudos distintos.");
            }
            if (fin == null || inicio != null) {
                Formularios.marcarError(cmbFin, "Elige dos nudos distintos.");
            }
            return;
        }
        try {
            var datos = ModeloCerchaService.interpretar(txtNudos.getText(), txtBarras.getText());
            if (datos.barras().stream().anyMatch(b -> b.nombre().equals(clave))) {
                Formularios.marcarError(txtBarraNombre, "Ya existe una barra con ese nombre.");
                return;
            }
            agregarLinea(txtBarras, String.join("; ", clave, inicio, fin,
                    Double.toString(modulo), Double.toString(area)));
            notificador.cerrarPanel();
        } catch (Exception ex) {
            error(ex);
        }
    }

    @FXML
    private void onCerrarPanel() {
        notificador.cerrarPanel();
    }

    // ----------------------------------------------------------------- apoyo

    private void limpiar() {
        txtNombre.clear();
        txtNudos.clear();
        txtBarras.clear();
        txtResultado.clear();
        limpiarLienzo();
    }

    private void limpiarLienzo() {
        cnvLienzo.getGraphicsContext2D().clearRect(0, 0, cnvLienzo.getWidth(), cnvLienzo.getHeight());
    }

    /** Devuelve el nombre sin espacios sobrantes, o {@code null} tras marcar el error en el campo. */
    private String leerIdentificador(TextField campo) {
        String v = campo.getText().trim();
        if (v.isEmpty() || v.contains(";") || v.contains("\n") || v.contains("\r")) {
            Formularios.marcarError(campo, "Ingresa un nombre sin punto y coma.");
            return null;
        }
        return v;
    }

    /** Devuelve el numero finito del campo, o {@code null} tras marcar el error en el campo. */
    private Double leerNumero(TextField campo) {
        try {
            double v = Double.parseDouble(campo.getText().trim().replace(',', '.'));
            if (!Double.isFinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            Formularios.marcarError(campo, "Ingresa números válidos y finitos.");
            return null;
        }
    }

    private void agregarLinea(TextArea campo, String linea) {
        campo.appendText((campo.getText().isBlank() ? "" : "\n") + linea);
    }

    private String formatear(AnalisisCercha.Resultado r) {
        StringBuilder s = new StringBuilder("NUDOS · desplazamientos mm · reacciones kN\n");
        for (var n : r.nudos()) {
            s.append(String.format(Locale.ROOT, "%s  Ux=%+.5f  Uy=%+.5f  Rx=%+.4f  Ry=%+.4f%n",
                    n.nombre(), n.uxMm(), n.uyMm(), n.rxKn(), n.ryKn()));
        }
        s.append("\nBARRAS · fuerza axial kN · esfuerzo MPa\nSigno positivo: tracción; negativo: compresión.\n");
        for (var b : r.barras()) {
            s.append(String.format(Locale.ROOT, "%s  L=%.3f m  N=%+.4f kN  σ=%+.4f MPa%n",
                    b.nombre(), b.longitudM(), b.axialKn(), b.tensionMpa()));
        }
        return s.toString();
    }

    private void dibujar(ModeloCerchaService.Datos datos) {
        if (datos.nudos().isEmpty()) {
            throw new IllegalArgumentException("Agrega nudos para visualizar el modelo.");
        }
        Map<String, AnalisisCercha.Nudo> mapa = new HashMap<>();
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minY = minX, maxY = maxX;
        for (var n : datos.nudos()) {
            if (!Double.isFinite(n.x()) || !Double.isFinite(n.y())) {
                throw new IllegalArgumentException("Coordenadas inválidas.");
            }
            mapa.put(n.nombre(), n);
            minX = Math.min(minX, n.x());
            maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y());
            maxY = Math.max(maxY, n.y());
        }
        double escala = Math.min(480 / Math.max(maxX - minX, 1), 220 / Math.max(maxY - minY, 1));
        double origenX = (cnvLienzo.getWidth() - (maxX - minX) * escala) / 2 - minX * escala;
        double origenY = (cnvLienzo.getHeight() + (maxY - minY) * escala) / 2 + minY * escala;

        GraphicsContext g = cnvLienzo.getGraphicsContext2D();
        g.setFill(COLOR_FONDO_LIENZO);
        g.fillRect(0, 0, cnvLienzo.getWidth(), cnvLienzo.getHeight());
        g.setStroke(COLOR_BARRA);
        g.setLineWidth(3);
        for (var b : datos.barras()) {
            var a = mapa.get(b.inicio());
            var z = mapa.get(b.fin());
            if (a == null || z == null) {
                throw new IllegalArgumentException("Barra " + b.nombre() + ": nudo inexistente.");
            }
            g.strokeLine(origenX + a.x() * escala, origenY - a.y() * escala,
                    origenX + z.x() * escala, origenY - z.y() * escala);
        }
        for (var n : datos.nudos()) {
            double x = origenX + n.x() * escala;
            double y = origenY - n.y() * escala;
            g.setFill(COLOR_NUDO);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.fillText(n.nombre() + " [" + (n.restringeX() ? "X" : "") + (n.restringeY() ? "Y" : "") + "]",
                    x + 6, y - 6);
            if (n.fx() != 0 || n.fy() != 0) {
                double longitud = Math.hypot(n.fx(), n.fy());
                g.setStroke(COLOR_CARGA);
                g.strokeLine(x - 35 * n.fx() / longitud, y + 35 * n.fy() / longitud, x, y);
                g.fillText("F", x - 30 * n.fx() / longitud, y + 30 * n.fy() / longitud);
                g.setStroke(COLOR_BARRA);
            }
        }
    }

    private void error(Exception ex) {
        String mensaje = ex.getMessage();
        notificador.error(mensaje == null ? "No se pudo completar la operación." : mensaje);
    }
}
