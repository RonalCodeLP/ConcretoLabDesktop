package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.service.ManualPdf;
import com.concretolab.ui.VisorAyuda;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.PDDestinationNameTreeNode;
import org.junit.jupiter.api.Test;

class ManualPdfTest {
    private static byte[] pdfDePrueba() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            String[] textos = {"Bienvenida al manual", "Modelo 3D y metrado", "Analisis sismico con la E.030"};
            for (String t : textos) {
                PDPage p = new PDPage(PDRectangle.A4);
                doc.addPage(p);
                try (PDPageContentStream c = new PDPageContentStream(doc, p)) {
                    c.beginText();
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                    c.newLineAtOffset(72, 700);
                    c.showText(t);
                    c.endText();
                }
            }
            PDDocumentOutline indice = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(indice);
            PDOutlineItem modelo = new PDOutlineItem();
            modelo.setTitle("Modelo 3D");
            PDPageFitWidthDestination dModelo = new PDPageFitWidthDestination();
            dModelo.setPage(doc.getPage(1));
            modelo.setDestination(dModelo);
            indice.addLast(modelo);
            PDOutlineItem sismo = new PDOutlineItem();
            sismo.setTitle("Análisis sísmico");
            PDPageFitWidthDestination dSismo = new PDPageFitWidthDestination();
            dSismo.setPage(doc.getPage(2));
            sismo.setDestination(dSismo);
            modelo.addLast(new PDOutlineItem());
            indice.addLast(sismo);

            PDPageXYZDestination ancla = new PDPageXYZDestination();
            ancla.setPage(doc.getPage(2));
            ancla.setTop((int) (PDRectangle.A4.getHeight() * 0.75));
            PDDestinationNameTreeNode destinos = new PDDestinationNameTreeNode();
            destinos.setNames(Map.<String, PDPageDestination>of("ayuda-sismo", ancla));
            PDDocumentNameDictionary nombres = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            nombres.setDests(destinos);
            doc.getDocumentCatalog().setNames(nombres);
            doc.save(salida);
            return salida.toByteArray();
        }
    }

    @Test
    void leeIndiceAnclasYTexto() throws Exception {
        try (ManualPdf manual = ManualPdf.fernandoHuancaAbrir(pdfDePrueba())) {
            assertThat(manual.fernandoHuancaPaginas()).isEqualTo(3);
            List<ManualPdf.Entrada> indice = manual.fernandoHuancaIndice();
            assertThat(indice).extracting(ManualPdf.Entrada::titulo).containsExactly("Modelo 3D", "Análisis sísmico");
            assertThat(indice).extracting(ManualPdf.Entrada::pagina).containsExactly(1, 2);

            var lugar = manual.fernandoHuancaAncla("ayuda-sismo").orElseThrow();
            assertThat(lugar.pagina()).isEqualTo(2);
            assertThat(lugar.desdeArriba()).isBetween(0.2, 0.3);
            assertThat(manual.fernandoHuancaAncla("ayuda-que-no-existe")).isEmpty();

            assertThat(manual.fernandoHuancaBuscar("ANÁLISIS SÍSMICO", 0)).isEqualTo(2);
            assertThat(manual.fernandoHuancaBuscar("modelo 3d", 2)).isEqualTo(1);
            assertThat(manual.fernandoHuancaBuscar("no aparece", 0)).isEqualTo(-1);

            var imagen = manual.fernandoHuancaDibujar(0, 0.5);
            assertThat(imagen.getWidth()).isBetween(296, 299);
        }
    }

    @Test
    void laAplicacionIncluyeElManualYTodasLasPantallasTienenAncla() throws Exception {
        var manual = ManualPdf.fernandoHuancaAbrirIncluido();
        assertThat(manual).as("src/main/resources/ayuda/Manual_de_usuario_ConcretoLab.pdf").isPresent();
        try (ManualPdf m = manual.get()) {
            assertThat(m.fernandoHuancaPaginas()).isPositive();
            assertThat(m.fernandoHuancaIndice()).isNotEmpty();
        }
        assertThat(VisorAyuda.FERNANDO_HUANCA_ANCLAS).containsKeys("inicio-view", "modelo-view", "sismo-view", "materiales-view",
                "granulometria-view", "balance-view", "mezclas-view", "datos-view");
    }
}
