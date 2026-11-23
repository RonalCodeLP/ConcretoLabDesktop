package com.concretolab.service;

/** Balance de volúmenes para un metro cúbico, con parámetros elegidos por el profesional. */
public final class VolumenAbsolutoService {
 private VolumenAbsolutoService(){}
 public record Datos(double agua,double relacionAguaCemento,double densidadCemento,double gruesoSss,double densidadGruesoSss,double densidadFinoSss,double airePorcentaje){}
 public record Resultado(double cemento,double finoSss,double volumenAgua,double volumenCemento,double volumenGrueso,double volumenFino,double volumenAire){}
 public static Resultado yefriLermaCalcular(Datos d){
  if(d==null||!yefriLermaPositivo(d.agua())||!yefriLermaPositivo(d.relacionAguaCemento())||!yefriLermaPositivo(d.densidadCemento())||!yefriLermaPositivo(d.gruesoSss())||!yefriLermaPositivo(d.densidadGruesoSss())||!yefriLermaPositivo(d.densidadFinoSss()))throw new IllegalArgumentException("Ingrese valores positivos y finitos para agua, relación, densidades y agregado grueso.");
  if(!Double.isFinite(d.airePorcentaje())||d.airePorcentaje()<0||d.airePorcentaje()>=100)throw new IllegalArgumentException("El aire debe ser de 0 a menos de 100 %.");
  double cemento=d.agua()/d.relacionAguaCemento(),agua=d.agua()/1000,volCemento=cemento/(1000*d.densidadCemento());
  double grueso=d.gruesoSss()/(1000*d.densidadGruesoSss()),aire=d.airePorcentaje()/100,fino=1-agua-volCemento-grueso-aire;
  double masaFino=fino*1000*d.densidadFinoSss();
  if(!Double.isFinite(masaFino)||fino<=0)throw new IllegalArgumentException("No queda volumen positivo para agregado fino. Revise los parámetros.");
  return new Resultado(cemento,masaFino,agua,volCemento,grueso,fino,aire);
 }
 private static boolean yefriLermaPositivo(double n){return Double.isFinite(n)&&n>0;}
}
