package com.concretolab.service;

public final class OsciladorSismico {
 private OsciladorSismico(){}
 public record Resultado(double periodoSegundos,double fuerzaKn,double desplazamientoMm){}
 public static Resultado yefriLermaCalcular(double masaToneladas,double rigidezKnPorMetro,double aceleracionEspectralG){
  if(!Double.isFinite(masaToneladas)||masaToneladas<=0||!Double.isFinite(rigidezKnPorMetro)||rigidezKnPorMetro<=0||!Double.isFinite(aceleracionEspectralG)||aceleracionEspectralG<0)
   throw new IllegalArgumentException("Ingrese masa y rigidez positivas y una aceleración no negativa.");
  double periodo=2*Math.PI*Math.sqrt(masaToneladas/rigidezKnPorMetro);
  double fuerza=masaToneladas*aceleracionEspectralG*9.80665,desplazamiento=1000*fuerza/rigidezKnPorMetro;
  if(!Double.isFinite(periodo)||!Double.isFinite(fuerza)||!Double.isFinite(desplazamiento))throw new IllegalArgumentException("Los valores exceden el rango calculable.");
  return new Resultado(periodo,fuerza,desplazamiento);
 }
}
