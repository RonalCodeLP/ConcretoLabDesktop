package com.concretolab;
import com.concretolab.model.*;
import com.concretolab.service.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=ConcretoLabApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class MezclaIntegracionTest {
 private static final Path BASE;
 static{try{BASE=Files.createTempDirectory("concretolab-mezcla-").resolve("test.db");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+BASE.toAbsolutePath());}
 @Autowired YefriLermaProyectoService proyectos; @Autowired MezclaService mezclas;
 @Autowired MaterialService materiales;
 @Autowired RegistroGranulometriaService granulometrias;
 @Test void registraYCalculaConHumedadYEnsayo() throws Exception {
  Proyecto p=proyectos.yefriLermaCrearProyecto("Prueba mezcla","");Mezcla m=new Mezcla();m.setProyectoId(p.getId());m.setNombre("M1");m.setResistencia(21);m.setCemento(350);m.setAgua(180);
  m.setFinoSss(808);m.setAbsorcionFino(1);m.setHumedadFino(3);m.setGruesoSss(1010);m.setAbsorcionGrueso(1);m.setHumedadGrueso(0.5);
  m=mezclas.yefriLermaGuardar(m);var r=MezclaService.yefriLermaCalcular(m,2);
  assertEquals(1648,r.finoHumedo(),0.00001);assertEquals(2010,r.gruesoHumedo(),0.00001);assertEquals(338,r.aguaAgregar(),0.00001);
  assertEquals(1,mezclas.yefriLermaListar(p.getId()).size());Ensayo e=new Ensayo();e.setMezclaId(m.getId());e.setFecha("2026-11-01");e.setIdentificacion("P1");e.setEdadDias(28);e.setResistenciaMpa(24);e.setAsentamientoMm(75);
  mezclas.yefriLermaGuardarEnsayo(e);assertEquals(1,mezclas.ensayos(m.getId()).size());
  PdfReader pdf=new PdfReader(ReporteMezcla.yefriLermaGenerar(p,m,2,mezclas.ensayos(m.getId())));String texto=new PdfTextExtractor(pdf).getTextFromPage(1);
  assertTrue(texto.contains("Prueba mezcla"));assertTrue(texto.contains("338.000"));assertTrue(texto.contains("P1"));pdf.close();
  assertThrows(IllegalArgumentException.class,()->MezclaService.yefriLermaCalcular(null,1));
  e.setFecha("invalida");assertThrows(IllegalArgumentException.class,()->mezclas.yefriLermaGuardarEnsayo(e));
 }
 @Test void conservaMaterialesYRechazaDatosInvalidos(){
  Material fino=new Material();fino.setNombre("Arena A");fino.setTipo("AGREGADO FINO");fino.setAbsorcion(1.2);fino.setHumedad(3.5);fino.setFechaEnsayo("2026-10-10");fino.setReferenciaEnsayo("Informe L-01");
  Material guardado=materiales.yefriLermaGuardar(fino);assertNotNull(guardado.getId());
  assertTrue(materiales.yefriLermaPorTipo("AGREGADO FINO").stream().anyMatch(m->"Informe L-01".equals(m.getReferenciaEnsayo())));
  RegistroGranulometria g=new RegistroGranulometria();g.setMaterialId(guardado.getId());g.setIdentificacion("Tamizado A");g.setFecha("2026-11-09");g.setMasaInicial(1000);g.setFondo(750);g.setMediciones("4.75;100\n2.36;150");
  granulometrias.yefriLermaGuardar(g);var recuperado=granulometrias.yefriLermaListar(guardado.getId());assertEquals(1,recuperado.size());
  assertEquals(75,GranulometriaService.yefriLermaCalcular(recuperado.get(0).getMasaInicial(),RegistroGranulometriaService.yefriLermaLeer(recuperado.get(0).getMediciones()),recuperado.get(0).getFondo()).filas().get(1).pasaPorcentaje(),1e-9);
  Material invalido=new Material();invalido.setNombre("Piedra");invalido.setTipo("AGREGADO GRUESO");invalido.setHumedad(-2.0);
  assertThrows(IllegalArgumentException.class,()->materiales.yefriLermaGuardar(invalido));
  invalido.setHumedad(1.0);invalido.setFechaEnsayo("no es una fecha");assertThrows(IllegalArgumentException.class,()->materiales.yefriLermaGuardar(invalido));
 }
}
