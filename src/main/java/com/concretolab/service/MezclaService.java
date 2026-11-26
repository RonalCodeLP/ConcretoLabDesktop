package com.concretolab.service;
import com.concretolab.model.*;
import com.concretolab.repository.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MezclaService {
 private final MezclaRepository mezclas; private final EnsayoRepository ensayos; private final ProyectoRepository proyectos;
 public MezclaService(MezclaRepository m,EnsayoRepository e,ProyectoRepository p){mezclas=m;ensayos=e;proyectos=p;}
 public record Resultado(double finoHumedo,double gruesoHumedo,double aguaAgregar,double relacionAguaCemento,double volumen) { }
 public static Resultado yefriLermaCalcular(Mezcla m,double volumen){
   if(!Double.isFinite(volumen)||volumen<=0)throw new IllegalArgumentException("Ingrese un volumen positivo y finito.");
   yefriLermaValidar(m);
   double finoSeco=m.getFinoSss()/(1+m.getAbsorcionFino()/100),gruesoSeco=m.getGruesoSss()/(1+m.getAbsorcionGrueso()/100);
   double fino=finoSeco*(1+m.getHumedadFino()/100),grueso=gruesoSeco*(1+m.getHumedadGrueso()/100);
   double agua=m.getAgua()-(finoSeco*(m.getHumedadFino()-m.getAbsorcionFino())/100)-(gruesoSeco*(m.getHumedadGrueso()-m.getAbsorcionGrueso())/100);
   if(agua<0)throw new IllegalArgumentException("El agua libre supera el agua de diseño: revise las mediciones y la dosificación.");
   Resultado r=new Resultado(fino*volumen,grueso*volumen,agua*volumen,m.getAgua()/m.getCemento(),volumen);
   if(!Double.isFinite(r.finoHumedo())||!Double.isFinite(r.gruesoHumedo())||!Double.isFinite(r.aguaAgregar())||!Double.isFinite(m.getCemento()*volumen)) throw new IllegalArgumentException("Las cantidades son demasiado grandes.");
   return r;
 }
 @Transactional(readOnly=true) public List<Mezcla> yefriLermaListar(Long proyectoId){return mezclas.findByProyectoIdOrderByIdDesc(proyectoId);}
 @Transactional public Mezcla yefriLermaGuardar(Mezcla m){if(m.getProyectoId()==null||!proyectos.existsById(m.getProyectoId()))throw new IllegalArgumentException("Seleccione un proyecto.");yefriLermaValidar(m);yefriLermaCalcular(m,1);return mezclas.save(m);}
 @Transactional(readOnly=true) public List<Ensayo> ensayos(Long mezclaId){return ensayos.findByMezclaIdOrderByIdDesc(mezclaId);}
 @Transactional public Ensayo yefriLermaGuardarEnsayo(Ensayo e){
   if(e.getMezclaId()==null||!mezclas.existsById(e.getMezclaId()))throw new IllegalArgumentException("Seleccione una mezcla.");
   if(e.getIdentificacion()==null||e.getIdentificacion().isBlank())throw new IllegalArgumentException("Ingrese la identificación de la probeta.");
   try{LocalDate.parse(e.getFecha());}catch(Exception ex){throw new IllegalArgumentException("Ingrese una fecha válida.");}
   if(!yefriLermaPositivo(e.getEdadDias())||!yefriLermaPositivo(e.getResistenciaMpa())||!yefriLermaNoNegativo(e.getAsentamientoMm()))throw new IllegalArgumentException("Revise edad, resistencia y asentamiento.");
   return ensayos.save(e);
 }
 private static void yefriLermaValidar(Mezcla m){
   if(m==null||m.getNombre()==null||m.getNombre().isBlank())throw new IllegalArgumentException("Ingrese el nombre de la mezcla.");
   if(!yefriLermaPositivo(m.getResistencia())||!yefriLermaPositivo(m.getCemento())||!yefriLermaPositivo(m.getAgua())||!yefriLermaPositivo(m.getFinoSss())||!yefriLermaPositivo(m.getGruesoSss()))throw new IllegalArgumentException("Ingrese cantidades y f'c positivas y finitas.");
   if(!yefriLermaPorcentaje(m.getAbsorcionFino())||!yefriLermaPorcentaje(m.getHumedadFino())||!yefriLermaPorcentaje(m.getAbsorcionGrueso())||!yefriLermaPorcentaje(m.getHumedadGrueso()))throw new IllegalArgumentException("Humedad y absorción deben estar entre 0 y 100 %.");
 }
 private static boolean yefriLermaPositivo(double v){return Double.isFinite(v)&&v>0;}
 private static boolean yefriLermaNoNegativo(double v){return Double.isFinite(v)&&v>=0;}
 private static boolean yefriLermaPorcentaje(double v){return yefriLermaNoNegativo(v)&&v<=100;}
}
