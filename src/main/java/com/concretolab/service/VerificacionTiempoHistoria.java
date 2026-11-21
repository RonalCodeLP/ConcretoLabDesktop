package com.concretolab.service;

import java.util.List;

/**
 * Verificación de distorsiones de entrepiso con un conjunto de análisis tiempo-historia
 * ({@link AnalisisTiempoHistoria#verificar}).
 *
 * <p>Criterio de diseño: E.030 2026 art. 49.1 (promedio de los máximos de los análisis, mínimo siete conjuntos, art. 47.1)
 * o E.030 2018 art. 4.7.3 (promedio si hay siete o más conjuntos; con menos de siete, el máximo; mínimo tres).
 * El límite es 1,25 × la distorsión admisible de la Tabla 14 (concreto armado: 0,007).</p>
 *
 * @param version                versión de la norma aplicada
 * @param criterio               "promedio" o "máximo"
 * @param conjuntos              número de registros analizados
 * @param conjuntosMinimos       mínimo exigido por la norma (7 en 2026, 3 en 2018)
 * @param conjuntosSuficientes   si se alcanza el mínimo exigido
 * @param limite                 distorsión límite usada (1,25·0,007)
 * @param pisosX                 resultado por piso en X
 * @param pisosY                 resultado por piso en Y
 * @param cumple                 todos los pisos cumplen en X e Y Y hay conjuntos suficientes
 * @param avisos                 advertencias (siempre incluye las limitaciones del método)
 */
public record VerificacionTiempoHistoria(NormaE030.Version version, String criterio, int conjuntos, int conjuntosMinimos,
                                         boolean conjuntosSuficientes, double limite,
                                         List<Piso> pisosX, List<Piso> pisosY, boolean cumple, List<String> avisos) {

    /**
     * Resultado por piso y dirección.
     *
     * @param piso            índice del piso (0 = primer nivel)
     * @param derivaDiseno    promedio (o máximo) de los máximos de deriva del piso
     * @param limite          distorsión límite
     * @param cumple          {@code derivaDiseno <= limite}
     */
    public record Piso(int piso, double derivaDiseno, double limite, boolean cumple) {}
}
