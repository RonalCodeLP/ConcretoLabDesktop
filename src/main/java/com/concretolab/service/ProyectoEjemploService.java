package com.concretolab.service;

import com.concretolab.model.Elemento;
import com.concretolab.model.Ensayo;
import com.concretolab.model.Material;
import com.concretolab.model.Mezcla;
import com.concretolab.model.Proyecto;
import com.concretolab.model.RegistroGranulometria;
import com.concretolab.service.CuadriculaService.Definicion;
import com.concretolab.service.CuadriculaService.Dimensiones;
import com.concretolab.service.CuadriculaService.Punto;
import com.concretolab.service.CuadriculaService.Solicitud;
import com.concretolab.service.CuadriculaService.Tramo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProyectoEjemploService {
    public static final String FERNANDO_HUANCA_NOMBRE = "EJEMPLO · Edificio de práctica";
    public static final String FERNANDO_HUANCA_DESCRIPCION =
            "Datos ficticios para practicar · Sugerido: Lima (zona 4), vivienda, suelo S2, f'c 21 MPa";
    public static final String FERNANDO_HUANCA_ARENA = "EJEMPLO · Arena gruesa";
    public static final String FERNANDO_HUANCA_PIEDRA = "EJEMPLO · Piedra chancada 3/4\"";
    public static final String FERNANDO_HUANCA_CEMENTO = "EJEMPLO · Cemento tipo I";
    public static final String FERNANDO_HUANCA_TAMIZADO = "EJEMPLO · Tamizado de arena gruesa";
    public static final String FERNANDO_HUANCA_MEZCLA = "EJEMPLO · Concreto f'c 21 MPa";

    static final List<Double> FERNANDO_HUANCA_SEPARACION_X = List.of(5.0, 5.0);
    static final List<Double> FERNANDO_HUANCA_SEPARACION_Y = List.of(4.0, 4.0);
    static final List<Double> FERNANDO_HUANCA_NIVELES = List.of(0.0, 3.0, 6.0, 9.0);
    static final Dimensiones FERNANDO_HUANCA_DIMENSIONES = new Dimensiones(1.6, 1.6, 0.6, 0.50, 0.30, 0.55);
    static final double FERNANDO_HUANCA_ESPESOR_LOSA = 0.20;

    private final YefriLermaProyectoService fernandoHuancaProyectos;
    private final CuadriculaService fernandoHuancaCuadriculas;
    private final MaterialService fernandoHuancaMateriales;
    private final RegistroGranulometriaService fernandoHuancaTamizados;
    private final MezclaService fernandoHuancaMezclas;

    @Transactional
    public Proyecto fernandoHuancaAgregar() {
        Proyecto proyecto = fernandoHuancaProyectos.yefriLermaCrearProyecto(fernandoHuancaNombreLibre(), FERNANDO_HUANCA_DESCRIPCION);
        fernandoHuancaEdificio(proyecto.getId());
        Material arena = fernandoHuancaMaterial(fernandoHuancaArena());
        fernandoHuancaMaterial(fernandoHuancaPiedra());
        fernandoHuancaMaterial(fernandoHuancaCemento());
        fernandoHuancaTamizado(arena);
        fernandoHuancaMezclaYProbetas(proyecto.getId());
        return proyecto;
    }

    private String fernandoHuancaNombreLibre() {
        Set<String> usados = new HashSet<>();
        fernandoHuancaProyectos.proyectos().forEach(p -> usados.add(p.getNombre()));
        String nombre = FERNANDO_HUANCA_NOMBRE;
        for (int n = 2; usados.contains(nombre); n++) nombre = FERNANDO_HUANCA_NOMBRE + " (" + n + ")";
        return nombre;
    }

    private void fernandoHuancaEdificio(Long proyectoId) {
        Definicion definicion = new Definicion(0, 0, FERNANDO_HUANCA_SEPARACION_X, FERNANDO_HUANCA_SEPARACION_Y, FERNANDO_HUANCA_NIVELES);
        int nx = FERNANDO_HUANCA_SEPARACION_X.size() + 1, ny = FERNANDO_HUANCA_SEPARACION_Y.size() + 1;
        Set<Punto> puntos = new HashSet<>();
        Set<Tramo> tramos = new HashSet<>();
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                puntos.add(new Punto(i, j));
                if (i + 1 < nx) tramos.add(new Tramo(new Punto(i, j), new Punto(i + 1, j)));
                if (j + 1 < ny) tramos.add(new Tramo(new Punto(i, j), new Punto(i, j + 1)));
            }
        }
        fernandoHuancaCuadriculas.walterQuispesayhuaCrear(proyectoId,
                new Solicitud(definicion, puntos, tramos, true, true, true, true, FERNANDO_HUANCA_DIMENSIONES));
        double anchoViga = FERNANDO_HUANCA_DIMENSIONES.anchoViga();
        for (int piso = 1; piso < FERNANDO_HUANCA_NIVELES.size(); piso++) {
            double nivel = FERNANDO_HUANCA_NIVELES.get(piso);
            double x0 = 0;
            for (int i = 0; i < FERNANDO_HUANCA_SEPARACION_X.size(); i++) {
                double sx = FERNANDO_HUANCA_SEPARACION_X.get(i), y0 = 0;
                for (int j = 0; j < FERNANDO_HUANCA_SEPARACION_Y.size(); j++) {
                    double sy = FERNANDO_HUANCA_SEPARACION_Y.get(j);
                    Elemento losa = new Elemento();
                    losa.setProyectoId(proyectoId);
                    losa.setTipo("LOSA");
                    losa.setNombre("L-" + piso + "-" + (i + 1) + "-" + (j + 1));
                    losa.setLargo(sx - anchoViga);
                    losa.setAncho(sy - anchoViga);
                    losa.setAltura(FERNANDO_HUANCA_ESPESOR_LOSA);
                    losa.setX(x0 + sx / 2);
                    losa.setY(y0 + sy / 2);
                    losa.setZ(nivel - FERNANDO_HUANCA_ESPESOR_LOSA / 2);
                    fernandoHuancaProyectos.yefriLermaGuardar(losa);
                    y0 += sy;
                }
                x0 += sx;
            }
        }
    }

    private Material fernandoHuancaMaterial(Material plantilla) {
        return fernandoHuancaMateriales.yefriLermaListar().stream()
                .filter(m -> plantilla.getNombre().equals(m.getNombre()))
                .findFirst()
                .orElseGet(() -> fernandoHuancaMateriales.yefriLermaGuardar(plantilla));
    }

    private static Material fernandoHuancaArena() {
        Material m = new Material();
        m.setTipo("AGREGADO FINO");
        m.setNombre(FERNANDO_HUANCA_ARENA);
        m.setProcedencia("Cantera ficticia (ejemplo)");
        m.setReferenciaEnsayo("EJ-LAB-01");
        m.setFechaEnsayo("2026-09-20");
        m.setDensidadRelativa(2.65);
        m.setAbsorcion(1.2);
        m.setHumedad(3.5);
        m.setModuloFinura(2.67);
        m.setObservaciones("Datos ficticios para practicar; no provienen de un ensayo real.");
        return m;
    }

    private static Material fernandoHuancaPiedra() {
        Material m = new Material();
        m.setTipo("AGREGADO GRUESO");
        m.setNombre(FERNANDO_HUANCA_PIEDRA);
        m.setProcedencia("Cantera ficticia (ejemplo)");
        m.setReferenciaEnsayo("EJ-LAB-02");
        m.setFechaEnsayo("2026-09-20");
        m.setDensidadRelativa(2.65);
        m.setAbsorcion(0.8);
        m.setHumedad(0.4);
        m.setTamanoMaximoMm(19.0);
        m.setPesoUnitarioCompactado(1600.0);
        m.setObservaciones("Datos ficticios para practicar; no provienen de un ensayo real.");
        return m;
    }

    private static Material fernandoHuancaCemento() {
        Material m = new Material();
        m.setTipo("CEMENTO TIPO I");
        m.setNombre(FERNANDO_HUANCA_CEMENTO);
        m.setDensidadRelativa(3.15);
        m.setReferenciaEnsayo("Datos ficticios");
        m.setObservaciones("Datos ficticios para practicar.");
        return m;
    }

    private void fernandoHuancaTamizado(Material arena) {
        boolean existe = fernandoHuancaTamizados.yefriLermaListar(arena.getId()).stream()
                .anyMatch(r -> FERNANDO_HUANCA_TAMIZADO.equals(r.getIdentificacion()));
        if (existe) return;
        RegistroGranulometria r = new RegistroGranulometria();
        r.setMaterialId(arena.getId());
        r.setIdentificacion(FERNANDO_HUANCA_TAMIZADO);
        r.setFecha("2026-09-20");
        r.setMasaInicial(500);
        r.setFondo(35);
        r.setMediciones("9.5; 0\n4.75; 15\n2.36; 50\n1.18; 85\n0.60; 110\n0.30; 120\n0.15; 85");
        fernandoHuancaTamizados.yefriLermaGuardar(r);
    }

    private void fernandoHuancaMezclaYProbetas(Long proyectoId) {
        Mezcla m = new Mezcla();
        m.setProyectoId(proyectoId);
        m.setNombre(FERNANDO_HUANCA_MEZCLA);
        m.setReferencia("Datos ficticios para practicar; no utilizar en obra");
        m.setResistencia(21);
        m.setCemento(360);
        m.setAgua(200);
        m.setFinoSss(746);
        m.setGruesoSss(1018);
        m.setAbsorcionFino(1.2);
        m.setHumedadFino(3.5);
        m.setAbsorcionGrueso(0.8);
        m.setHumedadGrueso(0.4);
        m.setMaterialFino(FERNANDO_HUANCA_ARENA + " | informe EJ-LAB-01");
        m.setMaterialGrueso(FERNANDO_HUANCA_PIEDRA + " | informe EJ-LAB-02");
        Mezcla guardada = fernandoHuancaMezclas.yefriLermaGuardar(m);
        fernandoHuancaProbeta(guardada.getId(), "EJEMPLO · P-01 (7 días)", "2026-10-05", 7, 16.0, 90);
        fernandoHuancaProbeta(guardada.getId(), "EJEMPLO · P-02 (7 días)", "2026-10-05", 7, 16.6, 90);
        fernandoHuancaProbeta(guardada.getId(), "EJEMPLO · P-03 (28 días)", "2026-10-26", 28, 24.6, 90);
        fernandoHuancaProbeta(guardada.getId(), "EJEMPLO · P-04 (28 días)", "2026-10-26", 28, 25.3, 90);
        fernandoHuancaProbeta(guardada.getId(), "EJEMPLO · P-05 (28 días)", "2026-10-26", 28, 23.9, 90);
    }

    private void fernandoHuancaProbeta(Long mezclaId, String identificacion, String fecha, double edad, double resistencia, double asentamiento) {
        Ensayo e = new Ensayo();
        e.setMezclaId(mezclaId);
        e.setIdentificacion(identificacion);
        e.setFecha(fecha);
        e.setEdadDias(edad);
        e.setResistenciaMpa(resistencia);
        e.setAsentamientoMm(asentamiento);
        e.setObservaciones("Dato ficticio para practicar.");
        fernandoHuancaMezclas.yefriLermaGuardarEnsayo(e);
    }
}
