package com.concretolab.ui;

import com.concretolab.model.Material;
import com.concretolab.service.MaterialService;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class PantallaMateriales {
 private final MaterialService servicio;
 private final TableView<Material> tabla=new TableView<>();
 private final Label detalle=new Label("Seleccione un material para ver sus datos de laboratorio.");
 public PantallaMateriales(MaterialService servicio){this.servicio=servicio;}
 public Parent crear(){
  Label titulo=new Label("Biblioteca de materiales");titulo.getStyleClass().add("page-heading");
  Label descripcion=new Label("Registre los resultados de laboratorio y reutilícelos al crear una mezcla.");descripcion.setWrapText(true);
  Button nuevo=new Button("+ Registrar material");nuevo.getStyleClass().add("principal");nuevo.setOnAction(e->formulario());
  columna("Material",Material::getNombre);columna("Tipo",Material::getTipo);columna("Procedencia",m->texto(m.getProcedencia()));
  columna("Ensayo / fecha",m->texto(m.getReferenciaEnsayo())+" "+texto(m.getFechaEnsayo()));
  tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);tabla.setPlaceholder(new Label("Aún no hay materiales registrados."));
  tabla.getSelectionModel().selectedItemProperty().addListener((o,a,m)->detalle.setText(m==null?"Seleccione un material para ver sus datos de laboratorio.":
   "Densidad relativa: "+formato(m.getDensidadRelativa())+" · Absorción: "+formato(m.getAbsorcion())+" % · Humedad: "+formato(m.getHumedad())+" %\n"+
   "Módulo de finura: "+formato(m.getModuloFinura())+" · Tamaño máximo: "+formato(m.getTamanoMaximoMm())+" mm · Peso unitario compactado: "+formato(m.getPesoUnitarioCompactado())+" kg/m³\n"+
   "Observaciones: "+texto(m.getObservaciones())));
  detalle.setWrapText(true); VBox ficha=new VBox(8,new Label("Datos del material"),detalle);ficha.getStyleClass().add("tarjeta");
  Label aviso=new Label("Los valores proceden de los ensayos que ingrese. Crear un nuevo registro conserva la trazabilidad de los datos anteriores.");aviso.setWrapText(true);aviso.getStyleClass().add("aviso");
  VBox contenido=new VBox(14,titulo,descripcion,nuevo,tabla,ficha,aviso);contenido.setPadding(new Insets(28));contenido.getStyleClass().add("raiz");VBox.setVgrow(tabla,Priority.ALWAYS);cargar();return contenido;
 }
 private void columna(String nombre,Function<Material,String> f){TableColumn<Material,String> c=new TableColumn<>(nombre);c.setCellValueFactory(v->new SimpleStringProperty(f.apply(v.getValue())));tabla.getColumns().add(c);}
 private void cargar(){tabla.setItems(FXCollections.observableArrayList(servicio.listar()));}
 private void formulario(){
  Dialog<ButtonType>d=new Dialog<>();d.setTitle("Registrar material");d.setHeaderText("Identificación y resultados de laboratorio");
  ComboBox<String> tipo=new ComboBox<>(FXCollections.observableArrayList("CEMENTO","AGREGADO FINO","AGREGADO GRUESO"));tipo.setValue("AGREGADO FINO");
  String[] nombres={"Nombre *","Procedencia","Referencia del ensayo / informe","Fecha del ensayo (AAAA-MM-DD)","Densidad relativa","Absorción (%)","Humedad total (%)","Módulo de finura","Tamaño máximo (mm)","Peso unitario compactado (kg/m³)","Observaciones"};
  TextField[] f=new TextField[nombres.length];GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(9);grid.setPadding(new Insets(12));
  grid.add(new Label("Tipo *"),0,0);grid.add(tipo,1,0);
  for(int i=0;i<nombres.length;i++){f[i]=new TextField();f[i].setPromptText(i==0?"Ej. Arena cantera A":"Opcional");grid.add(new Label(nombres[i]),0,i+1);grid.add(f[i],1,i+1);}
  Label nota=new Label("Ingrese los valores medidos y sus unidades; deje vacío lo que aún no conozca.");nota.setWrapText(true);grid.add(nota,0,nombres.length+1,2,1);
  ScrollPane scroll=new ScrollPane(grid);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(475);d.getDialogPane().setContent(scroll);
  ButtonType guardar=new ButtonType("Guardar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
  ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
   Material m=new Material();m.setTipo(tipo.getValue());m.setNombre(f[0].getText());m.setProcedencia(f[1].getText());m.setReferenciaEnsayo(f[2].getText());m.setFechaEnsayo(f[3].getText());
   m.setDensidadRelativa(numero(f[4]));m.setAbsorcion(numero(f[5]));m.setHumedad(numero(f[6]));m.setModuloFinura(numero(f[7]));m.setTamanoMaximoMm(numero(f[8]));m.setPesoUnitarioCompactado(numero(f[9]));m.setObservaciones(f[10].getText());servicio.guardar(m);
  }catch(Exception ex){e.consume();new Alert(Alert.AlertType.ERROR,ex instanceof NumberFormatException?"Ingrese números válidos en los resultados de ensayo.":ex.getMessage(),ButtonType.OK).showAndWait();}});
  d.showAndWait().ifPresent(b->{if(b==guardar)cargar();});
 }
 private Double numero(TextField f){return f.getText().isBlank()?null:Double.valueOf(f.getText().trim().replace(',','.'));}
 private static String texto(String s){return s==null?"":s;}
 private static String formato(Double n){return n==null?"Sin dato":String.format(Locale.ROOT,"%.3f",n);}
}
