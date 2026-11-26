package com.concretolab.ui;

import com.concretolab.model.Proyecto;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

@Component
public class SesionProyecto {
    private final ObjectProperty<Proyecto> fernandoHuancaActual = new SimpleObjectProperty<>();

    public ObjectProperty<Proyecto> proyectoProperty() { return fernandoHuancaActual; }
    public Proyecto getProyecto() { return fernandoHuancaActual.get(); }
    public void setProyecto(Proyecto p) { fernandoHuancaActual.set(p); }
}
