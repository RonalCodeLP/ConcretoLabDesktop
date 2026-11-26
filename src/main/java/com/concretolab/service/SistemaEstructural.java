package com.concretolab.service;

import com.concretolab.service.NormaE030.Version;

public enum SistemaEstructural {
    PORTICOS("Pórticos (columnas y vigas)",
            "Solo columnas y vigas de concreto: al menos el 80 % de la fuerza cortante en la base la toman las columnas de los pórticos.",
            8, 8, 35, true, "Es el sistema que modela la aplicación (CT = 35; con muros en ascensores/escaleras sería 45)."),
    DUAL("Dual (pórticos y muros)",
            "Pórticos más muros de concreto: los muros toman más del 20 % y menos del 70 % del cortante en la base.",
            7, 7, 60, false, "Aún no modelable en la aplicación."),
    MUROS("Muros estructurales",
            "Predominan los muros de concreto: toman al menos el 70 % del cortante en la base.",
            6, 6, 60, false, "Aún no modelable en la aplicación."),
    MUROS_DUCTILIDAD_LIMITADA("Muros de ductilidad limitada (EMDL)",
            "Alta densidad de muros delgados (desde 10 cm) sin extremos confinados; máximo 5 pisos.",
            3.5, 4, 60, false, "R0 = 3,5 en el texto 2026 y 4 en la versión 2018. Aún no modelable en la aplicación."),
    ALBANILERIA("Albañilería armada o confinada",
            "Muros de ladrillo o bloque que cargan el edificio y resisten el sismo (para la E.030 no se distingue armada de confinada).",
            3, 3, 60, false, "Aún no modelable en la aplicación.");

    public final String etiqueta;
    public final String descripcion;
    private final double r02026, r02018, ct;
    private final boolean modelable;
    public final String nota;

    SistemaEstructural(String etiqueta, String descripcion, double r02026, double r02018, double ct, boolean modelable, String nota) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.r02026 = r02026;
        this.r02018 = r02018;
        this.ct = ct;
        this.modelable = modelable;
        this.nota = nota;
    }

    public double r0(Version v) { return v == Version.V2026 ? r02026 : r02018; }

    public double ct() { return ct; }

    public boolean modelable() { return modelable; }

    @Override public String toString() { return etiqueta; }
}
