package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ExportadorIfc {
    private ExportadorIfc() { }

    private static final String ERICK_DELGADO_BASE64 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$";

    static String erickDelgadoGuid(String semilla) {
        UUID u = UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
        byte[] b = ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
        BigInteger n = new BigInteger(1, b);
        var sb = new StringBuilder();
        sb.append(ERICK_DELGADO_BASE64.charAt(n.shiftRight(126).intValue() & 0x3));
        for (int i = 20; i >= 0; i--) sb.append(ERICK_DELGADO_BASE64.charAt(n.shiftRight(i * 6).intValue() & 0x3F));
        return sb.toString();
    }

    static String erickDelgadoTexto(String s) {
        if (s == null) return "$";
        var sb = new StringBuilder("'");
        for (char c : s.toCharArray()) {
            if (c == '\'') sb.append("''");
            else if (c == '\\') sb.append("\\\\");
            else if (c < 128 && c >= 32) sb.append(c);
            else sb.append(String.format("\\X2\\%04X\\X0\\", (int) c));
        }
        return sb.append('\'').toString();
    }

    private static String r(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v);
        return s.contains(".") ? s : s + ".";
    }

    public static String erickDelgadoGenerar(String nombreProyecto, List<Elemento> elementos) {
        if (elementos == null || elementos.isEmpty()) {
            throw new IllegalArgumentException("El proyecto no tiene elementos para exportar.");
        }
        var d = new ArrayList<String>();
        int[] n = {0};
        java.util.function.Function<String, Integer> linea = s -> {
            d.add("#" + (++n[0]) + "=" + s + ";");
            return n[0];
        };
        String nombre = nombreProyecto == null || nombreProyecto.isBlank() ? "Proyecto ConcretoLab" : nombreProyecto;
        int persona = linea.apply("IFCPERSON($," + erickDelgadoTexto("ConcretoLab") + ",$,$,$,$,$,$)");
        int org = linea.apply("IFCORGANIZATION($," + erickDelgadoTexto("ConcretoLab") + ",$,$,$)");
        int po = linea.apply("IFCPERSONANDORGANIZATION(#" + persona + ",#" + org + ",$)");
        int app = linea.apply("IFCAPPLICATION(#" + org + "," + erickDelgadoTexto("0.1") + "," + erickDelgadoTexto("ConcretoLab Desktop") + "," + erickDelgadoTexto("ConcretoLab") + ")");
        long ahora = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        int dueno = linea.apply("IFCOWNERHISTORY(#" + po + ",#" + app + ",$,.ADDED.,$,$,$," + ahora + ")");
        int origen = linea.apply("IFCCARTESIANPOINT((0.,0.,0.))");
        int dirZ = linea.apply("IFCDIRECTION((0.,0.,1.))");
        int dirX = linea.apply("IFCDIRECTION((1.,0.,0.))");
        int eje = linea.apply("IFCAXIS2PLACEMENT3D(#" + origen + ",#" + dirZ + ",#" + dirX + ")");
        int norte = linea.apply("IFCDIRECTION((0.,1.))");
        int contexto = linea.apply("IFCGEOMETRICREPRESENTATIONCONTEXT($," + erickDelgadoTexto("Model") + ",3,1.E-05,#" + eje + ",#" + norte + ")");
        int cuerpo = linea.apply("IFCGEOMETRICREPRESENTATIONSUBCONTEXT(" + erickDelgadoTexto("Body") + "," + erickDelgadoTexto("Model") + ",*,*,*,*,#" + contexto + ",$,.MODEL_VIEW.,$)");
        int uLong = linea.apply("IFCSIUNIT(*,.LENGTHUNIT.,$,.METRE.)");
        int uArea = linea.apply("IFCSIUNIT(*,.AREAUNIT.,$,.SQUARE_METRE.)");
        int uVol = linea.apply("IFCSIUNIT(*,.VOLUMEUNIT.,$,.CUBIC_METRE.)");
        int uAng = linea.apply("IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.)");
        int unidades = linea.apply("IFCUNITASSIGNMENT((#" + uLong + ",#" + uArea + ",#" + uVol + ",#" + uAng + "))");
        int proyecto = linea.apply("IFCPROJECT('" + erickDelgadoGuid("proyecto|" + nombre) + "',#" + dueno + "," + erickDelgadoTexto(nombre) + ",$,$,$,$,(#" + contexto + "),#" + unidades + ")");
        int pSitio = linea.apply("IFCLOCALPLACEMENT($,#" + eje + ")");
        int sitio = linea.apply("IFCSITE('" + erickDelgadoGuid("sitio|" + nombre) + "',#" + dueno + "," + erickDelgadoTexto("Sitio") + ",$,$,#" + pSitio + ",$,$,.ELEMENT.,$,$,$,$,$)");
        int pEdificio = linea.apply("IFCLOCALPLACEMENT(#" + pSitio + ",#" + eje + ")");
        int edificio = linea.apply("IFCBUILDING('" + erickDelgadoGuid("edificio|" + nombre) + "',#" + dueno + "," + erickDelgadoTexto("Edificio") + ",$,$,#" + pEdificio + ",$,$,.ELEMENT.,$,$,$)");
        int pNivel = linea.apply("IFCLOCALPLACEMENT(#" + pEdificio + ",#" + eje + ")");
        int nivel = linea.apply("IFCBUILDINGSTOREY('" + erickDelgadoGuid("nivel|" + nombre) + "',#" + dueno + "," + erickDelgadoTexto("Nivel 0") + ",$,$,#" + pNivel + ",$,$,.ELEMENT.,0.)");
        linea.apply("IFCRELAGGREGATES('" + erickDelgadoGuid("ra1|" + nombre) + "',#" + dueno + ",$,$,#" + proyecto + ",(#" + sitio + "))");
        linea.apply("IFCRELAGGREGATES('" + erickDelgadoGuid("ra2|" + nombre) + "',#" + dueno + ",$,$,#" + sitio + ",(#" + edificio + "))");
        linea.apply("IFCRELAGGREGATES('" + erickDelgadoGuid("ra3|" + nombre) + "',#" + dueno + ",$,$,#" + edificio + ",(#" + nivel + "))");
        int material = linea.apply("IFCMATERIAL(" + erickDelgadoTexto("Concreto") + ")");

        var ids = new ArrayList<Integer>();
        int i = 0;
        for (Elemento e : elementos) {
            i++;
            double zBase = e.getZ() - e.getAltura() / 2;
            int pt2 = linea.apply("IFCCARTESIANPOINT((0.,0.))");
            int eje2 = linea.apply("IFCAXIS2PLACEMENT2D(#" + pt2 + ",$)");
            int perfil = linea.apply("IFCRECTANGLEPROFILEDEF(.AREA.," + erickDelgadoTexto(e.getNombre()) + ",#" + eje2 + "," + r(e.getLargo()) + "," + r(e.getAncho()) + ")");
            int solido = linea.apply("IFCEXTRUDEDAREASOLID(#" + perfil + ",#" + eje + ",#" + dirZ + "," + r(e.getAltura()) + ")");
            int rep = linea.apply("IFCSHAPEREPRESENTATION(#" + cuerpo + "," + erickDelgadoTexto("Body") + "," + erickDelgadoTexto("SweptSolid") + ",(#" + solido + "))");
            int forma = linea.apply("IFCPRODUCTDEFINITIONSHAPE($,$,(#" + rep + "))");
            int pt3 = linea.apply("IFCCARTESIANPOINT((" + r(e.getX()) + "," + r(e.getY()) + "," + r(zBase) + "))");
            int eje3 = linea.apply("IFCAXIS2PLACEMENT3D(#" + pt3 + ",#" + dirZ + ",#" + dirX + ")");
            int colocacion = linea.apply("IFCLOCALPLACEMENT(#" + pNivel + ",#" + eje3 + ")");
            String comunes = "'" + erickDelgadoGuid("el|" + nombre + "|" + i + "|" + e.getNombre()) + "',#" + dueno + "," + erickDelgadoTexto(e.getNombre()) + ","
                    + erickDelgadoTexto(e.getTipo()) + ",$,#" + colocacion + ",#" + forma + ",$";
            String entidad = switch (e.getTipo()) {
                case "COLUMNA" -> "IFCCOLUMN(" + comunes + ")";
                case "VIGA" -> "IFCBEAM(" + comunes + ")";
                case "MURO" -> "IFCWALLSTANDARDCASE(" + comunes + ")";
                case "LOSA" -> "IFCSLAB(" + comunes + ",.FLOOR.)";
                case "ZAPATA" -> "IFCFOOTING(" + comunes + ",.PAD_FOOTING.)";
                default -> "IFCBUILDINGELEMENTPROXY(" + comunes + ",.NOTDEFINED.)";
            };
            ids.add(linea.apply(entidad));
        }
        String lista = ids.stream().map(x -> "#" + x).reduce((a, b) -> a + "," + b).orElseThrow();
        linea.apply("IFCRELCONTAINEDINSPATIALSTRUCTURE('" + erickDelgadoGuid("rc|" + nombre) + "',#" + dueno + ",$,$,(" + lista + "),#" + nivel + ")");
        linea.apply("IFCRELASSOCIATESMATERIAL('" + erickDelgadoGuid("rm|" + nombre) + "',#" + dueno + ",$,$,(" + lista + "),#" + material + ")");

        var sb = new StringBuilder();
        sb.append("ISO-10303-21;\nHEADER;\n");
        sb.append("FILE_DESCRIPTION(('ViewDefinition [CoordinationView]'),'2;1');\n");
        sb.append("FILE_NAME(").append(erickDelgadoTexto(nombre + ".ifc")).append(",'")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("',('ConcretoLab'),('ConcretoLab Desktop'),'ConcretoLab','ConcretoLab Desktop','');\n");
        sb.append("FILE_SCHEMA(('IFC2X3'));\nENDSEC;\nDATA;\n");
        d.forEach(l -> sb.append(l).append('\n'));
        sb.append("ENDSEC;\nEND-ISO-10303-21;\n");
        return sb.toString();
    }
}
