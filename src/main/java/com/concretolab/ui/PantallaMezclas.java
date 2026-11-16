package com.concretolab.ui;

import com.concretolab.model.*;
import com.concretolab.service.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Locale;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

public class PantallaMezclas {
 private final ProyectoService proyectos; private final MezclaService servicio;
 private final MaterialService materiales;
 private final java.util.function.Consumer<Proyecto> seleccionarProyecto;
 private final ComboBox<Proyecto> proyecto=new ComboBox<>(); private final TableView<Mezcla> tabla=new TableView<>();
 private final TableView<Ensayo> ensayos=new TableView<>(); private final Label resultado=new Label("Seleccione una mezcla.");
 private final TextField volumen=new TextField("1");
 public PantallaMezclas(ProyectoService p,MezclaService s,MaterialService materiales,java.util.function.Consumer<Proyecto> seleccionarProyecto){proyectos=p;servicio=s;this.materiales=materiales;this.seleccionarProyecto=seleccionarProyecto;}
 public Parent crear(Proyecto seleccionado){
   Label titulo=new Label("Mezclas y ensayos");titulo.getStyleClass().add("page-heading");
   Label subtitulo=new Label("1. Selecciona un proyecto   2. Registra una dosificación   3. Calcula el lote   4. Registra las pruebas físicas");subtitulo.setWrapText(true);
   proyecto.setItems(FXCollections.observableArrayList(proyectos.proyectos()));
   if(seleccionado!=null)proyecto.getItems().stream().filter(p->p.getId().equals(seleccionado.getId())).findFirst().ifPresent(proyecto::setValue);
   proyecto.setPromptText("Seleccione un proyecto"); proyecto.setPrefWidth(250);proyecto.setOnAction(e->{seleccionarProyecto.accept(proyecto.getValue());cargar();});
   columna(tabla,"Mezcla",Mezcla::getNombre);columna(tabla,"f’c MPa",m->fmt(m.getResistencia()));columna(tabla,"Fuente",m->m.getReferencia()==null?"":m.getReferencia());
   tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);tabla.setPrefHeight(210);
   tabla.getSelectionModel().selectedItemProperty().addListener((o,a,b)->actualizar());
   columna(ensayos,"Probeta",Ensayo::getIdentificacion);columna(ensayos,"Fecha",Ensayo::getFecha);columna(ensayos,"Edad (días)",e->fmt(e.getEdadDias()));
   columna(ensayos,"Resistencia MPa",e->fmt(e.getResistenciaMpa()));columna(ensayos,"Asentamiento mm",e->fmt(e.getAsentamientoMm()));
   ensayos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);ensayos.setPrefHeight(200);
   Button nueva=new Button("+ Registrar mezcla"),prueba=new Button("+ Registrar ensayo"),recalcular=new Button("Calcular lote"),exportar=new Button("Guardar PDF"),usarMetrado=new Button("Elegir elementos del modelo");nueva.getStyleClass().add("principal");
   nueva.setOnAction(e->nueva());prueba.setOnAction(e->ensayo());recalcular.setOnAction(e->actualizar());exportar.setOnAction(e->exportar());
   usarMetrado.setOnAction(e->{if(proyecto.getValue()==null){error("Seleccione un proyecto.");return;}
     var elementos=proyectos.elementos(proyecto.getValue().getId());if(elementos.isEmpty()){error("El proyecto no tiene elementos.");return;}
     Dialog<ButtonType> d=new Dialog<>();d.setTitle("Elegir elementos");d.setHeaderText("Seleccione los elementos que utilizarán esta mezcla");
     ListView<Elemento> lista=new ListView<>(FXCollections.observableArrayList(elementos));lista.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);lista.setPrefSize(520,300);
     lista.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(Elemento item,boolean vacia){super.updateItem(item,vacia);setText(vacia||item==null?null:item.getNombre()+" · "+item.getTipo()+" · "+FormatoNumeros.volumen(item.volumen())+" m³");}});
     Label nota=new Label("Use Ctrl o Shift para seleccionar varios. La suma no descuenta intersecciones; revise el metrado.");nota.setWrapText(true);d.getDialogPane().setContent(new VBox(10,nota,lista));
     ButtonType usar=new ButtonType("Usar selección",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,usar);
     ((Button)d.getDialogPane().lookupButton(usar)).addEventFilter(javafx.event.ActionEvent.ACTION,ev->{if(lista.getSelectionModel().getSelectedItems().isEmpty()){ev.consume();error("Seleccione al menos un elemento.");}});
     d.showAndWait().ifPresent(b->{if(b==usar){double suma=lista.getSelectionModel().getSelectedItems().stream().mapToDouble(Elemento::volumen).sum();volumen.setText(Double.toString(suma));actualizar();}});});
   volumen.setPrefWidth(100);resultado.setWrapText(true);
   Label aviso=new Label("Dosificación suministrada por el usuario; no es un diseño ACI calculado ni una aprobación para obra. Los resultados de ensayo se registran sin dictaminar conformidad.");aviso.setWrapText(true);aviso.getStyleClass().add("aviso");
   VBox contenido=new VBox(12,titulo,subtitulo,new HBox(10,new Label("Proyecto:"),proyecto,nueva),tabla,
     new HBox(10,new Label("Volumen del lote (m³):"),volumen,recalcular,usarMetrado,exportar),resultado,
     new HBox(10,new Label("Ensayos registrados"),prueba),ensayos,aviso);contenido.setPadding(new Insets(24));contenido.getStyleClass().add("raiz");
   ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);cargar();return scroll;
 }
 private <T> void columna(TableView<T> t,String nombre,java.util.function.Function<T,String> f){TableColumn<T,String> c=new TableColumn<>(nombre);c.setCellValueFactory(v->new SimpleStringProperty(f.apply(v.getValue())));t.getColumns().add(c);}
 private void cargar(){tabla.getItems().clear();ensayos.getItems().clear();if(proyecto.getValue()!=null)tabla.getItems().setAll(servicio.listar(proyecto.getValue().getId()));resultado.setText("Seleccione una mezcla.");}
 private String texto(){Mezcla m=tabla.getSelectionModel().getSelectedItem();if(m==null)throw new IllegalArgumentException("Seleccione una mezcla.");double v=num(volumen);var r=MezclaService.calcular(m,v);
   return "Mezcla: "+m.getNombre()+" · f’c especificada: "+fmt(m.getResistencia())+" MPa\n"+
     "Volumen: "+FormatoNumeros.volumen(v)+" m³ · relación agua/cemento de diseño: "+fmt(r.relacionAguaCemento())+"\n"+
     "Cemento: "+fmt(m.getCemento()*v)+" kg · agua a agregar: "+fmt(r.aguaAgregar())+" kg\n"+
     "Agregado fino húmedo: "+fmt(r.finoHumedo())+" kg · agregado grueso húmedo: "+fmt(r.gruesoHumedo())+" kg\n"+
     "Base: masas de agregados SSS por m³; humedad y absorción sobre masa seca. Revise los datos y valide con mezcla de prueba.";
 }
 private void actualizar(){Mezcla m=tabla.getSelectionModel().getSelectedItem();ensayos.getItems().clear();if(m==null){resultado.setText("Seleccione una mezcla.");return;}ensayos.getItems().setAll(servicio.ensayos(m.getId()));try{resultado.setText(texto());}catch(Exception ex){resultado.setText(ex.getMessage());}}
 private void nueva(){if(proyecto.getValue()==null){error("Seleccione un proyecto en esta pantalla.");return;}
   TextField[] f=campos(12);String[] etiquetas={"Nombre", "Fuente o referencia", "f’c especificada (MPa)","Cemento (kg/m³)","Agua de diseño (kg/m³)","Fino SSS (kg/m³)","Grueso SSS (kg/m³)","Absorción fino (%)","Humedad total fino (%)","Absorción grueso (%)","Humedad total grueso (%)","Observaciones"};
   Dialog<ButtonType> d=dialogo("Registrar dosificación",etiquetas,f);
   ComboBox<Material> fino=new ComboBox<>(FXCollections.observableArrayList(materiales.porTipo("AGREGADO FINO"))),grueso=new ComboBox<>(FXCollections.observableArrayList(materiales.porTipo("AGREGADO GRUESO")));
   fino.setPromptText("Agregado fino de biblioteca (opcional)");grueso.setPromptText("Agregado grueso de biblioteca (opcional)");fino.setMaxWidth(Double.MAX_VALUE);grueso.setMaxWidth(Double.MAX_VALUE);
   fino.setOnAction(e->{Material m=fino.getValue();if(m!=null){f[7].setText(campo(m.getAbsorcion()));f[8].setText(campo(m.getHumedad()));}});
   grueso.setOnAction(e->{Material m=grueso.getValue();if(m!=null){f[9].setText(campo(m.getAbsorcion()));f[10].setText(campo(m.getHumedad()));}});
   javafx.scene.Node datos=d.getDialogPane().getContent();Label ayuda=new Label("Seleccione materiales para copiar humedad y absorción. Las masas son en condición SSS y deben provenir de una dosificación previa.");ayuda.setWrapText(true);
   d.getDialogPane().setContent(new VBox(8,ayuda,fino,grueso,datos));
   ButtonType guardar=new ButtonType("Guardar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
   ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
     Mezcla m=new Mezcla();m.setProyectoId(proyecto.getValue().getId());m.setNombre(f[0].getText().trim());m.setReferencia(f[1].getText().trim());m.setResistencia(num(f[2]));m.setCemento(num(f[3]));m.setAgua(num(f[4]));m.setFinoSss(num(f[5]));m.setGruesoSss(num(f[6]));m.setAbsorcionFino(num(f[7]));m.setHumedadFino(num(f[8]));m.setAbsorcionGrueso(num(f[9]));m.setHumedadGrueso(num(f[10]));m.setObservaciones(f[11].getText().trim());m.setMaterialFino(referencia(fino.getValue()));m.setMaterialGrueso(referencia(grueso.getValue()));servicio.guardar(m);
   }catch(Exception ex){e.consume();error(mensaje(ex));}});d.showAndWait().ifPresent(b->{if(b==guardar)cargar();});
 }
 private void ensayo(){Mezcla m=tabla.getSelectionModel().getSelectedItem();if(m==null){error("Seleccione una mezcla.");return;}
   TextField[] f=campos(6);f[1].setText(LocalDate.now().toString());String[] etiquetas={"Identificación probeta","Fecha (AAAA-MM-DD)","Edad (días)","Resistencia medida (MPa)","Asentamiento medido (mm)","Observaciones"};
   Dialog<ButtonType> d=dialogo("Registrar ensayo físico",etiquetas,f);ButtonType guardar=new ButtonType("Guardar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,guardar);
   ((Button)d.getDialogPane().lookupButton(guardar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{Ensayo x=new Ensayo();x.setMezclaId(m.getId());x.setIdentificacion(f[0].getText().trim());x.setFecha(f[1].getText().trim());x.setEdadDias(num(f[2]));x.setResistenciaMpa(num(f[3]));x.setAsentamientoMm(num(f[4]));x.setObservaciones(f[5].getText().trim());servicio.guardarEnsayo(x);}catch(Exception ex){e.consume();error(mensaje(ex));}});d.showAndWait().ifPresent(b->{if(b==guardar)actualizar();});
 }
 private Dialog<ButtonType> dialogo(String titulo,String[] etiquetas,TextField[] f){Dialog<ButtonType>d=new Dialog<>();d.setTitle(titulo);GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);g.setPadding(new Insets(12));for(int i=0;i<f.length;i++){g.add(new Label(etiquetas[i]),0,i);g.add(f[i],1,i);}ScrollPane s=new ScrollPane(g);s.setFitToWidth(true);s.setPrefViewportHeight(450);d.getDialogPane().setContent(s);return d;}
 private void exportar(){try{texto();Mezcla m=tabla.getSelectionModel().getSelectedItem();byte[] pdf=ReporteMezcla.generar(proyecto.getValue(),m,num(volumen),servicio.ensayos(m.getId()));FileChooser selector=new FileChooser();selector.setTitle("Guardar reporte");selector.setInitialFileName("mezcla.pdf");selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF","*.pdf"));var archivo=selector.showSaveDialog(tabla.getScene().getWindow());if(archivo!=null)Files.write(archivo.toPath(),pdf);}catch(Exception ex){error(mensaje(ex));}}
 private TextField[] campos(int n){TextField[] f=new TextField[n];for(int i=0;i<n;i++)f[i]=new TextField();return f;}
 private static String campo(Double n){return n==null?"":n.toString();}
 private static String referencia(Material m){return m==null?"No especificado":m.getNombre()+" | informe: "+(m.getReferenciaEnsayo()==null?"":m.getReferenciaEnsayo())+" | fecha: "+(m.getFechaEnsayo()==null?"":m.getFechaEnsayo());}
 private static double num(TextField f){return Double.parseDouble(f.getText().trim().replace(',','.'));}
 private static String fmt(double n){return String.format(Locale.ROOT,"%.3f",n);}
 private String mensaje(Exception ex){return ex instanceof NumberFormatException?"Ingrese valores numéricos válidos.":ex.getMessage();}
 private void error(String s){new Alert(Alert.AlertType.ERROR,s==null?"No se pudo completar la operación.":s,ButtonType.OK).showAndWait();}
}
