package com.concretolab.service;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDestinationDictionary;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

public final class ManualPdf implements Closeable {
    public static final String FERNANDO_HUANCA_RECURSO = "/ayuda/Manual_de_usuario_ConcretoLab.pdf";

    public record Entrada(String titulo, int pagina, List<Entrada> hijos) { }

    public record Lugar(int pagina, double desdeArriba) { }

    private final byte[] fernandoHuancaBytes;
    private final PDDocument fernandoHuancaDocumento;
    private final PDFRenderer fernandoHuancaDibujante;
    private String[] fernandoHuancaTextos;

    private ManualPdf(byte[] bytes) throws IOException {
        this.fernandoHuancaBytes = bytes;
        this.fernandoHuancaDocumento = Loader.loadPDF(bytes);
        this.fernandoHuancaDibujante = new PDFRenderer(fernandoHuancaDocumento);
    }

    public static Optional<ManualPdf> fernandoHuancaAbrirIncluido() throws IOException {
        try (InputStream in = ManualPdf.class.getResourceAsStream(FERNANDO_HUANCA_RECURSO)) {
            if (in == null) return Optional.empty();
            return Optional.of(new ManualPdf(in.readAllBytes()));
        }
    }

    public static ManualPdf fernandoHuancaAbrir(byte[] bytes) throws IOException {
        return new ManualPdf(bytes.clone());
    }

    public byte[] fernandoHuancaBytes() {
        return fernandoHuancaBytes.clone();
    }

    public synchronized int fernandoHuancaPaginas() {
        return fernandoHuancaDocumento.getNumberOfPages();
    }

    public synchronized double[] fernandoHuancaTamano(int pagina) {
        PDPage p = fernandoHuancaDocumento.getPage(pagina);
        PDRectangle caja = p.getCropBox();
        boolean girada = p.getRotation() % 180 != 0;
        return girada ? new double[]{caja.getHeight(), caja.getWidth()} : new double[]{caja.getWidth(), caja.getHeight()};
    }

    public synchronized BufferedImage fernandoHuancaDibujar(int pagina, double escala) throws IOException {
        return fernandoHuancaDibujante.renderImage(pagina, (float) escala, ImageType.ARGB);
    }

    public synchronized List<Entrada> fernandoHuancaIndice() {
        PDDocumentOutline indice = fernandoHuancaDocumento.getDocumentCatalog().getDocumentOutline();
        return indice == null ? List.of() : fernandoHuancaHijos(indice);
    }

    private List<Entrada> fernandoHuancaHijos(PDOutlineNode nodo) {
        List<Entrada> lista = new ArrayList<>();
        for (PDOutlineItem item : nodo.children()) {
            int pagina = -1;
            try {
                PDPage p = item.findDestinationPage(fernandoHuancaDocumento);
                if (p != null) pagina = fernandoHuancaDocumento.getPages().indexOf(p);
            } catch (IOException ex) {
            }
            String titulo = item.getTitle() == null ? "" : item.getTitle().strip();
            lista.add(new Entrada(titulo, pagina, fernandoHuancaHijos(item)));
        }
        return lista;
    }

    public synchronized Optional<Lugar> fernandoHuancaAncla(String nombre) {
        try {
            PDDocumentNameDictionary nombres = fernandoHuancaDocumento.getDocumentCatalog().getNames();
            PDPageDestination destino = null;
            if (nombres != null && nombres.getDests() != null) destino = nombres.getDests().getValue(nombre);
            if (destino == null) {
                PDDocumentNameDestinationDictionary antiguos = fernandoHuancaDocumento.getDocumentCatalog().getDests();
                if (antiguos != null && antiguos.getDestination(nombre) instanceof PDPageDestination d) destino = d;
            }
            if (destino == null) return Optional.empty();
            int pagina = destino.retrievePageNumber();
            if (pagina < 0 && destino.getPage() != null) pagina = fernandoHuancaDocumento.getPages().indexOf(destino.getPage());
            if (pagina < 0 || pagina >= fernandoHuancaDocumento.getNumberOfPages()) return Optional.empty();
            double desdeArriba = 0;
            if (destino instanceof PDPageXYZDestination xyz && xyz.getTop() > 0) {
                double alto = fernandoHuancaTamano(pagina)[1];
                desdeArriba = Math.max(0, Math.min(1, 1 - xyz.getTop() / alto));
            }
            return Optional.of(new Lugar(pagina, desdeArriba));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public synchronized int fernandoHuancaBuscar(String texto, int desde) throws IOException {
        String buscado = fernandoHuancaNormalizar(texto);
        if (buscado.isBlank()) return -1;
        if (fernandoHuancaTextos == null) {
            int n = fernandoHuancaDocumento.getNumberOfPages();
            String[] textos = new String[n];
            PDFTextStripper extractor = new PDFTextStripper();
            for (int i = 0; i < n; i++) {
                extractor.setStartPage(i + 1);
                extractor.setEndPage(i + 1);
                textos[i] = fernandoHuancaNormalizar(extractor.getText(fernandoHuancaDocumento));
            }
            fernandoHuancaTextos = textos;
        }
        int n = fernandoHuancaTextos.length;
        for (int k = 0; k < n; k++) {
            int i = Math.floorMod(desde + k, n);
            if (fernandoHuancaTextos[i].contains(buscado)) return i;
        }
        return -1;
    }

    static String fernandoHuancaNormalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return Arrays.stream(sinTildes.toLowerCase(Locale.ROOT).split("\\s+")).filter(t -> !t.isEmpty())
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    @Override
    public synchronized void close() throws IOException {
        fernandoHuancaDocumento.close();
    }
}
