package com.concretolab.ui;
import com.concretolab.service.AnalisisPortico;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Caso de referencia para verificar el motor de pórticos planos. */
public class PantallaPortico {
 public Parent crear(){
  Label titulo=new Label("Pórtico plano · caso de comprobación");titulo.getStyleClass().add("page-heading");
  Label detalle=new Label("Voladizo horizontal: extremo izquierdo empotrado y carga vertical en el extremo derecho. Introduce datos para contrastar desplazamiento y reacciones.");detalle.setWrapText(true);
  String[] nombres={"Longitud L (m)","Módulo E (kN/m²)","Área A (m²)","Inercia I (m⁴)","Carga vertical Fy (kN; negativo hacia abajo)"};
  String[] ejemplos={"2","200000000","0.01","0.0001","-10"};TextField[] campos=new TextField[nombres.length];GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(10);
  for(int i=0;i<campos.length;i++){campos[i]=new TextField(ejemplos[i]);grid.add(new Label(nombres[i]),0,i);grid.add(campos[i],1,i);}
  Label resultado=new Label("Pulsa Calcular para ejecutar el caso de ejemplo.");resultado.setWrapText(true);
  for(TextField campo:campos)campo.textProperty().addListener((o,a,b)->resultado.setText("Los datos cambiaron. Calcula nuevamente."));
  Button calcular=new Button("Calcular caso");calcular.getStyleClass().add("principal");calcular.setOnAction(e->{try{
   double l=num(campos[0]),modulo=num(campos[1]),area=num(campos[2]),inercia=num(campos[3]),carga=num(campos[4]);
   if(!Double.isFinite(l)||l<=0)throw new IllegalArgumentException("La longitud debe ser positiva y finita.");
   var nudos=List.of(new AnalisisPortico.Nudo("A",0,0,true,true,true,0,0,0),new AnalisisPortico.Nudo("B",l,0,false,false,false,0,carga,0));
   var barras=List.of(new AnalisisPortico.Barra("AB","A","B",modulo,area,inercia));var r=AnalisisPortico.resolver(nudos,barras);
   resultado.setText(String.format(Locale.ROOT,"Desplazamiento vertical B: %.6f m%nGiro B: %.6f rad%nReacción vertical A: %.3f kN%nMomento de reacción A: %.3f kN·m",r.nudos().get(1).uyM(),r.nudos().get(1).giroRad(),r.nudos().get(0).ryKn(),r.nudos().get(0).momentoKnM()));
  }catch(Exception ex){resultado.setText(ex instanceof NumberFormatException?"Ingrese números válidos.":ex.getMessage());}});
  Label aviso=new Label("Caso lineal elástico y educativo; no conectado al modelo 3D y no incluye cargas distribuidas, análisis dinámico ni verificación normativa. No usar para aprobar una edificación.");aviso.setWrapText(true);aviso.getStyleClass().add("aviso");
  VBox contenido=new VBox(16,titulo,detalle,grid,calcular,resultado,aviso);contenido.setPadding(new Insets(28));contenido.getStyleClass().add("raiz");return contenido;
 }
 private static double num(TextField f){return Double.parseDouble(f.getText().trim().replace(',','.'));}
}
