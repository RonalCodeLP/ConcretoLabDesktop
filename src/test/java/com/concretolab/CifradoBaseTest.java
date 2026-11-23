package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.concretolab.service.BaseCifrada;
import com.concretolab.service.BaseCifrada.Estado;
import com.concretolab.service.RespaldoDatos;
import com.concretolab.service.YefriLermaProyectoService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db, BaseCifrada.erickDelgadoPropiedades(clave));
             var rs = c.createStatement().executeQuery("select x from secreto")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void cifraUnaBaseExistenteSinPerderDatosYSinDejarTextoLegible(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        assertThat(BaseCifrada.erickDelgadoInspeccionar(db)).isEqualTo(Estado.SIN_CIFRAR);
        assertThat(BaseCifrada.erickDelgadoInspeccionar(dir.resolve("no-existe.db"))).isEqualTo(Estado.NO_EXISTE);

        Path copia = BaseCifrada.erickDelgadoCifrarExistente(db, "clave-segura-1".toCharArray());

        assertThat(BaseCifrada.erickDelgadoInspeccionar(db)).isEqualTo(Estado.CIFRADA);
        assertThat(new String(Files.readAllBytes(db), StandardCharsets.ISO_8859_1)).doesNotContain("confidencial").doesNotContain("SQLite format");
        assertThat(leer(db, "clave-segura-1".toCharArray())).isEqualTo("dato muy confidencial");
        assertThat(BaseCifrada.erickDelgadoClaveCorrecta(db, "clave-segura-1".toCharArray())).isTrue();
        assertThat(BaseCifrada.erickDelgadoClaveCorrecta(db, "otra-clave-xx".toCharArray())).isFalse();
        assertThat(BaseCifrada.erickDelgadoClaveCorrecta(db, null)).isFalse();
        assertThat(BaseCifrada.erickDelgadoInspeccionar(copia)).isEqualTo(Estado.SIN_CIFRAR); // la copia de seguridad previa es la original
        // el archivo cifrado se acepta como candidato de restauración
        assertThat(RespaldoDatos.erickDelgadoEsSqliteValido(db)).isTrue();
    }

    @Test
    void rechazaContrasenasCortasSinTocarLaBase(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        assertThatThrownBy(() -> BaseCifrada.erickDelgadoCifrarExistente(db, "corta".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThat(BaseCifrada.erickDelgadoInspeccionar(db)).isEqualTo(Estado.SIN_CIFRAR);
        assertThat(leer(db, null)).isEqualTo("dato muy confidencial");
    }

    @Test
    void cambiaLaContrasenaSoloConLaActualCorrecta(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("a.db");
        crearPlana(db);
        BaseCifrada.erickDelgadoCifrarExistente(db, "clave-uno-123".toCharArray());
        assertThatThrownBy(() -> BaseCifrada.erickDelgadoCambiarClave(db, "equivocada-12".toCharArray(), "clave-dos-456".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
        BaseCifrada.erickDelgadoCambiarClave(db, "clave-uno-123".toCharArray(), "clave-dos-456".toCharArray());
        assertThat(BaseCifrada.erickDelgadoClaveCorrecta(db, "clave-uno-123".toCharArray())).isFalse();
        assertThat(leer(db, "clave-dos-456".toCharArray())).isEqualTo("dato muy confidencial");
    }

    @Test
    void unaBaseProtegidaDeUnaVersionAnteriorSeConvierteSinPerderProyectosYConservaUnaCopia(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("app.db");
        Path copias = dir.resolve("backups");
        // 1) una base creada por la aplicación, con dos proyectos y elementos
        try (ConfigurableApplicationContext ctx = iniciar(db, null)) {
            var servicio = ctx.getBean(YefriLermaProyectoService.class);
            var p = servicio.yefriLermaCrearProyecto("Edificio Los Álamos", "Arequipa");
            servicio.yefriLermaCrearPlantilla(p.getId(), new YefriLermaProyectoService.Plantilla(5, 4, 1.2, 1.2, 0.5, 0.4, 3, 0.3, 0.5));
            servicio.yefriLermaCrearProyecto("Vivienda 2 pisos", "Lima");
        }
        // 2) como la dejaba la versión anterior: protegida con contraseña
        char[] clave = "clave-anterior-1".toCharArray();
        Files.delete(BaseCifrada.erickDelgadoCifrarExistente(db, clave));
        assertThat(BaseCifrada.erickDelgadoInspeccionar(db)).isEqualTo(Estado.CIFRADA);
        byte[] protegida = Files.readAllBytes(db);

        // contraseña equivocada: no se toca nada ni se crean copias
        assertThatThrownBy(() -> BaseCifrada.erickDelgadoQuitarClave(db, "equivocada-123".toCharArray(), copias))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(db)).isEqualTo(protegida);
        assertThat(copias).doesNotExist();

        // 3) conversión con la contraseña correcta
        Path copia = BaseCifrada.erickDelgadoQuitarClave(db, clave, copias);
        assertThat(BaseCifrada.erickDelgadoInspeccionar(db)).isEqualTo(Estado.SIN_CIFRAR);
        assertThat(copia.getParent()).isEqualTo(copias);
        assertThat(RespaldoDatos.ERICK_DELGADO_PATRON_NOMBRE.matcher(copia.getFileName().toString()).matches())
                .as("la copia aparece en «Datos y copias»").isTrue();
        assertThat(Files.readAllBytes(copia)).as("la copia es la base protegida tal como estaba").isEqualTo(protegida);
        assertThat(BaseCifrada.erickDelgadoClaveCorrecta(copia, clave)).isTrue();

        // 4) la aplicación abre sin contraseña y los proyectos siguen ahí
        try (ConfigurableApplicationContext ctx = iniciar(db, null)) {
            var servicio = ctx.getBean(YefriLermaProyectoService.class);
            var nombres = servicio.proyectos().stream().map(com.concretolab.model.Proyecto::getNombre).toList();
            assertThat(nombres).containsExactlyInAnyOrder("Edificio Los Álamos", "Vivienda 2 pisos");
            var alamos = servicio.proyectos().stream().filter(x -> x.getNombre().equals("Edificio Los Álamos")).findFirst().orElseThrow();
            assertThat(servicio.elementos(alamos.getId())).hasSize(12);
        }
        // una base que ya no está protegida no se vuelve a convertir
        assertThatThrownBy(() -> BaseCifrada.erickDelgadoQuitarClave(db, clave, copias)).isInstanceOf(IllegalStateException.class);
    }

    private static ConfigurableApplicationContext iniciar(Path db, char[] clave) {
        return new SpringApplicationBuilder(ConcretoLabApplication.class)
                .properties(BaseCifrada.erickDelgadoPropiedadesSpring(clave))
                .run("--spring.datasource.url=jdbc:sqlite:" + db.toAbsolutePath(), "--spring.main.web-application-type=none",
                        "--app.seeder.enabled=false", "--logging.file.name=target/pruebas.log");
    }
}
