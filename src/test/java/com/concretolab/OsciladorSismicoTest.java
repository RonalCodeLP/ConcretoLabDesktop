package com.concretolab;
import com.concretolab.service.OsciladorSismico;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OsciladorSismicoTest {
 @Test void calculaPeriodoYRespuesta(){var r=OsciladorSismico.yefriLermaCalcular(10,1000,.2);assertEquals(2*Math.PI*.1,r.periodoSegundos(),1e-10);assertEquals(19.6133,r.fuerzaKn(),1e-8);assertEquals(19.6133,r.desplazamientoMm(),1e-8);}
 @Test void rechazaDatosInvalidos(){assertThrows(IllegalArgumentException.class,()->OsciladorSismico.yefriLermaCalcular(0,1000,.2));assertThrows(IllegalArgumentException.class,()->OsciladorSismico.yefriLermaCalcular(10,1000,-1));}
}
