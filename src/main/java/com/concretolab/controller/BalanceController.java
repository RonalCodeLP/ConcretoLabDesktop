package com.concretolab.controller;

import com.concretolab.service.VolumenAbsolutoService;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Refrescable;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class BalanceController implements Refrescable {
    @FXML private TextField txtAgua;
    @FXML private TextField txtRelacionAc;
    @FXML private TextField txtDensidadCemento;
    @FXML private TextField txtGrueso;
    @FXML private TextField txtDensidadGrueso;
    @FXML private TextField txtDensidadFino;
    @FXML private TextField txtAire;
    @FXML private Label lblResultado;
    @FXML private Button btnUsarEnMezcla;

    private final MainController yefriLermaPrincipal;
    private final MezclasController yefriLermaMezclas;

    private record YefriLermaBalance(double cemento, double agua, double finoSss, double gruesoSss, double relacionAc, double aire) { }

    private YefriLermaBalance yefriLermaUltimo;

    public BalanceController(@Lazy MainController principal, @Lazy MezclasController mezclas) {
        this.yefriLermaPrincipal = principal;
        this.yefriLermaMezclas = mezclas;
    }

    @FXML
    public void initialize() {
        TextField[] campos = yefriLermaCampos();
        for (TextField campo : campos) {
            campo.textProperty().addListener((obs, anterior, actual) -> {
                lblResultado.setText("Los datos cambiaron. Calcule nuevamente.");
                yefriLermaUltimo = null;
                btnUsarEnMezcla.setDisable(true);
            });
        }
        Formularios.fernandoHuancaLimpiarAlEditar(campos);
    }

    @Override
    public void refrescar() {
    }

    @FXML
    private void onCalcular() {
        Formularios.fernandoHuancaLimpiarErrores(yefriLermaCampos());
        yefriLermaUltimo = null;
        btnUsarEnMezcla.setDisable(true);
        try {
            var datos = new VolumenAbsolutoService.Datos(yefriLermaNum(txtAgua), yefriLermaNum(txtRelacionAc), yefriLermaNum(txtDensidadCemento), yefriLermaNum(txtGrueso),
                    yefriLermaNum(txtDensidadGrueso), yefriLermaNum(txtDensidadFino), yefriLermaNum(txtAire));
            var r = VolumenAbsolutoService.yefriLermaCalcular(datos);
            lblResultado.setText(String.format(Locale.ROOT,
                    "Cemento: %.2f kg/m³%nAgregado fino SSS: %.2f kg/m³%nAgua: %.2f kg/m³ · Agregado grueso SSS: %.2f kg/m³%n%nVolúmenes (m³): agua %.4f · cemento %.4f · grueso %.4f · fino %.4f · aire %.4f",
                    r.cemento(), r.finoSss(), datos.agua(), datos.gruesoSss(), r.volumenAgua(), r.volumenCemento(), r.volumenGrueso(), r.volumenFino(), r.volumenAire()));
            yefriLermaUltimo = new YefriLermaBalance(r.cemento(), datos.agua(), r.finoSss(), datos.gruesoSss(),
                    datos.relacionAguaCemento(), datos.airePorcentaje());
            btnUsarEnMezcla.setDisable(false);
        } catch (Exception ex) {
            lblResultado.setText(ex instanceof NumberFormatException ? "Ingrese números válidos en todos los campos." : ex.getMessage());
        }
    }

    @FXML
    private void onUsarEnMezcla() {
        var b = yefriLermaUltimo;
        if (b == null) {
            return;
        }
        yefriLermaPrincipal.fernandoHuancaIrA("mezclas-view");
        yefriLermaMezclas.yefriLermaNuevaMezclaDesde(
                String.format(Locale.ROOT, "Balance a/c %.2f", b.relacionAc()),
                "Balance de volúmenes absolutos",
                b.cemento(), b.agua(), b.finoSss(), b.gruesoSss(),
                String.format(Locale.ROOT, "Dosificación del balance de volúmenes (a/c %.2f, aire %.1f %%). "
                        + "Indique la f’c, la humedad y la absorción de los agregados antes de guardar.", b.relacionAc(), b.aire()));
    }

    private TextField[] yefriLermaCampos() {
        return new TextField[]{txtAgua, txtRelacionAc, txtDensidadCemento, txtGrueso, txtDensidadGrueso, txtDensidadFino, txtAire};
    }

    private static double yefriLermaNum(TextField f) {
        return Double.parseDouble(f.getText().trim().replace(',', '.'));
    }
}
