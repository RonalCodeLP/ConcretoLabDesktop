package com.concretolab.service;

import java.util.*;

/** Método de rigidez para cerchas planas elásticas con barras articuladas y cargas nodales. */
public final class AnalisisCercha {
 private AnalisisCercha(){}
 public record Nudo(String nombre,double x,double y,boolean restringeX,boolean restringeY,double fx,double fy){}
 public record Barra(String nombre,String inicio,String fin,double moduloMpa,double areaCm2){}
 public record NodoResultado(String nombre,double uxMm,double uyMm,double rxKn,double ryKn){}
 public record BarraResultado(String nombre,double longitudM,double axialKn,double tensionMpa){}
 public record Resultado(List<NodoResultado> nudos,List<BarraResultado> barras){}
 public static Resultado resolver(List<Nudo> nudos,List<Barra> barras){
  if(nudos==null||nudos.size()<2||nudos.size()>80||barras==null||barras.isEmpty()||barras.size()>250)throw new IllegalArgumentException("Ingrese entre 2 y 80 nudos y entre 1 y 250 barras.");
  Map<String,Integer> indices=new LinkedHashMap<>();int n=nudos.size(),gdl=n*2;double[][] k=new double[gdl][gdl];double[] f=new double[gdl];boolean[] fijo=new boolean[gdl];
  for(int i=0;i<n;i++){
   Nudo nd=nudos.get(i);if(nd==null||nd.nombre()==null||nd.nombre().isBlank()||indices.putIfAbsent(nd.nombre(),i)!=null)throw new IllegalArgumentException("Cada nudo debe tener un nombre único.");
   if(!finito(nd.x(),nd.y(),nd.fx(),nd.fy()))throw new IllegalArgumentException("Las coordenadas y cargas deben ser finitas.");
   fijo[2*i]=nd.restringeX();fijo[2*i+1]=nd.restringeY();f[2*i]=nd.fx();f[2*i+1]=nd.fy();
  }
  Set<String> nombres=new HashSet<>();Set<String> pares=new HashSet<>();
  for(Barra b:barras){
   if(b==null||b.nombre()==null||b.nombre().isBlank()||!nombres.add(b.nombre()))throw new IllegalArgumentException("Cada barra debe tener un nombre único.");
   Integer i=indices.get(b.inicio()),j=indices.get(b.fin());if(i==null||j==null||i.equals(j))throw new IllegalArgumentException("La barra "+b.nombre()+" necesita dos nudos existentes y diferentes.");
   String par=Math.min(i,j)+":"+Math.max(i,j);if(!pares.add(par))throw new IllegalArgumentException("Hay barras duplicadas entre los mismos nudos.");
   if(!finito(b.moduloMpa(),b.areaCm2())||b.moduloMpa()<=0||b.areaCm2()<=0)throw new IllegalArgumentException("El módulo elástico y el área deben ser positivos.");
   Nudo a=nudos.get(i),z=nudos.get(j);double dx=z.x()-a.x(),dy=z.y()-a.y(),l=Math.hypot(dx,dy);
   if(!Double.isFinite(l)||l<1e-9)throw new IllegalArgumentException("La barra "+b.nombre()+" tiene longitud nula o inválida.");
   // MPa * cm² = 0.1 kN, de modo que EA/L está en kN/m.
   double c=dx/l,s=dy/l,rigidez=b.moduloMpa()*b.areaCm2()*0.1/l;double[] v={-c,-s,c,s};int[] ids={2*i,2*i+1,2*j,2*j+1};
   if(!Double.isFinite(rigidez))throw new IllegalArgumentException("Propiedades de barra fuera de rango.");
   for(int p=0;p<4;p++)for(int q=0;q<4;q++)k[ids[p]][ids[q]]+=rigidez*v[p]*v[q];
  }
  List<Integer> libres=new ArrayList<>();for(int i=0;i<gdl;i++)if(!fijo[i])libres.add(i);
  if(libres.isEmpty())throw new IllegalArgumentException("El modelo no tiene grados de libertad libres.");
  double[][] reducido=new double[libres.size()][libres.size()];double[] cargas=new double[libres.size()];
  for(int i=0;i<libres.size();i++){cargas[i]=f[libres.get(i)];for(int j=0;j<libres.size();j++)reducido[i][j]=k[libres.get(i)][libres.get(j)];}
  double[] solucion=resolverSistema(reducido,cargas),u=new double[gdl];for(int i=0;i<libres.size();i++)u[libres.get(i)]=solucion[i];
  List<NodoResultado> rn=new ArrayList<>();
  for(int i=0;i<n;i++){double[] reaccion={-f[2*i],-f[2*i+1]};for(int j=0;j<gdl;j++){reaccion[0]+=k[2*i][j]*u[j];reaccion[1]+=k[2*i+1][j]*u[j];}
   rn.add(new NodoResultado(nudos.get(i).nombre(),u[2*i]*1000,u[2*i+1]*1000,fijo[2*i]?reaccion[0]:0,fijo[2*i+1]?reaccion[1]:0));}
  List<BarraResultado> rb=new ArrayList<>();
  for(Barra b:barras){int i=indices.get(b.inicio()),j=indices.get(b.fin());Nudo a=nudos.get(i),z=nudos.get(j);double dx=z.x()-a.x(),dy=z.y()-a.y(),l=Math.hypot(dx,dy),c=dx/l,s=dy/l;
   double deformacion=((u[2*j]-u[2*i])*c+(u[2*j+1]-u[2*i+1])*s)/l;
   double tension=b.moduloMpa()*deformacion,axial=tension*b.areaCm2()*0.1;if(!finito(tension,axial))throw new IllegalArgumentException("No se obtuvo un resultado finito.");rb.add(new BarraResultado(b.nombre(),l,axial,tension));}
  return new Resultado(List.copyOf(rn),List.copyOf(rb));
 }
 private static double[] resolverSistema(double[][] a,double[] b){
  int n=b.length;double max=0;for(double[] fila:a)for(double x:fila){if(!Double.isFinite(x))throw new IllegalArgumentException("La matriz contiene valores inválidos.");max=Math.max(max,Math.abs(x));}
  if(max==0)throw new IllegalArgumentException("Modelo inestable: revise apoyos y conectividad.");
  for(int p=0;p<n;p++){int mejor=p;for(int i=p+1;i<n;i++)if(Math.abs(a[i][p])>Math.abs(a[mejor][p]))mejor=i;
   if(Math.abs(a[mejor][p])<max*1e-12)throw new IllegalArgumentException("Modelo inestable o mal condicionado: revise apoyos, conectividad y propiedades.");
   double[] tmp=a[p];a[p]=a[mejor];a[mejor]=tmp;double t=b[p];b[p]=b[mejor];b[mejor]=t;
   for(int i=p+1;i<n;i++){double factor=a[i][p]/a[p][p];for(int j=p;j<n;j++)a[i][j]-=factor*a[p][j];b[i]-=factor*b[p];}}
  double[] x=new double[n];for(int i=n-1;i>=0;i--){double valor=b[i];for(int j=i+1;j<n;j++)valor-=a[i][j]*x[j];x[i]=valor/a[i][i];if(!Double.isFinite(x[i]))throw new IllegalArgumentException("No se obtuvo un resultado finito.");}return x;
 }
 private static boolean finito(double... valores){for(double v:valores)if(!Double.isFinite(v))return false;return true;}
}
