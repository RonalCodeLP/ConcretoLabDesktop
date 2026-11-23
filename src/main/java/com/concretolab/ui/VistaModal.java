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
    private final ErickDelgadoVisor3D yefriLermaVisor=new ErickDelgadoVisor3D();
    private final List<Tramo> yefriLermaTramos=new ArrayList<>();
    private final List<Piso> yefriLermaPisos;
    private final List<Elemento> elementos;
    private double yefriLermaEscala,yefriLermaFase;
    private final Slider yefriLermaAmplitud=new Slider(0,1,0.65);
    private final Label yefriLermaDetalle=new Label();
    private final Button yefriLermaPlay=new Button("Reproducir");
    private AnimationTimer yefriLermaReloj;
    public VistaModal(List<Elemento> elementos,Resultado resultado){
        super(8);
        this.elementos=elementos; yefriLermaPisos=resultado.pisos();
        var modos=new ComboBox<Integer>();
        for(var m:resultado.modos())modos.getItems().add(m.numero());
        var controles=new FlowPane(8,6,new Label("Modo"),modos,yefriLermaPlay,new Label("Amplitud visual"),yefriLermaAmplitud);
        yefriLermaVisor.erickDelgadoNodo().setPrefHeight(430);setVgrow(yefriLermaVisor.erickDelgadoNodo(),Priority.ALWAYS);
        var leyenda=new Label("Forma modal normalizada · azul 0 → rojo 1 · magnitud horizontal relativa. Interpolación lineal entre niveles.");
        leyenda.setWrapText(true);
        getChildren().addAll(controles,yefriLermaDetalle,yefriLermaVisor.erickDelgadoNodo(),leyenda);
        modos.valueProperty().addListener((o,a,b)->yefriLermaPreparar(resultado.modos().get(b-1)));
        modos.getSelectionModel().selectFirst();
        yefriLermaAmplitud.valueProperty().addListener((o,a,b)->yefriLermaDibujar());
        yefriLermaPlay.setOnAction(ev->{if(yefriLermaReloj!=null){yefriLermaParar();return;}
            yefriLermaPlay.setText("Pausar");yefriLermaReloj=new AnimationTimer(){
                long previo;
                public void handle(long ahora){if(previo!=0)yefriLermaFase+=(ahora-previo)*1e-9*Math.PI;previo=ahora;yefriLermaDibujar();}
            };yefriLermaReloj.start();});
        sceneProperty().addListener((o,a,b)->{if(b==null)yefriLermaParar();});
    }
    private void yefriLermaParar(){if(yefriLermaReloj!=null)yefriLermaReloj.stop();yefriLermaReloj=null;yefriLermaPlay.setText("Reproducir");}
    private double[] yefriLermaMovimiento(Modo m,double x,double y,double z){
        double[] previo={0,0,0};
        for(int i=0;i<yefriLermaPisos.size();i++){
            var p=yefriLermaPisos.get(i);var f=m.forma();double giro=f.get(3*i+2);
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
    private void yefriLermaPreparar(Modo modo){
        yefriLermaParar();yefriLermaFase=Math.PI/2;yefriLermaTramos.clear();yefriLermaVisor.erickDelgadoMostrar(elementos,null,e->{},true);
        var grupo=yefriLermaVisor.erickDelgadoCapaElementos();grupo.getChildren().clear();
        double unidad=ErickDelgadoVisor3D.erickDelgadoUnidades(),max=0;
        for(var e:elementos){
            double base=e.getZ()-e.getAltura()/2, techo=base+e.getAltura();
            var cortes=new TreeSet<Double>();cortes.add(base);cortes.add(techo);
            if("COLUMNA".equals(e.getTipo()))for(var piso:yefriLermaPisos)for(int i=0;i<4;i++){
                double z=piso.cota()-piso.altura()*i/4;if(z>base && z<techo)cortes.add(z);}
            double z0=base;
            for(double z1:cortes.tailSet(base,false)){
                boolean fija="ZAPATA".equals(e.getTipo()), viga="VIGA".equals(e.getTipo()) || "LOSA".equals(e.getTipo());
                var a=fija?new double[3]:yefriLermaMovimiento(modo,e.getX(),e.getY(),viga?techo:z0);
                var b=fija?new double[3]:yefriLermaMovimiento(modo,e.getX(),e.getY(),z1);
                var caja=new Box(e.getLargo()*unidad,(z1-z0)*unidad,e.getAncho()*unidad);
                caja.setTranslateX(e.getX()*unidad);caja.setTranslateY(-(z0+z1)*unidad/2);caja.setTranslateZ(e.getY()*unidad);
                var t=new Affine();caja.getTransforms().add(t);grupo.getChildren().add(caja);
                yefriLermaTramos.add(new Tramo(caja,t,a,b));max=Math.max(max,Math.max(Math.hypot(a[0],a[1]),Math.hypot(b[0],b[1])));z0=z1;
            }
        }
        double altura=yefriLermaPisos.stream().mapToDouble(Piso::altura).sum();
        yefriLermaEscala=max>0?altura*0.08/max:0;
        for(var tramo:yefriLermaTramos){
            var a=tramo.inferior;var b=tramo.superior;
            double relativo=max>0?Math.hypot((a[0]+b[0])/2,(a[1]+b[1])/2)/max:0;
            tramo.caja.setMaterial(new PhongMaterial(Color.hsb(225*(1-relativo),0.84,0.94)));
        }
        yefriLermaDetalle.setText(String.format(Locale.ROOT,"T = %.3f s · masa participante X %.1f %% · Y %.1f %% · reproducción lenta: 2 s/ciclo",modo.periodo(),100*modo.masaEfectivaX(),100*modo.masaEfectivaY()));
        yefriLermaDibujar();
    }
    private void yefriLermaDibujar(){
        double f=yefriLermaEscala*yefriLermaAmplitud.getValue()*Math.sin(yefriLermaFase),u=ErickDelgadoVisor3D.erickDelgadoUnidades();
        for(var tramo:yefriLermaTramos){
            var a=tramo.inferior;var b=tramo.superior;var t=tramo.transform;
            t.setTx(f*u*(a[0]+b[0])/2);t.setTz(f*u*(a[1]+b[1])/2);
            t.setMxy(f*u*(a[0]-b[0])/tramo.caja.getHeight());t.setMzy(f*u*(a[1]-b[1])/tramo.caja.getHeight());
            t.setMxz(-f*(a[2]+b[2])/2);t.setMzx(-t.getMxz());
        }
    }
}
