package com.concretolab.service;

import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Entrada;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Resultado;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recomendación de mejora cuando la deriva no cumple: busca el menor aumento de la sección de las columnas, en pasos de
 * 5 cm y en la dirección que falla, que hace cumplir la deriva. Cada intento se vuelve a analizar con el mismo motor y los
 * mismos datos (peso propio incluido), así que la medida sale del cálculo y no de una regla aproximada. Es una sugerencia
 * de anteproyecto: el ingeniero decide la solución (columnas, vigas, placas…).
 */
public final class RecomendacionSismica {

    /** Paso del aumento (m): las secciones se trabajan en múltiplos de 5 cm. */
    public static final double FERNANDO_HUANCA_PASO = 0.05;
    /** Aumento máximo que se prueba por dirección (m); más allá conviene otro sistema (placas, sistema dual). */
    public static final double FERNANDO_HUANCA_MAXIMO = 0.60;
    /** A partir de este aumento (m) se sugiere además peraltar vigas o agregar placas. */
    static final double FERNANDO_HUANCA_AUMENTO_GRANDE = 0.30;

    private RecomendacionSismica() {
    }

    /** Nueva sección de una columna (en m): largo es la medida en X y ancho la medida en Y. */
    public record Cambio(Elemento elemento, double largoNuevo, double anchoNuevo) {
    }

    /**
     * Resultado de la búsqueda. {@code aumentoX}/{@code aumentoY} se suman a la medida en X (largo) y en Y (ancho) de todas
     * las columnas. {@code suficiente} es falso si ni con el aumento máximo se llega al límite.
     */
    public record Recomendacion(boolean necesaria, boolean suficiente, double aumentoX, double aumentoY, List<Cambio> cambios,
                                double derivaXAntes, double derivaYAntes, double derivaXDespues, double derivaYDespues, String texto) {
    }

    /** Mayor deriva (estática o espectral) en X. */
    public static double fernandoHuancaDerivaX(Resultado r) {
        return Math.max(r.estaticoX().derivaMaxima(), r.espectralX().derivaMaxima());
    }

    /** Mayor deriva (estática o espectral) en Y. */
    public static double fernandoHuancaDerivaY(Resultado r) {
        return Math.max(r.estaticoY().derivaMaxima(), r.espectralY().derivaMaxima());
    }

    public static Recomendacion fernandoHuancaRecomendar(Entrada entrada, Resultado resultado) {
        double lim = NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO;
        double dx0 = fernandoHuancaDerivaX(resultado), dy0 = fernandoHuancaDerivaY(resultado);
        if (dx0 <= lim && dy0 <= lim) {
            return new Recomendacion(false, true, 0, 0, List.of(), dx0, dy0, dx0, dy0,
                    "Las derivas cumplen el límite " + lim + ": no hace falta cambiar las columnas.");
        }
        List<Elemento> columnas = entrada.elementos().stream().filter(e -> "COLUMNA".equals(e.getTipo())).toList();
        if (columnas.isEmpty()) {
            return new Recomendacion(true, false, 0, 0, List.of(), dx0, dy0, dx0, dy0,
                    "El modelo no tiene columnas: agregue columnas o muros para dar rigidez lateral.");
        }
        double ax = 0, ay = 0, dx = dx0, dy = dy0;
        boolean suficiente = true;
        while (dx > lim || dy > lim) {
            if (dx > lim) ax += FERNANDO_HUANCA_PASO;
            if (dy > lim) ay += FERNANDO_HUANCA_PASO;
            if (ax > FERNANDO_HUANCA_MAXIMO + 1e-9 || ay > FERNANDO_HUANCA_MAXIMO + 1e-9) {
                ax = Math.min(ax, FERNANDO_HUANCA_MAXIMO);
                ay = Math.min(ay, FERNANDO_HUANCA_MAXIMO);
                suficiente = false;
                break;
            }
            Resultado prueba = FernandoHuancaAnalisisSismico.fernandoHuancaAnalizar(fernandoHuancaConAumento(entrada, ax, ay));
            dx = fernandoHuancaDerivaX(prueba);
            dy = fernandoHuancaDerivaY(prueba);
        }
        List<Cambio> cambios = new ArrayList<>();
        for (Elemento c : columnas) {
            cambios.add(new Cambio(c, fernandoHuancaRedondear(c.getLargo() + ax), fernandoHuancaRedondear(c.getAncho() + ay)));
        }
        return new Recomendacion(true, suficiente, ax, ay, List.copyOf(cambios), dx0, dy0, dx, dy,
                fernandoHuancaTexto(columnas, suficiente, ax, ay, dx0, dy0, dx, dy, lim));
    }

    /** Misma entrada con las columnas agrandadas (copias: no toca los elementos originales). */
    static Entrada fernandoHuancaConAumento(Entrada e, double ax, double ay) {
        List<Elemento> lista = new ArrayList<>();
        for (Elemento o : e.elementos()) {
            Elemento c = fernandoHuancaCopiar(o);
            if ("COLUMNA".equals(o.getTipo())) {
                c.setLargo(fernandoHuancaRedondear(o.getLargo() + ax));
                c.setAncho(fernandoHuancaRedondear(o.getAncho() + ay));
            }
            lista.add(c);
        }
        return new Entrada(lista, e.fcMpa(), e.poisson(), e.fraccionRigidez(), e.espesorLosa(), e.cargas(), e.norma());
    }

    private static Elemento fernandoHuancaCopiar(Elemento o) {
        Elemento c = new Elemento();
        c.setId(o.getId());
        c.setProyectoId(o.getProyectoId());
        c.setTipo(o.getTipo());
        c.setNombre(o.getNombre());
        c.setLargo(o.getLargo());
        c.setAncho(o.getAncho());
        c.setAltura(o.getAltura());
        c.setX(o.getX());
        c.setY(o.getY());
        c.setZ(o.getZ());
        return c;
    }

    /** Redondea a milímetros para no arrastrar errores de coma flotante (0,40 + 0,05 = 0,45 y no 0,45000000000000001). */
    private static double fernandoHuancaRedondear(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String fernandoHuancaTexto(List<Elemento> columnas, boolean suficiente, double ax, double ay,
                                              double dx0, double dy0, double dx, double dy, double lim) {
        var sb = new StringBuilder();
        String secciones = fernandoHuancaSecciones(columnas);
        if (suficiente) {
            sb.append("Aumente la sección de las ").append(columnas.size()).append(" columnas:\n");
        } else {
            sb.append("Aun con +").append(cm(FERNANDO_HUANCA_MAXIMO)).append(" cm en las columnas la deriva no llega al límite. ")
                    .append("Conviene rigidizar con placas (muros de concreto) o un sistema dual y validarlo en un programa de análisis.\n")
                    .append("Lo más que se probó en las ").append(columnas.size()).append(" columnas:\n");
        }
        if (ax > 0) sb.append("• Medida en X (Largo eje X): +").append(cm(ax)).append(" cm\n");
        if (ay > 0) sb.append("• Medida en Y (Ancho eje Y): +").append(cm(ay)).append(" cm\n");
        sb.append("Secciones actuales: ").append(secciones).append(" → nuevas: ").append(fernandoHuancaSeccionesNuevas(columnas, ax, ay)).append(".\n");
        sb.append(String.format(Locale.ROOT, "Deriva X: %.5f → %.5f   ·   Deriva Y: %.5f → %.5f   (límite %s)", dx0, dx, dy0, dy, lim));
        if (suficiente && Math.max(ax, ay) > FERNANDO_HUANCA_AUMENTO_GRANDE + 1e-9) {
            sb.append("\nEs un aumento grande: probablemente convenga más peraltar las vigas o agregar placas en esa dirección que agrandar tanto las columnas.");
        }
        sb.append("\nCalculado de nuevo con el mismo análisis y los mismos datos (incluye el peso de las columnas más grandes). ")
                .append("Es una recomendación de anteproyecto: también puede peraltar las vigas o agregar placas.");
        return sb.toString();
    }

    private static String fernandoHuancaSecciones(List<Elemento> columnas) {
        return columnas.stream().map(c -> cm(c.getLargo()) + "×" + cm(c.getAncho()) + " cm").distinct().limit(4)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String fernandoHuancaSeccionesNuevas(List<Elemento> columnas, double ax, double ay) {
        return columnas.stream().map(c -> cm(c.getLargo() + ax) + "×" + cm(c.getAncho() + ay) + " cm").distinct().limit(4)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String cm(double metros) {
        return String.format(Locale.ROOT, "%.0f", metros * 100);
    }
}
