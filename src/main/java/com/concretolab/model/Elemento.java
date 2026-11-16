package com.concretolab.model;
import jakarta.persistence.*;
@Entity @Table(name="elementos")
public class Elemento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long proyectoId;
    @Column(nullable=false) private String tipo;
    @Column(nullable=false) private String nombre;
    private double largo, ancho, altura, x, y, z;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getProyectoId(){return proyectoId;} public void setProyectoId(Long v){proyectoId=v;}
    public String getTipo(){return tipo;} public void setTipo(String v){tipo=v;} public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;}
    public double getLargo(){return largo;} public void setLargo(double v){largo=v;} public double getAncho(){return ancho;} public void setAncho(double v){ancho=v;}
    public double getAltura(){return altura;} public void setAltura(double v){altura=v;} public double getX(){return x;} public void setX(double v){x=v;}
    public double getY(){return y;} public void setY(double v){y=v;} public double getZ(){return z;} public void setZ(double v){z=v;}
    public double volumen(){return largo*ancho*altura;}
}
