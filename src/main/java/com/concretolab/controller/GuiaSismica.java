package com.concretolab.controller;

import com.concretolab.service.FernandoHuancaAnalisisSismico.Direccion;
import com.concretolab.service.FernandoHuancaAnalisisSismico.Resultado;
import com.concretolab.service.NormaE030;
import com.concretolab.service.NormaE030.Categoria;
import com.concretolab.service.NormaE030.Parametros;
import com.concretolab.service.NormaE030.Version;
import com.concretolab.service.NormaE030.Zona;
import com.concretolab.service.SistemaEstructural;
import com.concretolab.service.TipoSuelo;
import com.concretolab.service.UsoEdificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Logica no grafica de la pantalla sismica guiada: traduce las cuatro elecciones del asistente a parametros de la E.030,
 * aplica la regla de sistemas permitidos y arma la lista de verificaciones. No depende de JavaFX.
 */
public final class GuiaSismica {

    private GuiaSismica() {
    }

    /** Nivel de una verificacion mostrada en el semaforo. */
    public enum Nivel { OK, ALERTA, ERROR, INFO }

    /** Una linea de la lista de verificaciones. */
    public record Verificacion(Nivel nivel, String texto) {
    }

    /** Zona de la E.030 a partir del numero 1 a 4 del Anexo II. */
    public static Zona zonaDe(int numero) {
        return switch (numero) {
            case 1 -> Zona.Z1;
            case 2 -> Zona.Z2;
            case 3 -> Zona.Z3;
            case 4 -> Zona.Z4;
            default -> throw new IllegalArgumentException("Zona sísmica fuera de rango: " + numero);
        };
    }

    /** Numero de zona (1 a 4). */
    public static int numeroZona(Zona z) {
        return switch (z) {
            case Z1 -> 1;
            case Z2 -> 2;
            case Z3 -> 3;
            case Z4 -> 4;
        };
    }

    /**
     * Tabla 9 (E.030 2026, art. 21): en categoria A (A1/A2) y zonas 2 a 4 no se permiten porticos como sistema
     * (se exigen dual, muros o albanileria). Para B y C si se permiten.
     */
    public static boolean porticosPermitidos(Categoria categoria, Zona zona) {
        return porticosPermitidos(categoria, zona, false);
    }

    /** Con A1 (salud de 2.º y 3.er nivel) los pórticos no se permiten en ninguna zona: aislamiento en 3 y 4; dual, muros o albañilería en 1 y 2. */
    public static boolean porticosPermitidos(Categoria categoria, Zona zona, boolean esA1) {
        if (esA1) return false;
        return !(categoria == Categoria.A && zona != Zona.Z1);
    }

    /** Texto del aviso si los porticos no estan permitidos; {@code null} si lo estan. */
    public static String avisoTabla9(Categoria categoria, Zona zona, Version version) {
        return avisoTabla9(categoria, zona, version, false);
    }

    public static String avisoTabla9(Categoria categoria, Zona zona, Version version, boolean esA1) {
        if (porticosPermitidos(categoria, zona, esA1)) {
            return null;
        }
        String tabla = version == Version.V2026 ? "Tabla 9 (art. 21)" : "Tabla 6";
        if (esA1) return tabla + " de la E.030: en categoría A1 (salud de 2.º y 3.er nivel) los pórticos NO están permitidos: en zonas 3 y 4 se exige aislamiento sísmico y en zonas 1 y 2, sistema dual, muros estructurales o albañilería. Este cálculo es solo referencial.";
        return tabla + " de la E.030: en categoría A y " + zona.etiqueta.toLowerCase(Locale.ROOT)
                + " los pórticos NO están permitidos como sistema estructural; se exigen sistema dual, muros estructurales o albañilería. "
                + "Este cálculo es solo referencial.";
    }

    /** Cargas {cm, cv} en kN/m2 para cada piso segun el uso: todos con la carga viva de pisos salvo el ultimo, que usa la de techo. */
    public static double[][] cargasPorUso(UsoEdificacion uso, int pisos) {
        double[][] r = new double[Math.max(pisos, 0)][];
        for (int i = 0; i < r.length; i++) {
            boolean techo = i == r.length - 1;
            r[i] = new double[]{uso.cargaMuertaAdicionalSugerida, techo ? uso.cvTecho : uso.cvPisos};
        }
        return r;
    }

    /**
     * Arma los parametros de la norma desde las elecciones del asistente. {@code ct} nulo usa el del sistema; Vs30 solo se
     * aplica con la version 2026 y un suelo que interpola (S2/S3), en otro caso se ignora.
     */
    public static Parametros parametros(Version version, Zona zona, UsoEdificacion uso, TipoSuelo tipoSuelo,
                                        SistemaEstructural sistema, double ia, double ip, Double ct, double vs30) {
        NormaE030.Suelo suelo = tipoSuelo.suelo(version);
        double vs = version == Version.V2026 && suelo.interpola() ? vs30 : Double.NaN;
        return new Parametros(version, zona, uso.categoria(), suelo, sistema.r0(version), ia, ip,
                ct == null ? sistema.ct() : ct, vs);
    }

    /** Lo que falta elegir para poder calcular (lista vacia si todo esta listo). */
    public static List<String> faltantes(Zona zona, UsoEdificacion uso, TipoSuelo suelo, SistemaEstructural sistema, boolean hayPisos) {
        List<String> f = new ArrayList<>();
        if (zona == null) f.add("la ubicación o la zona sísmica (paso 1)");
        if (uso == null) f.add("el uso de la edificación (paso 2)");
        if (suelo == null) f.add("el tipo de suelo (paso 3)");
        if (sistema == null) f.add("el sistema estructural (paso 4)");
        else if (!sistema.modelable()) f.add("un sistema que la aplicación pueda modelar (solo pórticos)");
        if (!hayPisos) f.add("pisos en el proyecto");
        return f;
    }

    /** Verifica el resultado y devuelve las lineas del semaforo. */
    public static List<Verificacion> verificaciones(Parametros norma, Resultado r, UsoEdificacion uso) {
        List<Verificacion> v = new ArrayList<>();
        double lim = NormaE030.DERIVA_LIMITE_CONCRETO;
        for (var par : List.of(new Object[]{"Estático", r.estaticoX()}, new Object[]{"Estático", r.estaticoY()},
                new Object[]{"Espectral", r.espectralX()}, new Object[]{"Espectral", r.espectralY()})) {
            Direccion d = (Direccion) par[1];
            boolean ok = d.cumple();
            v.add(new Verificacion(ok ? Nivel.OK : Nivel.ERROR, "Deriva " + par[0].toString().toLowerCase(Locale.ROOT) + " " + d.nombre()
                    + ": " + String.format(Locale.ROOT, "%.5f", d.derivaMaxima()) + (ok ? " ≤ " : " > ") + lim));
        }
        double escala = Math.max(r.espectralX().factorEscala(), r.espectralY().factorEscala());
        v.add(escala > 1.0001
                ? new Verificacion(Nivel.ALERTA, "Cortante mínimo (" + norma.art("minimo") + "): el cortante dinámico era menor y se escalaron las fuerzas por "
                        + String.format(Locale.ROOT, "%.2f", escala) + ".")
                : new Verificacion(Nivel.OK, "Cortante mínimo (" + norma.art("minimo") + "): el cortante dinámico alcanza el mínimo, sin escalar."));
        double razon = razonTorsionMaxima(r);
        if (razon > 1.5) v.add(new Verificacion(Nivel.ERROR, "Torsión: Δmáx/Δprom = " + String.format(Locale.ROOT, "%.2f", razon) + " (irregularidad torsional extrema; revise Ip)."));
        else if (razon > 1.3) v.add(new Verificacion(Nivel.ALERTA, "Torsión: Δmáx/Δprom = " + String.format(Locale.ROOT, "%.2f", razon) + " (posible irregularidad torsional; revise Ip)."));
        else v.add(new Verificacion(Nivel.OK, "Torsión: Δmáx/Δprom = " + String.format(Locale.ROOT, "%.2f", razon) + " (sin irregularidad torsional)."));
        double hn = r.junta().alturaM();
        boolean estaticoAplica = norma.regular() ? !(hn > 30 && norma.zona() != Zona.Z1) : norma.zona() == Zona.Z1;
        v.add(estaticoAplica
                ? new Verificacion(Nivel.OK, "Método estático aplicable (" + norma.art("aplicabilidad") + ").")
                : new Verificacion(Nivel.ALERTA, "El método estático NO es aplicable (" + norma.art("aplicabilidad") + "): use el resultado espectral."));
        String t9 = avisoTabla9(norma.categoria(), norma.zona(), norma.version());
        v.add(t9 == null
                ? new Verificacion(Nivel.OK, "Sistema estructural: los pórticos están permitidos para la categoría " + norma.categoria().name() + " en esta zona.")
                : new Verificacion(Nivel.ERROR, t9));
        if (uso != null && uso.requiereAislamientoZ3Z4() && (norma.zona() == Zona.Z3 || norma.zona() == Zona.Z4))
            v.add(new Verificacion(Nivel.ERROR, "Tabla 7: esta edificación (A1) pide aislamiento sísmico en zonas 3 y 4; la aplicación no lo modela."));
        var j = r.junta();
        v.add(new Verificacion(Nivel.INFO, "Junta sísmica (" + norma.art("junta") + "): mínimo por norma " + String.format(Locale.ROOT, "%.1f cm", j.minimoNormaM() * 100)
                + "; este edificio aporta 2/3 de su desplazamiento = " + String.format(Locale.ROOT, "%.1f cm", j.dosTerciosPropioM() * 100)
                + ". Debe sumarse con el aporte del edificio vecino (2/3 de la suma de ambos desplazamientos) y no ser menor que el mínimo."));
        return v;
    }

    /** Mayor razon Δmax/Δprom entre las direcciones, en los pisos con deriva mayor que la mitad del limite. */
    static double razonTorsionMaxima(Resultado r) {
        double lim = NormaE030.DERIVA_LIMITE_CONCRETO;
        double max = 1;
        for (Direccion d : List.of(r.estaticoX(), r.estaticoY(), r.espectralX(), r.espectralY())) {
            for (int s = 0; s < d.derivasMaximas().length; s++) {
                if (d.derivasMaximas()[s] > 0.5 * lim) max = Math.max(max, d.razonTorsion()[s]);
            }
        }
        return max;
    }
}
