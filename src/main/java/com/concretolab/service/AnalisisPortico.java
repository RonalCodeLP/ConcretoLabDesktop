package com.concretolab.service;
import java.util.*;

public final class AnalisisPortico {
 private AnalisisPortico(){}
 public record Nudo(String nombre,double x,double y,boolean fijoX,boolean fijoY,boolean fijoGiro,double fx,double fy,double momento){}
 public record Barra(String nombre,String inicio,String fin,double eKnM2,double areaM2,double inerciaM4){}
 public record ResultadoNudo(String nombre,double uxM,double uyM,double giroRad,double rxKn,double ryKn,double momentoKnM){}
 public record Resultado(List<ResultadoNudo> nudos){}
 public static Resultado yefriLermaResolver(List<Nudo> nudos,List<Barra> barras){
  if(nudos==null||nudos.size()<2||nudos.size()>60||barras==null||barras.isEmpty()||barras.size()>180)throw new IllegalArgumentException("Ingrese entre 2 y 60 nudos y de 1 a 180 barras.");
  int total=nudos.size()*3;double[][] k=new double[total][total];double[] f=new double[total];boolean[] fijo=new boolean[total];Map<String,Integer> mapa=new HashMap<>();
  for(int i=0;i<nudos.size();i++){Nudo n=nudos.get(i);if(n==null||n.nombre()==null||n.nombre().isBlank()||mapa.putIfAbsent(n.nombre(),i)!=null||!finito(n.x(),n.y(),n.fx(),n.fy(),n.momento()))throw new IllegalArgumentException("Revise los nombres, coordenadas y cargas de los nudos.");
   f[3*i]=n.fx();f[3*i+1]=n.fy();f[3*i+2]=n.momento();fijo[3*i]=n.fijoX();fijo[3*i+1]=n.fijoY();fijo[3*i+2]=n.fijoGiro();}
  Set<String> nombres=new HashSet<>(),pares=new HashSet<>();int[] conexiones=new int[nudos.size()];
  for(Barra b:barras){if(b==null||b.nombre()==null||b.nombre().isBlank()||!nombres.add(b.nombre()))throw new IllegalArgumentException("Cada barra necesita un nombre único.");
   Integer i=mapa.get(b.inicio()),j=mapa.get(b.fin());if(i==null||j==null||i.equals(j))throw new IllegalArgumentException("Revise los extremos de la barra "+b.nombre());
   conexiones[i]++;conexiones[j]++;
   if(!pares.add(Math.min(i,j)+":"+Math.max(i,j)))throw new IllegalArgumentException("Hay barras duplicadas entre los mismos nudos.");
   if(!finito(b.eKnM2(),b.areaM2(),b.inerciaM4())||b.eKnM2()<=0||b.areaM2()<=0||b.inerciaM4()<=0)throw new IllegalArgumentException("E, área e inercia deben ser positivos.");
   Nudo a=nudos.get(i),z=nudos.get(j);double dx=z.x()-a.x(),dy=z.y()-a.y(),l=Math.hypot(dx,dy);if(!Double.isFinite(l)||l<1e-9)throw new IllegalArgumentException("La longitud de la barra es inválida.");
   double c=dx/l,s=dy/l,ea=b.eKnM2()*b.areaM2()/l,ei=b.eKnM2()*b.inerciaM4();double v=12*ei/(l*l*l),t=6*ei/(l*l),r=4*ei/l,q=2*ei/l;
   double[][] local={{ea,0,0,-ea,0,0},{0,v,t,0,-v,t},{0,t,r,0,-t,q},{-ea,0,0,ea,0,0},{0,-v,-t,0,v,-t},{0,t,q,0,-t,r}};
   double[][] transform={{c,s,0,0,0,0},{-s,c,0,0,0,0},{0,0,1,0,0,0},{0,0,0,c,s,0},{0,0,0,-s,c,0},{0,0,0,0,0,1}};
   int[] ids={3*i,3*i+1,3*i+2,3*j,3*j+1,3*j+2};
   for(int p=0;p<6;p++)for(int q2=0;q2<6;q2++){double valor=0;for(int u=0;u<6;u++)for(int w=0;w<6;w++)valor+=transform[u][p]*local[u][w]*transform[w][q2];k[ids[p]][ids[q2]]+=valor;}
  }
  for(int i=0;i<conexiones.length;i++)if(conexiones[i]==0)throw new IllegalArgumentException("El nudo "+nudos.get(i).nombre()+" no está conectado a ninguna barra.");
  List<Integer> libres=new ArrayList<>();for(int i=0;i<total;i++)if(!fijo[i])libres.add(i);if(libres.isEmpty())throw new IllegalArgumentException("No hay grados de libertad libres.");
  double[][] a=new double[libres.size()][libres.size()];double[] b=new double[libres.size()];for(int i=0;i<libres.size();i++){b[i]=f[libres.get(i)];for(int j=0;j<libres.size();j++)a[i][j]=k[libres.get(i)][libres.get(j)];}
  double[] solucion=yefriLermaResolverSistema(a,b),u=new double[total];for(int i=0;i<libres.size();i++)u[libres.get(i)]=solucion[i];
  List<ResultadoNudo> salida=new ArrayList<>();for(int i=0;i<nudos.size();i++){double[] reacciones={-f[3*i],-f[3*i+1],-f[3*i+2]};for(int j=0;j<total;j++)for(int h=0;h<3;h++)reacciones[h]+=k[3*i+h][j]*u[j];
   salida.add(new ResultadoNudo(nudos.get(i).nombre(),u[3*i],u[3*i+1],u[3*i+2],fijo[3*i]?reacciones[0]:0,fijo[3*i+1]?reacciones[1]:0,fijo[3*i+2]?reacciones[2]:0));}
  return new Resultado(List.copyOf(salida));
 }
 private static double[] yefriLermaResolverSistema(double[][] a,double[] b){int n=b.length;double max=0;for(double[] fila:a)for(double v:fila){if(!Double.isFinite(v))throw new IllegalArgumentException("Matriz de rigidez inválida.");max=Math.max(max,Math.abs(v));}if(max==0)throw new IllegalArgumentException("El modelo es inestable.");
  for(int p=0;p<n;p++){int mejor=p;for(int i=p+1;i<n;i++)if(Math.abs(a[i][p])>Math.abs(a[mejor][p]))mejor=i;if(Math.abs(a[mejor][p])<max*1e-12)throw new IllegalArgumentException("Modelo inestable o mal condicionado; revise apoyos y conexiones.");double[] tmp=a[p];a[p]=a[mejor];a[mejor]=tmp;double aux=b[p];b[p]=b[mejor];b[mejor]=aux;for(int i=p+1;i<n;i++){double m=a[i][p]/a[p][p];for(int j=p;j<n;j++)a[i][j]-=m*a[p][j];b[i]-=m*b[p];}}
  double[] x=new double[n];for(int i=n-1;i>=0;i--){double v=b[i];for(int j=i+1;j<n;j++)v-=a[i][j]*x[j];x[i]=v/a[i][i];if(!Double.isFinite(x[i]))throw new IllegalArgumentException("Resultado no finito.");}return x;}
 private static boolean finito(double... v){for(double x:v)if(!Double.isFinite(x))return false;return true;}
}
