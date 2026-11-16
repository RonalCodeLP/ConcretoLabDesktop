package com.concretolab;
import com.concretolab.service.AnalisisPortico;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalisisPorticoTest {
 @Test void voladizoConCargaEnExtremo(){
  var nodos=List.of(new AnalisisPortico.Nudo("A",0,0,true,true,true,0,0,0),new AnalisisPortico.Nudo("B",2,0,false,false,false,0,-10,0));
  var barras=List.of(new AnalisisPortico.Barra("AB","A","B",200000000,.01,.0001));var r=AnalisisPortico.resolver(nodos,barras);
  assertEquals(-10*8.0/(3*200000000*.0001),r.nudos().get(1).uyM(),1e-10);
  assertEquals(10,r.nudos().get(0).ryKn(),1e-8);assertEquals(20,r.nudos().get(0).momentoKnM(),1e-8);
 }
 @Test void sinApoyosEsInestable(){var n=List.of(new AnalisisPortico.Nudo("A",0,0,false,false,false,0,0,0),new AnalisisPortico.Nudo("B",2,0,false,false,false,0,-10,0));var b=List.of(new AnalisisPortico.Barra("AB","A","B",200000000,.01,.0001));assertThrows(IllegalArgumentException.class,()->AnalisisPortico.resolver(n,b));}
 @Test void nudoAisladoConApoyosNoSeAcepta(){
  var n=List.of(new AnalisisPortico.Nudo("A",0,0,true,true,true,0,0,0),new AnalisisPortico.Nudo("B",2,0,false,false,false,0,-10,0),new AnalisisPortico.Nudo("C",5,0,true,true,true,0,0,0));
  var b=List.of(new AnalisisPortico.Barra("AB","A","B",200000000,.01,.0001));
  assertThrows(IllegalArgumentException.class,()->AnalisisPortico.resolver(n,b));
 }
 @Test void cargaAxialEnVoladizoTieneEquilibrio(){
  var n=List.of(new AnalisisPortico.Nudo("A",0,0,true,true,true,0,0,0),new AnalisisPortico.Nudo("B",2,0,false,false,false,10,0,0));
  var b=List.of(new AnalisisPortico.Barra("AB","A","B",200000000,.01,.0001));var r=AnalisisPortico.resolver(n,b);
  assertEquals(10*2.0/(200000000*.01),r.nudos().get(1).uxM(),1e-12);assertEquals(-10,r.nudos().get(0).rxKn(),1e-9);
 }
}
