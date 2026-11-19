package com.concretolab.repository;

import com.concretolab.model.VinculoGeometrico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VinculoGeometricoRepository extends JpaRepository<VinculoGeometrico, Long> {
    List<VinculoGeometrico> findByColumnaId(Long columnaId);
    void deleteByColumnaId(Long columnaId);
    void deleteByColumnaIdOrVigaId(Long columnaId, Long vigaId);
}
