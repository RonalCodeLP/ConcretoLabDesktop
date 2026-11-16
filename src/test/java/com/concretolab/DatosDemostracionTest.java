package com.concretolab;
import com.concretolab.model.*;
import com.concretolab.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes=ConcretoLabConfig.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
@EnabledIfSystemProperty(named="concretolab.demo",matches="true")
class DatosDemostracionTest {
 @Autowired ProyectoService proyectos; @Autowired MaterialService materiales; @Autowired MezclaService mezclas; @Autowired RegistroGranulometriaService granulometrias; @Autowired ModeloCerchaService cerchas;
 @Autowired PlatformTransactionManager tx;
 @Test void crearDatos(){
  new TransactionTemplate(tx).executeWithoutResult(estado -> {
  String nombre="DEMO - Edificación de práctica";
  if(proyectos.proyectos().stream().anyMatch(p->p.getNombre().equals(nombre)))throw new IllegalStateException("El proyecto DEMO ya existe; no se duplicarán datos.");
  Proyecto p=proyectos.crearProyecto(nombre,"Datos ficticios para aprendizaje");
  proyectos.crearPlantilla(p.getId(),new ProyectoService.Plantilla(5,4,1.5,1.5,.5,.4,3,.3,.5));
  Material arena=new Material();arena.setTipo("AGREGADO FINO");arena.setNombre("DEMO - Arena de ensayo");arena.setProcedencia("Cantera ficticia");arena.setReferenciaEnsayo("DEMO-LAB-01");arena.setFechaEnsayo("2026-11-01");arena.setDensidadRelativa(2.65);arena.setAbsorcion(1.0);arena.setHumedad(3.0);arena.setModuloFinura(2.7);arena=materiales.guardar(arena);
  Material piedra=new Material();piedra.setTipo("AGREGADO GRUESO");piedra.setNombre("DEMO - Piedra de ensayo");piedra.setProcedencia("Cantera ficticia");piedra.setReferenciaEnsayo("DEMO-LAB-02");piedra.setFechaEnsayo("2026-11-01");piedra.setDensidadRelativa(2.65);piedra.setAbsorcion(1.0);piedra.setHumedad(.5);piedra.setTamanoMaximoMm(19.0);piedra.setPesoUnitarioCompactado(1600.0);materiales.guardar(piedra);
  Material cemento=new Material();cemento.setTipo("CEMENTO");cemento.setNombre("DEMO - Cemento de ensayo");cemento.setDensidadRelativa(3.15);cemento.setReferenciaEnsayo("Datos ficticios");materiales.guardar(cemento);
  RegistroGranulometria g=new RegistroGranulometria();g.setMaterialId(arena.getId());g.setIdentificacion("DEMO - Tamizado de arena");g.setFecha("2026-11-01");g.setMasaInicial(1000);g.setFondo(50);g.setMediciones("4.75; 20\n2.36; 80\n1.18; 200\n0.60; 300\n0.30; 250\n0.15; 100");granulometrias.guardar(g);
  Mezcla m=new Mezcla();m.setProyectoId(p.getId());m.setNombre("DEMO - Mezcla de práctica");m.setReferencia("Datos ficticios; no utilizar en obra");m.setResistencia(21);m.setCemento(350);m.setAgua(180);m.setFinoSss(808);m.setGruesoSss(1010);m.setAbsorcionFino(1);m.setHumedadFino(3);m.setAbsorcionGrueso(1);m.setHumedadGrueso(.5);m.setMaterialFino("DEMO - Arena de ensayo | informe DEMO-LAB-01");m.setMaterialGrueso("DEMO - Piedra de ensayo | informe DEMO-LAB-02");m=mezclas.guardar(m);
  Ensayo e=new Ensayo();e.setMezclaId(m.getId());e.setIdentificacion("DEMO - Probeta 28 días");e.setFecha("2026-11-15");e.setEdadDias(28);e.setResistenciaMpa(24);e.setAsentamientoMm(75);e.setObservaciones("Datos ficticios, no representan ensayos reales.");mezclas.guardarEnsayo(e);
  ModeloCercha c=new ModeloCercha();c.setProyectoId(p.getId());c.setNombre("DEMO - Cercha triangular");c.setNudos("A; 0; 0; SI; SI; 0; 0\nB; 4; 0; NO; SI; 0; 0\nC; 2; 3; NO; NO; 0; -30");c.setBarras("AB; A; B; 200000; 20\nAC; A; C; 200000; 20\nBC; B; C; 200000; 20");cerchas.guardar(c);
  });
 }
}
