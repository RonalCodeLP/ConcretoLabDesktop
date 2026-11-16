package com.concretolab.ui;
import com.concretolab.service.OsciladorSismico;
import com.concretolab.service.ProyectoService;
import com.concretolab.service.DiagnosticoSismico;
import com.concretolab.model.Proyecto;
import javafx.collections.FXCollections;
import java.util.function.Consumer;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class PantallaSismica {
 private final ProyectoService proyectos;private final Proyecto actual;private final Consumer<Proyecto> seleccionar;
 public PantallaSismica(ProyectoService proyectos,Proyecto actual,Consumer<Proyecto> seleccionar){this.proyectos=proyectos;this.actual=actual;this.seleccionar=seleccionar;}
 public Parent crear(){
  Label titulo=new Label("Respuesta sísmica idealizada");titulo.getStyleClass().add("page-heading");
  Label info=new Label("Modelo educativo de una masa y un resorte. Introduce la pseudoaceleración espectral correspondiente al período y amortiguamiento, obtenida por el profesional.");info.setWrapText(true);
  ComboBox<Proyecto> proyecto=new ComboBox<>(FXCollections.observableArrayList(proyectos.proyectos()));proyecto.setPromptText("Selecciona el proyecto que deseas analizar");proyecto.setPrefWidth(350);
  Label diagnostico=new Label("Selecciona un proyecto para revisar su preparación.");diagnostico.setWrapText(true);
  proyecto.setOnAction(e->seleccionar.accept(proyecto.getValue()));
  if(actual!=null)proyecto.getItems().stream().filter(p->p.getId().equals(actual.getId())).findFirst().ifPresent(proyecto::setValue);
  TextField masa=new TextField(),rigidez=new TextField(),aceleracion=new TextField();
  masa.setPromptText("Masa en toneladas");rigidez.setPromptText("Rigidez en kN/m");aceleracion.setPromptText("Aceleración espectral en g");
  GridPane g=new GridPane();g.setHgap(16);g.setVgap(10);String[] etiquetas={"Masa (t)","Rigidez lateral (kN/m)","Pseudoaceleración espectral PSA(T, ξ) (g)"};TextField[] campos={masa,rigidez,aceleracion};
  for(int i=0;i<campos.length;i++){g.add(new Label(etiquetas[i]),0,i);g.add(campos[i],1,i);}
  Label resultado=new Label("Ingresa los valores y pulsa Calcular.");resultado.setWrapText(true);
  for(TextField campo:campos)campo.textProperty().addListener((o,a,b)->resultado.setText("Los datos cambiaron. Calcula nuevamente."));
  Button calcular=new Button("Calcular");calcular.getStyleClass().add("principal");calcular.setOnAction(e->{try{
   var r=OsciladorSismico.calcular(numero(masa),numero(rigidez),numero(aceleracion));
   resultado.setText(String.format(Locale.ROOT,"Período natural no amortiguado: %.4f s%nFuerza elástica máxima equivalente: %.3f kN%nDesplazamiento espectral relativo: %.3f mm",r.periodoSegundos(),r.fuerzaKn(),r.desplazamientoMm()));
  }catch(Exception ex){resultado.setText(ex instanceof NumberFormatException?"Ingresa números válidos.":ex.getMessage());}});
  Label aviso=new Label("Modelo lineal de un grado de libertad, no conectado al modelo 3D. No calcula el espectro E.030, modos, torsión, combinaciones, derivas de edificio ni verifica seguridad. PSA debe corresponder al período calculado y amortiguamiento adoptado. No utilizar para diseñar una estructura real.");aviso.setWrapText(true);aviso.getStyleClass().add("aviso");
  TitledPane espectro=new TitledPane("Calculadora espectral independiente", new VBox(12,info,g,calcular,resultado,aviso)); espectro.setExpanded(false); titulo.setText("Simulación sísmica del proyecto"); VBox contenido=new VBox(16,titulo,proyecto,new SimuladorPiso(proyectos,proyecto),espectro);contenido.setPadding(new Insets(28));contenido.getStyleClass().add("raiz");ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
 }
 private static double numero(TextField f){return Double.parseDouble(f.getText().trim().replace(',','.'));}
}
