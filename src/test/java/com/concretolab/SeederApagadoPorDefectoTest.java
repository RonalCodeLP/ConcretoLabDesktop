package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import com.concretolab.seeder.DatabaseSeeder;
import com.concretolab.service.YefriLermaProyectoService;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = ConcretoLabApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeederApagadoPorDefectoTest {
    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("seeder-apagado", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
    }

    @Autowired ApplicationContext contexto;
    @Autowired YefriLermaProyectoService proyectos;

    @Test
    void noSeCreaElSeederNiLosDatos() {
        assertThat(contexto.getBeanNamesForType(DatabaseSeeder.class)).isEmpty();
        assertThat(proyectos.proyectos()).isEmpty();
    }
}
