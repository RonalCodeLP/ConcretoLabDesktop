package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.model.CuadriculaProyecto;
import com.concretolab.model.Elemento;
import com.concretolab.model.Ensayo;
import com.concretolab.model.ExpedienteEstructural;
import com.concretolab.model.Mezcla;
import com.concretolab.model.Proyecto;
import com.concretolab.model.VinculoGeometrico;
import com.concretolab.repository.CuadriculaProyectoRepository;
import com.concretolab.repository.EnsayoRepository;
import com.concretolab.repository.ExpedienteRepository;
import com.concretolab.repository.MezclaRepository;
import com.concretolab.repository.VinculoGeometricoRepository;
import com.concretolab.service.IntercambioProyectoService;
import com.concretolab.service.YefriLermaProyectoService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Exportación e importación de proyectos, siempre sobre una base SQLite temporal (nunca la real). */
@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IntercambioProyectoTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("intercambio-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "false");
    }

    @Autowired YefriLermaProyectoService proyectos;
    @Autowired IntercambioProyectoService intercambio;
    @Autowired VinculoGeometricoRepository vinculos;
    @Autowired CuadriculaProyectoRepository cuadriculas;
    @Autowired ExpedienteRepository expedientes;
    @Autowired MezclaRepository mezclas;
    @Autowired EnsayoRepository ensayos;

    @TempDir Path carpeta;

    private Long origen;
    private String nombreOrigen;

    @BeforeEach
    void prepararOrigen() {
        nombreOrigen = "Origen " + UUID.randomUUID();
        Proyecto p = proyectos.crearProyecto(nombreOrigen, "Lima, Perú");
        origen = p.getId();
        proyectos.crearPlantilla(origen, new YefriLermaProyectoService.Plantilla(5, 4, 1.5, 1.5, .5, .4, 3, .3, .5));
        List<Elemento> els = proyectos.elementos(origen);
        List<Elemento> columnas = els.stream().filter(e -> e.getTipo().equals("COLUMNA")).toList();
        List<Elemento> vigas = els.stream().filter(e -> e.getTipo().equals("VIGA")).toList();
        enlazar(columnas.get(0), vigas.get(0));
        enlazar(columnas.get(0), vigas.get(2));
        enlazar(columnas.get(3), vigas.get(1));

        CuadriculaProyecto c = new CuadriculaProyecto();
        c.setProyectoId(origen);
        c.setSeparacionesX("5,5");
        c.setSeparacionesY("4");
        c.setNiveles("3,3");
        c.setOrigenX(1.5);
        c.setOrigenY(-2);
        cuadriculas.save(c);

        ExpedienteEstructural x = new ExpedienteEstructural();
        x.setProyectoId(origen);
        x.setUbicacion("Lima");
        x.setUso("Vivienda");
        x.setProfesional("Ing. Prueba");
        x.setNorma("E.030");
        x.setObservaciones("Observación de prueba");
        expedientes.save(x);

        for (int i = 1; i <= 2; i++) {
            Mezcla m = new Mezcla();
            m.setProyectoId(origen);
            m.setNombre("Mezcla " + i);
            m.setReferencia("REF-" + i);
            m.setMaterialFino("Arena");
            m.setMaterialGrueso("Piedra");
            m.setResistencia(21 + i);
            m.setCemento(350);
            m.setAgua(190);
            m.setFinoSss(800);
            m.setGruesoSss(1000);
            m.setAbsorcionFino(1.5);
            m.setHumedadFino(3);
            m.setAbsorcionGrueso(1);
            m.setHumedadGrueso(0.5);
            m = mezclas.save(m);
            for (int j = 1; j <= i; j++) {
                Ensayo e = new Ensayo();
                e.setMezclaId(m.getId());
                e.setFecha("2026-01-0" + j);
                e.setIdentificacion("P-" + i + "-" + j);
                e.setEdadDias(7 * j);
                e.setResistenciaMpa(20.5 + j);
                e.setAsentamientoMm(80);
                ensayos.save(e);
            }
        }
    }

    private void enlazar(Elemento columna, Elemento viga) {
        VinculoGeometrico v = new VinculoGeometrico();
        v.setColumnaId(columna.getId());
        v.setVigaId(viga.getId());
        vinculos.save(v);
    }

    // ---- utilidades de comparación ----

    private record Forma(String tipo, String nombre, double largo, double ancho, double altura, double x, double y, double z) { }

    private List<Forma> formas(Long id) {
        return proyectos.elementos(id).stream()
                .map(e -> new Forma(e.getTipo(), e.getNombre(), e.getLargo(), e.getAncho(), e.getAltura(), e.getX(), e.getY(), e.getZ())).toList();
    }

    private Set<String> pares(Long id) {
        Map<Long, String> nombres = new LinkedHashMap<>();
        proyectos.elementos(id).forEach(e -> nombres.put(e.getId(), e.getNombre()));
        Set<String> r = new HashSet<>();
        for (VinculoGeometrico v : vinculos.findAll()) {
            if (nombres.containsKey(v.getColumnaId()) && nombres.containsKey(v.getVigaId())) {
                r.add(nombres.get(v.getColumnaId()) + "->" + nombres.get(v.getVigaId()));
            }
        }
        return r;
    }

    private void verificarCopia(IntercambioProyectoService.Resultado r) {
        Long id = r.proyecto().getId();
        assertThat(id).isNotEqualTo(origen);
        assertThat(r.proyecto().getUbicacion()).isEqualTo("Lima, Perú");
        assertThat(r.elementos()).isEqualTo(12);
        assertThat(r.mezclas()).isEqualTo(2);
        assertThat(r.ensayos()).isEqualTo(3);
        assertThat(formas(id)).isEqualTo(formas(origen));
        assertThat(pares(id)).hasSize(3).isEqualTo(pares(origen));
        CuadriculaProyecto c = cuadriculas.findById(id).orElseThrow();
        assertThat(c.getSeparacionesX()).isEqualTo("5,5");
        assertThat(c.getNiveles()).isEqualTo("3,3");
        assertThat(c.getOrigenX()).isEqualTo(1.5);
        assertThat(c.getOrigenY()).isEqualTo(-2);
        ExpedienteEstructural x = expedientes.findById(id).orElseThrow();
        assertThat(x.getUso()).isEqualTo("Vivienda");
        assertThat(x.getProfesional()).isEqualTo("Ing. Prueba");
        List<Mezcla> ms = mezclas.findByProyectoIdOrderByIdDesc(id);
        assertThat(ms).hasSize(2);
        assertThat(ms).extracting(Mezcla::getNombre).containsExactly("Mezcla 2", "Mezcla 1");
        int total = 0;
        for (Mezcla m : ms) total += ensayos.findByMezclaIdOrderByIdDesc(m.getId()).size();
        assertThat(total).isEqualTo(3);
        assertThat(ms.get(0).getResistencia()).isEqualTo(23);
    }

    // ---- casos ----

    @Test
    void exportaEImportaSinCifrarConservaTodo() {
        Path archivo = carpeta.resolve("sin-cifrar.concretolab");
        assertThat(intercambio.exportar(origen, archivo, null)).isEqualTo(archivo);
        assertThat(intercambio.estaCifrado(archivo)).isFalse();
        var r = intercambio.importar(archivo, null);
        assertThat(r.proyecto().getNombre()).isEqualTo(nombreOrigen + " (importado)");
        verificarCopia(r);
    }

    @Test
    void exportaEImportaConContrasena() {
        Path archivo = carpeta.resolve("cifrado.concretolab");
        intercambio.exportar(origen, archivo, "secreta123".toCharArray());
        assertThat(intercambio.estaCifrado(archivo)).isTrue();
        assertThatThrownBy(() -> intercambio.importar(archivo, null)).isInstanceOf(IllegalArgumentException.class);
        verificarCopia(intercambio.importar(archivo, "secreta123".toCharArray()));
    }

    @Test
    void contrasenaIncorrectaNoImportaNada() {
        Path archivo = carpeta.resolve("cifrado2.concretolab");
        intercambio.exportar(origen, archivo, "buena".toCharArray());
        int antes = proyectos.proyectos().size();
        assertThatThrownBy(() -> intercambio.importar(archivo, "mala".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contraseña incorrecta o archivo dañado.");
        assertThat(proyectos.proyectos()).hasSize(antes);
    }

    @Test
    void nombreDuplicadoAgregaSufijoImportado() {
        Path archivo = carpeta.resolve("dup.concretolab");
        intercambio.exportar(origen, archivo, null);
        var uno = intercambio.importar(archivo, null);
        var dos = intercambio.importar(archivo, null);
        var tres = intercambio.importar(archivo, null);
        assertThat(uno.proyecto().getNombre()).isEqualTo(nombreOrigen + " (importado)");
        assertThat(dos.proyecto().getNombre()).isEqualTo(nombreOrigen + " (importado 2)");
        assertThat(tres.proyecto().getNombre()).isEqualTo(nombreOrigen + " (importado 3)");
        assertThat(uno.avisos()).isNotEmpty();
    }

    @Test
    void archivoManipuladoSeRechaza() throws Exception {
        Path archivo = carpeta.resolve("original.concretolab");
        intercambio.exportar(origen, archivo, null);
        Map<String, byte[]> entradas = leerZip(Files.readAllBytes(archivo));
        byte[] datos = entradas.get("datos.json");
        for (int i = 0; i < datos.length; i++) {
            if (datos[i] == 'Z') { // un caracter de nombre o tipo: el JSON sigue siendo valido
                datos[i] = 'Y';
                break;
            }
        }
        Path manipulado = carpeta.resolve("manipulado.concretolab");
        Files.write(manipulado, escribirZip(entradas));
        int antes = proyectos.proyectos().size();
        assertThatThrownBy(() -> intercambio.importar(manipulado, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El archivo fue modificado o está dañado.");
        assertThat(proyectos.proyectos()).hasSize(antes);
    }

    @Test
    void zipConEntradaFueraDeRutaSeRechaza() throws Exception {
        Path archivo = carpeta.resolve("ok.concretolab");
        intercambio.exportar(origen, archivo, null);
        Map<String, byte[]> entradas = leerZip(Files.readAllBytes(archivo));
        entradas.put("../x", "malicioso".getBytes(StandardCharsets.UTF_8));
        Path malo = carpeta.resolve("ruta.concretolab");
        Files.write(malo, escribirZip(entradas));
        int antes = proyectos.proyectos().size();
        assertThatThrownBy(() -> intercambio.importar(malo, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(proyectos.proyectos()).hasSize(antes);
    }

    @Test
    void elementoDeTipoInvalidoNoGuardaNada() throws Exception {
        String json = """
                {"proyecto":{"nombre":"Con tipo malo","ubicacion":""},
                 "elementos":[{"tipo":"ZAPATA","nombre":"Z1","largo":1,"ancho":1,"altura":1,"x":0,"y":0,"z":0},
                              {"tipo":"PARED","nombre":"P1","largo":1,"ancho":1,"altura":1,"x":0,"y":0,"z":0}],
                 "vinculos":[],"mezclas":[]}
                """;
        Path archivo = carpeta.resolve("tipo-malo.concretolab");
        Files.write(archivo, zipConDatos(json, 1));
        int antes = proyectos.proyectos().size();
        assertThatThrownBy(() -> intercambio.importar(archivo, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo no válido");
        assertThat(proyectos.proyectos()).hasSize(antes);
    }

    @Test
    void dimensionNegativaNoGuardaNada() throws Exception {
        String json = """
                {"proyecto":{"nombre":"Con dimension mala"},
                 "elementos":[{"tipo":"ZAPATA","nombre":"Z1","largo":-1,"ancho":1,"altura":1,"x":0,"y":0,"z":0}]}
                """;
        Path archivo = carpeta.resolve("dim-mala.concretolab");
        Files.write(archivo, zipConDatos(json, 1));
        int antes = proyectos.proyectos().size();
        assertThatThrownBy(() -> intercambio.importar(archivo, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(proyectos.proyectos()).hasSize(antes);
    }

    @Test
    void versionDeFormatoMasNuevaSeRechazaConMensajeClaro() throws Exception {
        String json = "{\"proyecto\":{\"nombre\":\"Futuro\"},\"elementos\":[]}";
        Path archivo = carpeta.resolve("futuro.concretolab");
        Files.write(archivo, zipConDatos(json, 2));
        assertThatThrownBy(() -> intercambio.importar(archivo, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versión más nueva");
    }

    @Test
    void exportarProyectoInexistenteFalla() {
        Path archivo = carpeta.resolve("nada.concretolab");
        assertThatThrownBy(() -> intercambio.exportar(-12345L, archivo, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(archivo)).isFalse();
    }

    @Test
    void exportarNoDejaTemporalesEnLaCarpeta() throws Exception {
        Path destino = carpeta.resolve("limpio");
        Path archivo = destino.resolve("p.concretolab");
        intercambio.exportar(origen, archivo, null);
        try (var lista = Files.list(destino)) {
            assertThat(lista.map(p -> p.getFileName().toString()).toList()).containsExactly("p.concretolab");
        }
    }

    // ---- ayudas de zip ----

    private static Map<String, byte[]> leerZip(byte[] zip) throws Exception {
        Map<String, byte[]> r = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) r.put(e.getName(), in.readAllBytes());
        }
        return r;
    }

    private static byte[] escribirZip(Map<String, byte[]> entradas) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (var e : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    /** Zip valido (hash correcto) con el datos.json dado y la version de formato indicada. */
    private static byte[] zipConDatos(String datosJson, int version) throws Exception {
        byte[] datos = datosJson.getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(datos)) hex.append(String.format("%02x", b));
        String manifiesto = "{\"formato\":\"concretolab-proyecto\",\"version\":" + version
                + ",\"aplicacion\":\"prueba\",\"fecha\":\"2026-01-01T00:00:00Z\",\"sha256\":\"" + hex + "\",\"conteos\":{}}";
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("manifiesto.json", manifiesto.getBytes(StandardCharsets.UTF_8));
        entradas.put("datos.json", datos);
        return escribirZip(entradas);
    }
}
