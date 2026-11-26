package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.model.Elemento;
import com.concretolab.service.ExportadorEtabs;
import com.concretolab.service.ExportadorIfc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ExportacionIfcEtabsTest {
    private static Elemento el(String tipo, String nombre, double x, double y, double z, double l, double a, double h) {
        var e = new Elemento();
        e.setProyectoId(1L); e.setTipo(tipo); e.setNombre(nombre);
        e.setX(x); e.setY(y); e.setZ(z); e.setLargo(l); e.setAncho(a); e.setAltura(h);
        return e;
    }

    private static List<Elemento> modelo() {
        var lista = new ArrayList<Elemento>();
        double[][] cs = {{0, 0}, {5, 0}, {5, 4}, {0, 4}};
        for (int i = 0; i < 4; i++) lista.add(el("ZAPATA", "Z" + i, cs[i][0], cs[i][1], -0.25, 1.2, 1.2, 0.5));
        for (int piso = 1; piso <= 2; piso++) {
            double techo = piso * 3.0;
            for (int i = 0; i < 4; i++) lista.add(el("COLUMNA", "C" + piso + i, cs[i][0], cs[i][1], techo - 1.5, 0.4, 0.4, 3.0));
            lista.add(el("VIGA", "VX" + piso + "a", 2.5, 0, techo - 0.25, 4.6, 0.3, 0.5));
            lista.add(el("VIGA", "VX" + piso + "b", 2.5, 4, techo - 0.25, 4.6, 0.3, 0.5));
            lista.add(el("VIGA", "VY" + piso + "a", 0, 2, techo - 0.25, 0.3, 3.6, 0.5));
            lista.add(el("VIGA", "VY" + piso + "b", 5, 2, techo - 0.25, 0.3, 3.6, 0.5));
            lista.add(el("LOSA", "L" + piso, 2.5, 2, techo - 0.1, 5.4, 4.4, 0.2));
            lista.add(el("MURO", "M" + piso, 2.5, 0, techo - 1.5, 4.6, 0.15, 3.0));
        }
        return lista;
    }

    @Test
    void ifcTieneEstructuraValidaYReferenciasResueltas() {
        String ifc = ExportadorIfc.erickDelgadoGenerar("Edificio de prueba ñandú", modelo());
        assertThat(ifc).startsWith("ISO-10303-21;").contains("FILE_SCHEMA(('IFC2X3'));").endsWith("END-ISO-10303-21;\n");
        assertThat(ifc).contains("IFCPROJECT(", "IFCBUILDINGSTOREY(", "IFCRELCONTAINEDINSPATIALSTRUCTURE(");
        assertThat(count(ifc, "IFCCOLUMN(")).isEqualTo(8);
        assertThat(count(ifc, "IFCBEAM(")).isEqualTo(8);
        assertThat(count(ifc, "IFCSLAB(")).isEqualTo(2);
        assertThat(count(ifc, "IFCWALLSTANDARDCASE(")).isEqualTo(2);
        assertThat(count(ifc, "IFCFOOTING(")).isEqualTo(4);
        assertThat(ifc).contains("\\X2\\00F1\\X0\\");
        var definidos = new HashSet<String>();
        var m = Pattern.compile("(?m)^#(\\d+)=").matcher(ifc);
        while (m.find()) assertThat(definidos.add(m.group(1))).as("duplicado #" + m.group(1)).isTrue();
        var refs = Pattern.compile("#(\\d+)").matcher(ifc.substring(ifc.indexOf("DATA;")));
        while (refs.find()) assertThat(definidos).as("referencia #" + refs.group(1)).contains(refs.group(1));
        var guids = Pattern.compile("IFC[A-Z0-9]+\\('([^']{22})'").matcher(ifc);
        int cuantos = 0;
        while (guids.find()) { cuantos++; assertThat(guids.group(1)).matches("[0-9A-Za-z_$]{22}"); }
        assertThat(cuantos).isGreaterThan(20);
        for (String linea : ifc.split("\n")) {
            if (linea.startsWith("#")) assertThat(linea.chars().filter(c -> c == '(').count()).isEqualTo(linea.chars().filter(c -> c == ')').count());
        }
        assertThat(ifc).contains("IFCRECTANGLEPROFILEDEF(.AREA.,'C10',").contains("0.400000,0.400000)")
                .contains("IFCEXTRUDEDAREASOLID(").contains(",3.000000)");
    }

    @Test
    void ifcEsReproducibleEnSusGuidYRechazaProyectosVacios() {
        String a = ExportadorIfc.erickDelgadoGenerar("P", modelo());
        String b = ExportadorIfc.erickDelgadoGenerar("P", modelo());
        var g = Pattern.compile("IFCCOLUMN\\('([^']+)'");
        var ma = g.matcher(a); var mb = g.matcher(b);
        while (ma.find()) { assertThat(mb.find()).isTrue(); assertThat(mb.group(1)).isEqualTo(ma.group(1)); }
        assertThatThrownBy(() -> ExportadorIfc.erickDelgadoGenerar("P", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etabsDefinePisosSeccionesApoyosYAvisaDeLoNoExportado() {
        var r = ExportadorEtabs.erickDelgadoGenerar("P", 21, modelo());
        String t = r.texto();
        assertThat(t).contains("UNITS  \"KN\"  \"M\"  \"C\"")
                .contains("STORY  \"PISO2\"  HEIGHT 3  MASTERSTORY \"Yes\"")
                .contains("STORY  \"PISO1\"  HEIGHT 3")
                .contains("STORY  \"BASE\"  ELEV 0")
                .contains("FRAMESECTION  \"COL40X40\"").contains("FRAMESECTION  \"VIG30X50\"")
                .contains("SHELLPROP  \"LOSA20\"").contains("SHELLPROP  \"MURO15\"");
        assertThat(count(t, "  LINE  \"C")).isEqualTo(8);
        assertThat(count(t, "  LINE  \"B")).isEqualTo(8);
        assertThat(count(t, "  AREA  \"F")).isEqualTo(2);
        assertThat(count(t, "  AREA  \"W")).isEqualTo(2);
        assertThat(count(t, "RESTRAINT \"UX UY UZ RX RY RZ\"")).isEqualTo(4);
        assertThat(r.avisos()).anyMatch(a -> a.contains("4 zapata(s) no se exportan"))
                .anyMatch(a -> a.contains("experimental"));
        assertThat(r.avisos()).noneMatch(a -> a.contains("no termina sobre una columna"));
    }

    @Test
    void etabsValidaEntradas() {
        assertThatThrownBy(() -> ExportadorEtabs.erickDelgadoGenerar("P", 0, modelo())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExportadorEtabs.erickDelgadoGenerar("P", 21, List.of())).isInstanceOf(IllegalArgumentException.class);
        var soloZapata = List.of(el("ZAPATA", "Z", 0, 0, -0.25, 1, 1, 0.5));
        assertThatThrownBy(() -> ExportadorEtabs.erickDelgadoGenerar("P", 21, soloZapata)).isInstanceOf(IllegalArgumentException.class);
    }

    private static int count(String texto, String parte) {
        int n = 0;
        for (int i = texto.indexOf(parte); i >= 0; i = texto.indexOf(parte, i + 1)) n++;
        return n;
    }
}
