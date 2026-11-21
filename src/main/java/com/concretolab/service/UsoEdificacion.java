package com.concretolab.service;

import com.concretolab.service.NormaE030.Categoria;

/**
 * Uso de la edificación en lenguaje simple: categoría sísmica (E.030 texto 2026, Tabla 7, p. 10 impresa) y cargas vivas mínimas
 * (Norma E.020 «Cargas», RNE, Tabla 1, art. 6.1; techos art. 7.1). Valores de E.020 en kN/m² (kPa).
 *
 * <p>VERIFICACIÓN: las cargas vivas se contrastaron con el texto oficial de la E.020 publicado por el MVCS
 * (ww3.vivienda.gob.pe, «50 E.020 CARGAS.pdf»). Un uso con {@code verificado = false} tiene al menos un valor que no figura
 * literalmente en la Tabla 1 (se supuso por analogía o criterio conservador); su {@code fuente} lo indica.
 *
 * <p>La carga muerta adicional (acabados + tabiquería equivalente) NO está en la E.020, que exige usar pesos reales (arts. 3 y 4,
 * Anexo 1): es un valor típico SUGERIDO, nunca verificado contra la norma.
 *
 * <p>La categoría A se representa con {@link Categoria#A} (U = 1,5, %CV = 50 %). Para A1 (salud de 2.º y 3.er nivel) la Tabla 7 indica
 * «ver nota»: en zonas 3 y 4 lleva aislamiento sísmico y U = 1; en zonas 1 y 2, U mínimo 1,5.
 */
public enum UsoEdificacion {
    VIVIENDA("Vivienda", "Casa, departamento, edificio multifamiliar", Categoria.C, false,
            2.0, 2.0, 1.0, 2.0, true,
            "E.020 Tabla 1 Viviendas 2,0 (incluye corredores y escaleras); techo art. 7.1a"),
    OFICINAS("Oficinas", "Estudio, consultorio privado, banco pequeño, edificio de oficinas", Categoria.C, false,
            2.5, 4.0, 1.0, 1.5, true,
            "E.020 Tabla 1 Oficinas 2,5 (excepto archivo y computación), corredores y escaleras 4,0; no incluye tabiquería móvil (art. 6.3)"),
    HOTEL("Hotel o hostal", "Hotel, hostal, residencial (habitaciones)", Categoria.C, false,
            2.0, 4.0, 1.0, 2.0, true,
            "E.020 Tabla 1 Hoteles: cuartos 2,0, corredores y escaleras 4,0; salas públicas según lugares de asamblea"),
    RESTAURANTE("Restaurante", "Restaurante, cevichería, comedor público, discoteca", Categoria.C, false,
            4.0, 5.0, 1.0, 1.5, true,
            "E.020 Tabla 1 Lugares de asamblea: salones de baile y restaurantes 4,0, corredores y escaleras 5,0"),
    EDUCATIVO("Colegio, instituto o universidad", "Colegio, instituto tecnológico, universidad (aulas)", Categoria.A, false,
            2.5, 4.0, 1.0, 1.5, true,
            "E.020 Tabla 1 Centros de Educación: aulas 2,5, corredores y escaleras 4,0; categoría A2 (Tabla 7, instituciones educativas)"),
    SALUD_PRIMER_NIVEL("Posta o centro de salud (1.er nivel)", "Posta médica, puesto de salud, centro de salud sin hospitalización mayor",
            Categoria.A, false,
            2.0, 4.0, 1.0, 2.0, true,
            "E.020 Tabla 1 Hospitales: cuartos 2,0, corredores y escaleras 4,0; categoría A2 (salud no comprendida en A1)"),
    HOSPITAL("Hospital (2.º y 3.er nivel)", "Hospital, clínica o instituto de salud de segundo o tercer nivel", Categoria.A, true,
            3.0, 4.0, 1.0, 2.0, true,
            "E.020 Tabla 1 Hospitales: salas de operación, laboratorios y servicio 3,0 (cuartos 2,0), corredores y escaleras 4,0; "
                    + "categoría A1 (Tabla 7)"),
    COMERCIAL_ASAMBLEA("Centro comercial, cine, estadio o iglesia grande",
            "Centro comercial, tiendas, cine, teatro, estadio, coliseo, terminal de buses, museo, biblioteca",
            Categoria.B, false,
            5.0, 5.0, 1.0, 1.0, true,
            "E.020 Tabla 1 Tiendas 5,0 y corredores 5,0 (graderías y tribunas 5,0; cines/teatros zonas públicas según asamblea 4,0 a 5,0)"),
    INDUSTRIAL_COMUN("Industria o taller común", "Taller, fábrica pequeña, planta que no maneja materiales peligrosos", Categoria.C, false,
            5.0, 4.0, 1.0, 1.0, false,
            "E.020 Tabla 1 Industria: «Ver 6.4», sin valor fijo; 5,0 y 4,0 son valores SUGERIDOS (el proyectista debe justificar la carga real)"),
    DEPOSITO("Depósito o almacén", "Almacén, depósito de mercadería, cochera comercial (no almacena materiales peligrosos)", Categoria.C, false,
            5.0, 5.0, 1.0, 0.5, false,
            "E.020 Tabla 1 Almacenaje 5,0 (verificado); corredores y escaleras 5,0 asumido por conservadurismo (la Tabla 1 no lo fija); "
                    + "E.030 art. 31c: 80 % del peso almacenable"),
    ESENCIALES("Edificación esencial", "Bomberos, comisaría, cuartel, aeropuerto, puerto, central eléctrica, reservorio, local municipal",
            Categoria.A, false,
            2.5, 4.0, 1.0, 1.5, false,
            "E.030 Tabla 7 categoría A2; la E.020 no tiene fila propia: se asumen cargas de oficinas (2,5 y 4,0), SUGERIDO");

    public final String etiqueta;
    public final String ejemplos;
    private final Categoria categoria;
    private final boolean requiereAislamientoZ3Z4;
    /** Carga viva de pisos en kN/m². */
    public final double cvPisos;
    /** Carga viva de corredores y escaleras en kN/m². */
    public final double cvCorredoresEscaleras;
    /** Carga viva de techo en kN/m² (azotea plana hasta 3°, E.020 art. 7.1a). */
    public final double cvTecho;
    /** Carga muerta adicional (acabados + tabiquería equivalente) en kN/m²: valor típico SUGERIDO, no es de la norma. */
    public final double cargaMuertaAdicionalSugerida;
    /** Verdadero si todas las cargas vivas figuran literalmente en la E.020 Tabla 1 / art. 7.1. */
    public final boolean verificado;
    /** Fuente de las cargas vivas e indicación de su estado de verificación. */
    public final String fuente;

    UsoEdificacion(String etiqueta, String ejemplos, Categoria categoria, boolean aislamiento, double cvPisos, double cvCorredoresEscaleras,
                   double cvTecho, double cargaMuertaAdicionalSugerida, boolean verificado, String fuente) {
        this.etiqueta = etiqueta;
        this.ejemplos = ejemplos;
        this.categoria = categoria;
        this.requiereAislamientoZ3Z4 = aislamiento;
        this.cvPisos = cvPisos;
        this.cvCorredoresEscaleras = cvCorredoresEscaleras;
        this.cvTecho = cvTecho;
        this.cargaMuertaAdicionalSugerida = cargaMuertaAdicionalSugerida;
        this.verificado = verificado;
        this.fuente = (verificado ? "VERIFICADO contra texto oficial. " : "NO VERIFICADO (supuesto). ") + fuente
                + ". Carga muerta adicional: valor típico sugerido, no es de la norma (E.020 arts. 3 y 4 piden pesos reales).";
    }

    /** Categoría de la edificación según la Tabla 7 del texto 2026. */
    public Categoria categoria() { return categoria; }

    /** Verdadero para A1 (salud de 2.º y 3.er nivel): lleva aislamiento sísmico en zonas 3 y 4 (nota de la Tabla 7). */
    public boolean requiereAislamientoZ3Z4() { return requiereAislamientoZ3Z4; }

    @Override public String toString() { return etiqueta; }
}
