package com.concretolab;
import com.concretolab.model.*;
import com.concretolab.service.YefriLermaProyectoService;
import com.concretolab.service.ExpedienteService;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=ConcretoLabApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProyectoIntegracionTest {
    private static final Path BASE;
    static {try{BASE=Files.createTempDirectory("concretolab-test-").resolve("test.db");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+BASE.toAbsolutePath());}
    @Autowired YefriLermaProyectoService servicio;
    @Autowired ExpedienteService expedientes;
    @Autowired com.concretolab.repository.MezclaRepository mezclas;
    @Autowired com.concretolab.repository.EnsayoRepository ensayos;

    @Test void eliminaSoloElProyectoSeleccionadoYSusDatos() {
        var eliminar = servicio.yefriLermaCrearProyecto("Proyecto para eliminar", "");
        var conservar = servicio.yefriLermaCrearProyecto("Proyecto para conservar", "");
        var plantilla = new YefriLermaProyectoService.Plantilla(5,4,1.5,1.5,.5,.4,3,.3,.5);
        servicio.yefriLermaCrearPlantilla(eliminar.getId(), plantilla);
        servicio.yefriLermaCrearPlantilla(conservar.getId(), plantilla);

        var mezcla = new Mezcla();
        mezcla.setProyectoId(eliminar.getId());
        mezcla.setNombre("Mezcla vinculada");
        var guardada = mezclas.save(mezcla);
        var ensayo = new Ensayo();
        ensayo.setMezclaId(guardada.getId());
        var ensayoId = ensayos.save(ensayo).getId();

        servicio.yefriLermaEliminarProyecto(eliminar.getId());

        assertTrue(servicio.proyectos().stream().noneMatch(p -> p.getId().equals(eliminar.getId())));
        assertTrue(servicio.elementos(eliminar.getId()).isEmpty());
        assertFalse(mezclas.existsById(guardada.getId()));
        assertFalse(ensayos.existsById(ensayoId));
        assertEquals(12, servicio.elementos(conservar.getId()).size());
    }
    @Test void guardaElementosYValidaDimensiones(){
        Proyecto p=servicio.yefriLermaCrearProyecto("Proyecto de prueba","Lima");
        Elemento e=new Elemento();e.setProyectoId(p.getId());e.setTipo("ZAPATA");e.setNombre("Z1");e.setLargo(2);e.setAncho(2);e.setAltura(.5);
        e=servicio.yefriLermaGuardar(e);assertEquals(2.0,e.volumen(),0.000001);assertEquals(1,servicio.elementos(p.getId()).size());
        Elemento invalido=new Elemento();invalido.setProyectoId(p.getId());invalido.setTipo("VIGA");invalido.setNombre("V1");invalido.setLargo(-1);invalido.setAncho(1);invalido.setAltura(1);
        assertThrows(IllegalArgumentException.class,()->servicio.yefriLermaGuardar(invalido));assertEquals(1,servicio.elementos(p.getId()).size());
        servicio.yefriLermaEliminar(e.getId());assertTrue(servicio.elementos(p.getId()).isEmpty());
    }
    @Test void plantillaGeneraElementosYRechazaMedidasIncompatibles(){
        Proyecto p=servicio.yefriLermaCrearProyecto("Plantilla","Lima");
        servicio.yefriLermaCrearPlantilla(p.getId(),new YefriLermaProyectoService.Plantilla(5,4,1.5,1.5,.5,.4,3,.3,.5));
        var lista=servicio.elementos(p.getId());assertEquals(12,lista.size());
        assertEquals(4,lista.stream().filter(e->"ZAPATA".equals(e.getTipo())).count());
        assertEquals(4,lista.stream().filter(e->"COLUMNA".equals(e.getTipo())).count());
        assertEquals(4,lista.stream().filter(e->"VIGA".equals(e.getTipo())).count());
        assertThrows(IllegalArgumentException.class,()->servicio.yefriLermaCrearPlantilla(p.getId(),new YefriLermaProyectoService.Plantilla(.2,4,1.5,1.5,.5,.4,3,.3,.5)));
        assertEquals(12,servicio.elementos(p.getId()).size());
    }
    @Test void guardaExpedienteYValidaFecha(){
        Proyecto p=servicio.yefriLermaCrearProyecto("Expediente","Lima");ExpedienteEstructural e=new ExpedienteEstructural();e.setProyectoId(p.getId());e.setNorma("E.030 - versión indicada por profesional");e.setFechaProyecto("2026-11-16");expedientes.yefriLermaGuardar(e);
        assertEquals("2026-11-16",expedientes.yefriLermaObtener(p.getId()).orElseThrow().getFechaProyecto());
        e.setFechaProyecto("fecha inválida");assertThrows(IllegalArgumentException.class,()->expedientes.yefriLermaGuardar(e));
    }
    @Test void escenariosPersistidosRespondenAlMismoPulso(){
        servicio.yefriLermaPrepararEscenarios();
        servicio.yefriLermaPrepararEscenarios();
        double[] pulso=new double[2501];
        for(int i=0;i<=500;i++)pulso[i]=0.980665*Math.sin(2*Math.PI*i*0.002);
        var demos=servicio.proyectos().stream().filter(p->YefriLermaProyectoService.YEFRI_LERMA_ESCENARIOS.contains(p.getNombre())).toList();
        assertEquals(3,demos.size());
        double regular=0, flexible=0;
        for(var p:demos){
            int caso=YefriLermaProyectoService.YEFRI_LERMA_ESCENARIOS.indexOf(p.getNombre());
            var elementos=servicio.elementos(p.getId());
            var r=com.concretolab.service.EdificioCortante.yefriLermaSimular(elementos,25000,true,80,0.05,0.002,pulso);
            int n=caso==0?4:3;
            assertEquals(n,r.niveles().size());assertTrue(r.maximo()>0);
            if(caso!=1){
                double w=2*Math.sqrt(r.niveles().get(0).k()/80)*Math.sin(Math.PI/(4*n+2));
                assertEquals(2*Math.PI/w,r.periodo(),1e-9);
            }
            if(caso==1)flexible=r.derivas()[0];
            if(caso==2)regular=r.derivas()[0];
            System.out.printf("Escenario %d: T=%.4f s, deriva primer piso %.4f%%%n",caso,r.periodo(),100*r.derivas()[0]);
        }
        assertTrue(flexible>regular);
    }}
