package com.concretolab.service;
import java.util.*;

public final class GranulometriaService {
 private GranulometriaService(){}
 public record Tamiz(double aberturaMm,double retenidoG){}
 public record Fila(double aberturaMm,double retenidoG,double retenidoPorcentaje,double retenidoAcumulado,double pasaPorcentaje){}
 public record Resultado(List<Fila> filas,double recuperadoG,double diferenciaG,double diferenciaPorcentaje){}
 public static Resultado yefriLermaCalcular(double masaInicial,List<Tamiz> tamices,double fondo){
  if(!Double.isFinite(masaInicial)||masaInicial<=0)throw new IllegalArgumentException("La masa inicial seca debe ser positiva.");
  if(!Double.isFinite(fondo)||fondo<0)throw new IllegalArgumentException("La masa del fondo debe ser cero o positiva.");
  if(tamices==null||tamices.isEmpty())throw new IllegalArgumentException("Ingrese al menos un tamiz.");
  List<Fila> filas=new ArrayList<>();double anterior=Double.POSITIVE_INFINITY,acumulado=0;
  for(Tamiz t:tamices){
   if(t==null||!Double.isFinite(t.aberturaMm())||t.aberturaMm()<=0||t.aberturaMm()>=anterior)throw new IllegalArgumentException("Las aberturas deben ser positivas, únicas y estar ordenadas de mayor a menor.");
   if(!Double.isFinite(t.retenidoG())||t.retenidoG()<0)throw new IllegalArgumentException("Las masas retenidas deben ser cero o positivas.");
   acumulado+=t.retenidoG();if(!Double.isFinite(acumulado)||acumulado>masaInicial)throw new IllegalArgumentException("La masa retenida acumulada supera la masa inicial; revise las mediciones.");
   double porcentaje=acumulado/masaInicial*100;filas.add(new Fila(t.aberturaMm(),t.retenidoG(),t.retenidoG()/masaInicial*100,porcentaje,100-porcentaje));anterior=t.aberturaMm();
  }
  double recuperado=acumulado+fondo;if(!Double.isFinite(recuperado))throw new IllegalArgumentException("Las masas son demasiado grandes.");
  double diferencia=recuperado-masaInicial;return new Resultado(List.copyOf(filas),recuperado,diferencia,diferencia/masaInicial*100);
 }
}
