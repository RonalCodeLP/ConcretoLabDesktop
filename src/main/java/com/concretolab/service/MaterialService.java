package com.concretolab.service;
import com.concretolab.model.Material;
import com.concretolab.repository.MaterialRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MaterialService {
 public static final List<String> YEFRI_LERMA_TIPOS = List.of("CEMENTO", "CEMENTO TIPO I", "CEMENTO TIPO IP", "CEMENTO TIPO ICo",
   "CEMENTO TIPO II", "CEMENTO TIPO V", "CEMENTO TIPO MS", "CEMENTO TIPO HS", "AGREGADO FINO", "AGREGADO GRUESO", "ADITIVO");
 private final MaterialRepository yefriLermaRepository;
 public MaterialService(MaterialRepository repository){this.yefriLermaRepository=repository;}
 @Transactional(readOnly=true) public List<Material> yefriLermaListar(){return yefriLermaRepository.findAllByOrderByNombreAsc();}
 @Transactional(readOnly=true) public List<Material> yefriLermaPorTipo(String tipo){return yefriLermaRepository.findByTipoOrderByNombreAsc(tipo);}
 @Transactional(readOnly=true) public List<Material> yefriLermaCementos(){return yefriLermaRepository.findAllByOrderByNombreAsc().stream().filter(m->m.getTipo()!=null&&m.getTipo().startsWith("CEMENTO")).toList();}
 @Transactional public Material yefriLermaGuardar(Material m){
  if(m==null||m.getNombre()==null||m.getNombre().isBlank()||m.getNombre().trim().length()>120)throw new IllegalArgumentException("Ingrese un nombre de hasta 120 caracteres.");
  if(m.getTipo()==null||!YEFRI_LERMA_TIPOS.contains(m.getTipo()))throw new IllegalArgumentException("Seleccione un tipo de material.");
  m.setNombre(m.getNombre().trim());
  yefriLermaValidarPositivo(m.getDensidadRelativa(),"densidad relativa"); yefriLermaValidarPositivo(m.getModuloFinura(),"módulo de finura");
  yefriLermaValidarPositivo(m.getTamanoMaximoMm(),"tamaño máximo"); yefriLermaValidarPositivo(m.getPesoUnitarioCompactado(),"peso unitario compactado");
  yefriLermaPorcentaje(m.getAbsorcion(),"absorción");yefriLermaPorcentaje(m.getHumedad(),"humedad");
  if(m.getFechaEnsayo()!=null&&!m.getFechaEnsayo().isBlank())try{LocalDate.parse(m.getFechaEnsayo());}catch(Exception ex){throw new IllegalArgumentException("Ingrese una fecha de ensayo válida.");}
  for(String s:List.of(yefriLermaTexto(m.getProcedencia()),yefriLermaTexto(m.getReferenciaEnsayo()),yefriLermaTexto(m.getObservaciones())))if(s.length()>255)throw new IllegalArgumentException("Los textos no pueden superar 255 caracteres.");
  return yefriLermaRepository.save(m);
 }
 private static String yefriLermaTexto(String s){return s==null?"":s;}
 private static void yefriLermaValidarPositivo(Double n,String campo){if(n!=null&&(!Double.isFinite(n)||n<=0))throw new IllegalArgumentException("Revise "+campo+": debe ser positivo.");}
 private static void yefriLermaPorcentaje(Double n,String campo){if(n!=null&&(!Double.isFinite(n)||n<0||n>100))throw new IllegalArgumentException("Revise "+campo+": debe estar entre 0 y 100 %.");}
}
