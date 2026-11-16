package com.concretolab;
import com.concretolab.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalisisCerchaTest {
 @Test void barraAxialRespondeConDeformacionYReaccionConocidas(){
  var n=List.of(new AnalisisCercha.Nudo("A",0,0,true,true,0,0),new AnalisisCercha.Nudo("B",2,0,false,true,10,0));
  var b=List.of(new AnalisisCercha.Barra("AB","A","B",200000,10));var r=AnalisisCercha.resolver(n,b);
  assertEquals(.1,r.nudos().get(1).uxMm(),1e-9);assertEquals(-10,r.nudos().get(0).rxKn(),1e-9);
  assertEquals(10,r.barras().get(0).axialKn(),1e-9);assertEquals(10,r.barras().get(0).tensionMpa(),1e-9);
 }
 @Test void cerchaTriangularCumpleEquilibrioYBarrasEsperadas(){
  var d=ModeloCerchaService.interpretar("A;0;0;SI;SI;0;0\nB;4;0;NO;SI;0;0\nC;2;3;NO;NO;0;-30","AB;A;B;200000;20\nAC;A;C;200000;20\nBC;B;C;200000;20");
  var r=AnalisisCercha.resolver(d.nudos(),d.barras());assertEquals(15,r.nudos().get(0).ryKn(),1e-8);assertEquals(15,r.nudos().get(1).ryKn(),1e-8);
  assertEquals(10,r.barras().get(0).axialKn(),1e-8);assertEquals(-5*Math.sqrt(13),r.barras().get(1).axialKn(),1e-8);assertEquals(-5*Math.sqrt(13),r.barras().get(2).axialKn(),1e-8);
 }
 @Test void detectaMecanismosYDatosInvalidos(){
  var d=ModeloCerchaService.interpretar("A;0;0;SI;SI;0;0\nB;2;0;NO;NO;10;0","AB;A;B;200000;10");
  assertThrows(IllegalArgumentException.class,()->AnalisisCercha.resolver(d.nudos(),d.barras()));
  assertThrows(IllegalArgumentException.class,()->ModeloCerchaService.interpretar("A;0;0;quiza;SI;0;0",""));
 }
}
