package com.concretolab.service;
import com.concretolab.model.Material;
import com.concretolab.repository.MaterialRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MaterialService {
 private final MaterialRepository repository;
 public MaterialService(MaterialRepository repository){this.repository=repository;}
 @Transactional(readOnly=true) public List<Material> listar(){return repository.findAllByOrderByNombreAsc();}
 @Transactional(readOnly=true) public List<Material> porTipo(String tipo){return repository.findByTipoOrderByNombreAsc(tipo);}
 @Transactional public Material guardar(Material m){
  if(m==null||m.getNombre()==null||m.getNombre().isBlank()||m.getNombre().trim().length()>120)throw new IllegalArgumentException("Ingrese un nombre de hasta 120 caracteres.");
  if(m.getTipo()==null||!Set.of("CEMENTO","AGREGADO FINO","AGREGADO GRUESO").contains(m.getTipo()))throw new IllegalArgumentException("Seleccione un tipo de material.");
  m.setNombre(m.getNombre().trim());
  validarPositivo(m.getDensidadRelativa(),"densidad relativa"); validarPositivo(m.getModuloFinura(),"módulo de finura");
  validarPositivo(m.getTamanoMaximoMm(),"tamaño máximo"); validarPositivo(m.getPesoUnitarioCompactado(),"peso unitario compactado");
  porcentaje(m.getAbsorcion(),"absorción");porcentaje(m.getHumedad(),"humedad");
  if(m.getFechaEnsayo()!=null&&!m.getFechaEnsayo().isBlank())try{LocalDate.parse(m.getFechaEnsayo());}catch(Exception ex){throw new IllegalArgumentException("Ingrese una fecha de ensayo válida.");}
  for(String s:List.of(texto(m.getProcedencia()),texto(m.getReferenciaEnsayo()),texto(m.getObservaciones())))if(s.length()>255)throw new IllegalArgumentException("Los textos no pueden superar 255 caracteres.");
  return repository.save(m);
 }
 private static String texto(String s){return s==null?"":s;}
 private static void validarPositivo(Double n,String campo){if(n!=null&&(!Double.isFinite(n)||n<=0))throw new IllegalArgumentException("Revise "+campo+": debe ser positivo.");}
 private static void porcentaje(Double n,String campo){if(n!=null&&(!Double.isFinite(n)||n<0||n>100))throw new IllegalArgumentException("Revise "+campo+": debe estar entre 0 y 100 %.");}
}
