package com.concretolab.controller;

import com.concretolab.ui.Refrescable;
import javafx.fxml.FXML;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Pantalla de bienvenida: accesos directos a los módulos principales. */
@Component
public class InicioController implements Refrescable {

    private final MainController principal;

    public InicioController(@Lazy MainController principal) {
        this.principal = principal;
    }

    @Override
    public void refrescar() {
        // Sin datos que recargar.
    }

    @FXML private void onAbrirModelo() { principal.irA("modelo-view"); }
    @FXML private void onAbrirMateriales() { principal.irA("materiales-view"); }
    @FXML private void onAbrirMezclas() { principal.irA("mezclas-view"); }
    @FXML private void onAbrirSismo() { principal.irA("sismo-view"); }
}
