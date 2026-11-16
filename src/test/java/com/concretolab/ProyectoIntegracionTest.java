package com.concretolab;
import com.concretolab.model.*;
import com.concretolab.service.ProyectoService;
import com.concretolab.service.ModeloCerchaService;
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
    @Autowired ProyectoService servicio;
    @Autowired ModeloCerchaService cerchas;
    @Test void guardaElementosYValidaDimensiones(){
        Proyecto p=servicio.crearProyecto("Proyecto de prueba","Lima");
        Elemento e=new Elemento();e.setProyectoId(p.getId());e.setTipo("ZAPATA");e.setNombre("Z1");e.setLargo(2);e.setAncho(2);e.setAltura(.5);
        e=servicio.guardar(e);assertEquals(2.0,e.volumen(),0.000001);assertEquals(1,servicio.elementos(p.getId()).size());
        Elemento invalido=new Elemento();invalido.setProyectoId(p.getId());invalido.setTipo("VIGA");invalido.setNombre("V1");invalido.setLargo(-1);invalido.setAncho(1);invalido.setAltura(1);
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(invalido));assertEquals(1,servicio.elementos(p.getId()).size());
        servicio.eliminar(e.getId());assertTrue(servicio.elementos(p.getId()).isEmpty());
    }
    @Test void plantillaGeneraElementosYRechazaMedidasIncompatibles(){
        Proyecto p=servicio.crearProyecto("Plantilla","Lima");
        servicio.crearPlantilla(p.getId(),new ProyectoService.Plantilla(5,4,1.5,1.5,.5,.4,3,.3,.5));
        var lista=servicio.elementos(p.getId());assertEquals(12,lista.size());
        assertEquals(4,lista.stream().filter(e->"ZAPATA".equals(e.getTipo())).count());
        assertEquals(4,lista.stream().filter(e->"COLUMNA".equals(e.getTipo())).count());
        assertEquals(4,lista.stream().filter(e->"VIGA".equals(e.getTipo())).count());
        assertThrows(IllegalArgumentException.class,()->servicio.crearPlantilla(p.getId(),new ProyectoService.Plantilla(.2,4,1.5,1.5,.5,.4,3,.3,.5)));
        assertEquals(12,servicio.elementos(p.getId()).size());
    }
    @Test void guardaCerchaEnSuProyectoYConservaSusDatos(){
        Proyecto p=servicio.crearProyecto("Cercha guardada",""),otro=servicio.crearProyecto("Otro proyecto","");
        ModeloCercha m=new ModeloCercha();m.setProyectoId(p.getId());m.setNombre("Armadura A");
        m.setNudos("A;0;0;SI;SI;0;0\nB;2;0;NO;SI;10;0");m.setBarras("AB;A;B;200000;10");m=cerchas.guardar(m);
        assertEquals(1,cerchas.listar(p.getId()).size());assertTrue(cerchas.listar(otro.getId()).isEmpty());
        var datos=ModeloCerchaService.interpretar(cerchas.listar(p.getId()).get(0).getNudos(),cerchas.listar(p.getId()).get(0).getBarras());
        assertEquals(.1,com.concretolab.service.AnalisisCercha.resolver(datos.nudos(),datos.barras()).nudos().get(1).uxMm(),1e-9);
        m.setProyectoId(otro.getId());ModeloCercha copia=m;assertThrows(IllegalArgumentException.class,()->cerchas.guardar(copia));
    }
}
