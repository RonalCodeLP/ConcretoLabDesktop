package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="proyectos")
public class Proyecto {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String nombre;
    @Column(length=200) private String ubicacion;
    public Long getId(){return id;} public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;}
    public String getUbicacion(){return ubicacion;} public void setUbicacion(String v){ubicacion=v;}
    @Override public String toString(){return nombre;}
}
