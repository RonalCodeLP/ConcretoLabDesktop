package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="granulometrias")
public class RegistroGranulometria {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long materialId;
 private String identificacion, fecha;
 private double masaInicial, fondo;
 @Column(length=10000) private String mediciones;
 public Long getId(){return id;} public Long getMaterialId(){return materialId;} public void setMaterialId(Long v){materialId=v;}
 public String getIdentificacion(){return identificacion;} public void setIdentificacion(String v){identificacion=v;}
 public String getFecha(){return fecha;} public void setFecha(String v){fecha=v;}
 public double getMasaInicial(){return masaInicial;} public void setMasaInicial(double v){masaInicial=v;}
 public double getFondo(){return fondo;} public void setFondo(double v){fondo=v;}
 public String getMediciones(){return mediciones;} public void setMediciones(String v){mediciones=v;}
 @Override public String toString(){return identificacion+" · "+fecha;}
}
