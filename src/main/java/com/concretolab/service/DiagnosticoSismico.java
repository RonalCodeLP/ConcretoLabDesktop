package com.concretolab.service;
import com.concretolab.model.Elemento;
import java.util.*;

/** Diagnóstico del inventario geométrico; no construye un modelo analítico. */
public final class DiagnosticoSismico {
 private DiagnosticoSismico(){}
 public record Informe(int zapatas,int columnas,int vigas,List<String> faltantes){}
 public static Informe revisar(List<Elemento> elementos){
  if(elementos==null)throw new IllegalArgumentException("No se pudo cargar el proyecto.");
  int zapatas=0,columnas=0,vigas=0;
  for(Elemento e:elementos){if(e==null)continue;if("ZAPATA".equals(e.getTipo()))zapatas++;else if("COLUMNA".equals(e.getTipo()))columnas++;else if("VIGA".equals(e.getTipo()))vigas++;}
  List<String> pendientes=new ArrayList<>();
  if(elementos.isEmpty())pendientes.add("El proyecto no contiene elementos geométricos.");
  pendientes.add("Conectividad analítica y condiciones de apoyo por nudo.");
  pendientes.add("Materiales, rigideces, secciones y masas del modelo estructural.");
  pendientes.add("Casos de carga, combinaciones y fuente de masa.");
  pendientes.add("Acción sísmica aplicable y parámetros sustentados por el profesional.");
  pendientes.add("Motor dinámico validado y criterios normativos verificados.");
  return new Informe(zapatas,columnas,vigas,List.copyOf(pendientes));
 }
}
