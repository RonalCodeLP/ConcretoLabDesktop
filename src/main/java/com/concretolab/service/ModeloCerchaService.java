package com.concretolab.service;
import com.concretolab.model.ModeloCercha;
import com.concretolab.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModeloCerchaService {
 private final ModeloCerchaRepository modelos;private final ProyectoRepository proyectos;
 public ModeloCerchaService(ModeloCerchaRepository m,ProyectoRepository p){modelos=m;proyectos=p;}
 public record Datos(List<AnalisisCercha.Nudo> nudos,List<AnalisisCercha.Barra> barras){}
 public static Datos interpretar(String textoNudos,String textoBarras){
  if(textoNudos==null||textoBarras==null||textoNudos.length()>30000||textoBarras.length()>30000)throw new IllegalArgumentException("Revise el tamaño de los datos.");
  List<AnalisisCercha.Nudo> nudos=new ArrayList<>();List<AnalisisCercha.Barra> barras=new ArrayList<>();
  int numero=0;for(String linea:textoNudos.split("\\R")){numero++;if(linea.isBlank())continue;String[] v=linea.split(";",-1);
   if(v.length!=7)throw new IllegalArgumentException("Nudo línea "+numero+": use nombre; X; Y; restringir X; restringir Y; Fx; Fy.");
   try{nudos.add(new AnalisisCercha.Nudo(v[0].trim(),valor(v[1]),valor(v[2]),restriccion(v[3]),restriccion(v[4]),valor(v[5]),valor(v[6])));}
   catch(IllegalArgumentException ex){throw new IllegalArgumentException("Nudo línea "+numero+": "+ex.getMessage());}}
  numero=0;for(String linea:textoBarras.split("\\R")){numero++;if(linea.isBlank())continue;String[] v=linea.split(";",-1);
   if(v.length!=5)throw new IllegalArgumentException("Barra línea "+numero+": use nombre; nudo inicial; nudo final; E; área.");
   try{barras.add(new AnalisisCercha.Barra(v[0].trim(),v[1].trim(),v[2].trim(),valor(v[3]),valor(v[4])));}
   catch(IllegalArgumentException ex){throw new IllegalArgumentException("Barra línea "+numero+": "+ex.getMessage());}}
  return new Datos(List.copyOf(nudos),List.copyOf(barras));
 }
 private static double valor(String s){try{return Double.parseDouble(s.trim().replace(',','.'));}catch(NumberFormatException ex){throw new IllegalArgumentException("Ingrese un número válido.");}}
 private static boolean restriccion(String s){return switch(s.trim().toUpperCase(Locale.ROOT)){case "SI","SÍ","1"->true;case "NO","0"->false;default->throw new IllegalArgumentException("La restricción debe ser SI o NO.");};}
 @Transactional(readOnly=true) public List<ModeloCercha> listar(Long proyectoId){return modelos.findByProyectoIdOrderByNombreAsc(proyectoId);}
 @Transactional public ModeloCercha guardar(ModeloCercha m){
  if(m.getProyectoId()==null||!proyectos.existsById(m.getProyectoId()))throw new IllegalArgumentException("Seleccione un proyecto.");
  if(m.getNombre()==null||m.getNombre().isBlank()||m.getNombre().length()>120)throw new IllegalArgumentException("Ingrese un nombre de hasta 120 caracteres.");
  interpretar(m.getNudos(),m.getBarras());
  if(m.getId()!=null){ModeloCercha anterior=modelos.findById(m.getId()).orElseThrow(()->new IllegalArgumentException("El modelo ya no existe."));if(!anterior.getProyectoId().equals(m.getProyectoId()))throw new IllegalArgumentException("El modelo pertenece a otro proyecto.");}
  m.setNombre(m.getNombre().trim());return modelos.save(m);
 }
}
