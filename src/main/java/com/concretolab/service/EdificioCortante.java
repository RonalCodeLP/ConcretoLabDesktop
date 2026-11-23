package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.util.*;

/** Diafragmas rígidos, masas iguales y una traslación horizontal por planta. */
public final class EdificioCortante {
    private EdificioCortante() {}
    public record Nivel(double techo,double h,double k) {}
    public record Resultado(List<Nivel> niveles,double[][] u,double periodo,int pico,double maximo,double[] derivas) {
        public double movimiento(Elemento e,double fraccion,int t) {
            if ("ZAPATA".equals(e.getTipo())) return 0;
            int j=0;
            while(j<niveles.size()-1 && e.getZ()>niveles.get(j).techo()+1e-6) j++;
            if ("VIGA".equals(e.getTipo())) return u[j][t];
            double base=j==0?0:u[j-1][t];
            return base+(u[j][t]-base)*fraccion*fraccion*(3-2*fraccion);
        }
    }
    public static Resultado yefriLermaSimular(List<Elemento> elementos,double modulo,boolean x,double masa,double amort,double dt,double[] registro){
        if(!Double.isFinite(masa)||masa<=0)throw new IllegalArgumentException("Revisa la masa por piso.");
        var grupos=new TreeMap<Double,List<Elemento>>();
        for(var e:elementos)if("COLUMNA".equals(e.getTipo())){
            double techo=Math.rint((e.getZ()+e.getAltura()/2)*1e6)/1e6;
            grupos.computeIfAbsent(techo,key->new ArrayList<>()).add(e);
        }
        int n=grupos.size();
        if(n<1 || n>12)throw new IllegalArgumentException("Se requieren entre 1 y 12 niveles de columnas.");
        List<Nivel> niveles=new ArrayList<>();
        double[][] matriz=new double[n][n], vectores=new double[n][n];
        for(var grupo:grupos.entrySet()){
            var piso=HistoriaPiso.yefriLermaPiso(grupo.getValue(),modulo,x);
            int i=niveles.size();
            if(i>0 && Math.abs(grupo.getKey()-piso.altura()-niveles.get(i-1).techo())>2e-6)
                throw new IllegalArgumentException("Las cotas de las columnas deben formar entrepisos continuos.");
            niveles.add(new Nivel(grupo.getKey(),piso.altura(),piso.rigidez()));
            double k=piso.rigidez()/masa;
            matriz[i][i]+=k; vectores[i][i]=1;
            if(i>0){matriz[i-1][i-1]+=k;matriz[i][i-1]=matriz[i-1][i]=-k;}
        }
        yefriLermaDiagonalizar(matriz,vectores);
        double[][] u=new double[n][registro.length]; double periodo=0;
        for(int j=0;j<n;j++){
            var modo=HistoriaPiso.yefriLermaResolver(1,matriz[j][j],amort,dt,registro);
            periodo=Math.max(periodo,modo.periodo());
            double participacion=0;
            for(double[] v:vectores) participacion+=v[j];
            for(int i=0;i<n;i++)for(int t=0;t<registro.length;t++)
                u[i][t]+=vectores[i][j]*participacion*modo.desplazamiento()[t];
        }
        int pico=0; double max=0; double[] derivas=new double[n];
        for(int i=0;i<n;i++)for(int t=0;t<registro.length;t++){
            if(Math.abs(u[i][t])>max){max=Math.abs(u[i][t]);pico=t;}
            double relativo=u[i][t]-(i==0?0:u[i-1][t]);
            derivas[i]=Math.max(derivas[i],Math.abs(relativo)/niveles.get(i).h());
        }
        return new Resultado(List.copyOf(niveles),u,periodo,pico,max,derivas);
    }
    private static void yefriLermaDiagonalizar(double[][] a,double[][] v){
        int n=a.length;
        for(int iter=0;iter<100*n*n;iter++){
            int p=0,q=0;double mayor=0,diagonal=0;
            for(int i=0;i<n;i++){
                diagonal=Math.max(diagonal,Math.abs(a[i][i]));
                for(int j=i+1;j<n;j++)if(Math.abs(a[i][j])>mayor){mayor=Math.abs(a[i][j]);p=i;q=j;}
            }
            if(mayor<=diagonal*1e-14)return;
            double ang=0.5*Math.atan2(2*a[p][q],a[q][q]-a[p][p]), c=Math.cos(ang),s=Math.sin(ang);
            double pp=a[p][p],qq=a[q][q],pq=a[p][q];
            a[p][p]=c*c*pp-2*c*s*pq+s*s*qq;
            a[q][q]=s*s*pp+2*c*s*pq+c*c*qq;a[q][p]=a[p][q]=0;
            for(int i=0;i<n;i++){
                if(i!=p && i!=q){
                    double ip=a[i][p],iq=a[i][q];
                    a[i][p]=a[p][i]=c*ip-s*iq;a[i][q]=a[q][i]=s*ip+c*iq;
                }
                double vp=v[i][p],vq=v[i][q];v[i][p]=c*vp-s*vq;v[i][q]=s*vp+c*vq;
            }
        }
        throw new IllegalArgumentException("No convergió el cálculo modal.");
    }
}
