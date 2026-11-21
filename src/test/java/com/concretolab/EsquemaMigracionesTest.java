package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.io.File;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Las migraciones de Flyway (db/migration) deben dejar las tablas que las entidades necesitan, tanto en una base
 * nueva como en una creada antes con ddl-auto=update. Sustituye a ddl-auto=validate, que no sirve con SQLite.
 */
class EsquemaMigracionesTest {

    private static ConfigurableApplicationContext iniciar(File db) {
        return new SpringApplicationBuilder(ConcretoLabApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + db.getAbsolutePath(), "--spring.main.web-application-type=none",
                        "--app.seeder.enabled=false", "--logging.file.name=target/pruebas.log"); // argumentos: máxima prioridad, nunca la base real
    }

    private static List<String> consultarCadaEntidad(ConfigurableApplicationContext contexto) {
        var fallos = new ArrayList<String>();
        var emf = contexto.getBean(EntityManagerFactory.class);
        try (var em = emf.createEntityManager()) {
            for (var entidad : emf.getMetamodel().getEntities()) {
                try {
                    em.createQuery("select e from " + entidad.getName() + " e", entidad.getJavaType()).setMaxResults(1).getResultList();
                } catch (RuntimeException ex) {
                    fallos.add(entidad.getName() + ": " + ex.getMessage());
                }
            }
        }
        return fallos;
    }

    @Test
    void unaBaseNuevaQuedaCompletaConLasMigraciones() throws Exception {
        File db = Files.createTempFile("nueva", ".db").toFile();
        db.deleteOnExit();
        try (var contexto = iniciar(db)) {
            assertThat(consultarCadaEntidad(contexto)).as("entidades sin su tabla o columna").isEmpty();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath()); var st = c.createStatement();
             var rs = st.executeQuery("select count(*) from flyway_schema_history where success = 1 and version in ('1','2')")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void unaBaseCreadaAntesSeCompletaSinPerderDatos() throws Exception {
        File db = Files.createTempFile("antigua", ".db").toFile();
        db.deleteOnExit();
        // Simula una base creada por ddl-auto=update antes de las migraciones: sin tabla bitacora y sin historial de Flyway.
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath()); var st = c.createStatement()) {
            for (String linea : new String(getClass().getResourceAsStream("/db/migration/V1__esquema_base.sql").readAllBytes(), "UTF-8").split("\\R"))
                if (linea.startsWith("CREATE TABLE")) st.execute(linea.replace(" IF NOT EXISTS", ""));
            st.execute("insert into proyectos (nombre, ubicacion) values ('Proyecto anterior', 'Cusco')");
        }
        try (var contexto = iniciar(db)) {
            assertThat(consultarCadaEntidad(contexto)).isEmpty();
            var proyectos = contexto.getBean(com.concretolab.service.YefriLermaProyectoService.class).proyectos();
            assertThat(proyectos).extracting("nombre").contains("Proyecto anterior");
            assertThat(contexto.getBean(com.concretolab.service.BitacoraService.class).recientes()).isEmpty();
        }
    }
}