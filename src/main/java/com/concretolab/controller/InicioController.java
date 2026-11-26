package com.concretolab.controller;

import com.concretolab.model.Proyecto;
import com.concretolab.service.ProyectoEjemploService;
import com.concretolab.ui.Notificador;
import com.concretolab.ui.Refrescable;
import com.concretolab.ui.SesionProyecto;
import javafx.fxml.FXML;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class InicioController implements Refrescable {
    private final MainController fernandoHuancaPrincipal;
    private final ProyectoEjemploService fernandoHuancaEjemplo;
    private final SesionProyecto fernandoHuancaSesion;
    private final Notificador fernandoHuancaNotificador;

    public InicioController(@Lazy MainController principal, ProyectoEjemploService ejemplo, SesionProyecto sesion,
                            Notificador notificador) {
        this.fernandoHuancaPrincipal = principal;
        this.fernandoHuancaEjemplo = ejemplo;
        this.fernandoHuancaSesion = sesion;
        this.fernandoHuancaNotificador = notificador;
    }

    @Override
    public void refrescar() {
    }

    @FXML private void onAbrirModelo() { fernandoHuancaPrincipal.fernandoHuancaIrA("modelo-view"); }
    @FXML private void onAbrirMateriales() { fernandoHuancaPrincipal.fernandoHuancaIrA("materiales-view"); }
    @FXML private void onAbrirMezclas() { fernandoHuancaPrincipal.fernandoHuancaIrA("mezclas-view"); }
    @FXML private void onAbrirSismo() { fernandoHuancaPrincipal.fernandoHuancaIrA("sismo-view"); }

    @FXML private void onAgregarEjemplo() { fernandoHuancaAgregarEjemplo(); }

    public void fernandoHuancaAgregarEjemplo() {
        Proyecto proyecto;
        try {
            proyecto = fernandoHuancaEjemplo.fernandoHuancaAgregar();
        } catch (RuntimeException ex) {
            fernandoHuancaNotificador.fernandoHuancaError("No se pudo agregar el proyecto de ejemplo: " + ex.getMessage());
            return;
        }
        fernandoHuancaSesion.setProyecto(proyecto);
        fernandoHuancaPrincipal.fernandoHuancaIrA("modelo-view");
        fernandoHuancaNotificador.exito("Se agregó «" + proyecto.getNombre() + "» y quedó como proyecto activo. "
                + "Sus otros proyectos no se modificaron.");
    }
}
