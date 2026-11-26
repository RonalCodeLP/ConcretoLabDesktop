package com.concretolab.service;
import com.concretolab.model.*;
import com.concretolab.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class YefriLermaProyectoService {
    private final ProyectoRepository proyectos;
    private final ElementoRepository elementos;
    private final VinculoGeometricoRepository vinculos;
    private final CuadriculaProyectoRepository yefriLermaCuadriculas;
    private final ExpedienteRepository yefriLermaExpedientes;
    private final MezclaRepository mezclas;
    private final EnsayoRepository ensayos;
    private final BitacoraService bitacora;

    public YefriLermaProyectoService(ProyectoRepository proyectos, ElementoRepository elementos,
                                    VinculoGeometricoRepository vinculos, CuadriculaProyectoRepository cuadriculas,
                                    ExpedienteRepository expedientes,
                                    MezclaRepository mezclas, EnsayoRepository ensayos, BitacoraService bitacora) {
        this.proyectos = proyectos;
        this.elementos = elementos;
        this.vinculos = vinculos;
        this.yefriLermaCuadriculas = cuadriculas;
        this.yefriLermaExpedientes = expedientes;
        this.mezclas = mezclas;
        this.ensayos = ensayos;
        this.bitacora = bitacora;
    }
    @Transactional(readOnly=true) public List<Proyecto> proyectos(){return proyectos.findAll();}
    @Transactional public Proyecto yefriLermaCrearProyecto(String nombre,String ubicacion){
        if(nombre==null||nombre.isBlank()||nombre.trim().length()>120) throw new IllegalArgumentException("Ingrese un nombre de proyecto de hasta 120 caracteres.");
        if(ubicacion!=null&&ubicacion.length()>200) throw new IllegalArgumentException("La ubicación no puede superar 200 caracteres.");
        Proyecto p=new Proyecto();p.setNombre(nombre.trim());p.setUbicacion(ubicacion==null?"":ubicacion.trim());p=proyectos.save(p);bitacora.erickDelgadoRegistrar("PROYECTO_CREADO","Proyecto «"+p.getNombre()+"» (id "+p.getId()+")");return p;
    }
    @Transactional(readOnly=true) public List<Elemento> elementos(Long id){return elementos.findByProyectoIdOrderById(id);}
    @Transactional public Elemento yefriLermaGuardar(Elemento e){
        if(e.getProyectoId()==null||!proyectos.existsById(e.getProyectoId())) throw new IllegalArgumentException("Seleccione un proyecto existente.");
        if(!Set.of("ZAPATA","COLUMNA","VIGA","LOSA","MURO").contains(e.getTipo())) throw new IllegalArgumentException("Seleccione un tipo válido.");
        if(e.getNombre()==null||e.getNombre().isBlank()) throw new IllegalArgumentException("Ingrese un nombre.");
        if(!yefriLermaPositivo(e.getLargo())||!yefriLermaPositivo(e.getAncho())||!yefriLermaPositivo(e.getAltura())||!Double.isFinite(e.volumen())) throw new IllegalArgumentException("Las dimensiones deben ser números positivos y finitos.");
        if(!Double.isFinite(e.getX())||!Double.isFinite(e.getY())||!Double.isFinite(e.getZ())) throw new IllegalArgumentException("Las posiciones deben ser números finitos.");
        return elementos.save(e);
    }
    @Transactional public List<Elemento> yefriLermaGuardarVarios(List<Elemento> lista){
        List<Elemento> r=new java.util.ArrayList<>();
        for(Elemento e:lista) r.add(yefriLermaGuardar(e));
        return r;
    }
    @Transactional public void yefriLermaEliminar(Long id){vinculos.deleteByColumnaIdOrVigaId(id,id);elementos.deleteById(id);}
    @Transactional
    public void yefriLermaEliminarProyecto(Long id) {
        var proyecto = proyectos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto ya no existe."));
        var mezclasProyecto = mezclas.findByProyectoIdOrderByIdDesc(id);
        for (var mezcla : mezclasProyecto) {
            ensayos.deleteAllInBatch(ensayos.findByMezclaIdOrderByIdDesc(mezcla.getId()));
        }
        mezclas.deleteAllInBatch(mezclasProyecto);
        for (var elemento : elementos.findByProyectoIdOrderById(id)) {
            yefriLermaEliminar(elemento.getId());
        }
        yefriLermaCuadriculas.deleteById(id);
        yefriLermaExpedientes.deleteById(id);
        proyectos.delete(proyecto);
        bitacora.erickDelgadoRegistrar("PROYECTO_ELIMINADO", "Proyecto «" + proyecto.getNombre() + "» (id " + id + ") con sus elementos, mezclas y ensayos");
    }
    @Transactional public Proyecto yefriLermaGuardarPlano(Long id, String nombre, List<Elemento> candidatos) {
        Proyecto proyecto = id == null ? yefriLermaCrearProyecto(nombre, "") : proyectos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto ya no existe."));
        if (!elementos(proyecto.getId()).isEmpty())
            throw new IllegalArgumentException("Seleccione un proyecto vacío o cree uno para el DXF.");
        for (Elemento candidato : candidatos) {
            candidato.setProyectoId(proyecto.getId());
            yefriLermaGuardar(candidato);
        }
        return proyecto;
    }
    public record Plantilla(double separacionX,double separacionY,double largoZapata,double anchoZapata,double alturaZapata,double ladoColumna,double alturaColumna,double anchoViga,double alturaViga){}
    @Transactional public void yefriLermaCrearPlantilla(Long proyectoId,Plantilla p){
        if(proyectoId==null||!proyectos.existsById(proyectoId))throw new IllegalArgumentException("Seleccione un proyecto existente.");
        if(p==null||!yefriLermaPositivo(p.separacionX())||!yefriLermaPositivo(p.separacionY())||!yefriLermaPositivo(p.largoZapata())||!yefriLermaPositivo(p.anchoZapata())||!yefriLermaPositivo(p.alturaZapata())||!yefriLermaPositivo(p.ladoColumna())||!yefriLermaPositivo(p.alturaColumna())||!yefriLermaPositivo(p.anchoViga())||!yefriLermaPositivo(p.alturaViga()))throw new IllegalArgumentException("Todas las dimensiones deben ser positivas.");
        if(p.separacionX()<=p.ladoColumna()||p.separacionY()<=p.ladoColumna()||p.alturaViga()>p.alturaColumna()||p.anchoViga()>p.ladoColumna())throw new IllegalArgumentException("Revise las separaciones, la altura y el ancho de la viga respecto de la columna.");
        double[] xs={-p.separacionX()/2,p.separacionX()/2},ys={-p.separacionY()/2,p.separacionY()/2};int n=1;
        for(double x:xs)for(double y:ys){
            yefriLermaAgregar(proyectoId,"ZAPATA","Z"+n,p.largoZapata(),p.anchoZapata(),p.alturaZapata(),x,y,p.alturaZapata()/2);
            yefriLermaAgregar(proyectoId,"COLUMNA","C"+n,p.ladoColumna(),p.ladoColumna(),p.alturaColumna(),x,y,p.alturaZapata()+p.alturaColumna()/2);n++;
        }
        double z=p.alturaZapata()+p.alturaColumna()-p.alturaViga()/2;
        for(int i=0;i<2;i++)yefriLermaAgregar(proyectoId,"VIGA","VX"+(i+1),p.separacionX()-p.ladoColumna(),p.anchoViga(),p.alturaViga(),0,ys[i],z);
        for(int i=0;i<2;i++)yefriLermaAgregar(proyectoId,"VIGA","VY"+(i+1),p.anchoViga(),p.separacionY()-p.ladoColumna(),p.alturaViga(),xs[i],0,z);
    }
    private void yefriLermaAgregar(Long proyectoId,String tipo,String nombre,double largo,double ancho,double altura,double x,double y,double z){
        Elemento e=new Elemento();e.setProyectoId(proyectoId);e.setTipo(tipo);e.setNombre(nombre);e.setLargo(largo);e.setAncho(ancho);e.setAltura(altura);e.setX(x);e.setY(y);e.setZ(z);yefriLermaGuardar(e);
    }
    public static final List<String> YEFRI_LERMA_ESCENARIOS=List.of("LAB · 4 pisos · pórtico regular","LAB · 3 pisos · planta baja flexible","LAB · 3 pisos · referencia regular");
    @Transactional public void yefriLermaPrepararEscenarios(){
        var existentes=proyectos.findAll().stream().map(Proyecto::getNombre).toList();
        for(int caso=0;caso<YEFRI_LERMA_ESCENARIOS.size();caso++){
            String nombre=YEFRI_LERMA_ESCENARIOS.get(caso);
            if(existentes.contains(nombre))continue;
            long id=yefriLermaCrearProyecto(nombre,"Laboratorio didáctico · base fija").getId();
            double z=0.65;
            for(int nivel=1;nivel<=(caso==0?4:3);nivel++){
                boolean flexible=caso==1 && nivel==1;
                double h=flexible?4.5:3.1, lado=flexible?0.30:0.45;
                for(int i=0;i<3;i++)for(int j=0;j<2;j++){
                    double x=(i-1)*5,y=(j-0.5)*4;
                    String marca=nivel+"-"+i+j;
                    if(nivel==1)yefriLermaAgregar(id,"ZAPATA","Z-"+i+j,2,2,0.65,x,y,0.325);
                    yefriLermaAgregar(id,"COLUMNA","C-"+marca,lado,lado,h,x,y,z+h/2);
                    if(i<2)yefriLermaAgregar(id,"VIGA","VX-"+marca,5-lado,0.3,0.5,x+2.5,y,z+h-0.25);
                    if(j==0)yefriLermaAgregar(id,"VIGA","VY-"+marca,0.3,4-lado,0.5,x,0,z+h-0.25);
                }
                z+=h;
            }
        }
    }
    private boolean yefriLermaPositivo(double n){return Double.isFinite(n)&&n>0;}
}
