package com.concretolab.service;

import com.concretolab.model.Proyecto;
import com.concretolab.service.FernandoHuancaAnalisisSismico.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

/** Reporte PDF del análisis sísmico educativo. No es una memoria de cálculo para presentación a entidades. */
public final class ReporteSismo {
    private ReporteSismo() {}
    private static final Font FERNANDO_HUANCA_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16), FERNANDO_HUANCA_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12),
            FERNANDO_HUANCA_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8), FERNANDO_HUANCA_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8), FERNANDO_HUANCA_NOTA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    private static String f(double n, int d) { return String.format(Locale.ROOT, "%." + d + "f", n); }

    private static PdfPTable tabla(String[] cab, List<String[]> filas) {
        PdfPTable t = new PdfPTable(cab.length); t.setWidthPercentage(100); t.setSpacingBefore(4); t.setSpacingAfter(8);
        for (String c : cab) { var cel = new com.lowagie.text.pdf.PdfPCell(new Phrase(c, FERNANDO_HUANCA_CABECERA)); cel.setBackgroundColor(new java.awt.Color(226, 232, 240)); t.addCell(cel); }
        for (String[] fila : filas) for (String v : fila) t.addCell(new Phrase(v, FERNANDO_HUANCA_CELDA));
        return t;
    }

    public static byte[] fernandoHuancaGenerar(Proyecto p, Entrada in, Resultado r) { return fernandoHuancaGenerar(p, in, r, null); }

    /** Igual que la versión sin tiempo-historia, e incluye esa sección si {@code th} no es nulo. */
    public static byte[] fernandoHuancaGenerar(Proyecto p, Entrada in, Resultado r, ResumenTiempoHistoria th) {
        var n = in.norma(); ByteArrayOutputStream out = new ByteArrayOutputStream(); Document d = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(d, out); d.open();
        d.add(new Paragraph("ConcretoLab | Análisis sísmico según E.030 (educativo)", FERNANDO_HUANCA_TITULO));
        d.add(new Paragraph(n.version().etiqueta, FERNANDO_HUANCA_NOTA));
        d.add(new Paragraph("Proyecto: " + p.getNombre() + (p.getUbicacion() == null || p.getUbicacion().isBlank() ? "" : "    Ubicación: " + p.getUbicacion())));

        d.add(new Paragraph("1. Datos y parámetros sísmicos", FERNANDO_HUANCA_SECCION));
        d.add(tabla(new String[]{"Parámetro", "Valor", "Parámetro", "Valor"}, List.of(
                new String[]{"Zona", n.zona().etiqueta + " · Z = " + n.z(), "Categoría", n.categoria().etiqueta + " · U = " + n.u()},
                new String[]{"Suelo", n.suelo().etiqueta + " · S = " + f(n.s(), 3) + (Double.isNaN(n.vs30()) ? (n.suelo().interpola() ? " (sin Vs30: valor más desfavorable)" : "") : " · Vs30 = " + f(n.vs30(), 0) + " m/s"), "Tp / TL (s)", f(n.tp(), 3) + " / " + f(n.tl(), 3)},
                new String[]{"R0 / Ia / Ip", n.r0() + " / " + n.ia() + " / " + n.ip(), "R = R0·Ia·Ip", f(n.r(), 3) + (n.regular() ? " (regular)" : " (irregular)")},
                new String[]{"CT (T = hn/CT)", String.valueOf(n.ct()), "Deriva límite (concreto armado)", f(NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO, 3)},
                new String[]{"f'c (MPa)", f(in.fcMpa(), 1), "Ec (MPa) · E.060", f(r.ecMpa(), 0)},
                new String[]{"Poisson", f(in.poisson(), 2), "Fracción de rigidez EI", f(in.fraccionRigidez(), 2)},
                new String[]{"Espesor de losa (m)", f(in.espesorLosa(), 2), "Peso sísmico total (kN)", f(r.pesoTotal(), 1)})));

        d.add(new Paragraph("2. Pisos y pesos", FERNANDO_HUANCA_SECCION));
        d.add(tabla(new String[]{"Piso", "Cota (m)", "h (m)", "Área losa (m²)", "Peso (kN)", "X cm (m)", "Y cm (m)", "I polar (kN·m²)"},
                r.pisos().stream().map(s -> new String[]{"" + s.numero(), f(s.cota(), 2), f(s.altura(), 2), f(s.area(), 2), f(s.peso(), 1), f(s.xcm(), 2), f(s.ycm(), 2), f(s.inercia(), 0)}).toList()));
        d.add(new Paragraph("Peso sísmico (" + n.art("peso") + "): CM + " + (int) (n.categoria().porcentajeCv * 100) + " % de CV en pisos; 25 % de CV en azotea. Concreto armado: " + (int) FernandoHuancaAnalisisSismico.FERNANDO_HUANCA_PESO_CONCRETO + " kN/m³.", FERNANDO_HUANCA_NOTA));

        d.add(new Paragraph("3. Modos de vibración", FERNANDO_HUANCA_SECCION));
        d.add(tabla(new String[]{"Modo", "T (s)", "f (Hz)", "Masa efectiva X (%)", "Masa efectiva Y (%)"},
                r.modos().stream().map(m -> new String[]{"" + m.numero(), f(m.periodo(), 4), f(1 / m.periodo(), 3), f(m.masaEfectivaX() * 100, 1), f(m.masaEfectivaY() * 100, 1)}).toList()));
        d.add(new Paragraph("Masa efectiva acumulada: X = " + f(r.masaAcumuladaX() * 100, 1) + " %, Y = " + f(r.masaAcumuladaY() * 100, 1) + " %.", FERNANDO_HUANCA_NOTA));

        d.add(new Paragraph("4. Análisis estático (" + n.art("estatico") + ")", FERNANDO_HUANCA_SECCION));
        d.add(new Paragraph("T = hn/CT = " + f(r.estaticoX().periodo(), 3) + " s;  C = " + f(n.cEstatico(r.estaticoX().periodo()), 3) + ";  k = " + f(NormaE030.Parametros.k(r.estaticoX().periodo()), 2)
                + ";  V = " + f(r.estaticoX().cortanteBase(), 1) + " kN (ZUCS/R·P con C/R ≥ 0,11). Incluye excentricidad accidental de 0,05 de la dimensión perpendicular " + (n.fraccionPerpendicular() > 0 ? "y combinación direccional 100 % + 30 % por suma de absolutos (art. 33.3)." : "(versión 2018: sin componente perpendicular).")));
        d.add(tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fernandoHuancaFilas(r, r.estaticoX(), r.estaticoY())));

        d.add(new Paragraph("5. Análisis modal espectral (" + n.art("espectral") + ")", FERNANDO_HUANCA_SECCION));
        d.add(new Paragraph("Sa = Z·U·C·S·g/R; combinación CQC con 5 % de amortiguamiento; la excentricidad accidental (" + n.art("excentricidadEspectral") + ") se suma en valor absoluto a los desplazamientos" + (n.fraccionPerpendicular() > 0 ? "; combinación direccional 100 % + 30 % por raíz de la suma de cuadrados (art. 43)" : "; cada dirección al 100 %, sin componente perpendicular (versión 2018)") + ". Factor de escala por cortante mínimo: X = " + f(r.espectralX().factorEscala(), 3) + ", Y = " + f(r.espectralY().factorEscala(), 3) + "."));
        d.add(tabla(new String[]{"Piso", "F X (kN)", "V X (kN)", "F Y (kN)", "V Y (kN)"}, fernandoHuancaFilas(r, r.espectralX(), r.espectralY())));

        d.add(new Paragraph("6. Desplazamientos y derivas (inelásticos: " + f(n.factorInelastico(), 2) + " × elástico)", FERNANDO_HUANCA_SECCION));
        List<String[]> der = new java.util.ArrayList<>();
        for (int i = 0; i < r.pisos().size(); i++) {
            double max = Math.max(Math.max(r.estaticoX().derivasMaximas()[i], r.estaticoY().derivasMaximas()[i]), Math.max(r.espectralX().derivasMaximas()[i], r.espectralY().derivasMaximas()[i]));
            der.add(new String[]{"" + (i + 1), f(r.estaticoX().desplazamientosCm()[i] * 1000, 1), f(r.estaticoY().desplazamientosCm()[i] * 1000, 1), f(r.espectralX().desplazamientosCm()[i] * 1000, 1), f(r.espectralY().desplazamientosCm()[i] * 1000, 1),
                    f(r.estaticoX().derivasMaximas()[i], 5), f(r.estaticoY().derivasMaximas()[i], 5), f(r.espectralX().derivasMaximas()[i], 5), f(r.espectralY().derivasMaximas()[i], 5), max <= NormaE030.FERNANDO_HUANCA_DERIVA_LIMITE_CONCRETO ? "Cumple" : "No cumple"});
        }
        d.add(tabla(new String[]{"Piso", "Δ est X (mm)", "Δ est Y (mm)", "Δ esp X (mm)", "Δ esp Y (mm)", "Deriva est X", "Deriva est Y", "Deriva esp X", "Deriva esp Y", "Estado"}, der));
        d.add(new Paragraph("Las derivas son el máximo entre el centro de masas y los nudos de cada piso.", FERNANDO_HUANCA_NOTA));

        int sec = 7;
        if (th != null) { fernandoHuancaSeccionTiempoHistoria(d, th, sec++); }
        d.add(new Paragraph(sec++ + ". Advertencias", FERNANDO_HUANCA_SECCION));
        for (String a : r.advertencias()) d.add(new Paragraph("• " + a, FERNANDO_HUANCA_CELDA));

        d.add(new Paragraph(sec + ". Referencias", FERNANDO_HUANCA_SECCION));
        for (String ref : List.of(
                n.version() == NormaE030.Version.V2026
                        ? "RNE, Norma Técnica E.030 «Diseño Sismorresistente», texto 2026 (R.M. N° 183-2026-VIVIENDA). Tablas 1 a 14 y arts. 14, 17, 18, 22 a 26, 28, 31, 33 a 37, 39 a 45, 50 y 51."
                        : "RNE, Norma Técnica E.030 «Diseño Sismorresistente», versión 2018 (R.M. N° 355-2018-VIVIENDA, que modifica la aprobada por D.S. N° 003-2016-VIVIENDA). Tablas 1, 3, 4, 5, 7 a 11 y arts. 4.3 a 4.6 y 5.1.",
                "Nota: los proyectos con trámite iniciado pueden seguir sujetos a la versión anterior de la E.030 según la disposición transitoria de la R.M. 183-2026-VIVIENDA y sus modificaciones; verifique cuál rige para su proyecto.",
                "RNE, Norma Técnica E.060 «Concreto Armado» (módulo de elasticidad Ec = 15 000·√f'c, f'c en kgf/cm²).",
                "RNE, Norma Técnica E.020 «Cargas» (peso del concreto armado, 2 400 kgf/m³).",
                "Der Kiureghian, A. (1981). A response spectrum method for random vibration analysis of MDF systems. Earthquake Engineering & Structural Dynamics, 9(5), 419-435 (combinación CQC).",
                "McGuire, W., Gallagher, R. H. y Ziemian, R. D. (2000). Matrix Structural Analysis, 2.ª ed. (rigidez de pórtico 3D).",
                "Chopra, A. K. Dynamics of Structures (análisis modal y diafragma rígido).")) d.add(new Paragraph("• " + ref, FERNANDO_HUANCA_CELDA));

        d.add(new Paragraph(" "));
        d.add(new Paragraph("Resultados educativos y preliminares, con base empotrada, sin muros, sin zonas rígidas y con losas como diafragma rígido. No constituyen diseño ni verificación para obra y requieren validación por un ingeniero civil colegiado con un software y criterios normativos completos.", FERNANDO_HUANCA_NOTA));
        d.close(); return out.toByteArray();
    }

    private static void fernandoHuancaSeccionTiempoHistoria(Document d, ResumenTiempoHistoria th, int numero) {
        var ver = th.verificacion();
        var esc = th.escalado();
        d.add(new Paragraph(numero + ". Análisis tiempo-historia (arts. 46 a 49)", FERNANDO_HUANCA_SECCION));
        d.add(new Paragraph("Origen de los registros: " + th.origenRegistros() + ". Conjuntos simulados: " + ver.conjuntos()
                + " (mínimo exigido: " + ver.conjuntosMinimos() + ")."));
        d.add(new Paragraph("Periodo fundamental T = " + f(th.periodoFundamental(), 3) + " s (mayor entre X e Y). Rejilla de escalado: 0,2T a 1,5T = "
                + f(0.2 * th.periodoFundamental(), 3) + " a " + f(1.5 * th.periodoFundamental(), 3) + " s. Amortiguamiento 5 % (art. 48.5). Espectro de diseño con R = 1; "
                + "razón mínima promedio SRSS / diseño = " + f(esc.razonMinima(), 3) + (esc.cumple() ? " (cumple el escalado)." : " (NO cumple el escalado).")));
        d.add(tabla(ResumenTiempoHistoria.CABECERA, th.filasTabla()));
        StringBuilder fac = new StringBuilder("Factores de escala: ");
        for (int i = 0; i < esc.factores().length; i++) fac.append(i == 0 ? "" : "; ").append(i + 1).append(" = ").append(f(esc.factores()[i], 3));
        d.add(new Paragraph(fac.toString(), FERNANDO_HUANCA_NOTA));
        d.add(new Paragraph("Resultado del art. 49 (criterio: " + ver.criterio() + ", límite " + f(ver.limite(), 5) + "): " + th.veredicto() + ".", FERNANDO_HUANCA_CABECERA));
        List<String[]> pisos = new java.util.ArrayList<>();
        for (int i = ver.pisosX().size() - 1; i >= 0; i--) {
            var px = ver.pisosX().get(i); var py = ver.pisosY().get(i);
            pisos.add(new String[]{"" + (i + 1), f(px.derivaDiseno(), 5), px.cumple() ? "Cumple" : "No cumple", f(py.derivaDiseno(), 5), py.cumple() ? "Cumple" : "No cumple"});
        }
        d.add(tabla(new String[]{"Piso", "Deriva diseño X", "Estado X", "Deriva diseño Y", "Estado Y"}, pisos));
        d.add(new Paragraph("Advertencias del escalado y de la verificación:", FERNANDO_HUANCA_CABECERA));
        for (String a : esc.avisos()) d.add(new Paragraph("• " + a, FERNANDO_HUANCA_CELDA));
        for (String a : ver.avisos()) d.add(new Paragraph("• " + a, FERNANDO_HUANCA_CELDA));
        var propios = new java.util.LinkedHashSet<String>();
        for (var res : th.resultados()) propios.addAll(res.avisos());
        for (String a : propios) d.add(new Paragraph("• " + a, FERNANDO_HUANCA_CELDA));
        d.add(new Paragraph("• " + ResumenTiempoHistoria.ADVERTENCIA_LEGAL, FERNANDO_HUANCA_CELDA));
    }

    private static List<String[]> fernandoHuancaFilas(Resultado r, Direccion x, Direccion y) {
        List<String[]> l = new java.util.ArrayList<>();
        for (int i = r.pisos().size() - 1; i >= 0; i--) l.add(new String[]{"" + (i + 1), f(x.fuerzas()[i], 2), f(x.cortantes()[i], 2), f(y.fuerzas()[i], 2), f(y.cortantes()[i], 2)});
        return l;
    }
}
