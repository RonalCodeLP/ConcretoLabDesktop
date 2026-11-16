package com.concretolab.ui;

import com.concretolab.model.Elemento;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.List;
import java.util.Locale;

/** Vista axonométrica del desplazamiento relativo del modelo de piso rígido. */
final class VistaDesplazamiento {
    private record Camara(double x,double y,double z,double cos,double sin,double zoom){}
    private static double[] punto(Elemento e,int esquina,double nivel,double d,boolean ejeX,Camara c){
        double x=e.getX()-c.x+((esquina&1)-0.5)*e.getLargo();
        double y=e.getY()-c.y+((esquina>>1)-0.5)*e.getAncho();
        double z=e.getZ()-c.z+(nivel-0.5)*e.getAltura();
        if(ejeX)x+=d;else y+=d;
        return new double[]{320+c.zoom*(x*c.cos-y*c.sin),158+c.zoom*(0.38*(x*c.sin+y*c.cos)-0.925*z)};
    }
    private static void linea(GraphicsContext g,double[] a,double[] b){g.strokeLine(a[0],a[1],b[0],b[1]);}
    private static Color color(double fraccion){return Color.hsb(225*(1-Math.min(1,fraccion)),0.84,0.99);}
    static void dibujar(Canvas canvas,List<Elemento> elementos,double angulo,com.concretolab.service.EdificioCortante.Resultado respuesta,int muestra,double pico,double escala,boolean ejeX){
        double xmin=Double.POSITIVE_INFINITY,ymin=xmin,zmin=xmin,xmax=-xmin,ymax=-xmin,zmax=-xmin;
        for(var e:elementos){
            xmin=Math.min(xmin,e.getX()-e.getLargo()/2);xmax=Math.max(xmax,e.getX()+e.getLargo()/2);
            ymin=Math.min(ymin,e.getY()-e.getAncho()/2);ymax=Math.max(ymax,e.getY()+e.getAncho()/2);
            zmin=Math.min(zmin,e.getZ()-e.getAltura()/2);zmax=Math.max(zmax,e.getZ()+e.getAltura()/2);
        }
        double ancho=Math.hypot(xmax-xmin,ymax-ymin)+2*pico*escala;
        var c=new Camara((xmin+xmax)/2,(ymin+ymax)/2,(zmin+zmax)/2,Math.cos(angulo),Math.sin(angulo),
            Math.min(600/ancho,255/(0.38*ancho+0.925*(zmax-zmin))));
        var g=canvas.getGraphicsContext2D();g.setFill(Color.web("#101e30"));g.fillRect(0,0,640,350);
        for(boolean original:new boolean[]{true,false}){
            double factor=original?0:escala;
            g.setLineWidth(original?1:2);
            for(var e:elementos){
                int tramos="COLUMNA".equals(e.getTipo())?12:1;
                for(int j=0;j<=tramos;j++){
                    double h=(double)j/tramos; double u=respuesta.movimiento(e,h,muestra), d=factor*u;
                    g.setStroke(original?Color.SLATEGRAY:color(pico==0?0:Math.abs(u)/pico));
                    for(int k=0;k<4;k++){
                        var a=punto(e,k,h,d,ejeX,c);
                        if(j==0 || j==tramos){
                            if((k&1)==0)linea(g,a,punto(e,k+1,h,d,ejeX,c));
                            if(k<2)linea(g,a,punto(e,k+2,h,d,ejeX,c));
                        }
                        if(j>0)linea(g,a,punto(e,k,(j-1.0)/tramos,factor*respuesta.movimiento(e,(j-1.0)/tramos,muestra),ejeX,c));
                    }
                }
            }
        }
        g.setFill(Color.WHITE);g.fillText("Geometría inicial en gris · Arrastra para girar",16,20);
        g.fillText("Desplazamiento relativo |u"+(ejeX?"X":"Y")+"| (mm)",16,315);
        for(int i=0;i<150;i++){g.setFill(color(pico==0?0:i/149.0));g.fillRect(18+i,324,1,8);}
        g.setFill(Color.WHITE);g.fillText("0",17,346);g.fillText(String.format(Locale.ROOT,"%.3f",pico*1000),152,346);
    }
}
