package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="modelos_cercha")
public class ModeloCercha {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long proyectoId;
 @Column(nullable=false,length=120) private String nombre;
 @Column(nullable=false,length=30000) private String nudos;
 @Column(nullable=false,length=30000) private String barras;
 public Long getId(){return id;} public void setId(Long id){this.id=id;}
 public Long getProyectoId(){return proyectoId;} public void setProyectoId(Long id){proyectoId=id;}
 public String getNombre(){return nombre;} public void setNombre(String nombre){this.nombre=nombre;}
 public String getNudos(){return nudos;} public void setNudos(String nudos){this.nudos=nudos;}
 public String getBarras(){return barras;} public void setBarras(String barras){this.barras=barras;}
 @Override public String toString(){return nombre;}
}
