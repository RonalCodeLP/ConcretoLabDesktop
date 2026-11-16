package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="ensayos")
public class Ensayo {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long mezclaId; private String fecha, identificacion, observaciones; private double edadDias, resistenciaMpa, asentamientoMm;
 public Long getId(){return id;} public Long getMezclaId(){return mezclaId;} public void setMezclaId(Long v){mezclaId=v;}
 public String getFecha(){return fecha;} public void setFecha(String v){fecha=v;} public String getIdentificacion(){return identificacion;} public void setIdentificacion(String v){identificacion=v;}
 public String getObservaciones(){return observaciones;} public void setObservaciones(String v){observaciones=v;}
 public double getEdadDias(){return edadDias;} public void setEdadDias(double v){edadDias=v;} public double getResistenciaMpa(){return resistenciaMpa;} public void setResistenciaMpa(double v){resistenciaMpa=v;}
 public double getAsentamientoMm(){return asentamientoMm;} public void setAsentamientoMm(double v){asentamientoMm=v;}
}
