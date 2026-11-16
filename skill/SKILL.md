---
name: javafx-spring-sqlite-desktop
description: Crea o extiende apps de escritorio CRUD con JavaFX + Spring Boot (sin web) + JPA/SQLite + AtlantaFX, siguiendo la arquitectura de crud-persona-spring (modelo/repository/service/controller FXML, panel lateral de formularios, paginador, seeders, PDF/Excel). Usar al iniciar un proyecto nuevo con esta pila, agregar un modulo CRUD, una vista, un seeder o un reporte.
---

# Arquitectura JavaFX + Spring Boot + SQLite (escritorio)

Proyecto de referencia: `C:\documentos\Proyectos\bases para otros\crud-persona-spring`
(modulos: Personas, Categorias, Productos, Ventas con IGV, Compras, Inventario/kardex, Dashboard).
Si existe, **copia el patron de sus archivos reales**; ver tambien `templates.md` en esta skill.
Idioma del codigo y la UI: **espanol** (clases, metodos, mensajes, FXML).

## Pila
- Java 17, Spring Boot 3.3.x (`starter-data-jpa`, `starter-validation`), Lombok (excluido del jar).
- JavaFX 21 (`javafx-controls`, `javafx-fxml`), AtlantaFX 2.x (tema PrimerLight/PrimerDark), Ikonli Feather (`fth-*`).
- SQLite (`sqlite-jdbc` + `hibernate-community-dialects`), OpenPDF (comprobantes), Apache POI `poi-ooxml` (Excel).
- Plugins: `spring-boot-maven-plugin` y `javafx-maven-plugin` con `mainClass` = `Launcher`.

## Arranque (3 clases, no cambiar el patron)
1. `Launcher` — `main` que solo hace `Application.launch(JavaFxApplication.class, args)` (evita el error de "JavaFX runtime components missing" en el jar).
2. `JavaFxApplication extends Application`:
   - `init()`: `new SpringApplicationBuilder(XApplication.class).web(WebApplicationType.NONE).run()`.
   - `start()`: `setUserAgentStylesheet(new PrimerLight()...)`, `context.getBean(Exportador.class).setHostServices(getHostServices())`, `FXMLLoader` de `/view/main-view.fxml` con **`loader.setControllerFactory(context::getBean)`**, agregar `/css/app.css`.
   - `stop()`: `context.close(); Platform.exit();`.
3. `XApplication` — `@SpringBootApplication` normal.

`application.properties`: `spring.main.web-application-type=none`, `jdbc:sqlite:<nombre>.db`, `hikari.maximum-pool-size=1` (SQLite), `ddl-auto=update`, `open-in-view=false`, dialecto `org.hibernate.community.dialect.SQLiteDialect`, `app.seeder.enabled=true`, `app.empresa.*` (datos del emisor).

## Estructura de paquetes (`com.<org>.<app>`)
```
config/      @ConfigurationProperties (DatosEmpresa -> app.empresa.*)
controller/  un @Component por vista FXML (+ MainController de navegacion)
exception/   NegocioException extends RuntimeException
model/       @Entity + enums
repository/  JpaRepository (+ JpaSpecificationExecutor si hay busqueda dinamica)
seeder/      DatabaseSeeder (ApplicationRunner) + un Seeder idempotente por entidad
service/     @Service @Transactional(readOnly=true); reportes PDF/Excel; utilidades (CalculadoraIgv, Periodo...)
ui/          helpers JavaFX reutilizables (Notificador, Formularios, Tablas, Paginador, Exportador, Refrescable, Autocompletar)
resources/view/*.fxml   css/app.css
```

## Reglas por capa

**Modelo**: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString @EqualsAndHashCode(of="id")`; `@GeneratedValue(IDENTITY)`; Bean Validation con mensajes en espanol (`@NotBlank(message=...)`, `@Size`, `@DecimalMin`, `@Digits`); dinero siempre `BigDecimal` con `precision/scale`; `@ManyToOne(fetch=LAZY)`; unicidad con `@UniqueConstraint(name="uk_...")`; columnas snake_case via `@Column(name=...)`; enums con `@Enumerated(STRING)`; colecciones hijas con `cascade=ALL, orphanRemoval=true` y metodo `agregarDetalle()` que fija la relacion inversa y el `item`. En documentos (ventas/compras) **guardar snapshot** (codigo, nombre, precio, totales) para que no cambien al editar el catalogo.

**Repository**: `@Query` con `LEFT JOIN FETCH` para evitar LazyInitialization (sesion cerrada por `open-in-view=false`); metodos paginados `Page<T> buscar(@Param texto, Pageable)` con `LOWER(...) LIKE LOWER(CONCAT('%', :texto, '%'))` y `countQuery` cuando hay JOIN FETCH; `existsByXId` para validar borrado; consultas de dashboard devuelven `List<Object[]>`.

**Service**:
- `@Service @RequiredArgsConstructor @Slf4j @Transactional(readOnly = true)`; los metodos de escritura llevan `@Transactional`.
- `@Validated` + `@Valid` en `guardar(...)` -> lanza `ConstraintViolationException` que la UI pinta por campo.
- Reglas de negocio -> `throw new NegocioException("mensaje para el usuario")` (unicidad, "no se puede eliminar: tiene ventas", stock insuficiente...).
- Campos que no edita el formulario (stock, costo) se **preservan** desde la BD en `guardar`.
- Entradas complejas como `record` internos (`NuevaVenta`, `LineaCarrito`, `Totalizado`, `ResumenInventario`) en vez de DTO suelto.
- Operaciones con fecha: ofrecer sobrecarga `registrar(solicitud, LocalDateTime fecha)` para que el seeder genere historico.
- Un **unico servicio** muta cada dato derivado (ej. `InventarioService` es el unico que toca `stock` y deja un `MovimientoInventario`/kardex con saldo resultante; compra suma y recalcula costo promedio ponderado; venta resta; anular revierte). Valida todo el carrito **antes** de modificar nada.
- SQLite devuelve sumas como Double/Long: normalizar con helper `decimal(Object)` a `BigDecimal` escala 2.
- Anular en vez de borrar documentos emitidos (estado `EMITIDA/ANULADA`).

**Controller (FXML)**:
- `@Component @RequiredArgsConstructor` (servicios + `Notificador` por constructor), campos `@FXML` con prefijos `txt`, `cmb`, `col`, `btn`, `lbl`, `tabla`, `paginador`.
- Implementa `Refrescable` (`refrescar()` se llama cada vez que se muestra la vista).
- `initialize()`: `cellValueFactory`/`cellFactory` (`Tablas.celdaId/celdaMoneda/celdaChip/...`), `Tablas.configurar(tabla, textoVacio, this::editar)` (doble clic edita), `btnEditar/btnEliminar.disableProperty().bind(seleccion.isNull())`, `paginador.setOnCambio(this::cargarTabla)`, buscador -> `paginador.irAPrimera()`, `Formularios.soloDecimal/soloDigitos/limpiarAlEditar`.
- Estado de edicion: `Long idEditando` (null = nuevo).
- `onGuardar()`: limpiar errores -> parsear numeros (error de formato con `Formularios.marcarError`) -> construir entidad con `builder()` -> `try { service.guardar; notificador.cerrarPanel(); notificador.exito(...); cargarTabla(); } catch (ConstraintViolationException e) { Formularios.mostrarViolaciones(e, Map.of("campoEntidad", control...)) -> sobrantes a notificador.advertencia } catch (NegocioException e) { marcarError(campoClave, msg) } catch (Exception e) { notificador.error }`. Las claves del `Map` son los **nombres de propiedad de la entidad**.
- `onEliminar()`: `notificador.confirmar(titulo, mensaje, "Eliminar", () -> { try {service.eliminar; exito; cargarTabla} catch (NegocioException e) {error} })`.
- Nunca hacer consultas pesadas/generacion de archivos en el hilo FX: PDF/Excel via `Exportador` (Task en hilo daemon).

**Vista FXML** (`/view/<nombre>-view.fxml`): raiz `VBox styleClass="pagina"` con `fx:controller`; el formulario va en `<fx:define><VBox fx:id="panelFormulario" styleClass="drawer" prefWidth="440">` (header con icono/titulo, `ScrollPane` con campos, footer Cancelar/Guardar) y se muestra con `notificador.abrirPanel(panelFormulario)`. Cada campo: `<VBox styleClass="campo">` con `Label.campo-label`, control, y `<Label styleClass="campo-error" visible="false" managed="false"/>` (lo requiere `Formularios`). Luego cabecera (`pagina-cabecera`, boton `accent,boton-principal`), tarjeta (`tarjeta`) con `barra-herramientas` (buscador `CustomTextField` + Editar/Eliminar), `TableView` y `<Paginador/>`.

**Navegacion**: `MainController` con `ToggleGroup` de `ToggleButton.nav-item`; `setUserData("<nombre>-view")`; al cambiar seleccion carga `/view/<nombre>.fxml` **una sola vez** (cache `Map<String,Vista>`), con `setControllerFactory(context::getBean)` y `setClassLoader(...)`, y llama `Refrescable.refrescar()`. Incluye indicador animado, menu contraible y cambio tema claro/oscuro (clase `dark` en la raiz). `irA(vista)` permite saltar entre vistas. Capas globales en `main-view.fxml`: `ModalPane modalPanel` (drawer derecho), `ModalPane modalDialogo` (centro), `VBox zonaNotificaciones` (toasts), registradas en `Notificador.registrar(...)`.

**Helpers `ui/`** (copiarlos tal cual a proyectos nuevos, son genericos salvo `Tablas.celdaStock`): `Notificador` (toasts exito/advertencia/error/info, `confirmar`, `exitoConAcciones`, `abrirPanel/cerrarPanel`), `Formularios` (marcarError con shake, limpiar, mostrarViolaciones, soloDigitos/soloDecimal), `Paginador` (control FXML con "Mostrando x-y de z", filas por pagina, botones con elipsis; `actualizar(Page)`), `Tablas` (estilo, estado vacio, celdas), `Exportador` (FileChooser + Task + `HostServices.showDocument`), `Refrescable`, `Autocompletar`.

**Seeders**: `DatabaseSeeder implements ApplicationRunner` con `@ConditionalOnProperty("app.seeder.enabled")`, llama a cada `XSeeder.sembrar()` **en orden de dependencias** (categorias -> productos -> personas -> proveedores -> inventario inicial -> ventas). Cada seeder es `@Component @Transactional` e **idempotente** (consulta existentes por codigo y omite); datos realistas con `record Plantilla`, codigos `PRD-%03d`, dejar algunos registros sin relacion opcional para probar casos nulos.

**Reportes**: PDF con OpenPDF (`ComprobantePdfService`, `NumeroALetras` para el monto en letras), Excel con POI (`ReporteExcelService`, `LibroExcel` helper de estilos); reciben `File destino` y se ejecutan via `Exportador`. Calculos tributarios aislados en una clase pura (`CalculadoraIgv`, tasa 18 %, con/sin IGV incluido).

**CSS** (`app.css`): clases reutilizadas por todas las vistas: `.app/.app.dark`, `.sidebar`, `.nav-item`, `.pagina`, `.pagina-cabecera`, `.tarjeta`, `.barra-herramientas`, `.tabla`, `.chip`, `.drawer*`, `.campo*`, `.paginador*`, `.dialogo*`, `.toast`. Variables de color para claro/oscuro; no estilos inline en FXML.

## Receta: agregar un modulo CRUD nuevo (ej. `Cliente`)
1. `model/Cliente.java` (validaciones + unique).
2. `repository/ClienteRepository.java` (`buscar` paginado, `existsBy...`).
3. `service/ClienteService.java` (`listar(filtro,pagina,tamanio)`, `guardar(@Valid)`, `eliminar` con chequeo de referencias, `contar`).
4. `view/cliente-view.fxml` copiando `producto-view.fxml`.
5. `controller/ClienteController.java` copiando `ProductoController`.
6. `MainController`: agregar `@FXML ToggleButton navCliente`, `setUserData("cliente-view")`, incluirlo en `botonesMenu()`; en `main-view.fxml` agregar el `ToggleButton` con icono `fth-*`.
7. `seeder/ClienteSeeder` + llamarlo en `DatabaseSeeder` en el orden correcto.
8. Si la nueva entidad la referencian otras, actualizar el chequeo de borrado de esas otras en su servicio.

## Receta: proyecto nuevo
1. Generar `pom.xml` como el de referencia (cambiar groupId/artifactId/paquete/`mainClass`), `.gitignore` (target/, *.db opcional), `mvnw`.
2. Crear `Launcher`, `JavaFxApplication`, `XApplication`, `application.properties`.
3. Copiar `ui/*`, `exception/NegocioException`, `css/app.css`, `main-view.fxml` + `MainController` y recortar el menu a los modulos del proyecto.
4. Agregar modulos con la receta anterior; ejecutar con `./mvnw javafx:run` (o `spring-boot:run`).

## Patrones agregados en bodega-pos (`C:\documentos\Proyectos\bases para otros\bodega-pos`)
Proyecto derivado que ya trae, listo para copiar a otros rubros:
- **Login y roles**: `Usuario` (BCrypt via `spring-security-crypto`, sin Spring Security), `Rol {ADMIN, CAJERO}`, `UsuarioService.autenticar/guardar/eliminar` (protege al ultimo admin), `Sesion` (bean con el usuario actual), `Ventanas` (bean que cambia el Stage entre `login-view` y `main-view`), `UsuarioInicial` (ApplicationRunner que crea el admin si no hay usuarios; `app.admin.*`). `JavaFxApplication.start()` solo llama `ventanas.setStage(stage); ventanas.mostrarLogin()`. `MainController.aplicarPermisos()` oculta (visible+managed=false) botones y secciones por rol y elige la vista inicial; al expandir el menu hay que volver a llamarlo. Como los controllers son singletons, `initialize()` debe **reiniciar su estado** (`vistas.clear()`, `carrito.clear()`...) porque se vuelve a ejecutar tras cerrar sesion.
- **Caja**: `Caja` + `MovimientoCaja`, `CajaService` (abrir, registrarMovimiento, resumen/arqueo, cerrar). Efectivo esperado = inicial + ventas en efectivo + ingresos - egresos; los demas `MedioPago` (Yape, Plin, tarjeta...) se informan aparte. `app.caja.exigir-apertura` se valida **en el controller del POS**, no en `VentaService`, para no romper seeders ni tests.
- **Mostrador**: cliente generico `PersonaService.clienteVarios()` (DNI 00000000, no eliminable) preseleccionado; `TextField.setOnAction` + `ProductoService.buscarPorCodigoExacto` para el lector de barras (Enter); `Autocompletar` consume Enter solo si hay sugerencia seleccionada.
- **Compatibilidad al ampliar records**: agregar un campo a `NuevaVenta` y un constructor secundario con el valor por defecto, asi no se rompen llamadas existentes.
- **Pruebas**: `*IntegracionTest` con `@SpringBootTest` + `@DynamicPropertySource` (SQLite temporal, `app.seeder.enabled=true`); `VistasFxmlTest` arranca `Platform.startup`, carga todos los FXML con `context::getBean` y llama `refrescar()` (se omite con `assumeTrue` si no hay pantalla). Atrapa fx:id/handlers faltantes sin abrir la app.
- **Compilar en esta maquina**: el `java` del PATH es 8; hay un JBR 25 en `C:\Users\ferna\.jbr\jbr-25.0.4-windows-x64-b329.128`. Con ese JDK: `JAVA_HOME=<jbr> ./mvnw -Dlombok.version=1.18.42 -Djava.version=17 -Dmaven.compiler.proc=full test` (Lombok 1.18.34 y el procesamiento de anotaciones implicito no funcionan en JDK 23+). Idealmente instalar un JDK 17 y quitar esos flags.
- En bash de esta maquina evitar heredocs largos con muchas comillas: usar la herramienta Write.

## Trampas conocidas
- Sin `open-in-view`, acceder a una relacion LAZY en el controller falla: usar `JOIN FETCH` en el repository.
- `ddl-auto=update` en SQLite no altera restricciones existentes; para cambios fuertes borrar el `.db` de desarrollo (el seeder lo repuebla).
- Pool de Hikari de tamano 1 para SQLite; evitar transacciones largas.
- Los `ModalPane` cachean transiciones: llamar `usePredefinedTransitionFactories` en cada apertura (ya hecho en `Notificador`).
- El stock/saldos solo se modifican via el servicio de inventario; los formularios de producto **no** lo editan.
- `Page.getTotalPages()` puede dejar la pagina fuera de rango tras eliminar: `Paginador.actualizar` ya retrocede.
