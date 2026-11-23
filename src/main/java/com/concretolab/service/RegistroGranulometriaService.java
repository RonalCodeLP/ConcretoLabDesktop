package com.concretolab.service;
import com.concretolab.model.RegistroGranulometria;
import com.concretolab.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RegistroGranulometriaService {
 private final GranulometriaRepository yefriLermaRegistros;private final MaterialRepository materiales;
 public RegistroGranulometriaService(GranulometriaRepository registros,MaterialRepository materiales){this.yefriLermaRegistros=registros;this.materiales=materiales;}
 public static List<GranulometriaService.Tamiz> yefriLermaLeer(String texto){
  if(texto==null||texto.isBlank()||texto.length()>10000)throw new IllegalArgumentException("Ingrese los tamices y las masas retenidas.");
  List<GranulometriaService.Tamiz> resultado=new ArrayList<>();
  try{for(String linea:texto.split("\\R")){if(linea.isBlank())continue;String[] partes=linea.split(";");if(partes.length!=2)throw new IllegalArgumentException("Use una línea por tamiz con el formato abertura; masa retenida.");resultado.add(new GranulometriaService.Tamiz(Double.parseDouble(partes[0].trim().replace(',','.')),Double.parseDouble(partes[1].trim().replace(',','.'))));}}
  catch(NumberFormatException ex){throw new IllegalArgumentException("Ingrese números válidos en los tamices.");}return resultado;
 }
 @Transactional(readOnly=true) public List<RegistroGranulometria> yefriLermaListar(Long id){return yefriLermaRegistros.findByMaterialIdOrderByIdDesc(id);}
 @Transactional public RegistroGranulometria yefriLermaGuardar(RegistroGranulometria r){
  if(r.getMaterialId()==null||!materiales.existsById(r.getMaterialId()))throw new IllegalArgumentException("Seleccione un material existente.");
  if(r.getIdentificacion()==null||r.getIdentificacion().isBlank()||r.getIdentificacion().length()>120)throw new IllegalArgumentException("Ingrese una identificación de hasta 120 caracteres.");
  try{LocalDate.parse(r.getFecha());}catch(Exception e){throw new IllegalArgumentException("Ingrese una fecha válida.");}
  GranulometriaService.yefriLermaCalcular(r.getMasaInicial(),yefriLermaLeer(r.getMediciones()),r.getFondo());return yefriLermaRegistros.save(r);
 }
}
