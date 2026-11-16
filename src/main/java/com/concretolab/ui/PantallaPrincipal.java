package com.concretolab.ui;

import com.concretolab.model.*;
import com.concretolab.service.ProyectoService;
import com.concretolab.service.MezclaService;
import com.concretolab.service.MaterialService;
import com.concretolab.service.RegistroGranulometriaService;
import com.concretolab.service.ModeloCerchaService;
import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

@Component @Lazy
public class PantallaPrincipal {
    private final ProyectoService servicio;
    private final MezclaService mezclas;
    private final MaterialService materiales;
    private final RegistroGranulometriaService granulometrias;
    private final ModeloCerchaService cerchas;
    private final ComboBox<Proyecto> proyectos=new ComboBox<>();
    private final TableView<Elemento> tabla=new TableView<>();
    private final Label resumen=new Label("Seleccione un proyecto"), estado=new Label();
    private final Group mundo=new Group(), modelo=new Group();
    private final Rotate rotacionX=new Rotate(-20,Rotate.X_AXIS), rotacionY=new Rotate(-35,Rotate.Y_AXIS);
    private double inicioX,inicioY,anguloX,anguloY,zoom=1;
    public PantallaPrincipal(ProyectoService servicio,MezclaService mezclas,MaterialService materiales,RegistroGranulometriaService granulometrias,ModeloCerchaService cerchas){this.servicio=servicio;this.mezclas=mezclas;this.materiales=materiales;this.granulometrias=granulometrias;this.cerchas=cerchas;}
    public Parent crear(){
        Label titulo=new Label("ConcretoLab"); titulo.getStyleClass().add("titulo");
        Label descripcion=new Label("Proyectos de concreto · modelo 3D esquemático"); descripcion.getStyleClass().add("subtitulo");
        Button nuevoProyecto=new Button("+ Proyecto"); nuevoProyecto.getStyleClass().add("principal");
        nuevoProyecto.setOnAction(e -> crearProyecto());
        proyectos.setPromptText("Seleccionar proyecto"); proyectos.setPrefWidth(260); proyectos.setOnAction(e -> cargar());
        HBox cabecera=new HBox(12,new VBox(3,titulo,descripcion),new Region(),proyectos,nuevoProyecto); HBox.setHgrow(cabecera.getChildren().get(1),Priority.ALWAYS); cabecera.getStyleClass().add("cabecera");
        columna("Tipo",Elemento::getTipo); columna("Nombre",Elemento::getNombre);
        columna("Volumen m³",e -> FormatoNumeros.volumen(e.volumen()));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); tabla.setPlaceholder(new Label("Agregue elementos al proyecto"));
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,b)->dibujar());
        Button agregar=new Button("+ Elemento"), editar=new Button("Editar"), eliminar=new Button("Eliminar"),plantilla=new Button("Crear plantilla rectangular"); agregar.getStyleClass().add("principal");
        agregar.setOnAction(e -> formulario(null)); editar.setOnAction(e -> {Elemento seleccionado=tabla.getSelectionModel().getSelectedItem(); if(seleccionado!=null) formulario(seleccionado);});
        plantilla.setOnAction(e -> plantilla());
        eliminar.setOnAction(e -> {Elemento seleccionado=tabla.getSelectionModel().getSelectedItem(); if(seleccionado!=null && new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar el elemento seleccionado?",ButtonType.CANCEL,ButtonType.OK).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){servicio.eliminar(seleccionado.getId());cargar();}});
        Label lista=new Label("Elementos"); lista.getStyleClass().add("seccion"); resumen.setWrapText(true); estado.setWrapText(true); estado.getStyleClass().add("estado");
        VBox izquierda=new VBox(12,lista,new HBox(8,agregar,editar,eliminar),plantilla,tabla,resumen,estado); izquierda.getStyleClass().add("tarjeta"); izquierda.setPrefWidth(420); VBox.setVgrow(tabla,Priority.ALWAYS);
        mundo.getChildren().addAll(modelo,new AmbientLight(Color.WHITE)); modelo.getTransforms().addAll(rotacionX,rotacionY);
        SubScene escena=new SubScene(mundo,600,500,true,SceneAntialiasing.BALANCED); escena.setFill(Color.web("#edf3fa"));
        PerspectiveCamera camara=new PerspectiveCamera(true); camara.setNearClip(0.1); camara.setFarClip(10000); camara.setTranslateZ(-1200); escena.setCamera(camara);
        escena.setOnMousePressed(e->{inicioX=e.getSceneX();inicioY=e.getSceneY();anguloX=rotacionX.getAngle();anguloY=rotacionY.getAngle();});
        escena.setOnMouseDragged(e->{rotacionY.setAngle(anguloY+(e.getSceneX()-inicioX)*0.5);rotacionX.setAngle(anguloX-(e.getSceneY()-inicioY)*0.5);});
        escena.setOnScroll(e->{zoom=Math.max(0.2,Math.min(4,zoom*(e.getDeltaY()>0?1.1:1/1.1)));modelo.setScaleX(zoom);modelo.setScaleY(zoom);modelo.setScaleZ(zoom);});
        StackPane visor=new StackPane(escena); escena.widthProperty().bind(visor.widthProperty()); escena.heightProperty().bind(visor.heightProperty());
        Label vista=new Label("Vista 3D esquemática"); vista.getStyleClass().add("seccion");
        Label ayuda=new Label("Arrastra para girar · Rueda para acercar · Posiciones expresadas en metros"); ayuda.getStyleClass().add("subtitulo");
        Label limite=new Label("La vista no realiza análisis estructural. La suma bruta no descuenta intersecciones."); limite.setWrapText(true); limite.getStyleClass().add("aviso");
        VBox derecha=new VBox(10,vista,ayuda,visor,limite); derecha.getStyleClass().add("tarjeta"); VBox.setVgrow(visor,Priority.ALWAYS); HBox.setHgrow(derecha,Priority.ALWAYS);
        HBox centro=new HBox(16,izquierda,derecha); centro.setPadding(new Insets(20)); VBox.setVgrow(centro,Priority.ALWAYS);
        VBox raiz=new VBox(cabecera,centro); raiz.getStyleClass().add("raiz"); actualizarProyectos();
        BorderPane marco=new BorderPane(); VBox menu=new VBox(8);menu.getStyleClass().add("sidebar");
        Label marca=new Label("ConcretoLab");marca.getStyleClass().add("titulo"); Label grupo=new Label("PROYECTO");grupo.getStyleClass().add("menu-grupo");
        Button inicioBtn=new Button("Inicio"),modeloBtn=new Button("Modelo 3D y metrado"), cerchaBtn=new Button("Cerchas 2D"), materialBtn=new Button("Materiales y ensayos"), granuloBtn=new Button("Granulometría"), balanceBtn=new Button("Balance de volúmenes"), mezclaBtn=new Button("Mezclas y probetas");
        List<Button> navegacion=List.of(inicioBtn,modeloBtn,cerchaBtn,materialBtn,granuloBtn,balanceBtn,mezclaBtn);for(Button b:navegacion)b.getStyleClass().add("menu-boton");
        java.util.function.Consumer<Button> activar=b->{for(Button boton:navegacion)boton.getStyleClass().remove("activo");b.getStyleClass().add("activo");};
        modeloBtn.setOnAction(e->{activar.accept(modeloBtn);marco.setCenter(raiz);});
        cerchaBtn.setOnAction(e->{activar.accept(cerchaBtn);marco.setCenter(new PantallaCercha(servicio,cerchas,proyectos::setValue).crear(proyectos.getValue()));});
        materialBtn.setOnAction(e->{activar.accept(materialBtn);marco.setCenter(new PantallaMateriales(materiales).crear());});
        granuloBtn.setOnAction(e->{activar.accept(granuloBtn);marco.setCenter(new PantallaGranulometria(materiales,granulometrias).crear());});
        balanceBtn.setOnAction(e->{activar.accept(balanceBtn);marco.setCenter(new PantallaVolumenAbsoluto().crear());});
        mezclaBtn.setOnAction(e->{activar.accept(mezclaBtn);marco.setCenter(new PantallaMezclas(servicio,mezclas,materiales,proyectos::setValue).crear(proyectos.getValue()));});
        inicioBtn.setOnAction(e->{activar.accept(inicioBtn);marco.setCenter(inicio(modeloBtn,materialBtn,granuloBtn,mezclaBtn));});
        menu.getChildren().addAll(marca,grupo,inicioBtn,modeloBtn,new Label("ANÁLISIS ESTRUCTURAL"){{getStyleClass().add("menu-grupo");}},cerchaBtn,new Label("DISEÑO DE CONCRETO"){{getStyleClass().add("menu-grupo");}},materialBtn,granuloBtn,balanceBtn,mezclaBtn);
        marco.setLeft(menu);inicioBtn.fire();return marco;
    }
    private Parent inicio(Button modeloBtn,Button materialBtn,Button granuloBtn,Button mezclaBtn){
        Label titulo=new Label("Bienvenido a ConcretoLab");titulo.getStyleClass().add("page-heading");
        Label descripcion=new Label("Organiza la información del proyecto, los materiales y las pruebas en un solo lugar.");descripcion.getStyleClass().add("subtitulo");descripcion.setWrapText(true);
        VBox contenido=new VBox(18,titulo,descripcion,
            paso("01", "Proyecto y modelo", "Crea un proyecto y representa sus elementos. Puedes comenzar con una plantilla de cuatro zapatas, columnas y vigas.","Abrir modelo",modeloBtn),
            paso("02", "Materiales y granulometría", "Registra resultados reales del laboratorio. Calcula los porcentajes del tamizado a partir de las masas medidas.","Abrir materiales",materialBtn),
            paso("03", "Mezclas y pruebas", "Registra una dosificación existente, corrige por humedad y conserva los resultados de ensayos.","Abrir mezclas",mezclaBtn));
        Label nota=new Label("El sistema apoya el registro y los cálculos indicados; no diseña automáticamente según ACI ni verifica la seguridad estructural.");nota.setWrapText(true);nota.getStyleClass().add("aviso");contenido.getChildren().add(nota);
        contenido.setPadding(new Insets(32));contenido.getStyleClass().add("raiz");ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
    }
    private VBox paso(String numero,String nombre,String detalle,String accion,Button destino){
        Label encabezado=new Label(numero+"   "+nombre);encabezado.getStyleClass().add("seccion");Label texto=new Label(detalle);texto.setWrapText(true);texto.getStyleClass().add("subtitulo");
        Button boton=new Button(accion);boton.setOnAction(e->destino.fire());VBox tarjeta=new VBox(9,encabezado,texto,boton);tarjeta.getStyleClass().add("tarjeta");return tarjeta;
    }
    private void columna(String titulo,java.util.function.Function<Elemento,String> valor){TableColumn<Elemento,String> c=new TableColumn<>(titulo);c.setCellValueFactory(f->new SimpleStringProperty(valor.apply(f.getValue())));tabla.getColumns().add(c);}
    private void actualizarProyectos(){proyectos.setItems(FXCollections.observableArrayList(servicio.proyectos()));}
    private void crearProyecto(){
        Dialog<Proyecto> d=new Dialog<>();d.setTitle("Nuevo proyecto");d.setHeaderText("Datos del proyecto");
        TextField nombre=new TextField(),ubicacion=new TextField();nombre.setPromptText("Nombre del proyecto");ubicacion.setPromptText("Ubicación (opcional)");
        d.getDialogPane().setContent(new VBox(10,nombre,ubicacion));ButtonType guardar=new ButtonType("Crear",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
        ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{if(nombre.getText().isBlank()||nombre.getText().trim().length()>120||ubicacion.getText().length()>200)throw new IllegalArgumentException("Revise el nombre y la ubicación.");}catch(Exception ex){e.consume();error(ex.getMessage());}});
        d.setResultConverter(b->b==guardar?servicio.crearProyecto(nombre.getText(),ubicacion.getText()):null);
        d.showAndWait().ifPresent(p->{actualizarProyectos();proyectos.setValue(p);});
    }
    private void formulario(Elemento anterior){
        Proyecto p=proyectos.getValue();if(p==null){estado.setText("Seleccione o cree un proyecto.");return;}
        Dialog<Elemento> d=new Dialog<>();d.setTitle(anterior==null?"Nuevo elemento":"Editar elemento");d.setHeaderText("Dimensiones y posición en metros");
        ComboBox<String> tipo=new ComboBox<>(FXCollections.observableArrayList("ZAPATA","COLUMNA","VIGA"));tipo.setValue(anterior==null?"ZAPATA":anterior.getTipo());
        TextField nombre=new TextField(anterior==null?"":anterior.getNombre());
        TextField largo=campo(anterior==null?1:anterior.getLargo()),ancho=campo(anterior==null?1:anterior.getAncho()),altura=campo(anterior==null?1:anterior.getAltura());
        TextField x=campo(anterior==null?0:anterior.getX()),y=campo(anterior==null?0:anterior.getY()),z=campo(anterior==null?0:anterior.getZ());
        GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);String[] etiquetas={"Tipo","Nombre","Largo eje X","Ancho eje Y","Altura eje Z","Centro X","Centro Y","Centro Z"};
        Node[] campos={tipo,nombre,largo,ancho,altura,x,y,z};for(int i=0;i<campos.length;i++){g.add(new Label(etiquetas[i]),0,i);g.add(campos[i],1,i);}d.getDialogPane().setContent(g);
        ButtonType guardar=new ButtonType("Guardar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
        final Elemento[] resultado=new Elemento[1];
        ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
            Elemento elemento=new Elemento(); if(anterior!=null) elemento.setId(anterior.getId()); elemento.setProyectoId(p.getId());elemento.setTipo(tipo.getValue());elemento.setNombre(nombre.getText().trim());
            elemento.setLargo(numero(largo));elemento.setAncho(numero(ancho));elemento.setAltura(numero(altura));elemento.setX(numero(x));elemento.setY(numero(y));elemento.setZ(numero(z));
            resultado[0]=servicio.guardar(elemento);
        }catch(Exception ex){e.consume();error(ex instanceof NumberFormatException?"Ingrese números válidos.":ex.getMessage());}});
        d.setResultConverter(b->b==guardar?resultado[0]:null);d.showAndWait().ifPresent(e->cargar());
    }
    private TextField campo(double n){return new TextField(Double.toString(n));}
    private void plantilla(){
        Proyecto p=proyectos.getValue();if(p==null){estado.setText("Seleccione o cree un proyecto.");return;}
        Dialog<ButtonType> d=new Dialog<>();d.setTitle("Plantilla rectangular");d.setHeaderText("4 zapatas, 4 columnas y 4 vigas");
        String[] etiquetas={"Separación entre ejes X (m)","Separación entre ejes Y (m)","Largo de zapata X (m)","Ancho de zapata Y (m)","Altura de zapata (m)","Lado de columna cuadrada (m)","Altura de columna (m)","Ancho de viga (m)","Altura de viga (m)"};
        TextField[] f=new TextField[etiquetas.length];GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);
        for(int i=0;i<f.length;i++){f[i]=new TextField();f[i].setPromptText("Dimensión definida en el proyecto");g.add(new Label(etiquetas[i]),0,i);g.add(f[i],1,i);}
        Label nota=new Label("Plantilla geométrica: ingresa medidas del proyecto. No diseña ni verifica la estructura. Las vigas llegan hasta las caras de las columnas.");nota.setWrapText(true);
        d.getDialogPane().setContent(new VBox(12,nota,g));ButtonType guardar=new ButtonType("Crear elementos",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
        ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
            servicio.crearPlantilla(p.getId(),new ProyectoService.Plantilla(numero(f[0]),numero(f[1]),numero(f[2]),numero(f[3]),numero(f[4]),numero(f[5]),numero(f[6]),numero(f[7]),numero(f[8])));
        }catch(Exception ex){e.consume();error(ex instanceof NumberFormatException?"Ingrese todas las dimensiones con números válidos.":ex.getMessage());}});
        d.showAndWait().ifPresent(b->{if(b==guardar)cargar();});
    }
    private double numero(TextField c){return Double.parseDouble(c.getText().trim().replace(',','.'));}
    private void error(String mensaje){new Alert(Alert.AlertType.ERROR,mensaje==null?"No se pudo completar la operación.":mensaje,ButtonType.OK).showAndWait();}
    private void cargar(){Proyecto p=proyectos.getValue();tabla.getItems().clear();if(p==null){dibujar();return;}tabla.getItems().setAll(servicio.elementos(p.getId()));
        double suma=tabla.getItems().stream().mapToDouble(Elemento::volumen).sum();resumen.setText("Suma bruta: "+FormatoNumeros.volumen(suma)+" m³\nNo descuenta superposiciones.");estado.setText("");dibujar();}
    private void dibujar(){modelo.getChildren().clear();Elemento seleccionado=tabla.getSelectionModel().getSelectedItem();double escala=65;
        for(Elemento e:tabla.getItems()){
            Box caja=new Box(e.getLargo()*escala,e.getAltura()*escala,e.getAncho()*escala);
            caja.setTranslateX(e.getX()*escala);caja.setTranslateY(-e.getZ()*escala);caja.setTranslateZ(e.getY()*escala);
            Color color=e==seleccionado?Color.ORANGE:switch(e.getTipo()){case "ZAPATA"->Color.web("#64748b");case "COLUMNA"->Color.web("#2563eb");default->Color.web("#0d9488");};
            caja.setMaterial(new PhongMaterial(color));caja.setOnMouseClicked(ev->{if(ev.isStillSincePress())tabla.getSelectionModel().select(e);});modelo.getChildren().add(caja);
        }
    }
}
