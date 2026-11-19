package com.concretolab.ui;

import com.concretolab.model.Proyecto;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

/**
 * Proyecto seleccionado en la aplicación. Lo comparten todas las vistas: el selector está en «Modelo 3D y metrado»
 * y las demás pantallas lo leen en {@code refrescar()}.
 */
@Component
public class SesionProyecto {
    private final ObjectProperty<Proyecto> actual = new SimpleObjectProperty<>();

    public ObjectProperty<Proyecto> proyectoProperty() { return actual; }
    public Proyecto getProyecto() { return actual.get(); }
    public void setProyecto(Proyecto p) { actual.set(p); }
}
