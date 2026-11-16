package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="expedientes_estructurales")
public class ExpedienteEstructural {
 @Id private Long proyectoId;
 private String ubicacion,uso,fechaProyecto,estadoProyecto,profesional,norma,justificacionNorma,estudioSuelos,observaciones;
 public Long getProyectoId(){return proyectoId;} public void setProyectoId(Long v){proyectoId=v;}
 public String getUbicacion(){return ubicacion;} public void setUbicacion(String v){ubicacion=v;}
 public String getUso(){return uso;} public void setUso(String v){uso=v;}
 public String getFechaProyecto(){return fechaProyecto;} public void setFechaProyecto(String v){fechaProyecto=v;}
 public String getEstadoProyecto(){return estadoProyecto;} public void setEstadoProyecto(String v){estadoProyecto=v;}
 public String getProfesional(){return profesional;} public void setProfesional(String v){profesional=v;}
 public String getNorma(){return norma;} public void setNorma(String v){norma=v;}
 public String getJustificacionNorma(){return justificacionNorma;} public void setJustificacionNorma(String v){justificacionNorma=v;}
 public String getEstudioSuelos(){return estudioSuelos;} public void setEstudioSuelos(String v){estudioSuelos=v;}
 public String getObservaciones(){return observaciones;} public void setObservaciones(String v){observaciones=v;}
}
