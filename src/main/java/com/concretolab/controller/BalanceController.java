package com.concretolab.controller;

import com.concretolab.service.VolumenAbsolutoService;
import com.concretolab.ui.Formularios;
import com.concretolab.ui.Refrescable;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

/** Balance de volúmenes: cemento y agregado fino por m³ a partir de los parámetros que ingresa el profesional. */
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

    @FXML
    public void initialize() {
        TextField[] campos = campos();
        for (TextField campo : campos) {
            campo.textProperty().addListener((obs, anterior, actual) -> lblResultado.setText("Los datos cambiaron. Calcula nuevamente."));
        }
        Formularios.limpiarAlEditar(campos);
    }

    @Override
    public void refrescar() {
        // Pantalla sin datos persistentes: se conservan los valores ingresados.
    }

    @FXML
    private void onCalcular() {
        Formularios.limpiarErrores(campos());
        try {
            var datos = new VolumenAbsolutoService.Datos(num(txtAgua), num(txtRelacionAc), num(txtDensidadCemento), num(txtGrueso),
                    num(txtDensidadGrueso), num(txtDensidadFino), num(txtAire));
            var r = VolumenAbsolutoService.calcular(datos);
            lblResultado.setText(String.format(Locale.ROOT,
                    "Cemento: %.2f kg/m³%nAgregado fino SSS: %.2f kg/m³%nAgua: %.2f kg/m³ · Agregado grueso SSS: %.2f kg/m³%n%nVolúmenes (m³): agua %.4f · cemento %.4f · grueso %.4f · fino %.4f · aire %.4f",
                    r.cemento(), r.finoSss(), datos.agua(), datos.gruesoSss(), r.volumenAgua(), r.volumenCemento(), r.volumenGrueso(), r.volumenFino(), r.volumenAire()));
        } catch (Exception ex) {
            lblResultado.setText(ex instanceof NumberFormatException ? "Ingrese números válidos en todos los campos." : ex.getMessage());
        }
    }

    private TextField[] campos() {
        return new TextField[]{txtAgua, txtRelacionAc, txtDensidadCemento, txtGrueso, txtDensidadGrueso, txtDensidadFino, txtAire};
    }

    private static double num(TextField f) {
        return Double.parseDouble(f.getText().trim().replace(',', '.'));
    }
}
