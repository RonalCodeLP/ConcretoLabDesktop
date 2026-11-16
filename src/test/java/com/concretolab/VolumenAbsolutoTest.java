package com.concretolab;
import com.concretolab.service.VolumenAbsolutoService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VolumenAbsolutoTest {
 @Test void balanceaUnMetroCubico(){
  var d=new VolumenAbsolutoService.Datos(180,.5,3.0,1000,2.5,2.5,2);
  var r=VolumenAbsolutoService.calcular(d);
  assertEquals(360,r.cemento(),1e-9);assertEquals(700,r.finoSss(),1e-9);
  assertEquals(1,r.volumenAgua()+r.volumenCemento()+r.volumenGrueso()+r.volumenFino()+r.volumenAire(),1e-9);
 }
 @Test void rechazaVolumenInviable(){assertThrows(IllegalArgumentException.class,()->VolumenAbsolutoService.calcular(new VolumenAbsolutoService.Datos(500,.1,3,2000,2.5,2.5,2)));}
}
