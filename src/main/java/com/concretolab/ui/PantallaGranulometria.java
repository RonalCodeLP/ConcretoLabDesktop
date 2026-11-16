package com.concretolab.ui;
import com.concretolab.service.GranulometriaService;
import com.concretolab.service.MaterialService;
import com.concretolab.service.RegistroGranulometriaService;
import com.concretolab.model.Material;
import com.concretolab.model.RegistroGranulometria;
import java.time.LocalDate;
import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class PantallaGranulometria {
 private final MaterialService materiales;private final RegistroGranulometriaService registros;
 public PantallaGranulometria(MaterialService materiales,RegistroGranulometriaService registros){this.materiales=materiales;this.registros=registros;}
 public Parent crear(){
  Label titulo=new Label("Calculadora de granulometría");titulo.getStyleClass().add("page-heading");
  Label guia=new Label("Ingresa los datos medidos del tamizado. Cada línea representa un tamiz: abertura en mm y masa retenida en gramos.");guia.setWrapText(true);
  TextField masa=new TextField(),fondo=new TextField();masa.setPromptText("Masa seca inicial (g)");fondo.setPromptText("Masa en fondo (g)");
  ComboBox<Material> material=new ComboBox<>(FXCollections.observableArrayList(materiales.listar().stream().filter(m->m.getTipo().startsWith("AGREGADO")).toList()));material.setPromptText("Agregado de la biblioteca");material.setPrefWidth(230);
  ComboBox<RegistroGranulometria> historial=new ComboBox<>();historial.setPromptText("Abrir ensayo guardado");historial.setPrefWidth(220);
  TextField identificacion=new TextField(),fecha=new TextField(LocalDate.now().toString());identificacion.setPromptText("Identificación del ensayo");fecha.setPromptText("AAAA-MM-DD");
  TextArea entradas=new TextArea();entradas.setPromptText("Ejemplo de formato, de mayor a menor:\n4.75; 10\n2.36; 100\n1.18; 250");entradas.setPrefRowCount(7);
  material.setOnAction(e->{historial.getItems().clear();if(material.getValue()!=null)historial.getItems().setAll(registros.listar(material.getValue().getId()));});
  historial.setOnAction(e->{RegistroGranulometria r=historial.getValue();if(r!=null){identificacion.setText(r.getIdentificacion());fecha.setText(r.getFecha());masa.setText(Double.toString(r.getMasaInicial()));fondo.setText(Double.toString(r.getFondo()));entradas.setText(r.getMediciones());}});
  TableView<GranulometriaService.Fila> tabla=new TableView<>();tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);tabla.setPrefHeight(210);
  columna(tabla,"Abertura mm",r->fmt(r.aberturaMm()));columna(tabla,"Retenido g",r->fmt(r.retenidoG()));columna(tabla,"Retenido %",r->fmt(r.retenidoPorcentaje()));columna(tabla,"Acumulado %",r->fmt(r.retenidoAcumulado()));columna(tabla,"Pasa %",r->fmt(r.pasaPorcentaje()));
  CategoryAxis x=new CategoryAxis();x.setLabel("Abertura de tamiz (mm)");NumberAxis y=new NumberAxis(0,100,20);y.setLabel("Porcentaje que pasa (%)");
  LineChart<String,Number> grafico=new LineChart<>(x,y);grafico.setLegendVisible(false);grafico.setAnimated(false);grafico.setCreateSymbols(true);grafico.setPrefHeight(230);
  Label resumen=new Label("El resultado se calculará al ingresar las mediciones.");resumen.setWrapText(true);
  Button calcular=new Button("Calcular resultados");calcular.getStyleClass().add("principal");
  Runnable invalidar=()->{tabla.getItems().clear();grafico.getData().clear();resumen.setText("Los datos cambiaron. Calcula nuevamente los resultados.");};
  masa.textProperty().addListener((o,a,b)->invalidar.run());fondo.textProperty().addListener((o,a,b)->invalidar.run());entradas.textProperty().addListener((o,a,b)->invalidar.run());
  calcular.setOnAction(e->{try{
   List<GranulometriaService.Tamiz> tamices=RegistroGranulometriaService.leer(entradas.getText());
   var r=GranulometriaService.calcular(numero(masa.getText()),tamices,numero(fondo.getText()));tabla.setItems(FXCollections.observableArrayList(r.filas()));
   XYChart.Series<String,Number> serie=new XYChart.Series<>();for(var fila:r.filas())serie.getData().add(new XYChart.Data<>(fmt(fila.aberturaMm()),fila.pasaPorcentaje()));grafico.getData().setAll(serie);
   resumen.setText("Masa recuperada: "+fmt(r.recuperadoG())+" g · Diferencia frente a la masa inicial: "+fmt(r.diferenciaG())+" g ("+fmt(r.diferenciaPorcentaje())+" %). Revise cualquier diferencia con el procedimiento del laboratorio.");
  }catch(Exception ex){tabla.getItems().clear();grafico.getData().clear();resumen.setText(ex instanceof NumberFormatException?"Ingrese números válidos.":ex.getMessage());}});
  Button guardar=new Button("Guardar ensayo");guardar.setOnAction(e->{try{
   if(material.getValue()==null)throw new IllegalArgumentException("Seleccione un agregado de la biblioteca antes de guardar.");
   RegistroGranulometria r=new RegistroGranulometria();r.setMaterialId(material.getValue().getId());r.setIdentificacion(identificacion.getText().trim());r.setFecha(fecha.getText().trim());r.setMasaInicial(numero(masa.getText()));r.setFondo(numero(fondo.getText()));r.setMediciones(entradas.getText());registros.guardar(r);
   historial.getItems().setAll(registros.listar(material.getValue().getId()));resumen.setText("Ensayo guardado. Selecciónalo en el historial para volver a consultarlo.");
  }catch(Exception ex){new Alert(Alert.AlertType.ERROR,ex instanceof NumberFormatException?"Ingrese masas numéricas válidas.":ex.getMessage(),ButtonType.OK).showAndWait();}});
  Label nota=new Label("Gráfico categórico, no semilogarítmico. Esta herramienta no determina cumplimiento de una norma.");nota.setWrapText(true);nota.getStyleClass().add("aviso");
  VBox contenido=new VBox(12,titulo,guia,new HBox(10,material,historial),new HBox(10,identificacion,fecha),new HBox(10,masa,fondo),entradas,new HBox(10,calcular,guardar),resumen,tabla,grafico,nota);contenido.setPadding(new Insets(26));contenido.getStyleClass().add("raiz");
  ScrollPane scroll=new ScrollPane(contenido);scroll.setFitToWidth(true);return scroll;
 }
 private static void columna(TableView<GranulometriaService.Fila> t,String nombre,java.util.function.Function<GranulometriaService.Fila,String> valor){TableColumn<GranulometriaService.Fila,String> c=new TableColumn<>(nombre);c.setCellValueFactory(v->new SimpleStringProperty(valor.apply(v.getValue())));t.getColumns().add(c);}
 private static double numero(String s){return Double.parseDouble(s.trim().replace(',','.'));}
 private static String fmt(double n){return String.format(Locale.ROOT,"%.2f",n);}
}
