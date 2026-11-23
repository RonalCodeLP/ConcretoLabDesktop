package com.concretolab.service;

import com.concretolab.model.RegistroBitacora;
import com.concretolab.repository.BitacoraRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora de cambios importantes (crear o eliminar proyectos, importar, exportar, copias y restauraciones).
 * Se une a la transacción de la operación registrada (SQLite trabaja con una sola conexión, así que no puede abrir otra):\n * el evento se confirma o se revierte junto con ella. Si falla al guardar, lo anota en el registro técnico.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BitacoraService {

    private final BitacoraRepository erickDelgadoRepositorio;

    @Transactional
    public void erickDelgadoRegistrar(String accion, String detalle) {
        try {
            erickDelgadoRepositorio.save(RegistroBitacora.builder()
                    .fecha(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString())
                    .accion(erickDelgadoRecortar(accion, 40))
                    .detalle(erickDelgadoRecortar(detalle, 400))
                    .build());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la bitácora «{}»: {}", accion, e.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<RegistroBitacora> erickDelgadoRecientes() {
        return erickDelgadoRepositorio.findTop200ByOrderByIdDesc();
    }

    private static String erickDelgadoRecortar(String texto, int maximo) {
        if (texto == null) return null;
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
