package com.concretolab.ui;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
@Slf4j
public class Exportador {
    @FunctionalInterface
    public interface Generador {
        void generar(File destino) throws Exception;
    }

    public enum Formato {
        PDF("Documento PDF", "*.pdf", ".pdf"),
        EXCEL("Libro de Excel", "*.xlsx", ".xlsx");

        final String descripcion;
        final String patron;
        final String extension;

        Formato(String descripcion, String patron, String extension) {
            this.descripcion = descripcion;
            this.patron = patron;
            this.extension = extension;
        }
    }

    private final Notificador fernandoHuancaNotificador;
    private HostServices fernandoHuancaHostServices;
    private File fernandoHuancaUltimaCarpeta = new File(System.getProperty("user.home"));

    public void setHostServices(HostServices hostServices) {
        this.fernandoHuancaHostServices = hostServices;
    }

    public void fernandoHuancaGuardar(Window ventana, String titulo, String nombreSugerido, Formato formato, Generador generador) {
        FileChooser selector = new FileChooser();
        selector.setTitle(titulo);
        selector.setInitialFileName(nombreSugerido + formato.extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.descripcion, formato.patron));
        if (fernandoHuancaUltimaCarpeta.isDirectory()) {
            selector.setInitialDirectory(fernandoHuancaUltimaCarpeta);
        }
        File elegido = selector.showSaveDialog(ventana);
        if (elegido == null) {
            return;
        }
        File destino = elegido.getName().toLowerCase().endsWith(formato.extension)
                ? elegido
                : new File(elegido.getParentFile(), elegido.getName() + formato.extension);
        fernandoHuancaUltimaCarpeta = destino.getParentFile();

        fernandoHuancaEjecutar(destino, generador, () -> {
            fernandoHuancaNotificador.exito("Archivo guardado: " + destino.getName());
            fernandoHuancaAbrir(destino);
        });
    }

    public void fernandoHuancaAbrirTemporal(String nombre, Formato formato, Generador generador) {
        File destino;
        try {
            File carpeta = Files.createTempDirectory("comprobantes").toFile();
            carpeta.deleteOnExit();
            destino = new File(carpeta, nombre + formato.extension);
            destino.deleteOnExit();
        } catch (IOException e) {
            fernandoHuancaNotificador.fernandoHuancaError("No se pudo crear el archivo temporal: " + e.getMessage());
            return;
        }
        fernandoHuancaEjecutar(destino, generador, () -> {
            fernandoHuancaNotificador.fernandoHuancaInfo("Abriendo " + destino.getName() + "…");
            fernandoHuancaAbrir(destino);
        });
    }

    private void fernandoHuancaEjecutar(File destino, Generador generador, Runnable alTerminar) {
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                generador.generar(destino);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> alTerminar.run());
        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException();
            log.error("No se pudo generar {}", destino, causa);
            fernandoHuancaNotificador.fernandoHuancaError("No se pudo generar el archivo: " + causa.getMessage());
        });
        Thread hilo = new Thread(tarea, "exportador");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void fernandoHuancaAbrirCarpeta(File carpeta) {
        if (carpeta == null || !carpeta.isDirectory()) {
            fernandoHuancaNotificador.fernandoHuancaAdvertencia("La carpeta todavía no existe.");
            return;
        }
        fernandoHuancaAbrir(carpeta);
    }

    private void fernandoHuancaAbrir(File archivo) {
        if (fernandoHuancaHostServices != null) {
            fernandoHuancaHostServices.showDocument(archivo.toURI().toString());
        }
    }
}
