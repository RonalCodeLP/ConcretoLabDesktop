package com.concretolab;

import static org.junit.jupiter.api.Assertions.*;

import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaPlanoDxf;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Robustez del lector DXF: archivos dañados, codificación, unidades y ubicación del conversor DWG. */
class PlanoDxfRobustezTest {

    private final FernandoHuancaPlanoDxf lector = new FernandoHuancaPlanoDxf();

    private static Elemento el(String tipo, double largo, double ancho, double alto, double x, double y, double z) {
        Elemento e = new Elemento(); e.setTipo(tipo); e.setNombre(tipo); e.setLargo(largo); e.setAncho(ancho); e.setAltura(alto); e.setX(x); e.setY(y); e.setZ(z); return e;
    }

    private static String dxfLineaSimple(String version, String capa) {
        return "0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\n" + version + "\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n"
                + "0\nLINE\n8\n" + capa + "\n10\n0.0\n20\n0.0\n30\n0.0\n11\n5.0\n21\n0.0\n31\n0.0\n0\nENDSEC\n0\nEOF\n";
    }

    @Test void archivosDanadosFallanConMensajeClaroYNuncaConOtraExcepcion(@TempDir Path dir) throws Exception {
        Path vacio = Files.writeString(dir.resolve("vacio.dxf"), "");
        Path truncado = Files.writeString(dir.resolve("truncado.dxf"), "0\nSECTION\n2\nENTITIES\n0\nLINE\n8");
        Path basura = Files.writeString(dir.resolve("basura.dxf"), "esto no es un dxf\ncualquier cosa\n");
        Path binario = Files.write(dir.resolve("binario.dxf"), "AutoCAD Binary DXF\r\n\u001a\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1));
        Path sinEof = Files.writeString(dir.resolve("sineof.dxf"), "0\nSECTION\n2\nENTITIES\n0\nLINE\n8\nX\n10\n0\n20\n0\n11\n1\n21\n1\n0\nENDSEC\n");
        for (Path p : List.of(vacio, truncado, basura, binario, sinEof))
            assertThrows(IllegalArgumentException.class, () -> lector.fernandoHuancaLeer(p), p.getFileName().toString());
    }

    @Test void rechazaArchivosDeMasDeDiezMegabytes(@TempDir Path dir) throws Exception {
        Path grande = dir.resolve("grande.dxf");
        try (var out = Files.newOutputStream(grande)) { out.write(new byte[10_000_001]); }
        assertThrows(IllegalArgumentException.class, () -> lector.fernandoHuancaLeer(grande));
    }

    @Test void respetaTildesYEnesSegunLaVersionDelDxf(@TempDir Path dir) throws Exception {
        // AutoCAD 2007 en adelante guarda en UTF-8; las versiones anteriores, en Windows-1252.
        Path utf8 = dir.resolve("nuevo.dxf");
        Files.write(utf8, dxfLineaSimple("AC1027", "COLUMNAS_ÁREA_Ñ").getBytes(StandardCharsets.UTF_8));
        Path antiguo = dir.resolve("antiguo.dxf");
        Files.write(antiguo, dxfLineaSimple("AC1015", "COLUMNAS_ÁREA_Ñ").getBytes(Charset.forName("windows-1252")));
        for (Path p : List.of(utf8, antiguo)) {
            var plano = lector.fernandoHuancaLeer(p);
            assertTrue(plano.entidades().stream().anyMatch(e -> "COLUMNAS_ÁREA_Ñ".equals(e.capa())), "capa corrupta en " + p.getFileName());
        }
    }

    @Test void convierteUnidadesDeAutocadAMetros() {
        assertEquals(1.0, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("6"));
        assertEquals(0.001, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("4"));
        assertEquals(0.01, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("5"));
        assertEquals(0.0254, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("1"), 1e-12);
        assertEquals(0.3048, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("2"), 1e-12);
        assertTrue(Double.isNaN(FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("0")));   // sin unidad declarada
        assertTrue(Double.isNaN(FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("")));
        assertTrue(Double.isNaN(FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad(null)));
        assertTrue(Double.isNaN(FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("99")));
    }

    @Test void unModeloEnMilimetrosSeEscalaAMetrosEnVezDeQuedarMilVecesMayor() {
        var enMm = List.of(el("COLUMNA", 400, 400, 3000, 5000, 4000, 1500));
        var enM = FernandoHuancaPlanoDxf.fernandoHuancaEscalar(enMm, FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad("4"));
        assertEquals(0.4, enM.get(0).getLargo(), 1e-12);
        assertEquals(3.0, enM.get(0).getAltura(), 1e-12);
        assertEquals(5.0, enM.get(0).getX(), 1e-12);
        assertEquals(1.5, enM.get(0).getZ(), 1e-12);
        assertEquals(400, enMm.get(0).getLargo(), 0.0); // el original no se modifica
    }

    @Test void exportarEImportarConservaElModeloExacto(@TempDir Path dir) throws Exception {
        var original = List.of(el("ZAPATA", 1.5, 1.5, 0.5, 0, 0, 0.25), el("COLUMNA", 0.4, 0.4, 3, 0, 0, 2.0), el("VIGA", 4.6, 0.3, 0.5, 2.5, 0, 3.25));
        Path archivo = dir.resolve("modelo.dxf");
        lector.fernandoHuancaExportar(archivo, original);
        var leido = lector.fernandoHuancaLeer(archivo).modelo3d();
        assertEquals(3, leido.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(original.get(i).getTipo(), leido.get(i).getTipo());
            assertEquals(original.get(i).getLargo(), leido.get(i).getLargo(), 1e-9);
            assertEquals(original.get(i).getAncho(), leido.get(i).getAncho(), 1e-9);
            assertEquals(original.get(i).getAltura(), leido.get(i).getAltura(), 1e-9);
            assertEquals(original.get(i).getX(), leido.get(i).getX(), 1e-9);
            assertEquals(original.get(i).getZ(), leido.get(i).getZ(), 1e-9);
        }
    }

    @Test void ubicaElConversorDwgSinDependerDeLaCarpetaDeCompilacion(@TempDir Path dir) throws Exception {
        Path tools = Files.createDirectories(dir.resolve("libredwg-0.99"));
        Path falso = Files.writeString(tools.resolve("dwgread.exe"), "binario falso");
        String anterior = System.getProperty("concretolab.tools");
        System.setProperty("concretolab.tools", dir.toString());
        try {
            assertEquals(falso, FernandoHuancaPlanoDxf.fernandoHuancaLocalizarDwgread().orElseThrow());
        } finally {
            if (anterior == null) System.clearProperty("concretolab.tools"); else System.setProperty("concretolab.tools", anterior);
        }
    }
}
