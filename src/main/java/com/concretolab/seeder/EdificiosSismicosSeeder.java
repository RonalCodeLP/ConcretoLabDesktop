package com.concretolab.seeder;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edificios de pórticos de varios pisos para probar el análisis sísmico: uno regular que cumple, uno asimétrico que
 * muestra torsión y una torre flexible que no cumple la deriva. Las dimensiones y cargas son ficticias.
 * Cada proyecto es idempotente (se omite si ya existe uno con el mismo nombre).
 */
@Component
@RequiredArgsConstructor
public class EdificiosSismicosSeeder {

    public static final String REGULAR = "DEMO - Pórtico regular de 3 pisos";
    public static final String ASIMETRICO = "DEMO - Edificio asimétrico de 4 pisos";
    public static final String TORRE = "DEMO - Torre flexible de 6 pisos";

    private static final String SUGERENCIA = "Sismo sugerido: zona 4, categoría C, suelo S2, f'c 21 MPa, CM 1,0 y CV 2,0 kN/m² (datos ficticios)";

    private final YefriLermaProyectoService proyectos;

    /** Definición de un edificio de pórticos: malla de columnas y secciones. */
    record Edificio(String nombre, double[] xs, double[] ys, int pisos, double alturaPiso, double lado, double anchoViga, double alturaViga,
                    double ladoEspecial, int indiceColumnaEspecial) {}

    @Transactional
    public int sembrar() {
        int nuevos = 0;
        for (Edificio e : List.of(
                new Edificio(REGULAR, new double[]{0, 5, 10}, new double[]{0, 4, 8}, 3, 3.0, 0.50, 0.30, 0.55, 0, -1),
                // Una columna de esquina mucho más rígida desplaza el centro de rigidez y genera torsión.
                new Edificio(ASIMETRICO, new double[]{0, 5, 10}, new double[]{0, 4}, 4, 3.0, 0.35, 0.25, 0.45, 0.90, 0),
                new Edificio(TORRE, new double[]{0, 5}, new double[]{0, 4}, 6, 3.2, 0.30, 0.25, 0.40, 0, -1))) {
            if (proyectos.proyectos().stream().noneMatch(p -> e.nombre().equals(p.getNombre()))) {
                crear(e);
                nuevos++;
            }
        }
        return nuevos;
    }

    private void crear(Edificio e) {
        Proyecto p = proyectos.crearProyecto(e.nombre(), SUGERENCIA);
        double hz = 0.5, alturaTotal = e.pisos() * e.alturaPiso();
        int n = 1;
        for (double y : e.ys()) {
            for (double x : e.xs()) {
                double lado = n - 1 == e.indiceColumnaEspecial() ? e.ladoEspecial() : e.lado();
                agregar(p, "ZAPATA", "Z" + n, Math.max(1.4, lado * 2.6), Math.max(1.4, lado * 2.6), hz, x, y, hz / 2);
                agregar(p, "COLUMNA", "C" + n, lado, lado, alturaTotal, x, y, hz + alturaTotal / 2);
                n++;
            }
        }
        for (int piso = 1; piso <= e.pisos(); piso++) {
            double z = hz + piso * e.alturaPiso() - e.alturaViga() / 2;
            for (int j = 0; j < e.ys().length; j++) {
                for (int i = 0; i + 1 < e.xs().length; i++) {
                    double largo = e.xs()[i + 1] - e.xs()[i] - e.lado();
                    agregar(p, "VIGA", "VX" + piso + "-" + (j + 1) + (i + 1), largo, e.anchoViga(), e.alturaViga(), (e.xs()[i] + e.xs()[i + 1]) / 2, e.ys()[j], z);
                }
            }
            for (int i = 0; i < e.xs().length; i++) {
                for (int j = 0; j + 1 < e.ys().length; j++) {
                    double largo = e.ys()[j + 1] - e.ys()[j] - e.lado();
                    agregar(p, "VIGA", "VY" + piso + "-" + (i + 1) + (j + 1), e.anchoViga(), largo, e.alturaViga(), e.xs()[i], (e.ys()[j] + e.ys()[j + 1]) / 2, z);
                }
            }
        }
    }

    private void agregar(Proyecto p, String tipo, String nombre, double largo, double ancho, double altura, double x, double y, double z) {
        Elemento e = new Elemento();
        e.setProyectoId(p.getId());
        e.setTipo(tipo);
        e.setNombre(nombre);
        e.setLargo(largo);
        e.setAncho(ancho);
        e.setAltura(altura);
        e.setX(x);
        e.setY(y);
        e.setZ(z);
        proyectos.guardar(e);
    }
}
