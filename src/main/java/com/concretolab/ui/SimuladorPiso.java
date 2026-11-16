package com.concretolab.ui;

import com.concretolab.model.Proyecto;
import com.concretolab.service.EdificioCortante;
import com.concretolab.service.ProyectoService;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.util.Locale;

final class SimuladorPiso extends VBox {
    private final TextField modulo=new TextField(), masa=new TextField(), amort=new TextField("5"), paso=new TextField("0.005");
    private final ComboBox<String> eje=new ComboBox<>();
    private final CheckBox aceptar=new CheckBox("Confirmo el modelo de masas iguales por nivel, diafragma rígido, base fija y giros impedidos.");
    private final Label registro=new Label("Pulso sintético de práctica · 0,1 g · 1 Hz durante 1 s y 4 s de vibración libre"),
        resultado=new Label(), instante=new Label();
    private final Canvas canvas=new Canvas(640,350);
    private java.util.List<com.concretolab.model.Elemento> elementos=java.util.List.of();
    private double angulo=0.65, inicioArrastre;
    private final Slider tiempo=new Slider();
    private final Button reproducir=new Button("Reproducir");
    private double[] importado;
    private EdificioCortante.Resultado respuesta;
    private double altura, dt;
    private AnimationTimer animacion;

    SimuladorPiso(ProyectoService proyectos, ComboBox<Proyecto> proyecto) {
        super(10);
        canvas.setOnMousePressed(ev->inicioArrastre=ev.getX());
        canvas.setOnMouseDragged(ev->{angulo+=(ev.getX()-inicioArrastre)/130;inicioArrastre=ev.getX();dibujar();});
        eje.getItems().addAll("X","Y"); eje.setValue("X");
        var campos=new GridPane(); campos.setHgap(12); campos.setVgap(6);
        campos.addRow(0,new Label("Dirección horizontal"),eje);
        campos.addRow(1,new Label("Módulo E efectivo de las columnas (MPa)"),modulo);
        campos.addRow(2,new Label("Masa sísmica por piso (t)"),masa);
        campos.addRow(3,new Label("Amortiguamiento (%)"),amort);
        campos.addRow(4,new Label("Intervalo entre muestras (s)"),paso);
        var cargar=new Button("Abrir acelerograma local"); var pulso=new Button("Usar pulso de práctica");
        String demo=registro.getText();
        cargar.setOnAction(ev->{
            var fc=new FileChooser(); fc.setTitle("Una aceleración en m/s² por línea, a intervalo constante");
            var f=fc.showOpenDialog(getScene().getWindow()); if(f==null)return;
            invalidar();
            try(var lineas=Files.lines(f.toPath())){
                double[] datos=lineas.filter(s->!s.isBlank()).limit(200001).mapToDouble(SimuladorPiso::numero).toArray();
                if(datos.length<2 || datos.length>200000) throw new IllegalArgumentException("Cantidad de muestras inválida.");
                importado=datos; registro.setText(f.getName()+" · "+datos.length+" muestras en m/s². Verifica el intervalo.");
            }catch(Exception ex){importado=null; registro.setText(demo); resultado.setText("No se cargó el archivo. Usa una aceleración finita por línea, sin encabezado.");}
        });
        pulso.setOnAction(ev->{invalidar();importado=null;registro.setText(demo);});
        var calcular=new Button("Simular movimiento"); calcular.getStyleClass().add("principal");
        calcular.setOnAction(ev->{invalidar();try{
            if(proyecto.getValue()==null || !aceptar.isSelected()) throw new IllegalArgumentException("Selecciona el proyecto y confirma sus hipótesis.");
            elementos=proyectos.elementos(proyecto.getValue().getId());
dt=numero(paso.getText()); double[] aceleracion=importado;
            if(aceleracion==null){
                if(dt<=0 || dt>0.05 || 5/dt>199999) throw new IllegalArgumentException("Para el pulso usa un paso entre 0,000026 y 0,05 s.");
                aceleracion=new double[(int)Math.round(5/dt)+1];
                for(int i=0;i<aceleracion.length && i*dt<=1;i++) aceleracion[i]=0.980665*Math.sin(2*Math.PI*i*dt);
            }
            respuesta=EdificioCortante.simular(elementos,numero(modulo.getText()),"X".equals(eje.getValue()),numero(masa.getText()),numero(amort.getText())/100,dt,aceleracion);
            altura=respuesta.niveles().get(0).h();
            var resumen=new StringBuilder(String.format(Locale.ROOT,"Período fundamental %.3f s · pico global %.3f mm",respuesta.periodo(),1000*respuesta.maximo()));
            for(int j=0;j<respuesta.niveles().size();j++){
                var nivel=respuesta.niveles().get(j);
                resumen.append(String.format(Locale.ROOT,"%nPiso %d · K %.0f kN/m · deriva máxima %.3f %% · cortante elástico máximo %.2f kN",j+1,nivel.k(),100*respuesta.derivas()[j],nivel.k()*nivel.h()*respuesta.derivas()[j]));
            }
            resultado.setText(resumen.toString());
            tiempo.setMax(aceleracion.length-1); tiempo.setDisable(false); reproducir.setDisable(false);
            tiempo.setValue(respuesta.pico()); dibujar();
        }catch(Exception ex){resultado.setText(ex.getMessage());}});
        reproducir.setOnAction(ev->{if(animacion!=null){detener();return;} tiempo.setValue(0);
            animacion=new AnimationTimer(){long inicio; public void handle(long ahora){if(inicio==0)inicio=ahora;
                tiempo.setValue((ahora-inicio)*1e-9/dt); if(tiempo.getValue()>=tiempo.getMax())detener();}};
            reproducir.setText("Pausar");animacion.start();
        });
        tiempo.valueProperty().addListener((o,a,b)->dibujar());
        sceneProperty().addListener((o,a,b)->{if(b==null)detener();});
        for(var campo:new TextField[]{modulo,masa,amort,paso})campo.textProperty().addListener((o,a,b)->invalidar());
        eje.valueProperty().addListener((o,a,b)->invalidar()); aceptar.selectedProperty().addListener((o,a,b)->invalidar());
        proyecto.valueProperty().addListener((o,a,b)->{aceptar.setSelected(false);masa.clear();modulo.clear();cargarEjemplo(b);invalidar();});
        cargarEjemplo(proyecto.getValue());
        var descripcion=new Label("Las secciones y alturas provienen de las columnas guardadas. Cada piso se traslada en un solo eje. Introduce la masa común por planta y el módulo efectivo. Las vigas y zapatas se representan mediante las restricciones confirmadas. Se omiten torsión, daño y flexibilidad de cimentación.");
        descripcion.setWrapText(true);
        getChildren().addAll(descripcion,campos,aceptar,new HBox(10,cargar,pulso),registro,calcular,resultado,canvas,tiempo,new HBox(10,reproducir,instante));
        invalidar();
    }
    private void cargarEjemplo(Proyecto p){if(p!=null && ProyectoService.ESCENARIOS.contains(p.getNombre())){masa.setText("80");modulo.setText("25000");paso.setText("0.002");amort.setText("5");}}
    private void detener(){if(animacion!=null)animacion.stop();animacion=null;reproducir.setText("Reproducir");}
    private void invalidar(){detener();respuesta=null;tiempo.setDisable(true);reproducir.setDisable(true);resultado.setText("Pendiente de cálculo.");instante.setText("");canvas.getGraphicsContext2D().clearRect(0,0,canvas.getWidth(),canvas.getHeight());}
    private static double numero(String texto){double n=Double.parseDouble(texto.trim().replace(',','.'));if(!Double.isFinite(n))throw new IllegalArgumentException("Ingresa valores finitos.");return n;}
    private void dibujar(){
        if(respuesta==null)return;
        int i=(int)Math.round(tiempo.getValue()); double[] u=respuesta.u()[respuesta.u().length-1];
        i=Math.min(i,u.length-1); double escala=Math.min(100,altura/(3*Math.max(respuesta.maximo(),1e-15)));
        VistaDesplazamiento.dibujar(canvas,elementos,angulo,respuesta,i,respuesta.maximo(),escala,"X".equals(eje.getValue()));
        instante.setText(String.format(Locale.ROOT,"t = %.3f s · u techo = %.3f mm · deformada ×%.2f",i*dt,1000*u[i],escala));
    }
}
