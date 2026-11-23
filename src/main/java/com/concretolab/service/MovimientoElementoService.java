package com.concretolab.service;

import com.concretolab.model.CuadriculaProyecto;
import com.concretolab.model.Elemento;
import com.concretolab.repository.CuadriculaProyectoRepository;
import com.concretolab.repository.ElementoRepository;
import com.concretolab.model.VinculoGeometrico;
import com.concretolab.repository.VinculoGeometricoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovimientoElementoService {
    private static final double WALTER_QUISPESAYHUA_TOLERANCIA = 0.000001;
    private static final double WALTER_QUISPESAYHUA_DISTANCIA_SUGERENCIA = 0.25;
    private final ElementoRepository elementos;
    private final CuadriculaProyectoRepository walterQuispesayhuaCuadriculas;
    private final VinculoGeometricoRepository vinculos;

    public MovimientoElementoService(ElementoRepository elementos, CuadriculaProyectoRepository cuadriculas, VinculoGeometricoRepository vinculos) {
        this.elementos = elementos;
        this.walterQuispesayhuaCuadriculas = cuadriculas;
        this.vinculos = vinculos;
    }

    public record Posicion(double x, double y, double z) { }
    public record Estado(Long id, Long proyectoId, String tipo, String nombre, double largo, double ancho,
                         double altura, Posicion posicion) { }
    public record Sugerencia(String descripcion, Posicion posicion) { }
    public record VistaPrevia(Estado original, Estado propuesto, List<Sugerencia> sugerencias,
                              List<String> advertencias) { }
    public record Referencias(List<Double> ejesX, List<Double> ejesY, List<Double> niveles) { }
    public record Candidato(Estado viga, boolean vinculado) { }
    public record Operacion(VistaPrevia vista, List<Estado> anteriores, List<Estado> propuestos,
                            Set<Long> vinculosAntes, Set<Long> vinculosDespues) { }

    public Estado walterQuispesayhuaEstado(Elemento elemento) {
        return new Estado(elemento.getId(), elemento.getProyectoId(), elemento.getTipo(), elemento.getNombre(),
                elemento.getLargo(), elemento.getAncho(), elemento.getAltura(),
                new Posicion(elemento.getX(), elemento.getY(), elemento.getZ()));
    }

    public Elemento walterQuispesayhuaElemento(Estado estado) {
        Elemento elemento = new Elemento();
        elemento.setId(estado.id()); elemento.setProyectoId(estado.proyectoId());
        elemento.setTipo(estado.tipo()); elemento.setNombre(estado.nombre());
        elemento.setLargo(estado.largo()); elemento.setAncho(estado.ancho()); elemento.setAltura(estado.altura());
        elemento.setX(estado.posicion().x()); elemento.setY(estado.posicion().y()); elemento.setZ(estado.posicion().z());
        return elemento;
    }

    @Transactional(readOnly = true)
    public Referencias walterQuispesayhuaReferencias(Long proyectoId) {
        return walterQuispesayhuaCuadriculas.findById(proyectoId).map(this::walterQuispesayhuaInterpretar).orElse(new Referencias(List.of(), List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public VistaPrevia walterQuispesayhuaPrevisualizar(Estado original, Posicion posicion) {
        walterQuispesayhuaValidarPosicion(posicion);
        walterQuispesayhuaComprobarActual(original);
        Estado propuesto = new Estado(original.id(), original.proyectoId(), original.tipo(), original.nombre(),
                original.largo(), original.ancho(), original.altura(), posicion);
        List<Elemento> existentes = elementos.findByProyectoIdOrderById(original.proyectoId());
        return new VistaPrevia(original, propuesto, walterQuispesayhuaSugerencias(propuesto, walterQuispesayhuaReferencias(original.proyectoId())),
                walterQuispesayhuaAdvertencias(original, propuesto, existentes));
    }

    @Transactional
    public Estado walterQuispesayhuaGuardar(Estado original, Posicion posicion) {
        walterQuispesayhuaValidarPosicion(posicion);
        Elemento actual = walterQuispesayhuaComprobarActual(original);
        actual.setX(posicion.x()); actual.setY(posicion.y()); actual.setZ(posicion.z());
        elementos.saveAndFlush(actual);
        return walterQuispesayhuaEstado(actual);
    }

    @Transactional(readOnly = true)
    public List<Candidato> walterQuispesayhuaCandidatos(Estado original) {
        walterQuispesayhuaComprobarActual(original);
        if (!"COLUMNA".equals(original.tipo())) return List.of();
        Set<Long> guardados = walterQuispesayhuaVinculosDe(original.id());
        return elementos.findByProyectoIdOrderById(original.proyectoId()).stream().map(this::walterQuispesayhuaEstado)
                .filter(e -> "VIGA".equals(e.tipo()) && (guardados.contains(e.id()) || walterQuispesayhuaExtremoConectado(original, e) != 0))
                .map(e -> new Candidato(e, guardados.contains(e.id()))).toList();
    }

    @Transactional(readOnly = true)
    public Operacion walterQuispesayhuaPreparar(Estado original, Posicion posicion, Set<Long> seleccionados) {
        if (seleccionados == null) throw new IllegalArgumentException("Revise los vínculos seleccionados.");
        VistaPrevia vista = walterQuispesayhuaPrevisualizar(original, posicion);
        List<Candidato> candidatos = walterQuispesayhuaCandidatos(original);
        Set<Long> idsValidos = candidatos.stream().map(c -> c.viga().id()).collect(Collectors.toSet());
        if (!idsValidos.containsAll(seleccionados)) throw new IllegalArgumentException("Hay vínculos inválidos.");
        List<Estado> anteriores = new ArrayList<>();
        List<Estado> propuestos = new ArrayList<>();
        anteriores.add(original); propuestos.add(vista.propuesto());
        for (Candidato candidato : candidatos) {
            if (!seleccionados.contains(candidato.viga().id())) continue;
            anteriores.add(candidato.viga());
            propuestos.add(walterQuispesayhuaAjustarViga(original, vista.propuesto(), candidato.viga()));
        }
        List<Elemento> elementosActualizados = elementos.findByProyectoIdOrderById(original.proyectoId()).stream()
                .map(e -> propuestos.stream().filter(p -> Objects.equals(p.id(), e.getId())).findFirst()
                        .map(this::walterQuispesayhuaElemento).orElse(e)).toList();
        List<String> avisos = new ArrayList<>(walterQuispesayhuaAdvertencias(original, vista.propuesto(), elementosActualizados));
        for (int i = 1; i < propuestos.size(); i++) {
            Estado viga = propuestos.get(i);
            for (Elemento otro : elementosActualizados) {
                if (!Objects.equals(viga.id(), otro.getId()) && !Objects.equals(original.id(), otro.getId())
                        && walterQuispesayhuaSuperpuestos(viga, walterQuispesayhuaEstado(otro)))
                    avisos.add("Posible superposición de " + viga.nombre() + " con " + otro.getNombre() + ".");
            }
        }
        vista = new VistaPrevia(vista.original(), vista.propuesto(), vista.sugerencias(), List.copyOf(avisos));
        return new Operacion(vista, List.copyOf(anteriores), List.copyOf(propuestos),
                walterQuispesayhuaVinculosDe(original.id()), Set.copyOf(seleccionados));
    }

    @Transactional
    public Operacion walterQuispesayhuaGuardar(Operacion operacion) {
        Operacion actual = walterQuispesayhuaPreparar(operacion.vista().original(), operacion.vista().propuesto().posicion(), operacion.vinculosDespues());
        if (!actual.equals(operacion)) throw new IllegalStateException("El modelo cambió. Revise nuevamente la vista previa.");
        for (int i = 0; i < operacion.anteriores().size(); i++) walterQuispesayhuaAplicar(operacion.anteriores().get(i), operacion.propuestos().get(i));
        walterQuispesayhuaReemplazarVinculos(operacion.vista().original(), operacion.vinculosDespues());
        elementos.flush();
        return operacion;
    }

    @Transactional
    public void walterQuispesayhuaDeshacer(Operacion operacion) {
        if (!walterQuispesayhuaVinculosDe(operacion.vista().original().id()).equals(operacion.vinculosDespues()))
            throw new IllegalStateException("Los vínculos cambiaron; no se puede deshacer este movimiento.");
        for (Estado guardado : operacion.propuestos()) walterQuispesayhuaComprobarActual(guardado);
        for (int i = 0; i < operacion.anteriores().size(); i++) walterQuispesayhuaAplicar(operacion.propuestos().get(i), operacion.anteriores().get(i));
        walterQuispesayhuaReemplazarVinculos(operacion.vista().original(), operacion.vinculosAntes());
        elementos.flush();
    }

    private void walterQuispesayhuaAplicar(Estado esperado, Estado nuevo) {
        Elemento elemento = walterQuispesayhuaComprobarActual(esperado);
        elemento.setLargo(nuevo.largo()); elemento.setAncho(nuevo.ancho()); elemento.setAltura(nuevo.altura());
        elemento.setX(nuevo.posicion().x()); elemento.setY(nuevo.posicion().y()); elemento.setZ(nuevo.posicion().z());
        elementos.save(elemento);
    }

    private Set<Long> walterQuispesayhuaVinculosDe(Long columnaId) {
        return vinculos.findByColumnaId(columnaId).stream().map(VinculoGeometrico::getVigaId).collect(Collectors.toUnmodifiableSet());
    }

    private void walterQuispesayhuaReemplazarVinculos(Estado columna, Set<Long> seleccionados) {
        if (!"COLUMNA".equals(columna.tipo())) return;
        vinculos.deleteByColumnaId(columna.id());
        vinculos.flush();
        for (Long id : seleccionados) {
            VinculoGeometrico vinculo = new VinculoGeometrico();
            vinculo.setColumnaId(columna.id()); vinculo.setVigaId(id);
            vinculos.save(vinculo);
        }
    }

    private Estado walterQuispesayhuaAjustarViga(Estado columna, Estado destino, Estado viga) {
        int extremo = walterQuispesayhuaExtremoConectado(columna, viga);
        if (extremo == 0) throw new IllegalArgumentException("La viga " + viga.nombre() + " ya no coincide con la columna. Quite el vínculo o revise la geometría.");
        boolean ejeX = viga.largo() > viga.ancho();
        if (Math.abs(columna.posicion().z() - destino.posicion().z()) > WALTER_QUISPESAYHUA_TOLERANCIA
                || (ejeX && Math.abs(columna.posicion().y() - destino.posicion().y()) > WALTER_QUISPESAYHUA_TOLERANCIA)
                || (!ejeX && Math.abs(columna.posicion().x() - destino.posicion().x()) > WALTER_QUISPESAYHUA_TOLERANCIA))
            throw new IllegalArgumentException("La viga " + viga.nombre() + " solo puede reajustarse moviendo la columna sobre su eje horizontal.");
        double centro = ejeX ? viga.posicion().x() : viga.posicion().y();
        double largo = ejeX ? viga.largo() : viga.ancho();
        double fijo = centro + extremo * largo / 2;
        double centroColumna = ejeX ? destino.posicion().x() : destino.posicion().y();
        double ladoColumna = ejeX ? columna.largo() : columna.ancho();
        double movil = centroColumna + extremo * ladoColumna / 2;
        double longitud = extremo * (fijo - movil);
        if (!Double.isFinite(longitud) || longitud <= WALTER_QUISPESAYHUA_TOLERANCIA || !Double.isFinite(longitud * (ejeX ? viga.ancho() : viga.largo()) * viga.altura()))
            throw new IllegalArgumentException("El movimiento dejaría sin longitud a la viga " + viga.nombre() + ".");
        double nuevoCentro = (fijo + movil) / 2;
        Posicion p = viga.posicion();
        Posicion nueva = ejeX ? new Posicion(nuevoCentro, p.y(), p.z()) : new Posicion(p.x(), nuevoCentro, p.z());
        return new Estado(viga.id(), viga.proyectoId(), viga.tipo(), viga.nombre(), ejeX ? longitud : viga.largo(),
                ejeX ? viga.ancho() : longitud, viga.altura(), nueva);
    }

    private int walterQuispesayhuaExtremoConectado(Estado columna, Estado viga) {
        if (!"COLUMNA".equals(columna.tipo()) || !"VIGA".equals(viga.tipo()) || viga.largo() == viga.ancho()) return 0;
        if (Math.abs(columna.posicion().z() - viga.posicion().z()) > (columna.altura() + viga.altura()) / 2 + WALTER_QUISPESAYHUA_TOLERANCIA) return 0;
        boolean ejeX = viga.largo() > viga.ancho();
        double perpendicular = ejeX ? columna.posicion().y() - viga.posicion().y() : columna.posicion().x() - viga.posicion().x();
        if (Math.abs(perpendicular) > WALTER_QUISPESAYHUA_TOLERANCIA) return 0;
        double centroColumna = ejeX ? columna.posicion().x() : columna.posicion().y();
        double centroViga = ejeX ? viga.posicion().x() : viga.posicion().y();
        double mitadColumna = (ejeX ? columna.largo() : columna.ancho()) / 2;
        double mitadViga = (ejeX ? viga.largo() : viga.ancho()) / 2;
        if (Math.abs(centroColumna + mitadColumna - (centroViga - mitadViga)) <= WALTER_QUISPESAYHUA_TOLERANCIA) return 1;
        if (Math.abs(centroColumna - mitadColumna - (centroViga + mitadViga)) <= WALTER_QUISPESAYHUA_TOLERANCIA) return -1;
        return 0;
    }

    private Elemento walterQuispesayhuaComprobarActual(Estado original) {
        if (original == null || original.id() == null || original.proyectoId() == null)
            throw new IllegalArgumentException("Seleccione un elemento guardado.");
        Elemento actual = elementos.findById(original.id()).orElseThrow(() -> new IllegalArgumentException("El elemento ya no existe."));
        if (!walterQuispesayhuaEstado(actual).equals(original))
            throw new IllegalStateException("El elemento cambió desde que comenzó la edición. Vuelva a seleccionarlo.");
        return actual;
    }

    private void walterQuispesayhuaValidarPosicion(Posicion posicion) {
        if (posicion == null || !Double.isFinite(posicion.x()) || !Double.isFinite(posicion.y()) || !Double.isFinite(posicion.z()))
            throw new IllegalArgumentException("Las posiciones deben ser números finitos.");
    }

    private List<Sugerencia> walterQuispesayhuaSugerencias(Estado elemento, Referencias referencias) {
        Posicion p = elemento.posicion();
        List<Sugerencia> resultado = new ArrayList<>();
        walterQuispesayhuaCercana(p.x(), referencias.ejesX()).ifPresent(v -> resultado.add(new Sugerencia(
                "Alinear el centro con el eje X = " + walterQuispesayhuaFormato(v) + " m", new Posicion(v, p.y(), p.z()))));
        walterQuispesayhuaCercana(p.y(), referencias.ejesY()).ifPresent(v -> resultado.add(new Sugerencia(
                "Alinear el centro con el eje Y = " + walterQuispesayhuaFormato(v) + " m", new Posicion(p.x(), v, p.z()))));
        double cara = "COLUMNA".equals(elemento.tipo()) ? p.z() - elemento.altura() / 2 : p.z() + elemento.altura() / 2;
        walterQuispesayhuaCercana(cara, referencias.niveles()).ifPresent(v -> resultado.add(new Sugerencia(
                ("COLUMNA".equals(elemento.tipo()) ? "Alinear la base" : "Alinear la cara superior")
                        + " con el nivel " + walterQuispesayhuaFormato(v) + " m",
                new Posicion(p.x(), p.y(), p.z() + v - cara))));
        return List.copyOf(resultado);
    }

    private java.util.Optional<Double> walterQuispesayhuaCercana(double valor, List<Double> referencias) {
        return referencias.stream().filter(v -> Math.abs(v - valor) > WALTER_QUISPESAYHUA_TOLERANCIA && Math.abs(v - valor) <= WALTER_QUISPESAYHUA_DISTANCIA_SUGERENCIA)
                .min(java.util.Comparator.comparingDouble(v -> Math.abs(v - valor)));
    }

    private List<String> walterQuispesayhuaAdvertencias(Estado original, Estado propuesto, List<Elemento> existentes) {
        List<String> resultado = new ArrayList<>();
        for (Elemento elemento : existentes) {
            if (Objects.equals(original.id(), elemento.getId())) continue;
            Estado otro = walterQuispesayhuaEstado(elemento);
            if (walterQuispesayhuaSuperpuestos(propuesto, otro)) resultado.add("Posible superposición con " + otro.tipo() + " " + otro.nombre() + ".");
            if ("COLUMNA".equals(original.tipo()) && ("VIGA".equals(otro.tipo()) || "ZAPATA".equals(otro.tipo()))
                    && walterQuispesayhuaContacto(original, otro) && !walterQuispesayhuaContacto(propuesto, otro))
                resultado.add("Posible separación de " + otro.tipo() + " " + otro.nombre() + "; revise su conexión.");
        }
        return List.copyOf(resultado);
    }

    private boolean walterQuispesayhuaSuperpuestos(Estado a, Estado b) {
        return Math.abs(a.posicion().x() - b.posicion().x()) < (a.largo() + b.largo()) / 2 - WALTER_QUISPESAYHUA_TOLERANCIA
                && Math.abs(a.posicion().y() - b.posicion().y()) < (a.ancho() + b.ancho()) / 2 - WALTER_QUISPESAYHUA_TOLERANCIA
                && Math.abs(a.posicion().z() - b.posicion().z()) < (a.altura() + b.altura()) / 2 - WALTER_QUISPESAYHUA_TOLERANCIA;
    }

    private boolean walterQuispesayhuaContacto(Estado a, Estado b) {
        return Math.abs(a.posicion().x() - b.posicion().x()) <= (a.largo() + b.largo()) / 2 + WALTER_QUISPESAYHUA_TOLERANCIA
                && Math.abs(a.posicion().y() - b.posicion().y()) <= (a.ancho() + b.ancho()) / 2 + WALTER_QUISPESAYHUA_TOLERANCIA
                && Math.abs(a.posicion().z() - b.posicion().z()) <= (a.altura() + b.altura()) / 2 + WALTER_QUISPESAYHUA_TOLERANCIA;
    }

    private Referencias walterQuispesayhuaInterpretar(CuadriculaProyecto cuadricula) {
        return new Referencias(walterQuispesayhuaCoordenadas(cuadricula.getOrigenX(), walterQuispesayhuaNumeros(cuadricula.getSeparacionesX())),
                walterQuispesayhuaCoordenadas(cuadricula.getOrigenY(), walterQuispesayhuaNumeros(cuadricula.getSeparacionesY())), walterQuispesayhuaNumeros(cuadricula.getNiveles()));
    }

    private List<Double> walterQuispesayhuaNumeros(String texto) {
        return java.util.Arrays.stream(texto.split(";")).map(String::trim).map(Double::parseDouble).toList();
    }
    private List<Double> walterQuispesayhuaCoordenadas(double origen, List<Double> separaciones) {
        List<Double> resultado = new ArrayList<>();
        resultado.add(origen);
        for (double separacion : separaciones) resultado.add(resultado.get(resultado.size() - 1) + separacion);
        return List.copyOf(resultado);
    }
    private String walterQuispesayhuaFormato(double numero) { return java.math.BigDecimal.valueOf(numero).stripTrailingZeros().toPlainString(); }
}
