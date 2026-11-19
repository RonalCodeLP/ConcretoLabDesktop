package com.concretolab.service;

import com.concretolab.model.CuadriculaProyecto;
import com.concretolab.model.Elemento;
import com.concretolab.repository.CuadriculaProyectoRepository;
import com.concretolab.repository.ProyectoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CuadriculaService {
    private final CuadriculaProyectoRepository cuadriculas;
    private final ProyectoRepository proyectos;
    private final YefriLermaProyectoService modelos;

    public CuadriculaService(CuadriculaProyectoRepository cuadriculas, ProyectoRepository proyectos,
                             YefriLermaProyectoService modelos) {
        this.cuadriculas = cuadriculas;
        this.proyectos = proyectos;
        this.modelos = modelos;
    }

    public record Punto(int x, int y) { }
    public record Tramo(Punto inicio, Punto fin) { }
    public record Dimensiones(double largoZapata, double anchoZapata, double alturaZapata,
                              double ladoColumna, double anchoViga, double alturaViga) { }
    public record Definicion(double origenX, double origenY, List<Double> separacionesX,
                             List<Double> separacionesY, List<Double> niveles) { }
    public record Solicitud(Definicion definicion, Set<Punto> puntos, Set<Tramo> tramos, boolean zapatas,
                            boolean columnas, boolean vigasX, boolean vigasY, Dimensiones dimensiones) { }
    public record VistaPrevia(List<Elemento> elementos, List<String> superposiciones) { }

    @Transactional(readOnly = true)
    public Optional<CuadriculaProyecto> obtener(Long proyectoId) {
        return cuadriculas.findById(proyectoId);
    }

    @Transactional
    public void guardarCuadricula(Long proyectoId, Definicion definicion) {
        validarProyecto(proyectoId);
        validarDefinicion(definicion);
        CuadriculaProyecto cuadricula = new CuadriculaProyecto();
        cuadricula.setProyectoId(proyectoId);
        cuadricula.setOrigenX(definicion.origenX());
        cuadricula.setOrigenY(definicion.origenY());
        cuadricula.setSeparacionesX(unir(definicion.separacionesX()));
        cuadricula.setSeparacionesY(unir(definicion.separacionesY()));
        cuadricula.setNiveles(unir(definicion.niveles()));
        cuadriculas.save(cuadricula);
    }

    @Transactional(readOnly = true)
    public VistaPrevia previsualizar(Long proyectoId, Solicitud solicitud) {
        validarProyecto(proyectoId);
        List<Elemento> nuevos = generar(proyectoId, solicitud);
        List<String> conflictos = new ArrayList<>();
        List<Elemento> existentes = modelos.elementos(proyectoId);
        for (Elemento nuevo : nuevos) {
            for (Elemento existente : existentes) {
                if (superpuestos(nuevo, existente)) {
                    conflictos.add(nuevo.getNombre() + " con " + existente.getNombre());
                }
            }
        }
        for (int i = 0; i < nuevos.size(); i++) {
            for (int j = i + 1; j < nuevos.size(); j++) {
                if (superpuestos(nuevos.get(i), nuevos.get(j))) {
                    conflictos.add(nuevos.get(i).getNombre() + " con " + nuevos.get(j).getNombre());
                }
            }
        }
        return new VistaPrevia(List.copyOf(nuevos), List.copyOf(conflictos));
    }

    @Transactional
    public void crear(Long proyectoId, Solicitud solicitud) {
        VistaPrevia vista = previsualizar(proyectoId, solicitud);
        if (vista.elementos().isEmpty()) throw new IllegalArgumentException("Seleccione elementos para generar.");
        guardarCuadricula(proyectoId, solicitud.definicion());
        vista.elementos().forEach(modelos::guardar);
    }

    public List<Double> coordenadas(double origen, List<Double> separaciones) {
        List<Double> resultado = new ArrayList<>();
        resultado.add(origen);
        for (double separacion : separaciones) {
            double siguiente = resultado.get(resultado.size() - 1) + separacion;
            if (!Double.isFinite(siguiente)) throw new IllegalArgumentException("Las coordenadas deben ser finitas.");
            resultado.add(siguiente);
        }
        return List.copyOf(resultado);
    }

    public void validarDefinicion(Definicion d) {
        if (d == null || !Double.isFinite(d.origenX()) || !Double.isFinite(d.origenY()))
            throw new IllegalArgumentException("Los orígenes deben ser números finitos.");
        if (d.separacionesX() == null || d.separacionesY() == null || d.niveles() == null
                || d.separacionesX().isEmpty() || d.separacionesY().isEmpty()
                || d.separacionesX().size() > 19 || d.separacionesY().size() > 19
                || d.niveles().size() < 2 || d.niveles().size() > 20)
            throw new IllegalArgumentException("Ingrese entre 2 y 20 ejes por dirección y entre 2 y 20 niveles.");
        if (d.separacionesX().stream().anyMatch(n -> n == null || !positivo(n))
                || d.separacionesY().stream().anyMatch(n -> n == null || !positivo(n)))
            throw new IllegalArgumentException("Las separaciones deben ser positivas y finitas.");
        for (int i = 0; i < d.niveles().size(); i++) {
            Double nivel = d.niveles().get(i);
            if (nivel == null || !Double.isFinite(nivel) || (i > 0 && nivel <= d.niveles().get(i - 1)))
                throw new IllegalArgumentException("Los niveles deben ser finitos y estar en orden ascendente.");
        }
        coordenadas(d.origenX(), d.separacionesX());
        coordenadas(d.origenY(), d.separacionesY());
    }

    private List<Elemento> generar(Long proyectoId, Solicitud s) {
        if (s == null) throw new IllegalArgumentException("Complete la solicitud.");
        validarDefinicion(s.definicion());
        Dimensiones d = s.dimensiones();
        if (d == null || !positivo(d.largoZapata()) || !positivo(d.anchoZapata()) || !positivo(d.alturaZapata())
                || !positivo(d.ladoColumna()) || !positivo(d.anchoViga()) || !positivo(d.alturaViga()))
            throw new IllegalArgumentException("Todas las dimensiones deben ser positivas y finitas.");
        if (s.puntos() == null || s.puntos().isEmpty() || s.tramos() == null) throw new IllegalArgumentException("Seleccione al menos una intersección.");
        List<Double> xs = coordenadas(s.definicion().origenX(), s.definicion().separacionesX());
        List<Double> ys = coordenadas(s.definicion().origenY(), s.definicion().separacionesY());
        List<Double> niveles = s.definicion().niveles();
        for (Punto punto : s.puntos()) {
            if (punto == null || punto.x() < 0 || punto.x() >= xs.size() || punto.y() < 0 || punto.y() >= ys.size())
                throw new IllegalArgumentException("Hay una intersección fuera de la cuadrícula.");
        }
        long maximo = (long) s.puntos().size() * ((s.zapatas() ? 1 : 0)
                + (long) (niveles.size() - 1) * ((s.columnas() ? 1 : 0) + (s.vigasX() ? 1 : 0) + (s.vigasY() ? 1 : 0)));
        if (maximo > 2000) throw new IllegalArgumentException("Genere como máximo 2000 elementos por operación; reduzca la selección.");
        if ((s.vigasX() || s.vigasY()) && d.anchoViga() > d.ladoColumna())
            throw new IllegalArgumentException("El ancho de viga no puede superar el lado de columna.");
        List<Elemento> resultado = new ArrayList<>();
        for (int x = 0; x < xs.size(); x++) for (int y = 0; y < ys.size(); y++) {
            if (!s.puntos().contains(new Punto(x, y))) continue;
            String marca = (x + 1) + "-" + (y + 1);
            double cx = xs.get(x), cy = ys.get(y);
            if (s.zapatas()) agregar(resultado, proyectoId, "ZAPATA", "Z-" + marca,
                    d.largoZapata(), d.anchoZapata(), d.alturaZapata(), cx, cy, niveles.get(0) - d.alturaZapata() / 2);
            for (int piso = 1; piso < niveles.size(); piso++) {
                double inferior = niveles.get(piso - 1), superior = niveles.get(piso), altura = superior - inferior;
                if (!Double.isFinite(altura)) throw new IllegalArgumentException("La altura entre niveles debe ser finita.");
                if ((s.vigasX() || s.vigasY()) && d.alturaViga() > altura)
                    throw new IllegalArgumentException("La altura de viga no puede superar la altura entre niveles.");
                if (s.columnas()) agregar(resultado, proyectoId, "COLUMNA", "C-" + piso + "-" + marca,
                        d.ladoColumna(), d.ladoColumna(), altura, cx, cy, inferior + altura / 2);
                if (s.vigasX() && x + 1 < xs.size() && s.puntos().contains(new Punto(x + 1, y))
                        && s.tramos().contains(new Tramo(new Punto(x, y), new Punto(x + 1, y)))) {
                    double longitud = xs.get(x + 1) - cx - d.ladoColumna();
                    if (!positivo(longitud)) throw new IllegalArgumentException("La separación X debe superar el lado de columna.");
                    agregar(resultado, proyectoId, "VIGA", "VX-" + piso + "-" + marca, longitud, d.anchoViga(), d.alturaViga(),
                            cx + (xs.get(x + 1) - cx) / 2, cy, superior - d.alturaViga() / 2);
                }
                if (s.vigasY() && y + 1 < ys.size() && s.puntos().contains(new Punto(x, y + 1))
                        && s.tramos().contains(new Tramo(new Punto(x, y), new Punto(x, y + 1)))) {
                    double longitud = ys.get(y + 1) - cy - d.ladoColumna();
                    if (!positivo(longitud)) throw new IllegalArgumentException("La separación Y debe superar el lado de columna.");
                    agregar(resultado, proyectoId, "VIGA", "VY-" + piso + "-" + marca, d.anchoViga(), longitud, d.alturaViga(),
                            cx, cy + (ys.get(y + 1) - cy) / 2, superior - d.alturaViga() / 2);
                }
            }
        }
        return resultado;
    }

    private void agregar(List<Elemento> lista, Long id, String tipo, String nombre, double largo, double ancho,
                         double altura, double x, double y, double z) {
        if (!positivo(largo) || !positivo(ancho) || !positivo(altura) || !Double.isFinite(largo * ancho * altura)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("La geometría calculada debe ser finita y positiva.");
        Elemento e = new Elemento();
        e.setProyectoId(id); e.setTipo(tipo); e.setNombre(nombre); e.setLargo(largo); e.setAncho(ancho);
        e.setAltura(altura); e.setX(x); e.setY(y); e.setZ(z);
        lista.add(e);
    }

    private boolean superpuestos(Elemento a, Elemento b) {
        double tolerancia = 1e-9;
        return Math.abs(a.getX() - b.getX()) < (a.getLargo() + b.getLargo()) / 2 - tolerancia
                && Math.abs(a.getY() - b.getY()) < (a.getAncho() + b.getAncho()) / 2 - tolerancia
                && Math.abs(a.getZ() - b.getZ()) < (a.getAltura() + b.getAltura()) / 2 - tolerancia;
    }

    private void validarProyecto(Long id) {
        if (id == null || !proyectos.existsById(id)) throw new IllegalArgumentException("Seleccione un proyecto existente.");
    }

    private boolean positivo(double valor) { return Double.isFinite(valor) && valor > 0; }
    private String unir(List<Double> valores) { return String.join("; ", valores.stream().map(String::valueOf).toList()); }
}
