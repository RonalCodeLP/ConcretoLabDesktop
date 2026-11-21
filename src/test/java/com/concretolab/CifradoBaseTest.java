package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.service.BaseCifrada;
import com.concretolab.service.BaseCifrada.Estado;
import com.concretolab.service.ClaveBaseService;
import com.concretolab.service.RespaldoDatos;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class CifradoBaseTest {

    private static void crearPlana(Path db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); var s = c.createStatement()) {
            s.execute("create table secreto(x text)");
            s.execute("insert into secreto values ('dato muy confidencial')");
        }
    }

    private static String leer(Path db, char[] clave) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db, BaseCifrada.propiedades(clave));
             var rs = c.createStatement().executeQuery("select x from secreto")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void cifraUnaBaseExistenteSinPerderDatosYSinDejarTextoLegible(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        assertThat(BaseCifrada.inspeccionar(db)).isEqualTo(Estado.SIN_CIFRAR);
        assertThat(BaseCifrada.inspeccionar(dir.resolve("no-existe.db"))).isEqualTo(Estado.NO_EXISTE);

        Path copia = BaseCifrada.cifrarExistente(db, "clave-segura-1".toCharArray());

        assertThat(BaseCifrada.inspeccionar(db)).isEqualTo(Estado.CIFRADA);
        assertThat(new String(Files.readAllBytes(db), StandardCharsets.ISO_8859_1)).doesNotContain("confidencial").doesNotContain("SQLite format");
        assertThat(leer(db, "clave-segura-1".toCharArray())).isEqualTo("dato muy confidencial");
        assertThat(BaseCifrada.claveCorrecta(db, "clave-segura-1".toCharArray())).isTrue();
        assertThat(BaseCifrada.claveCorrecta(db, "otra-clave-xx".toCharArray())).isFalse();
        assertThat(BaseCifrada.claveCorrecta(db, null)).isFalse();
        assertThat(BaseCifrada.inspeccionar(copia)).isEqualTo(Estado.SIN_CIFRAR); // la copia de seguridad previa es la original
        // el archivo cifrado se acepta como candidato de restauración
        assertThat(RespaldoDatos.esSqliteValido(db)).isTrue();
    }

    @Test
    void rechazaContrasenasCortasSinTocarLaBase(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        assertThatThrownBy(() -> BaseCifrada.cifrarExistente(db, "corta".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThat(BaseCifrada.inspeccionar(db)).isEqualTo(Estado.SIN_CIFRAR);
        assertThat(leer(db, null)).isEqualTo("dato muy confidencial");
    }

    @Test
    void cambiaLaContrasenaSoloConLaActualCorrecta(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        BaseCifrada.cifrarExistente(db, "clave-uno-123".toCharArray());
        assertThatThrownBy(() -> BaseCifrada.cambiarClave(db, "equivocada-12".toCharArray(), "clave-dos-456".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
        BaseCifrada.cambiarClave(db, "clave-uno-123".toCharArray(), "clave-dos-456".toCharArray());
        assertThat(BaseCifrada.claveCorrecta(db, "clave-uno-123".toCharArray())).isFalse();
        assertThat(leer(db, "clave-dos-456".toCharArray())).isEqualTo("dato muy confidencial");
    }

    @Test
    void laAplicacionArrancaConBaseCifradaYSusCopiasQuedanCifradas(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("app.db");
        char[] clave = "clave-app-2026".toCharArray();
        try (ConfigurableApplicationContext ctx = iniciar(db, clave)) {
            assertThat(BaseCifrada.inspeccionar(db)).isEqualTo(Estado.CIFRADA);
            DataSource ds = ctx.getBean(DataSource.class);
            Path copia = dir.resolve("copia.db");
            try (Connection c = ds.getConnection(); var s = c.createStatement()) {
                s.execute("VACUUM INTO '" + copia.toString().replace('\\', '/') + "'"); // lo que hace RespaldoService
            }
            assertThat(BaseCifrada.inspeccionar(copia)).isEqualTo(Estado.CIFRADA);
            assertThat(BaseCifrada.claveCorrecta(copia, clave)).isTrue();
            assertThat(BaseCifrada.claveCorrecta(copia, "otra-clave-xx".toCharArray())).isFalse();

            var servicio = ctx.getBean(ClaveBaseService.class);
            assertThat(servicio.estaCifrada()).isTrue();
            assertThatThrownBy(() -> servicio.cambiar("mala-clave-xx".toCharArray(), "nueva-clave-99".toCharArray()))
                    .isInstanceOf(IllegalArgumentException.class);
            servicio.cambiar(clave, "nueva-clave-99".toCharArray());
            assertThat(BaseCifrada.claveCorrecta(db, "nueva-clave-99".toCharArray())).isTrue();
            assertThat(BaseCifrada.claveCorrecta(db, clave)).isFalse();
        }
        // reabre con la contraseña nueva (los datos y el esquema siguen ahí)
        try (ConfigurableApplicationContext ctx = iniciar(db, "nueva-clave-99".toCharArray())) {
            assertThat(ctx.getBean(DataSource.class)).isNotNull();
        }
        // y con la vieja no abre
        assertThatThrownBy(() -> iniciar(db, clave).close()).isInstanceOf(RuntimeException.class);
    }

    private static ConfigurableApplicationContext iniciar(Path db, char[] clave) {
        return new SpringApplicationBuilder(ConcretoLabApplication.class)
                .properties(BaseCifrada.propiedadesSpring(clave))
                .run("--spring.datasource.url=jdbc:sqlite:" + db.toAbsolutePath(), "--spring.main.web-application-type=none",
                        "--app.seeder.enabled=false", "--logging.file.name=target/pruebas.log");
    }
}
