package com.concretolab.service;

import java.util.List;

/**
 * Resultado del análisis dinámico tiempo-historia lineal ({@link AnalisisTiempoHistoria#resolver}).
 *
 * <p>Convenciones: los desplazamientos {@code ux}, {@code uy}, {@code rz} son RELATIVOS a la base (al suelo), indexados
 * {@code [paso][piso]} con el piso 0 como primer nivel sobre la cimentación; unidades m y rad. El desplazamiento
 * ABSOLUTO de un piso es el relativo más el del terreno ({@link #desplazamientoAbsolutoX(int, int)}).
 * Las derivas son elásticas (sin factor de ductilidad) y adimensionales. Los cortantes están en kN.</p>
 *
 * @param nombreRegistro               nombre del registro sísmico usado
 * @param dt                           paso de tiempo (s)
 * @param pasos                        número de instantes (incluye t = 0)
 * @param pisos                        número de pisos
 * @param tiempo                       instantes t[i] = i·dt (s)
 * @param ux                           desplazamiento relativo en X del centro de masas [paso][piso] (m)
 * @param uy                           desplazamiento relativo en Y del centro de masas [paso][piso] (m)
 * @param rz                           giro relativo [paso][piso] (rad)
 * @param sueloX                       desplazamiento del terreno en X (m)
 * @param sueloY                       desplazamiento del terreno en Y (m)
 * @param aceleracionSueloX            aceleración del terreno en X (m/s²)
 * @param aceleracionSueloY            aceleración del terreno en Y (m/s²)
 * @param derivaX                      deriva de entrepiso en X en el centro de masas [paso][piso]
 * @param derivaY                      deriva de entrepiso en Y en el centro de masas [paso][piso]
 * @param derivaMaximaPisoX            máximo en el tiempo y entre todos los puntos de control del piso, por piso (X)
 * @param derivaMaximaPisoY            ídem en Y
 * @param derivaMaximaX                máximo de {@code derivaMaximaPisoX} entre pisos
 * @param derivaMaximaY                máximo de {@code derivaMaximaPisoY} entre pisos
 * @param desplazamientoTechoMaximoX   máximo |ux| relativo del último piso (m)
 * @param desplazamientoTechoMaximoY   máximo |uy| relativo del último piso (m)
 * @param cortanteBaseX                serie del cortante basal en X: −Σ m·(ü_rel + ag) (kN)
 * @param cortanteBaseY                serie del cortante basal en Y (kN)
 * @param cortanteBaseMaximoX          máximo |cortanteBaseX| (kN)
 * @param cortanteBaseMaximoY          máximo |cortanteBaseY| (kN)
 * @param avisos                       advertencias del cálculo
 */
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

    /** Desplazamiento absoluto en X del centro de masas del piso: relativo + terreno (m). Útil para animar. */
    public double desplazamientoAbsolutoX(int paso, int piso) {
        return ux[paso][piso] + sueloX[paso];
    }

    /** Desplazamiento absoluto en Y del centro de masas del piso: relativo + terreno (m). */
    public double desplazamientoAbsolutoY(int paso, int piso) {
        return uy[paso][piso] + sueloY[paso];
    }
}
