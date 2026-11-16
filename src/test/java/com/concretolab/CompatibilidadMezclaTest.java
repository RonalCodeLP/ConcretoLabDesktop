package com.concretolab;
import com.concretolab.repository.MezclaRepository;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=ConcretoLabConfig.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CompatibilidadMezclaTest {
 private static final Path BASE;
 static {try{
  BASE=Files.createTempDirectory("concretolab-anterior-").resolve("test.db");
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+BASE);var s=c.createStatement()){
   s.execute("CREATE TABLE mezclas (id INTEGER PRIMARY KEY, proyecto_id INTEGER, nombre TEXT, referencia TEXT, observaciones TEXT, resistencia REAL NOT NULL, cemento REAL NOT NULL, agua REAL NOT NULL, fino_sss REAL NOT NULL, grueso_sss REAL NOT NULL, absorcion_fino REAL NOT NULL, humedad_fino REAL NOT NULL, absorcion_grueso REAL NOT NULL, humedad_grueso REAL NOT NULL)");
   s.execute("INSERT INTO mezclas VALUES (1,1,'Mezcla anterior','Informe','',21,350,180,800,1000,1,2,1,1)");
  }
 }catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+BASE.toAbsolutePath());}
 @Autowired MezclaRepository mezclas;
 @Test void conservaMezclaDeEsquemaAnterior(){var m=mezclas.findById(1L).orElseThrow();assertEquals("Mezcla anterior",m.getNombre());assertEquals(350,m.getCemento());assertNull(m.getMaterialFino());}
}
