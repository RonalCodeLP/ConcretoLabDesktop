package com.concretolab.model;
import jakarta.persistence.*;

@Entity @Table(name="materiales")
public class Material {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String nombre;
 @Column(nullable=false) private String tipo;
 private String procedencia, referenciaEnsayo, fechaEnsayo, observaciones;
 private Double densidadRelativa, absorcion, humedad, moduloFinura, tamanoMaximoMm, pesoUnitarioCompactado;
 public Long getId(){return id;}
 public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;}
 public String getTipo(){return tipo;} public void setTipo(String v){tipo=v;}
 public String getProcedencia(){return procedencia;} public void setProcedencia(String v){procedencia=v;}
 public String getReferenciaEnsayo(){return referenciaEnsayo;} public void setReferenciaEnsayo(String v){referenciaEnsayo=v;}
 public String getFechaEnsayo(){return fechaEnsayo;} public void setFechaEnsayo(String v){fechaEnsayo=v;}
 public String getObservaciones(){return observaciones;} public void setObservaciones(String v){observaciones=v;}
 public Double getDensidadRelativa(){return densidadRelativa;} public void setDensidadRelativa(Double v){densidadRelativa=v;}
 public Double getAbsorcion(){return absorcion;} public void setAbsorcion(Double v){absorcion=v;}
 public Double getHumedad(){return humedad;} public void setHumedad(Double v){humedad=v;}
 public Double getModuloFinura(){return moduloFinura;} public void setModuloFinura(Double v){moduloFinura=v;}
 public Double getTamanoMaximoMm(){return tamanoMaximoMm;} public void setTamanoMaximoMm(Double v){tamanoMaximoMm=v;}
 public Double getPesoUnitarioCompactado(){return pesoUnitarioCompactado;} public void setPesoUnitarioCompactado(Double v){pesoUnitarioCompactado=v;}
 @Override public String toString(){return nombre+" · "+tipo;}
}
