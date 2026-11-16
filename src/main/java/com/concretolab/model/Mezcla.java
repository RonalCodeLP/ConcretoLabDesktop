package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="mezclas")
public class Mezcla {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long proyectoId;
 private String nombre, referencia, observaciones;
 private String materialFino, materialGrueso;
 private double resistencia, cemento, agua, finoSss, gruesoSss, absorcionFino, humedadFino, absorcionGrueso, humedadGrueso;
 public Long getId(){return id;} public Long getProyectoId(){return proyectoId;} public void setProyectoId(Long v){proyectoId=v;}
 public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;} public String getReferencia(){return referencia;} public void setReferencia(String v){referencia=v;}
 public String getObservaciones(){return observaciones;} public void setObservaciones(String v){observaciones=v;}
 public String getMaterialFino(){return materialFino;} public void setMaterialFino(String v){materialFino=v;}
 public String getMaterialGrueso(){return materialGrueso;} public void setMaterialGrueso(String v){materialGrueso=v;}
 public double getResistencia(){return resistencia;} public void setResistencia(double v){resistencia=v;} public double getCemento(){return cemento;} public void setCemento(double v){cemento=v;}
 public double getAgua(){return agua;} public void setAgua(double v){agua=v;} public double getFinoSss(){return finoSss;} public void setFinoSss(double v){finoSss=v;}
 public double getGruesoSss(){return gruesoSss;} public void setGruesoSss(double v){gruesoSss=v;}
 public double getAbsorcionFino(){return absorcionFino;} public void setAbsorcionFino(double v){absorcionFino=v;} public double getHumedadFino(){return humedadFino;} public void setHumedadFino(double v){humedadFino=v;}
 public double getAbsorcionGrueso(){return absorcionGrueso;} public void setAbsorcionGrueso(double v){absorcionGrueso=v;} public double getHumedadGrueso(){return humedadGrueso;} public void setHumedadGrueso(double v){humedadGrueso=v;}
}
