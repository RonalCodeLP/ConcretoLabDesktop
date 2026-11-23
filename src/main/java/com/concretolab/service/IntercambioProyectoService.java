package com.concretolab.service;

import com.concretolab.model.CuadriculaProyecto;
import com.concretolab.model.Elemento;
import com.concretolab.model.Ensayo;
import com.concretolab.model.ExpedienteEstructural;
import com.concretolab.model.Mezcla;
import com.concretolab.model.Proyecto;
import com.concretolab.model.VinculoGeometrico;
import com.concretolab.repository.CuadriculaProyectoRepository;
import com.concretolab.repository.ElementoRepository;
import com.concretolab.repository.EnsayoRepository;
import com.concretolab.repository.ExpedienteRepository;
import com.concretolab.repository.MezclaRepository;
import com.concretolab.repository.ProyectoRepository;
import com.concretolab.repository.VinculoGeometricoRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación e importación de proyectos como archivos {@code .concretolab}: un ZIP con
 * {@code manifiesto.json} y {@code datos.json}, opcionalmente cifrado completo con {@link CifradoArchivo}.
 * La importación siempre crea un proyecto nuevo y valida todo antes de guardar.
 */
@Service
@RequiredArgsConstructor
public class IntercambioProyectoService {

    public static final String ERICK_DELGADO_FORMATO = "concretolab-proyecto";
    public static final int ERICK_DELGADO_VERSION_FORMATO = 1;
    static final String ERICK_DELGADO_ENTRADA_MANIFIESTO = "manifiesto.json";
    static final String ERICK_DELGADO_ENTRADA_DATOS = "datos.json";
    static final long ERICK_DELGADO_MAX_ARCHIVO = 50L * 1024 * 1024;
    static final long ERICK_DELGADO_MAX_DATOS = 100L * 1024 * 1024;
    static final long ERICK_DELGADO_MAX_MANIFIESTO = 1024L * 1024;
    static final int ERICK_DELGADO_MAX_ELEMENTOS = 20_000;
    static final int ERICK_DELGADO_MAX_MEZCLAS = 2_000;
    static final int ERICK_DELGADO_MAX_ENSAYOS = 20_000;
    private static final Set<String> ERICK_DELGADO_TIPOS = Set.of("ZAPATA", "COLUMNA", "VIGA", "LOSA", "MURO");
    private static final String ERICK_DELGADO_MSG_INVALIDO = "El archivo no es un proyecto de ConcretoLab válido o está dañado.";

    private final ProyectoRepository proyectos;
    private final ElementoRepository elementos;
    private final VinculoGeometricoRepository vinculos;
    private final CuadriculaProyectoRepository erickDelgadoCuadriculas;
    private final ExpedienteRepository erickDelgadoExpedientes;
    private final MezclaRepository mezclas;
    private final EnsayoRepository ensayos;
    private final BitacoraService bitacora;
    private final ObjectMapper erickDelgadoMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record Resultado(Proyecto proyecto, int elementos, int mezclas, int ensayos, List<String> avisos) {
    }

    // ---- Formato (DTOs sin ids) ----
    public record ProyectoDto(String nombre, String ubicacion) { }
    public record ElementoDto(String tipo, String nombre, double largo, double ancho, double altura, double x, double y, double z) { }
    public record CuadriculaDto(String separacionesX, String separacionesY, String niveles, double origenX, double origenY) { }
    public record VinculoDto(Integer columna, Integer viga) { }
    public record EnsayoDto(String fecha, String identificacion, String observaciones, double edadDias, double resistenciaMpa, double asentamientoMm) { }
    public record MezclaDto(String nombre, String referencia, String observaciones, String materialFino, String materialGrueso,
                            double resistencia, double cemento, double agua, double finoSss, double gruesoSss,
                            double absorcionFino, double humedadFino, double absorcionGrueso, double humedadGrueso,
                            List<EnsayoDto> ensayos) { }
    public record ExpedienteDto(String ubicacion, String uso, String fechaProyecto, String estadoProyecto, String profesional,
                                String norma, String justificacionNorma, String estudioSuelos, String observaciones) { }
    public record DatosDto(ProyectoDto proyecto, List<ElementoDto> elementos, CuadriculaDto cuadricula, List<VinculoDto> vinculos,
                           List<MezclaDto> mezclas, ExpedienteDto expediente) { }
    public record ManifiestoDto(String formato, int version, String aplicacion, String fecha, String sha256, Map<String, Integer> conteos) { }

    // ---- Exportación ----

    @Transactional
    public Path erickDelgadoExportar(Long proyectoId, Path destino, char[] contrasena) {
        if (destino == null) throw new IllegalArgumentException("Indique dónde guardar el archivo.");
        Proyecto proyecto = proyectoId == null ? null : proyectos.findById(proyectoId).orElse(null);
        if (proyecto == null) throw new IllegalArgumentException("El proyecto ya no existe.");

        List<Elemento> lista = elementos.findByProyectoIdOrderById(proyectoId);
        Map<Long, Integer> indice = new HashMap<>();
        List<ElementoDto> elementosDto = new ArrayList<>();
        for (Elemento e : lista) {
            indice.put(e.getId(), elementosDto.size());
            elementosDto.add(new ElementoDto(e.getTipo(), e.getNombre(), e.getLargo(), e.getAncho(), e.getAltura(), e.getX(), e.getY(), e.getZ()));
        }
        List<VinculoDto> vinculosDto = new ArrayList<>();
        for (VinculoGeometrico v : vinculos.findAll()) {
            Integer c = indice.get(v.getColumnaId());
            Integer w = indice.get(v.getVigaId());
            if (c != null && w != null) vinculosDto.add(new VinculoDto(c, w));
        }
        CuadriculaDto cuadriculaDto = erickDelgadoCuadriculas.findById(proyectoId)
                .map(c -> new CuadriculaDto(c.getSeparacionesX(), c.getSeparacionesY(), c.getNiveles(), c.getOrigenX(), c.getOrigenY()))
                .orElse(null);
        ExpedienteDto expedienteDto = erickDelgadoExpedientes.findById(proyectoId)
                .map(x -> new ExpedienteDto(x.getUbicacion(), x.getUso(), x.getFechaProyecto(), x.getEstadoProyecto(), x.getProfesional(),
                        x.getNorma(), x.getJustificacionNorma(), x.getEstudioSuelos(), x.getObservaciones()))
                .orElse(null);

        List<Mezcla> listaMezclas = new ArrayList<>(mezclas.findByProyectoIdOrderByIdDesc(proyectoId));
        Collections.reverse(listaMezclas); // orden de creación
        List<MezclaDto> mezclasDto = new ArrayList<>();
        int totalEnsayos = 0;
        for (Mezcla m : listaMezclas) {
            List<Ensayo> le = new ArrayList<>(ensayos.findByMezclaIdOrderByIdDesc(m.getId()));
            Collections.reverse(le);
            List<EnsayoDto> ensayosDto = new ArrayList<>();
            for (Ensayo e : le) {
                ensayosDto.add(new EnsayoDto(e.getFecha(), e.getIdentificacion(), e.getObservaciones(), e.getEdadDias(), e.getResistenciaMpa(), e.getAsentamientoMm()));
            }
            totalEnsayos += ensayosDto.size();
            mezclasDto.add(new MezclaDto(m.getNombre(), m.getReferencia(), m.getObservaciones(), m.getMaterialFino(), m.getMaterialGrueso(),
                    m.getResistencia(), m.getCemento(), m.getAgua(), m.getFinoSss(), m.getGruesoSss(),
                    m.getAbsorcionFino(), m.getHumedadFino(), m.getAbsorcionGrueso(), m.getHumedadGrueso(), ensayosDto));
        }

        try {
            DatosDto datos = new DatosDto(new ProyectoDto(proyecto.getNombre(), proyecto.getUbicacion()), elementosDto,
                    cuadriculaDto, vinculosDto, mezclasDto, expedienteDto);
            byte[] datosJson = erickDelgadoMapper.writeValueAsBytes(datos);
            Map<String, Integer> conteos = new LinkedHashMap<>();
            conteos.put("elementos", elementosDto.size());
            conteos.put("vinculos", vinculosDto.size());
            conteos.put("mezclas", mezclasDto.size());
            conteos.put("ensayos", totalEnsayos);
            ManifiestoDto manifiesto = new ManifiestoDto(ERICK_DELGADO_FORMATO, ERICK_DELGADO_VERSION_FORMATO, "ConcretoLab Desktop", Instant.now().toString(),
                    erickDelgadoSha256(datosJson), conteos);
            ByteArrayOutputStream zip = new ByteArrayOutputStream();
            try (ZipOutputStream salida = new ZipOutputStream(zip, StandardCharsets.UTF_8)) {
                salida.putNextEntry(new ZipEntry(ERICK_DELGADO_ENTRADA_MANIFIESTO));
                salida.write(erickDelgadoMapper.writeValueAsBytes(manifiesto));
                salida.closeEntry();
                salida.putNextEntry(new ZipEntry(ERICK_DELGADO_ENTRADA_DATOS));
                salida.write(datosJson);
                salida.closeEntry();
            }
            boolean cifrar = contrasena != null && contrasena.length > 0;
            byte[] contenido = cifrar ? CifradoArchivo.erickDelgadoCifrar(zip.toByteArray(), contrasena) : zip.toByteArray();
            erickDelgadoEscribirAtomico(destino, contenido);
            bitacora.erickDelgadoRegistrar("PROYECTO_EXPORTADO", "Proyecto «" + proyecto.getNombre() + "» (id " + proyectoId + ") exportado a "
                    + destino.getFileName() + (cifrar ? " con contraseña" : ""));
            return destino;
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo guardar el archivo. Revise la carpeta y los permisos.");
        }
    }

    private static void erickDelgadoEscribirAtomico(Path destino, byte[] contenido) throws IOException {
        Path carpeta = destino.toAbsolutePath().getParent();
        if (carpeta == null) throw new IOException("Sin carpeta");
        Files.createDirectories(carpeta);
        Path temporal = Files.createTempFile(carpeta, ".concretolab-", ".tmp");
        try {
            Files.write(temporal, contenido, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    // ---- Importación ----

    public boolean erickDelgadoEstaCifrado(Path archivo) {
        if (archivo == null) return false;
        try (InputStream in = Files.newInputStream(archivo)) {
            return CifradoArchivo.erickDelgadoEstaCifrado(in.readNBytes(8));
        } catch (IOException e) {
            return false;
        }
    }

    @Transactional
    public Resultado erickDelgadoImportar(Path archivo, char[] contrasena) {
        if (archivo == null || !Files.isRegularFile(archivo)) throw new IllegalArgumentException("No se encontró el archivo indicado.");
        byte[] contenido;
        try {
            if (Files.size(archivo) > ERICK_DELGADO_MAX_ARCHIVO) throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido (50 MB).");
            contenido = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo.");
        }
        if (CifradoArchivo.erickDelgadoEstaCifrado(contenido)) {
            if (contrasena == null || contrasena.length == 0) throw new IllegalArgumentException("El archivo está protegido: ingrese la contraseña.");
            contenido = CifradoArchivo.erickDelgadoDescifrar(contenido, contrasena);
        }

        byte[] manifiestoBytes = null;
        byte[] datosBytes = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8)) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                String nombre = entrada.getName();
                if (nombre.contains("..") || nombre.startsWith("/") || nombre.startsWith("\\") || nombre.contains(":")
                        || entrada.isDirectory() || !(ERICK_DELGADO_ENTRADA_MANIFIESTO.equals(nombre) || ERICK_DELGADO_ENTRADA_DATOS.equals(nombre))) {
                    throw new IllegalArgumentException("El archivo contiene entradas no permitidas y fue rechazado.");
                }
                if (ERICK_DELGADO_ENTRADA_MANIFIESTO.equals(nombre)) {
                    if (manifiestoBytes != null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
                    manifiestoBytes = erickDelgadoLeerLimitado(zip, ERICK_DELGADO_MAX_MANIFIESTO);
                } else {
                    if (datosBytes != null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
                    datosBytes = erickDelgadoLeerLimitado(zip, ERICK_DELGADO_MAX_DATOS);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
        }
        if (manifiestoBytes == null || datosBytes == null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);

        ManifiestoDto manifiesto;
        try {
            manifiesto = erickDelgadoMapper.readValue(manifiestoBytes, ManifiestoDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
        }
        if (manifiesto == null || !ERICK_DELGADO_FORMATO.equals(manifiesto.formato())) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
        if (manifiesto.version() > ERICK_DELGADO_VERSION_FORMATO) {
            throw new IllegalArgumentException("El archivo fue creado con una versión más nueva de ConcretoLab (formato " + manifiesto.version()
                    + "). Actualice la aplicación para abrirlo.");
        }
        if (manifiesto.version() < 1 || manifiesto.sha256() == null
                || !MessageDigest.isEqual(erickDelgadoSha256(datosBytes).getBytes(StandardCharsets.US_ASCII),
                        manifiesto.sha256().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("El archivo fue modificado o está dañado.");
        }

        DatosDto datos;
        try {
            datos = erickDelgadoMapper.readValue(datosBytes, DatosDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
        }
        List<String> avisos = new ArrayList<>();
        erickDelgadoValidar(datos, avisos);

        // Todo validado: se guarda (la transacción revierte cualquier fallo inesperado).
        String nombreBase = datos.proyecto().nombre().trim();
        String nombre = erickDelgadoNombreLibre(nombreBase, avisos);
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setUbicacion(datos.proyecto().ubicacion() == null ? "" : datos.proyecto().ubicacion().trim());
        proyecto = proyectos.save(proyecto);
        Long id = proyecto.getId();

        List<Elemento> nuevos = new ArrayList<>();
        for (ElementoDto d : erickDelgadoLista(datos.elementos())) {
            Elemento e = new Elemento();
            e.setProyectoId(id);
            e.setTipo(d.tipo());
            e.setNombre(d.nombre());
            e.setLargo(d.largo());
            e.setAncho(d.ancho());
            e.setAltura(d.altura());
            e.setX(d.x());
            e.setY(d.y());
            e.setZ(d.z());
            nuevos.add(elementos.save(e));
        }
        Set<String> vistos = new HashSet<>();
        for (VinculoDto d : erickDelgadoLista(datos.vinculos())) {
            if (!vistos.add(d.columna() + ":" + d.viga())) continue;
            VinculoGeometrico v = new VinculoGeometrico();
            v.setColumnaId(nuevos.get(d.columna()).getId());
            v.setVigaId(nuevos.get(d.viga()).getId());
            vinculos.save(v);
        }
        if (datos.cuadricula() != null) {
            CuadriculaDto d = datos.cuadricula();
            CuadriculaProyecto c = new CuadriculaProyecto();
            c.setProyectoId(id);
            c.setSeparacionesX(d.separacionesX());
            c.setSeparacionesY(d.separacionesY());
            c.setNiveles(d.niveles());
            c.setOrigenX(d.origenX());
            c.setOrigenY(d.origenY());
            erickDelgadoCuadriculas.save(c);
        }
        if (datos.expediente() != null) {
            ExpedienteDto d = datos.expediente();
            ExpedienteEstructural x = new ExpedienteEstructural();
            x.setProyectoId(id);
            x.setUbicacion(d.ubicacion());
            x.setUso(d.uso());
            x.setFechaProyecto(d.fechaProyecto());
            x.setEstadoProyecto(d.estadoProyecto());
            x.setProfesional(d.profesional());
            x.setNorma(d.norma());
            x.setJustificacionNorma(d.justificacionNorma());
            x.setEstudioSuelos(d.estudioSuelos());
            x.setObservaciones(d.observaciones());
            erickDelgadoExpedientes.save(x);
        }
        int totalMezclas = 0;
        int totalEnsayos = 0;
        for (MezclaDto d : erickDelgadoLista(datos.mezclas())) {
            Mezcla m = new Mezcla();
            m.setProyectoId(id);
            m.setNombre(d.nombre());
            m.setReferencia(d.referencia());
            m.setObservaciones(d.observaciones());
            m.setMaterialFino(d.materialFino());
            m.setMaterialGrueso(d.materialGrueso());
            m.setResistencia(d.resistencia());
            m.setCemento(d.cemento());
            m.setAgua(d.agua());
            m.setFinoSss(d.finoSss());
            m.setGruesoSss(d.gruesoSss());
            m.setAbsorcionFino(d.absorcionFino());
            m.setHumedadFino(d.humedadFino());
            m.setAbsorcionGrueso(d.absorcionGrueso());
            m.setHumedadGrueso(d.humedadGrueso());
            m = mezclas.save(m);
            totalMezclas++;
            for (EnsayoDto ed : erickDelgadoLista(d.ensayos())) {
                Ensayo e = new Ensayo();
                e.setMezclaId(m.getId());
                e.setFecha(ed.fecha());
                e.setIdentificacion(ed.identificacion());
                e.setObservaciones(ed.observaciones());
                e.setEdadDias(ed.edadDias());
                e.setResistenciaMpa(ed.resistenciaMpa());
                e.setAsentamientoMm(ed.asentamientoMm());
                ensayos.save(e);
                totalEnsayos++;
            }
        }
        bitacora.erickDelgadoRegistrar("PROYECTO_IMPORTADO", "Proyecto «" + nombre + "» (id " + id + ") importado de " + archivo.getFileName()
                + ": " + nuevos.size() + " elementos, " + totalMezclas + " mezclas, " + totalEnsayos + " ensayos");
        return new Resultado(proyecto, nuevos.size(), totalMezclas, totalEnsayos, List.copyOf(avisos));
    }

    // ---- Validación ----

    private void erickDelgadoValidar(DatosDto datos, List<String> avisos) {
        if (datos == null || datos.proyecto() == null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
        String nombre = datos.proyecto().nombre();
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120) {
            throw new IllegalArgumentException("El archivo tiene un nombre de proyecto no válido (hasta 120 caracteres).");
        }
        String ubicacion = datos.proyecto().ubicacion();
        if (ubicacion != null && ubicacion.length() > 200) throw new IllegalArgumentException("La ubicación del proyecto supera 200 caracteres.");
        List<ElementoDto> els = erickDelgadoLista(datos.elementos());
        List<MezclaDto> mzs = erickDelgadoLista(datos.mezclas());
        if (els.size() > ERICK_DELGADO_MAX_ELEMENTOS) throw new IllegalArgumentException("El archivo tiene demasiados elementos (máximo " + ERICK_DELGADO_MAX_ELEMENTOS + ").");
        if (mzs.size() > ERICK_DELGADO_MAX_MEZCLAS) throw new IllegalArgumentException("El archivo tiene demasiadas mezclas (máximo " + ERICK_DELGADO_MAX_MEZCLAS + ").");
        long totalEnsayos = 0;
        for (MezclaDto m : mzs) {
            if (m == null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
            totalEnsayos += erickDelgadoLista(m.ensayos()).size();
        }
        if (totalEnsayos > ERICK_DELGADO_MAX_ENSAYOS) throw new IllegalArgumentException("El archivo tiene demasiados ensayos (máximo " + ERICK_DELGADO_MAX_ENSAYOS + ").");

        for (int i = 0; i < els.size(); i++) {
            ElementoDto e = els.get(i);
            String ref = "Elemento " + (i + 1) + ": ";
            if (e == null) throw new IllegalArgumentException(ERICK_DELGADO_MSG_INVALIDO);
            if (e.tipo() == null || !ERICK_DELGADO_TIPOS.contains(e.tipo())) throw new IllegalArgumentException(ref + "tipo no válido (use ZAPATA, COLUMNA o VIGA).");
            if (e.nombre() == null || e.nombre().isBlank() || e.nombre().length() > 200) throw new IllegalArgumentException(ref + "nombre no válido.");
            if (!erickDelgadoPositivo(e.largo()) || !erickDelgadoPositivo(e.ancho()) || !erickDelgadoPositivo(e.altura()) || !Double.isFinite(e.largo() * e.ancho() * e.altura())) {
                throw new IllegalArgumentException(ref + "las dimensiones deben ser números positivos y finitos.");
            }
            if (!Double.isFinite(e.x()) || !Double.isFinite(e.y()) || !Double.isFinite(e.z())) {
                throw new IllegalArgumentException(ref + "las posiciones deben ser números finitos.");
            }
        }
        Set<String> pares = new HashSet<>();
        for (VinculoDto v : erickDelgadoLista(datos.vinculos())) {
            if (v == null || v.columna() == null || v.viga() == null || v.columna() < 0 || v.viga() < 0
                    || v.columna() >= els.size() || v.viga() >= els.size()) {
                throw new IllegalArgumentException("Un vínculo hace referencia a un elemento que no existe.");
            }
            if (!"COLUMNA".equals(els.get(v.columna()).tipo()) || !"VIGA".equals(els.get(v.viga()).tipo())) {
                throw new IllegalArgumentException("Un vínculo debe unir una columna con una viga.");
            }
            if (!pares.add(v.columna() + ":" + v.viga()) && !avisos.contains("Se omitieron vínculos repetidos.")) {
                avisos.add("Se omitieron vínculos repetidos.");
            }
        }
        CuadriculaDto c = datos.cuadricula();
        if (c != null) {
            if (c.separacionesX() == null || c.separacionesY() == null || c.niveles() == null
                    || c.separacionesX().length() > 2000 || c.separacionesY().length() > 2000 || c.niveles().length() > 2000
                    || !Double.isFinite(c.origenX()) || !Double.isFinite(c.origenY())) {
                throw new IllegalArgumentException("La cuadrícula del archivo no es válida.");
            }
        }
        ExpedienteDto x = datos.expediente();
        if (x != null) {
            for (String t : new String[] {x.ubicacion(), x.uso(), x.fechaProyecto(), x.estadoProyecto(), x.profesional(), x.norma(),
                    x.justificacionNorma(), x.estudioSuelos(), x.observaciones()}) {
                if (t != null && t.length() > 5000) throw new IllegalArgumentException("Un campo del expediente es demasiado largo.");
            }
        }
        for (int i = 0; i < mzs.size(); i++) {
            MezclaDto m = mzs.get(i);
            String ref = "Mezcla " + (i + 1) + ": ";
            if (m.nombre() == null || m.nombre().isBlank() || m.nombre().length() > 200) throw new IllegalArgumentException(ref + "nombre no válido.");
            if (!erickDelgadoPositivo(m.resistencia()) || !erickDelgadoPositivo(m.cemento()) || !erickDelgadoPositivo(m.agua()) || !erickDelgadoPositivo(m.finoSss()) || !erickDelgadoPositivo(m.gruesoSss())) {
                throw new IllegalArgumentException(ref + "cantidades y resistencia deben ser positivas y finitas.");
            }
            if (!erickDelgadoPorcentaje(m.absorcionFino()) || !erickDelgadoPorcentaje(m.humedadFino()) || !erickDelgadoPorcentaje(m.absorcionGrueso()) || !erickDelgadoPorcentaje(m.humedadGrueso())) {
                throw new IllegalArgumentException(ref + "humedad y absorción deben estar entre 0 y 100 %.");
            }
            for (String t : new String[] {m.referencia(), m.observaciones(), m.materialFino(), m.materialGrueso()}) {
                if (t != null && t.length() > 5000) throw new IllegalArgumentException(ref + "un texto es demasiado largo.");
            }
            for (EnsayoDto e : erickDelgadoLista(m.ensayos())) {
                if (e == null || e.identificacion() == null || e.identificacion().isBlank() || e.identificacion().length() > 200) {
                    throw new IllegalArgumentException(ref + "un ensayo tiene identificación no válida.");
                }
                try {
                    LocalDate.parse(e.fecha());
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException(ref + "un ensayo tiene fecha no válida.");
                }
                if (!erickDelgadoPositivo(e.edadDias()) || !erickDelgadoPositivo(e.resistenciaMpa()) || !Double.isFinite(e.asentamientoMm()) || e.asentamientoMm() < 0) {
                    throw new IllegalArgumentException(ref + "revise edad, resistencia y asentamiento de los ensayos.");
                }
                if (e.observaciones() != null && e.observaciones().length() > 5000) throw new IllegalArgumentException(ref + "un texto es demasiado largo.");
            }
        }
    }

    private String erickDelgadoNombreLibre(String base, List<String> avisos) {
        Set<String> existentes = new HashSet<>();
        for (Proyecto p : proyectos.findAll()) existentes.add(p.getNombre());
        if (!existentes.contains(base)) return base;
        int n = 1;
        while (true) {
            String sufijo = n == 1 ? " (importado)" : " (importado " + n + ")";
            String recortada = base.length() + sufijo.length() > 120 ? base.substring(0, 120 - sufijo.length()).trim() : base;
            String candidato = recortada + sufijo;
            if (!existentes.contains(candidato)) {
                avisos.add("Ya existía un proyecto llamado «" + base + "»; se importó como «" + candidato + "».");
                return candidato;
            }
            n++;
        }
    }

    // ---- Utilidades ----

    private static <T> List<T> erickDelgadoLista(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static boolean erickDelgadoPositivo(double v) {
        return Double.isFinite(v) && v > 0;
    }

    private static boolean erickDelgadoPorcentaje(double v) {
        return Double.isFinite(v) && v >= 0 && v <= 100;
    }

    private static byte[] erickDelgadoLeerLimitado(InputStream in, long maximo) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maximo) throw new IllegalArgumentException("El contenido del archivo supera el tamaño permitido.");
            salida.write(buffer, 0, n);
        }
        return salida.toByteArray();
    }

    static String erickDelgadoSha256(byte[] datos) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(datos);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.");
        }
    }
}
