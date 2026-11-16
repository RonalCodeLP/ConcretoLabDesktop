package com.concretolab;

import com.concretolab.model.Elemento;
import com.concretolab.service.HistoriaPiso;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoriaPisoTest {
    @Test void aceleracionConstanteSolucionCerrada() {
        double[] a=new double[1001]; Arrays.fill(a,1);
        double dt=0.001;
        var r=HistoriaPiso.resolver(1,16,0,dt,a);
        for(int i=0;i<a.length;i++){
            double t=i*dt;
            assertEquals((Math.cos(4*t)-1)/16,r.desplazamiento()[i],4e-7);
            assertEquals(-Math.sin(4*t)/4,r.velocidad()[i],2e-6);
        }
    }
    @Test void geometriaYEjes() {
        Elemento c=new Elemento(); c.setTipo("COLUMNA"); c.setAltura(3); c.setLargo(0.4); c.setAncho(0.2);
        assertEquals(25000000*0.2*Math.pow(0.4,3)/27,HistoriaPiso.piso(List.of(c),25000,true).rigidez(),1e-8);
        assertEquals(4,HistoriaPiso.piso(List.of(c),25000,true).rigidez()/HistoriaPiso.piso(List.of(c),25000,false).rigidez(),1e-10);
        assertThrows(IllegalArgumentException.class,()->HistoriaPiso.piso(List.of(c,c),25000,false));
    }
    @Test void rechazaMuestreoInsuficiente(){
        assertThrows(IllegalArgumentException.class,()->HistoriaPiso.resolver(1,100,0.05,1,new double[]{0,1}));
    }
}
