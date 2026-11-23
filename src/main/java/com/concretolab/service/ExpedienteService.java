package com.concretolab.service;
import com.concretolab.model.ExpedienteEstructural;
import com.concretolab.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class ExpedienteService {
 private final ExpedienteRepository yefriLermaExpedientes;private final ProyectoRepository proyectos;
 public ExpedienteService(ExpedienteRepository e,ProyectoRepository p){yefriLermaExpedientes=e;proyectos=p;}
 @Transactional(readOnly=true) public Optional<ExpedienteEstructural> yefriLermaObtener(Long id){return yefriLermaExpedientes.findById(id);}
 @Transactional public ExpedienteEstructural yefriLermaGuardar(ExpedienteEstructural e){
  if(e==null||e.getProyectoId()==null||!proyectos.existsById(e.getProyectoId()))throw new IllegalArgumentException("Seleccione un proyecto existente.");
  for(String s:Arrays.asList(e.getUbicacion(),e.getUso(),e.getFechaProyecto(),e.getEstadoProyecto(),e.getProfesional(),e.getNorma(),e.getJustificacionNorma(),e.getEstudioSuelos(),e.getObservaciones()))if(s!=null&&s.length()>255)throw new IllegalArgumentException("Los campos admiten hasta 255 caracteres.");
  if(e.getFechaProyecto()!=null&&!e.getFechaProyecto().isBlank())try{LocalDate.parse(e.getFechaProyecto());}catch(Exception x){throw new IllegalArgumentException("Ingrese la fecha en formato AAAA-MM-DD.");}
  return yefriLermaExpedientes.save(e);
 }
}
