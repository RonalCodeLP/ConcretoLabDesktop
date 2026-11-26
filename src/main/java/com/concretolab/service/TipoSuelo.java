package com.concretolab.service;

import com.concretolab.service.NormaE030.Suelo;

public enum TipoSuelo {
    ROCA("Roca sana o muy dura",
            "Cerro de roca sana o poco fracturada, afloramiento rocoso donde se cimenta directamente sobre la roca.",
            Suelo.S0, Suelo.S1_2018, 800, Double.POSITIVE_INFINITY,
            "En 2018 la roca dura S0 exige Vs30 > 1500 m/s; sin medirlo se toma S1 de 2018 (más desfavorable)."),
    MUY_RIGIDO("Suelo muy firme",
            "Grava arenosa muy densa (con bolonería) o arcilla muy compacta; no se hunde la punta de un pico al golpear.",
            Suelo.S1, Suelo.S1_2018, 550, 800, ""),
    RIGIDO("Suelo firme",
            "Arena densa o grava medianamente densa; arcilla muy compacta de menos de 20 m de espesor.",
            Suelo.S2, Suelo.S2_2018, 350, 550, ""),
    INTERMEDIO("Suelo intermedio",
            "Arena media a fina o arcilla firme; terreno que cede un poco al caminar con carga pero no es blando.",
            Suelo.S3, Suelo.S2_2018, 200, 350, ""),
    BLANDO("Suelo blando",
            "Arena suelta, arcilla blanda o relleno; el pie se hunde y el terreno se siente esponjoso o húmedo.",
            Suelo.S4, Suelo.S3_2018, 0, 200,
            "En zona 4 el texto 2026 no tabula el factor S del suelo S4: exige análisis de respuesta de sitio."),
    NO_SE("No tengo estudio de suelos",
            "Se usa un perfil conservador (suelo intermedio en 2026, suelo blando en 2018) hasta contar con el estudio.",
            Suelo.S3, Suelo.S3_2018, Double.NaN, Double.NaN,
            "El art. 16.1 de la E.030 (2026) exige un Estudio de Mecánica de Suelos o Informe Técnico de Suelos (E.050) en todo proyecto. "
                    + "Este resultado es solo referencial.");

    public final String etiqueta;
    public final String descripcion;
    public final String aviso;
    private final Suelo s2026, s2018;
    private final double vs30Min, vs30Max;

    TipoSuelo(String etiqueta, String descripcion, Suelo s2026, Suelo s2018, double vs30Min, double vs30Max, String aviso) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.s2026 = s2026;
        this.s2018 = s2018;
        this.vs30Min = vs30Min;
        this.vs30Max = vs30Max;
        this.aviso = aviso;
    }

    public Suelo suelo2026() { return s2026; }

    public Suelo suelo2018() { return s2018; }

    public Suelo suelo(NormaE030.Version v) { return v == NormaE030.Version.V2026 ? s2026 : s2018; }

    public double vs30Min() { return vs30Min; }

    public double vs30Max() { return vs30Max; }

    public boolean esSuposicion() { return this == NO_SE; }

    @Override public String toString() { return etiqueta; }
}
