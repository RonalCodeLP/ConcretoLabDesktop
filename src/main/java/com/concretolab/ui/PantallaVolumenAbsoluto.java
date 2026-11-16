package com.concretolab.ui;
import com.concretolab.service.VolumenAbsolutoService;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class PantallaVolumenAbsoluto {
 public Parent crear(){
  Label titulo=new Label("Balance de volúmenes");titulo.getStyleClass().add("page-heading");
  Label descripcion=new Label("Calcula cemento y agregado fino por m³ a partir de parámetros elegidos y justificados por el profesional.");descripcion.setWrapText(true);
  String[] etiquetas={"Agua de diseño (kg/m³)","Relación agua/cemento (a/c)","Densidad relativa del cemento", "Agregado grueso SSS (kg/m³)","Densidad relativa grueso SSS", "Densidad relativa fino SSS", "Aire estimado (%)"};
  TextField[] f=new TextField[etiquetas.length];GridPane formulario=new GridPane();formulario.setHgap(16);formulario.setVgap(10);
  for(int i=0;i<f.length;i++){f[i]=new TextField();f[i].setPromptText("Dato definido por el profesional");formulario.add(new Label(etiquetas[i]),0,i);formulario.add(f[i],1,i);}
  Button calcular=new Button("Calcular balance");calcular.getStyleClass().add("principal");Label resultado=new Label("Completa los datos para calcular.");resultado.setWrapText(true);
  for(TextField campo:f)campo.textProperty().addListener((o,a,b)->resultado.setText("Los datos cambiaron. Calcula nuevamente."));
  calcular.setOnAction(e->{try{
   var d=new VolumenAbsolutoService.Datos(num(f[0]),num(f[1]),num(f[2]),num(f[3]),num(f[4]),num(f[5]),num(f[6]));var r=VolumenAbsolutoService.calcular(d);
   resultado.setText(String.format(Locale.ROOT,"Cemento: %.2f kg/m³%nAgregado fino SSS: %.2f kg/m³%nAgua: %.2f kg/m³ · Agregado grueso SSS: %.2f kg/m³%n%nVolúmenes (m³): agua %.4f · cemento %.4f · grueso %.4f · fino %.4f · aire %.4f",r.cemento(),r.finoSss(),d.agua(),d.gruesoSss(),r.volumenAgua(),r.volumenCemento(),r.volumenGrueso(),r.volumenFino(),r.volumenAire()));
  }catch(Exception ex){resultado.setText(ex instanceof NumberFormatException?"Ingrese números válidos en todos los campos.":ex.getMessage());}});
  Label nota=new Label("Es un balance matemático sin aditivos ni materiales cementantes adicionales. No selecciona agua, a/c ni agregado grueso; no garantiza f’c, trabajabilidad, durabilidad o cumplimiento ACI. Requiere revisión profesional y mezcla de prueba.");nota.setWrapText(true);nota.getStyleClass().add("aviso");
  VBox contenido=new VBox(16,titulo,descripcion,formulario,calcular,resultado,nota);contenido.setPadding(new Insets(28));contenido.getStyleClass().add("raiz");ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
 }
 private static double num(TextField f){return Double.parseDouble(f.getText().trim().replace(',','.'));}
}
