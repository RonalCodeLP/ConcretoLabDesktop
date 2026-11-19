package com.concretolab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cuadriculas_proyecto")
public class CuadriculaProyecto {
    @Id
    private Long proyectoId;
    @Column(nullable = false, length = 2000)
    private String separacionesX;
    @Column(nullable = false, length = 2000)
    private String separacionesY;
    @Column(nullable = false, length = 2000)
    private String niveles;
    private double origenX;
    private double origenY;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public String getSeparacionesX() { return separacionesX; }
    public void setSeparacionesX(String separacionesX) { this.separacionesX = separacionesX; }
    public String getSeparacionesY() { return separacionesY; }
    public void setSeparacionesY(String separacionesY) { this.separacionesY = separacionesY; }
    public String getNiveles() { return niveles; }
    public void setNiveles(String niveles) { this.niveles = niveles; }
    public double getOrigenX() { return origenX; }
    public void setOrigenX(double origenX) { this.origenX = origenX; }
    public double getOrigenY() { return origenY; }
    public void setOrigenY(double origenY) { this.origenY = origenY; }
}
