package com.concretolab.service;

import java.util.List;

public record ResultadoTiempoHistoria(String nombreRegistro, double dt, int pasos, int pisos, double[] tiempo,
                                      double[][] ux, double[][] uy, double[][] rz,
                                      double[] sueloX, double[] sueloY,
                                      double[] aceleracionSueloX, double[] aceleracionSueloY,
                                      double[][] derivaX, double[][] derivaY,
                                      double[] derivaMaximaPisoX, double[] derivaMaximaPisoY,
                                      double derivaMaximaX, double derivaMaximaY,
                                      double desplazamientoTechoMaximoX, double desplazamientoTechoMaximoY,
                                      double[] cortanteBaseX, double[] cortanteBaseY,
                                      double cortanteBaseMaximoX, double cortanteBaseMaximoY,
                                      List<String> avisos) {
    public double desplazamientoAbsolutoX(int paso, int piso) {
        return ux[paso][piso] + sueloX[paso];
    }

    public double desplazamientoAbsolutoY(int paso, int piso) {
        return uy[paso][piso] + sueloY[paso];
    }
}
