package com.concretolab.service;

import java.util.List;

public record VerificacionTiempoHistoria(NormaE030.Version version, String criterio, int conjuntos, int conjuntosMinimos,
                                         boolean conjuntosSuficientes, double limite,
                                         List<Piso> pisosX, List<Piso> pisosY, boolean cumple, List<String> avisos) {
    public record Piso(int piso, double derivaDiseno, double limite, boolean cumple) {}
}
