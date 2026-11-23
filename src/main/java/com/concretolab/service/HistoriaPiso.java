package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.util.HashSet;
import java.util.List;

/** Traslación elástica de un piso, diafragma rígido y giros impedidos en ambos extremos. */
public final class HistoriaPiso {
    private HistoriaPiso() {}
    public record Piso(double altura, double rigidez, int columnas) {}
    public record Respuesta(double periodo, double[] desplazamiento, double[] velocidad, int pico) {}

    public static Piso yefriLermaPiso(List<Elemento> elementos, double moduloMPa, boolean ejeX) {
        yefriLermaPositivo(moduloMPa, "Módulo E");
        double altura = 0, cota = 0, rigidez = 0;
        var posiciones = new HashSet<List<Double>>();
        for (var e : elementos) {
            if (!"COLUMNA".equals(e.getTipo())) continue;
            double h = e.getAltura(), x = e.getLargo(), y = e.getAncho();
            yefriLermaPositivo(h, "Altura"); yefriLermaPositivo(x, "Sección X"); yefriLermaPositivo(y, "Sección Y");
            if (!Double.isFinite(e.getZ()) || !Double.isFinite(e.getX()) || !Double.isFinite(e.getY()))
                throw new IllegalArgumentException("Coordenadas inválidas.");
            if (altura != 0 && (Math.abs(h-altura)>1e-6 || Math.abs(cota-e.getZ())>1e-6))
                throw new IllegalArgumentException("Este análisis requiere columnas de un solo piso con iguales cotas de base y techo.");
            if (!posiciones.add(List.of(e.getX()+0.0,e.getY()+0.0)))
                throw new IllegalArgumentException("Hay columnas superpuestas.");
            altura = h; cota = e.getZ();
            double inercia = ejeX ? y*Math.pow(x,3)/12 : x*Math.pow(y,3)/12;
            rigidez += 12*moduloMPa*1000*inercia/Math.pow(h,3);
        }
        if (posiciones.isEmpty()) throw new IllegalArgumentException("Agrega columnas al proyecto.");
        yefriLermaPositivo(rigidez,"Rigidez");
        return new Piso(altura, rigidez, posiciones.size());
    }

    // Newmark de aceleración media. Entrada de base en m/s², respuesta relativa en m.
    public static Respuesta yefriLermaResolver(double masa, double k, double amortiguamiento, double dt, double[] ag) {
        yefriLermaPositivo(masa,"Masa"); yefriLermaPositivo(k,"Rigidez"); yefriLermaPositivo(dt,"Paso de tiempo");
        if (!Double.isFinite(amortiguamiento) || amortiguamiento<0 || amortiguamiento>=1)
            throw new IllegalArgumentException("Amortiguamiento entre 0 y menos de 100 %.");
        if (ag.length<2 || ag.length>200000) throw new IllegalArgumentException("Se requieren de 2 a 200000 muestras.");
        double w=Math.sqrt(k/masa), periodo=2*Math.PI/w;
        if (dt>periodo/20) throw new IllegalArgumentException("Paso demasiado grande para este modelo. Se requiere Δt ≤ "+periodo/20+" s.");
        double c=2*amortiguamiento*w, den=1+c*dt/2+w*w*dt*dt/4;
        double[] u=new double[ag.length], v=new double[ag.length];
        double a=-ag[0]; int pico=0;
        for (int i=1;i<ag.length;i++) {
            double up=u[i-1]+dt*v[i-1]+dt*dt*a/4;
            double vp=v[i-1]+dt*a/2;
            a=(-ag[i]-c*vp-w*w*up)/den;
            u[i]=up+dt*dt*a/4; v[i]=vp+dt*a/2;
            if (!Double.isFinite(u[i]) || !Double.isFinite(v[i])) throw new IllegalArgumentException("Registro o respuesta fuera de rango.");
            if (Math.abs(u[i])>Math.abs(u[pico])) pico=i;
        }
        return new Respuesta(periodo,u,v,pico);
    }
    private static void yefriLermaPositivo(double n,String nombre) {
        if (!Double.isFinite(n)||n<=0) throw new IllegalArgumentException(nombre+" debe ser positivo y finito.");
    }
}
