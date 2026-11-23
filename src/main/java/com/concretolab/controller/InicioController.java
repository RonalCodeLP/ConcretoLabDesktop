package com.concretolab.controller;

import com.concretolab.ui.Refrescable;
import javafx.fxml.FXML;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Pantalla de bienvenida: accesos directos a los módulos principales. */
@Component
public class InicioController implements Refrescable {

    private final MainController fernandoHuancaPrincipal;

    public InicioController(@Lazy MainController principal) {
        this.fernandoHuancaPrincipal = principal;
    }

    @Override
    public void refrescar() {
        // Sin datos que recargar.
    }

    @FXML private void onAbrirModelo() { fernandoHuancaPrincipal.fernandoHuancaIrA("modelo-view"); }
    @FXML private void onAbrirMateriales() { fernandoHuancaPrincipal.fernandoHuancaIrA("materiales-view"); }
    @FXML private void onAbrirMezclas() { fernandoHuancaPrincipal.fernandoHuancaIrA("mezclas-view"); }
    @FXML private void onAbrirSismo() { fernandoHuancaPrincipal.fernandoHuancaIrA("sismo-view"); }
}
