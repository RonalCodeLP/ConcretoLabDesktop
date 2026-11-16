package com.concretolab.ui;
import com.concretolab.model.*;
import com.concretolab.service.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
public class PantallaExpediente {
 private final ProyectoService proyectos;private final ExpedienteService expedientes;private final Consumer<Proyecto> seleccionar;
 public PantallaExpediente(ProyectoService p,ExpedienteService e,Consumer<Proyecto> seleccionar){proyectos=p;expedientes=e;this.seleccionar=seleccionar;}
 public Parent crear(Proyecto actual){
  Label titulo=new Label("Expediente estructural");titulo.getStyleClass().add("page-heading");
  Label ayuda=new Label("Registra los datos de referencia y la edición normativa que determine el profesional. Los campos vacíos indican información pendiente.");ayuda.setWrapText(true);
  ComboBox<Proyecto> proyecto=new ComboBox<>(FXCollections.observableArrayList(proyectos.proyectos()));proyecto.setPromptText("Seleccionar proyecto");proyecto.setPrefWidth(300);
  String[] nombres={"Ubicación exacta","Uso de la edificación","Fecha del proyecto (AAAA-MM-DD)","Situación del proyecto","Profesional responsable","Edición de E.030 seleccionada","Justificación de la edición","Referencia del estudio de suelos","Observaciones"};
  TextField[] f=new TextField[nombres.length];GridPane grid=new GridPane();grid.setHgap(16);grid.setVgap(10);
  for(int i=0;i<f.length;i++){f[i]=new TextField();f[i].setPromptText("Pendiente");f[i].setPrefWidth(440);grid.add(new Label(nombres[i]),0,i);grid.add(f[i],1,i);}
  Label estado=new Label();estado.setWrapText(true);
  Runnable cargar=()->{for(TextField campo:f)campo.clear();Proyecto p=proyecto.getValue();if(p==null)return;expedientes.obtener(p.getId()).ifPresent(e->{String[] valores={e.getUbicacion(),e.getUso(),e.getFechaProyecto(),e.getEstadoProyecto(),e.getProfesional(),e.getNorma(),e.getJustificacionNorma(),e.getEstudioSuelos(),e.getObservaciones()};for(int i=0;i<f.length;i++)f[i].setText(valores[i]==null?"":valores[i]);});estado.setText("");};
  proyecto.setOnAction(e->{seleccionar.accept(proyecto.getValue());cargar.run();});
  if(actual!=null)proyecto.getItems().stream().filter(p->p.getId().equals(actual.getId())).findFirst().ifPresent(proyecto::setValue);
  Button guardar=new Button("Guardar expediente");guardar.getStyleClass().add("principal");guardar.setOnAction(ev->{try{
   if(proyecto.getValue()==null)throw new IllegalArgumentException("Seleccione un proyecto.");ExpedienteEstructural e=new ExpedienteEstructural();e.setProyectoId(proyecto.getValue().getId());
   e.setUbicacion(f[0].getText().trim());e.setUso(f[1].getText().trim());e.setFechaProyecto(f[2].getText().trim());e.setEstadoProyecto(f[3].getText().trim());e.setProfesional(f[4].getText().trim());e.setNorma(f[5].getText().trim());e.setJustificacionNorma(f[6].getText().trim());e.setEstudioSuelos(f[7].getText().trim());e.setObservaciones(f[8].getText().trim());expedientes.guardar(e);estado.setText("Expediente guardado localmente.");
  }catch(Exception ex){estado.setText(ex.getMessage());}});
  Label aviso=new Label("La selección de la norma se registra como dato del profesional; el sistema aún no determina su vigencia, realiza análisis sísmico del edificio ni verifica cumplimiento.");aviso.setWrapText(true);aviso.getStyleClass().add("aviso");
  VBox contenido=new VBox(15,titulo,ayuda,proyecto,grid,guardar,estado,aviso);contenido.setPadding(new Insets(28));contenido.getStyleClass().add("raiz");ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
 }
}
