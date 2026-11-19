# Plantillas minimas (sustituir `Cliente`, `com.ejemplo.app`)

## Bootstrap
```java
public class Launcher { public static void main(String[] a) { Application.launch(JavaFxApplication.class, a); } }

public class JavaFxApplication extends Application {
    private ConfigurableApplicationContext context;
    @Override public void init() {
        context = new SpringApplicationBuilder(AppApplication.class).web(WebApplicationType.NONE).run();
    }
    @Override public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        context.getBean(Exportador.class).setHostServices(getHostServices());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        loader.setControllerFactory(context::getBean);          // clave: controllers inyectados por Spring
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("Panel administrativo"); stage.setScene(scene);
        stage.setMinWidth(1100); stage.setMinHeight(700); stage.show();
    }
    @Override public void stop() { context.close(); Platform.exit(); }
}
```

## Entidad
```java
@Entity
@Table(name = "cliente", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_documento", columnNames = "documento"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString @EqualsAndHashCode(of = "id")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 255)
    @Column(nullable = false, length = 255) private String nombre;
    @NotBlank(message = "El documento es obligatorio") @Column(nullable = false, length = 20) private String documento;
}
```

## Repository
```java
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByDocumento(String documento);
    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR LOWER(c.documento) LIKE LOWER(CONCAT('%', :texto, '%'))")
    Page<Cliente> buscar(@Param("texto") String texto, Pageable pageable);
}
```

## Service
```java
@Service @Validated @RequiredArgsConstructor @Slf4j @Transactional(readOnly = true)
public class ClienteService {
    private final ClienteRepository repository;
    public Page<Cliente> listar(String filtro, int pagina, int tamanio) {
        Pageable p = PageRequest.of(pagina, tamanio, Sort.by("id").ascending());
        return filtro == null || filtro.isBlank() ? repository.findAll(p) : repository.buscar(filtro.trim(), p);
    }
    @Transactional
    public Cliente guardar(@Valid Cliente c) {
        repository.findByDocumento(c.getDocumento()).ifPresent(e -> {
            if (c.getId() == null || !e.getId().equals(c.getId()))
                throw new NegocioException("Ya existe un cliente con el documento " + c.getDocumento());
        });
        return repository.save(c);
    }
    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) throw new NegocioException("El cliente ya no existe.");
        // + chequeos existsByClienteId en repos que lo referencian
        repository.deleteById(id);
    }
}
```

## Controller (esqueleto)
```java
@Component @RequiredArgsConstructor
public class ClienteController implements Refrescable {
    private final ClienteService service; private final Notificador notificador;
    @FXML private VBox panelFormulario; @FXML private Label lblTituloFormulario;
    @FXML private TextField txtNombre, txtDocumento, txtBuscar;
    @FXML private TableView<Cliente> tabla; @FXML private Button btnEditar, btnEliminar; @FXML private Paginador paginador;
    private final ObservableList<Cliente> datos = FXCollections.observableArrayList();
    private Long idEditando;

    @FXML public void initialize() {
        Tablas.configurar(tabla, "Registra un cliente o cambia la busqueda.", this::editar);
        tabla.setItems(datos);
        var sel = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(sel.isNull()); btnEliminar.disableProperty().bind(sel.isNull());
        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        Formularios.limpiarAlEditar(txtNombre, txtDocumento);
    }
    @Override public void refrescar() { cargarTabla(); }
    private void cargarTabla() {
        Page<Cliente> pg = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pg.getContent()); paginador.actualizar(pg);
    }
    // onNuevo / editar / onGuardar / onEliminar: ver ProductoController del proyecto de referencia
}
```

## Seeder idempotente
```java
@Component @RequiredArgsConstructor
public class ClienteSeeder {
    private final ClienteRepository repository;
    @Transactional public int sembrar() {
        Set<String> existentes = repository.findAll().stream().map(Cliente::getDocumento).collect(Collectors.toSet());
        List<Cliente> nuevos = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            String doc = String.format("%08d", 10000000 + i);
            if (!existentes.contains(doc)) nuevos.add(Cliente.builder().documento(doc).nombre("Cliente " + i).build());
        }
        repository.saveAll(nuevos); return nuevos.size();
    }
}
```

## Fragmento FXML de campo (requerido por `Formularios`)
```xml
<VBox styleClass="campo">
    <Label text="Nombre *" styleClass="campo-label"/>
    <TextField fx:id="txtNombre" promptText="Ej. Juan Perez"/>
    <Label styleClass="campo-error" visible="false" managed="false"/>
</VBox>
```
