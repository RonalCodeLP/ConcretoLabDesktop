package com.concretolab.ui;

import com.concretolab.model.*;
import com.concretolab.service.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class PantallaCercha {
 private final ProyectoService proyectos;private final ModeloCerchaService modelos;private final Consumer<Proyecto> seleccionarProyecto;
 private final ComboBox<Proyecto> proyecto=new ComboBox<>();private final ComboBox<ModeloCercha> guardados=new ComboBox<>();
 private final TextField nombre=new TextField();private final TextArea nudos=new TextArea(),barras=new TextArea(),resultado=new TextArea();
 private final Canvas lienzo=new Canvas(580,320);private ModeloCercha activo;
 public PantallaCercha(ProyectoService p,ModeloCerchaService m,Consumer<Proyecto> seleccionar){proyectos=p;modelos=m;seleccionarProyecto=seleccionar;}
 public Parent crear(Proyecto seleccionado){
  Label titulo=new Label("Análisis de cerchas 2D");titulo.getStyleClass().add("page-heading");
  Label subtitulo=new Label("1. Define nudos, apoyos y cargas   2. Conecta barras   3. Revisa el modelo   4. Calcula");subtitulo.setWrapText(true);
  proyecto.setItems(FXCollections.observableArrayList(proyectos.proyectos()));proyecto.setPromptText("Selecciona un proyecto");proyecto.setPrefWidth(200);
  guardados.setPromptText("Modelo guardado");guardados.setPrefWidth(200);
  if(seleccionado!=null)proyecto.getItems().stream().filter(p->p.getId().equals(seleccionado.getId())).findFirst().ifPresent(proyecto::setValue);
  proyecto.setOnAction(e->{seleccionarProyecto.accept(proyecto.getValue());activo=null;guardados.getItems().clear();if(proyecto.getValue()!=null)guardados.getItems().setAll(modelos.listar(proyecto.getValue().getId()));limpiar();});
  guardados.setOnAction(e->{ModeloCercha m=guardados.getValue();if(m!=null){activo=m;nombre.setText(m.getNombre());nudos.setText(m.getNudos());barras.setText(m.getBarras());}});
  if(proyecto.getValue()!=null)guardados.setItems(FXCollections.observableArrayList(modelos.listar(proyecto.getValue().getId())));
  nombre.setPromptText("Nombre del modelo");nudos.setPrefRowCount(6);barras.setPrefRowCount(6);resultado.setEditable(false);resultado.setPrefRowCount(12);resultado.setWrapText(false);
  Button ejemplo=new Button("Cargar ejemplo educativo"),guardar=new Button("Guardar modelo"),nuevo=new Button("Nuevo modelo"),dibujar=new Button("Ver geometría"),calcular=new Button("Calcular");calcular.getStyleClass().add("principal");
  Button agregarNudo=new Button("+ Nudo y apoyo"),agregarBarra=new Button("+ Barra");agregarNudo.setOnAction(e->agregarNudo());agregarBarra.setOnAction(e->agregarBarra());
  ejemplo.setOnAction(e->{activo=null;guardados.getSelectionModel().clearSelection();nombre.setText("Cercha triangular de ejemplo");nudos.setText("A; 0; 0; SI; SI; 0; 0\nB; 4; 0; NO; SI; 0; 0\nC; 2; 3; NO; NO; 0; -30");barras.setText("AB; A; B; 200000; 20\nAC; A; C; 200000; 20\nBC; B; C; 200000; 20");});
  nuevo.setOnAction(e->{activo=null;guardados.getSelectionModel().clearSelection();limpiar();});
  guardar.setOnAction(e->guardar());dibujar.setOnAction(e->{try{var d=ModeloCerchaService.interpretar(nudos.getText(),barras.getText());dibujar(d);}catch(Exception ex){error(ex.getMessage());}});
  calcular.setOnAction(e->{try{var d=ModeloCerchaService.interpretar(nudos.getText(),barras.getText());var r=AnalisisCercha.resolver(d.nudos(),d.barras());dibujar(d);resultado.setText(formatear(r));}catch(Exception ex){resultado.clear();error(ex.getMessage());}});
  Runnable invalida=()->{resultado.setText("Los datos cambiaron. Vuelve a dibujar y calcular.");lienzo.getGraphicsContext2D().clearRect(0,0,lienzo.getWidth(),lienzo.getHeight());};
  nudos.textProperty().addListener((o,a,b)->invalida.run());barras.textProperty().addListener((o,a,b)->invalida.run());
  Label guiaNudos=new Label("Nudos: nombre; X (m); Y (m); apoyo X SI/NO; apoyo Y SI/NO; Fx (kN); Fy (kN)");guiaNudos.setWrapText(true);
  Label guiaBarras=new Label("Barras: nombre; nudo inicial; nudo final; módulo E (MPa); área (cm²)");guiaBarras.setWrapText(true);
  Label nota=new Label("Uso educativo y preliminar: cercha plana lineal de barras articuladas, cargas en nudos y pequeñas deformaciones. No representa flexión, pórticos, losas, sismo ni diseño de concreto; no usar como verificación de seguridad para obra.");nota.setWrapText(true);nota.getStyleClass().add("aviso");
  VBox entrada=new VBox(8,new Label("Proyecto y modelo"),new HBox(8,proyecto,guardados),nombre,new HBox(8,nuevo,ejemplo,guardar),guiaNudos,agregarNudo,nudos,guiaBarras,agregarBarra,barras,new HBox(8,dibujar,calcular));entrada.getStyleClass().add("tarjeta");entrada.setPrefWidth(560);HBox.setHgrow(entrada,Priority.ALWAYS);
  Label explicacion=new Label("Esquema: azul = barra, rojo = carga. Los apoyos se identifican en cada nudo.");explicacion.setWrapText(true);
  VBox vista=new VBox(8,new Label("Modelo y resultados"),explicacion,lienzo,resultado);vista.getStyleClass().add("tarjeta");HBox.setHgrow(vista,Priority.ALWAYS);
  VBox contenido=new VBox(14,titulo,subtitulo,new HBox(14,entrada,vista),nota);contenido.setPadding(new Insets(24));contenido.getStyleClass().add("raiz");
  ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
 }
 private void limpiar(){nombre.clear();nudos.clear();barras.clear();resultado.clear();lienzo.getGraphicsContext2D().clearRect(0,0,lienzo.getWidth(),lienzo.getHeight());}
 private void agregarNudo(){
  Dialog<ButtonType>d=new Dialog<>();d.setTitle("Añadir nudo");d.setHeaderText("Coordenadas, apoyos y cargas nodales");
  TextField id=new TextField(),x=new TextField(),y=new TextField(),fx=new TextField("0"),fy=new TextField("0");CheckBox apoyoX=new CheckBox("Restringir desplazamiento en X"),apoyoY=new CheckBox("Restringir desplazamiento en Y");
  GridPane grid=campos(new String[]{"Nombre","X (m)","Y (m)","Fx (kN)","Fy (kN)"},new TextField[]{id,x,y,fx,fy});d.getDialogPane().setContent(new VBox(10,grid,apoyoX,apoyoY));
  ButtonType agregar=new ButtonType("Agregar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,agregar);
  ((Button)d.getDialogPane().lookupButton(agregar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
   String clave=identificador(id.getText());double px=numero(x),py=numero(y),cx=numero(fx),cy=numero(fy);
   var actual=ModeloCerchaService.interpretar(nudos.getText(),barras.getText());if(actual.nudos().stream().anyMatch(n->n.nombre().equals(clave)))throw new IllegalArgumentException("Ya existe un nudo con ese nombre.");
   agregarLinea(nudos,String.join("; ",clave,Double.toString(px),Double.toString(py),apoyoX.isSelected()?"SI":"NO",apoyoY.isSelected()?"SI":"NO",Double.toString(cx),Double.toString(cy)));
  }catch(Exception ex){e.consume();error(ex.getMessage());}});d.showAndWait();
 }
 private void agregarBarra(){
  try{var datos=ModeloCerchaService.interpretar(nudos.getText(),barras.getText());if(datos.nudos().size()<2)throw new IllegalArgumentException("Agrega al menos dos nudos antes de crear una barra.");
   Dialog<ButtonType>d=new Dialog<>();d.setTitle("Añadir barra articulada");d.setHeaderText("Conexión y propiedades elásticas");TextField id=new TextField(),modulo=new TextField(),area=new TextField();
   ComboBox<String> inicio=new ComboBox<>(),fin=new ComboBox<>();for(var n:datos.nudos()){inicio.getItems().add(n.nombre());fin.getItems().add(n.nombre());}
   GridPane grid=campos(new String[]{"Nombre","Módulo E (MPa)","Área (cm²)"},new TextField[]{id,modulo,area});grid.add(new Label("Nudo inicial"),0,3);grid.add(inicio,1,3);grid.add(new Label("Nudo final"),0,4);grid.add(fin,1,4);d.getDialogPane().setContent(grid);
   ButtonType agregar=new ButtonType("Agregar",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,agregar);
   ((Button)d.getDialogPane().lookupButton(agregar)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{
    String clave=identificador(id.getText());double E=numero(modulo),A=numero(area);
    if(E<=0||A<=0)throw new IllegalArgumentException("El módulo y el área deben ser positivos.");
    if(inicio.getValue()==null||fin.getValue()==null||inicio.getValue().equals(fin.getValue()))throw new IllegalArgumentException("Elige dos nudos distintos.");
    if(datos.barras().stream().anyMatch(b->b.nombre().equals(clave)))throw new IllegalArgumentException("Ya existe una barra con ese nombre.");
    agregarLinea(barras,String.join("; ",clave,inicio.getValue(),fin.getValue(),Double.toString(E),Double.toString(A)));
   }catch(Exception ex){e.consume();error(ex.getMessage());}});d.showAndWait();
  }catch(Exception ex){error(ex.getMessage());}
 }
 private GridPane campos(String[] nombres,TextField[] campos){GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);for(int i=0;i<nombres.length;i++){g.add(new Label(nombres[i]),0,i);g.add(campos[i],1,i);}return g;}
 private String identificador(String valor){String v=valor.trim();if(v.isEmpty()||v.contains(";")||v.contains("\n")||v.contains("\r"))throw new IllegalArgumentException("Ingresa un nombre sin punto y coma.");return v;}
 private double numero(TextField f){try{double v=Double.parseDouble(f.getText().trim().replace(',','.'));if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(NumberFormatException e){throw new IllegalArgumentException("Ingresa números válidos y finitos.");}}
 private void agregarLinea(TextArea campo,String linea){campo.appendText((campo.getText().isBlank()?"":"\n")+linea);}
 private void guardar(){try{
  if(proyecto.getValue()==null)throw new IllegalArgumentException("Selecciona un proyecto en esta pantalla.");
  ModeloCercha m=new ModeloCercha();if(activo!=null)m.setId(activo.getId());m.setProyectoId(proyecto.getValue().getId());m.setNombre(nombre.getText());m.setNudos(nudos.getText());m.setBarras(barras.getText());
  activo=modelos.guardar(m);guardados.setItems(FXCollections.observableArrayList(modelos.listar(proyecto.getValue().getId())));resultado.setText("Modelo guardado localmente. Pulsa Calcular para obtener resultados.");
 }catch(Exception ex){error(ex.getMessage());}}
 private String formatear(AnalisisCercha.Resultado r){StringBuilder s=new StringBuilder("NUDOS · desplazamientos mm · reacciones kN\n");
  for(var n:r.nudos())s.append(String.format(Locale.ROOT,"%s  Ux=%+.5f  Uy=%+.5f  Rx=%+.4f  Ry=%+.4f%n",n.nombre(),n.uxMm(),n.uyMm(),n.rxKn(),n.ryKn()));
  s.append("\nBARRAS · fuerza axial kN · esfuerzo MPa\nSigno positivo: tracción; negativo: compresión.\n");
  for(var b:r.barras())s.append(String.format(Locale.ROOT,"%s  L=%.3f m  N=%+.4f kN  σ=%+.4f MPa%n",b.nombre(),b.longitudM(),b.axialKn(),b.tensionMpa()));return s.toString();}
 private void dibujar(ModeloCerchaService.Datos datos){
  if(datos.nudos().isEmpty())throw new IllegalArgumentException("Agrega nudos para visualizar el modelo.");
  Map<String,AnalisisCercha.Nudo> mapa=new HashMap<>();double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
  for(var n:datos.nudos()){if(!Double.isFinite(n.x())||!Double.isFinite(n.y()))throw new IllegalArgumentException("Coordenadas inválidas.");mapa.put(n.nombre(),n);minX=Math.min(minX,n.x());maxX=Math.max(maxX,n.x());minY=Math.min(minY,n.y());maxY=Math.max(maxY,n.y());}
  double escala=Math.min(480/Math.max(maxX-minX,1),220/Math.max(maxY-minY,1));double origenX=(lienzo.getWidth()-(maxX-minX)*escala)/2-minX*escala,origenY=(lienzo.getHeight()+(maxY-minY)*escala)/2+minY*escala;
  GraphicsContext g=lienzo.getGraphicsContext2D();g.setFill(Color.web("#f2f6fc"));g.fillRect(0,0,lienzo.getWidth(),lienzo.getHeight());g.setStroke(Color.web("#2263bd"));g.setLineWidth(3);
  for(var b:datos.barras()){var a=mapa.get(b.inicio());var z=mapa.get(b.fin());if(a==null||z==null)throw new IllegalArgumentException("Barra "+b.nombre()+": nudo inexistente.");g.strokeLine(origenX+a.x()*escala,origenY-a.y()*escala,origenX+z.x()*escala,origenY-z.y()*escala);}
  for(var n:datos.nudos()){double x=origenX+n.x()*escala,y=origenY-n.y()*escala;g.setFill(Color.web("#0f2740"));g.fillOval(x-4,y-4,8,8);g.fillText(n.nombre()+" ["+(n.restringeX()?"X":"")+(n.restringeY()?"Y":"")+"]",x+6,y-6);
   if(n.fx()!=0||n.fy()!=0){double longitud=Math.hypot(n.fx(),n.fy());g.setStroke(Color.FIREBRICK);g.strokeLine(x-35*n.fx()/longitud,y+35*n.fy()/longitud,x,y);g.fillText("F",x-30*n.fx()/longitud,y+30*n.fy()/longitud);g.setStroke(Color.web("#2263bd"));}}
 }
 private void error(String msg){new Alert(Alert.AlertType.ERROR,msg==null?"No se pudo completar la operación.":msg,ButtonType.OK).showAndWait();}
}
