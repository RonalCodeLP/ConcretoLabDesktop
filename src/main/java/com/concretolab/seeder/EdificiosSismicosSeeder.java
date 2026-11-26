package com.concretolab.seeder;

import com.concretolab.model.Elemento;
import com.concretolab.model.Proyecto;
import com.concretolab.service.YefriLermaProyectoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EdificiosSismicosSeeder {
    public static final String YEFRI_LERMA_REGULAR = "DEMO - Pórtico regular de 3 pisos";
    public static final String YEFRI_LERMA_ASIMETRICO = "DEMO - Edificio asimétrico de 4 pisos";
    public static final String YEFRI_LERMA_TORRE = "DEMO - Torre flexible de 6 pisos";

    private static final String YEFRI_LERMA_SUGERENCIA = "Sismo sugerido: zona 4, categoría C, suelo S2, f'c 21 MPa, CM 1,0 y CV 2,0 kN/m² (datos ficticios)";

    private final YefriLermaProyectoService proyectos;

    record Edificio(String nombre, double[] xs, double[] ys, int pisos, double alturaPiso, double lado, double anchoViga, double alturaViga,
                    double ladoEspecial, int indiceColumnaEspecial) {}

    @Transactional
    public int yefriLermaSembrar() {
        int nuevos = 0;
        for (Edificio e : List.of(
                new Edificio(YEFRI_LERMA_REGULAR, new double[]{0, 5, 10}, new double[]{0, 4, 8}, 3, 3.0, 0.50, 0.30, 0.55, 0, -1),
                new Edificio(YEFRI_LERMA_ASIMETRICO, new double[]{0, 5, 10}, new double[]{0, 4}, 4, 3.0, 0.35, 0.25, 0.45, 0.90, 0),
                new Edificio(YEFRI_LERMA_TORRE, new double[]{0, 5}, new double[]{0, 4}, 6, 3.2, 0.30, 0.25, 0.40, 0, -1))) {
            if (proyectos.proyectos().stream().noneMatch(p -> e.nombre().equals(p.getNombre()))) {
                yefriLermaCrear(e);
                nuevos++;
            }
        }
        return nuevos;
    }

    private void yefriLermaCrear(Edificio e) {
        Proyecto p = proyectos.yefriLermaCrearProyecto(e.nombre(), YEFRI_LERMA_SUGERENCIA);
        double hz = 0.5, alturaTotal = e.pisos() * e.alturaPiso();
        int n = 1;
        for (double y : e.ys()) {
            for (double x : e.xs()) {
                double lado = n - 1 == e.indiceColumnaEspecial() ? e.ladoEspecial() : e.lado();
                yefriLermaAgregar(p, "ZAPATA", "Z" + n, Math.max(1.4, lado * 2.6), Math.max(1.4, lado * 2.6), hz, x, y, hz / 2);
                yefriLermaAgregar(p, "COLUMNA", "C" + n, lado, lado, alturaTotal, x, y, hz + alturaTotal / 2);
                n++;
            }
        }
        for (int piso = 1; piso <= e.pisos(); piso++) {
            double z = hz + piso * e.alturaPiso() - e.alturaViga() / 2;
            for (int j = 0; j < e.ys().length; j++) {
                for (int i = 0; i + 1 < e.xs().length; i++) {
                    double largo = e.xs()[i + 1] - e.xs()[i] - e.lado();
                    yefriLermaAgregar(p, "VIGA", "VX" + piso + "-" + (j + 1) + (i + 1), largo, e.anchoViga(), e.alturaViga(), (e.xs()[i] + e.xs()[i + 1]) / 2, e.ys()[j], z);
                }
            }
            for (int i = 0; i < e.xs().length; i++) {
                for (int j = 0; j + 1 < e.ys().length; j++) {
                    double largo = e.ys()[j + 1] - e.ys()[j] - e.lado();
                    yefriLermaAgregar(p, "VIGA", "VY" + piso + "-" + (i + 1) + (j + 1), e.anchoViga(), largo, e.alturaViga(), e.xs()[i], (e.ys()[j] + e.ys()[j + 1]) / 2, z);
                }
            }
        }
    }

    private void yefriLermaAgregar(Proyecto p, String tipo, String nombre, double largo, double ancho, double altura, double x, double y, double z) {
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
        proyectos.yefriLermaGuardar(e);
    }
}
