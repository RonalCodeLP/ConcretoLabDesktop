package com.concretolab.ui;

import com.concretolab.model.Elemento;
import com.concretolab.service.FernandoHuancaAnalisisSismico.*;
import javafx.animation.AnimationTimer;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Box;
import javafx.scene.transform.Affine;
import java.util.*;

public final class VistaModal extends VBox {
    private record Tramo(Box caja,Affine transform,double[] inferior,double[] superior) {}
    private final MiajeldDelgadoVisor3D visor=new MiajeldDelgadoVisor3D();
    private final List<Tramo> tramos=new ArrayList<>();
    private final List<Piso> pisos;
    private final List<Elemento> elementos;
    private double escala,fase;
    private final Slider amplitud=new Slider(0,1,0.65);
    private final Label detalle=new Label();
    private final Button play=new Button("Reproducir");
    private AnimationTimer reloj;
    public VistaModal(List<Elemento> elementos,Resultado resultado){
        super(8);
        this.elementos=elementos; pisos=resultado.pisos();
        var modos=new ComboBox<Integer>();
        for(var m:resultado.modos())modos.getItems().add(m.numero());
        var controles=new FlowPane(8,6,new Label("Modo"),modos,play,new Label("Amplitud visual"),amplitud);
        visor.nodo().setPrefHeight(430);setVgrow(visor.nodo(),Priority.ALWAYS);
        var leyenda=new Label("Forma modal normalizada · azul 0 → rojo 1 · magnitud horizontal relativa. Interpolación lineal entre niveles.");
        leyenda.setWrapText(true);
        getChildren().addAll(controles,detalle,visor.nodo(),leyenda);
        modos.valueProperty().addListener((o,a,b)->preparar(resultado.modos().get(b-1)));
        modos.getSelectionModel().selectFirst();
        amplitud.valueProperty().addListener((o,a,b)->dibujar());
        play.setOnAction(ev->{if(reloj!=null){parar();return;}
            play.setText("Pausar");reloj=new AnimationTimer(){
                long previo;
                public void handle(long ahora){if(previo!=0)fase+=(ahora-previo)*1e-9*Math.PI;previo=ahora;dibujar();}
            };reloj.start();});
        sceneProperty().addListener((o,a,b)->{if(b==null)parar();});
    }
    private void parar(){if(reloj!=null)reloj.stop();reloj=null;play.setText("Reproducir");}
    private double[] movimiento(Modo m,double x,double y,double z){
        double[] previo={0,0,0};
        for(int i=0;i<pisos.size();i++){
            var p=pisos.get(i);var f=m.forma();double giro=f.get(3*i+2);
            double[] actual={f.get(3*i)-(y-p.ycm())*giro,f.get(3*i+1)+(x-p.xcm())*giro,giro};
            if(z<=p.cota()){
                double h=Math.max(0,1-(p.cota()-z)/p.altura());
                for(int j=0;j<3;j++)actual[j]=previo[j]+h*(actual[j]-previo[j]);
                return actual;
            }
            previo=actual;
        }
        return previo;
    }
    private void preparar(Modo modo){
        parar();fase=Math.PI/2;tramos.clear();visor.mostrar(elementos,null,e->{},true);
        var grupo=visor.capaElementos();grupo.getChildren().clear();
        double unidad=MiajeldDelgadoVisor3D.unidades(),max=0;
        for(var e:elementos){
            double base=e.getZ()-e.getAltura()/2, techo=base+e.getAltura();
            var cortes=new TreeSet<Double>();cortes.add(base);cortes.add(techo);
            if("COLUMNA".equals(e.getTipo()))for(var piso:pisos)for(int i=0;i<4;i++){
                double z=piso.cota()-piso.altura()*i/4;if(z>base && z<techo)cortes.add(z);}
            double z0=base;
            for(double z1:cortes.tailSet(base,false)){
                boolean fija="ZAPATA".equals(e.getTipo()), viga="VIGA".equals(e.getTipo());
                var a=fija?new double[3]:movimiento(modo,e.getX(),e.getY(),viga?techo:z0);
                var b=fija?new double[3]:movimiento(modo,e.getX(),e.getY(),z1);
                var caja=new Box(e.getLargo()*unidad,(z1-z0)*unidad,e.getAncho()*unidad);
                caja.setTranslateX(e.getX()*unidad);caja.setTranslateY(-(z0+z1)*unidad/2);caja.setTranslateZ(e.getY()*unidad);
                var t=new Affine();caja.getTransforms().add(t);grupo.getChildren().add(caja);
                tramos.add(new Tramo(caja,t,a,b));max=Math.max(max,Math.max(Math.hypot(a[0],a[1]),Math.hypot(b[0],b[1])));z0=z1;
            }
        }
        double altura=pisos.stream().mapToDouble(Piso::altura).sum();
        escala=max>0?altura*0.08/max:0;
        for(var tramo:tramos){
            var a=tramo.inferior;var b=tramo.superior;
            double relativo=max>0?Math.hypot((a[0]+b[0])/2,(a[1]+b[1])/2)/max:0;
            tramo.caja.setMaterial(new PhongMaterial(Color.hsb(225*(1-relativo),0.84,0.94)));
        }
        detalle.setText(String.format(Locale.ROOT,"T = %.3f s · masa participante X %.1f %% · Y %.1f %% · reproducción lenta: 2 s/ciclo",modo.periodo(),100*modo.masaEfectivaX(),100*modo.masaEfectivaY()));
        dibujar();
    }
    private void dibujar(){
        double f=escala*amplitud.getValue()*Math.sin(fase),u=MiajeldDelgadoVisor3D.unidades();
        for(var tramo:tramos){
            var a=tramo.inferior;var b=tramo.superior;var t=tramo.transform;
            t.setTx(f*u*(a[0]+b[0])/2);t.setTz(f*u*(a[1]+b[1])/2);
            t.setMxy(f*u*(a[0]-b[0])/tramo.caja.getHeight());t.setMzy(f*u*(a[1]-b[1])/tramo.caja.getHeight());
            t.setMxz(-f*(a[2]+b[2])/2);t.setMzx(-t.getMxz());
        }
    }
}
