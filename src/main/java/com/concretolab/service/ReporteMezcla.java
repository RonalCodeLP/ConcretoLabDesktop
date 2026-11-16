package com.concretolab.service;
import com.concretolab.model.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

public final class ReporteMezcla {
 private ReporteMezcla(){}
 private static String f(double n){return String.format(Locale.ROOT,"%.3f",n);}
 public static byte[] generar(Proyecto p,Mezcla m,double volumen,List<Ensayo> ensayos){
  var r=MezclaService.calcular(m,volumen);ByteArrayOutputStream out=new ByteArrayOutputStream();Document d=new Document(PageSize.A4,40,40,40,40);
  PdfWriter.getInstance(d,out);d.open();
  d.add(new Paragraph("ConcretoLab | Resumen de dosificación",FontFactory.getFont(FontFactory.HELVETICA_BOLD,16)));
  d.add(new Paragraph("Proyecto: "+p.getNombre()+"    Mezcla: "+m.getNombre()));
  d.add(new Paragraph("Referencia: "+(m.getReferencia()==null?"":m.getReferencia())));
  d.add(new Paragraph("Agregado fino: "+(m.getMaterialFino()==null?"No especificado":m.getMaterialFino())));
  d.add(new Paragraph("Agregado grueso: "+(m.getMaterialGrueso()==null?"No especificado":m.getMaterialGrueso())));
  d.add(new Paragraph(" "));d.add(new Paragraph("Datos de diseño ingresados por el usuario",FontFactory.getFont(FontFactory.HELVETICA_BOLD,12)));
  d.add(new Paragraph("f'c: "+f(m.getResistencia())+" MPa    Cemento: "+f(m.getCemento())+" kg/m³    Agua de diseño: "+f(m.getAgua())+" kg/m³"));
  d.add(new Paragraph("Fino SSS: "+f(m.getFinoSss())+" kg/m³; absorción: "+f(m.getAbsorcionFino())+" %; humedad total: "+f(m.getHumedadFino())+" %."));
  d.add(new Paragraph("Grueso SSS: "+f(m.getGruesoSss())+" kg/m³; absorción: "+f(m.getAbsorcionGrueso())+" %; humedad total: "+f(m.getHumedadGrueso())+" %."));
  d.add(new Paragraph("Observaciones: "+(m.getObservaciones()==null?"":m.getObservaciones())));
  d.add(new Paragraph(" "));d.add(new Paragraph("Cantidades corregidas para "+f(volumen)+" m³",FontFactory.getFont(FontFactory.HELVETICA_BOLD,12)));
  d.add(new Paragraph("Cemento: "+f(m.getCemento()*volumen)+" kg; agua a añadir: "+f(r.aguaAgregar())+" kg."));
  d.add(new Paragraph("Fino húmedo: "+f(r.finoHumedo())+" kg; grueso húmedo: "+f(r.gruesoHumedo())+" kg."));
  d.add(new Paragraph("Relación agua/cemento de diseño: "+f(r.relacionAguaCemento())));
  d.add(new Paragraph(" "));d.add(new Paragraph("Ensayos físicos registrados",FontFactory.getFont(FontFactory.HELVETICA_BOLD,12)));
  if(ensayos.isEmpty())d.add(new Paragraph("No se registraron ensayos."));
  for(Ensayo e:ensayos)d.add(new Paragraph(e.getIdentificacion()+" | "+e.getFecha()+" | edad: "+f(e.getEdadDias())+" días | resistencia: "+f(e.getResistenciaMpa())+" MPa | asentamiento: "+f(e.getAsentamientoMm())+" mm | "+(e.getObservaciones()==null?"":e.getObservaciones())));
  d.add(new Paragraph(" "));d.add(new Paragraph("Registro de dosificación proporcionada por el usuario. No constituye diseño ACI, aprobación ni verificación de conformidad. Requiere revisión profesional y mezcla de prueba."));d.close();return out.toByteArray();
 }
}
