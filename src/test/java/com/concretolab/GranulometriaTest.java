package com.concretolab;
import com.concretolab.service.GranulometriaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GranulometriaTest {
 @Test void calculaPorcentajesYBalanceDeMasas(){
  var r=GranulometriaService.calcular(1000,List.of(new GranulometriaService.Tamiz(4.75,100),new GranulometriaService.Tamiz(2.36,250),new GranulometriaService.Tamiz(1.18,400)),245);
  assertEquals(10,r.filas().get(0).retenidoPorcentaje(),1e-9);assertEquals(35,r.filas().get(1).retenidoAcumulado(),1e-9);
  assertEquals(25,r.filas().get(2).pasaPorcentaje(),1e-9);assertEquals(995,r.recuperadoG(),1e-9);assertEquals(-0.5,r.diferenciaPorcentaje(),1e-9);
 }
 @Test void rechazaAberturasDesordenadasYMasasImposibles(){
  assertThrows(IllegalArgumentException.class,()->GranulometriaService.calcular(100,List.of(new GranulometriaService.Tamiz(1,30),new GranulometriaService.Tamiz(2,20)),50));
  assertThrows(IllegalArgumentException.class,()->GranulometriaService.calcular(100,List.of(new GranulometriaService.Tamiz(2,101)),0));
  assertThrows(IllegalArgumentException.class,()->GranulometriaService.calcular(0,List.of(new GranulometriaService.Tamiz(2,1)),0));
 }
}
