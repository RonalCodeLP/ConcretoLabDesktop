package com.concretolab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "vinculos_geometricos", uniqueConstraints = @UniqueConstraint(columnNames = {"columna_id", "viga_id"}))
public class VinculoGeometrico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "columna_id", nullable = false)
    private Long columnaId;
    @Column(name = "viga_id", nullable = false)
    private Long vigaId;

    public Long getId() { return id; }
    public Long getColumnaId() { return columnaId; }
    public void setColumnaId(Long columnaId) { this.columnaId = columnaId; }
    public Long getVigaId() { return vigaId; }
    public void setVigaId(Long vigaId) { this.vigaId = vigaId; }
}
