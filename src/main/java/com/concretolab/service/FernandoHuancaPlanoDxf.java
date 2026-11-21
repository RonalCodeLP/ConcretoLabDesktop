package com.concretolab.service;

import com.concretolab.model.Elemento;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FernandoHuancaPlanoDxf {
    public record Punto(double x, double y, double z) {
        double distancia(Punto otro) { return Math.hypot(x - otro.x, y - otro.y); }
    }
    public record Entidad(String tipo, String capa, Punto inicio, Punto fin) {}
    private record Huella(Punto centro, double largo, double ancho) {}
    public record Plano(List<Entidad> entidades, String unidad, List<Elemento> modelo3d) {}
    public record Datos(String columnas, String vigas, String zapatas, double escala,
                        int pisos, double altoPiso, double ladoColumna, double anchoViga,
                        double altoViga, double ladoZapata, double altoZapata) {}

    /** Metros por unidad según $INSUNITS del DXF; NaN si no hay unidad declarada (0) o es una que la app no convierte. */
    public static double metrosPorUnidad(String insunits) {
        if (insunits == null) return Double.NaN;
        return switch (insunits.strip()) {
            case "1" -> 0.0254;   // pulgadas
            case "2" -> 0.3048;   // pies
            case "4" -> 0.001;    // milímetros
            case "5" -> 0.01;     // centímetros
            case "6" -> 1.0;      // metros
            case "14" -> 0.1;     // decímetros
            default -> Double.NaN;
        };
    }

    public static String nombreUnidad(String insunits) {
        if (insunits == null) return "sin unidad declarada";
        return switch (insunits.strip()) {
            case "1" -> "pulgadas"; case "2" -> "pies"; case "4" -> "milímetros"; case "5" -> "centímetros";
            case "6" -> "metros"; case "14" -> "decímetros"; default -> "sin unidad declarada";
        };
    }

    /** Copia los elementos multiplicando todas sus dimensiones y coordenadas por {@code factor}. */
    public static List<Elemento> escalar(List<Elemento> elementos, double factor) {
        var copia = new ArrayList<Elemento>(elementos.size());
        for (var e : elementos) {
            var n = new Elemento();
            n.setTipo(e.getTipo()); n.setNombre(e.getNombre());
            n.setLargo(e.getLargo() * factor); n.setAncho(e.getAncho() * factor); n.setAltura(e.getAltura() * factor);
            n.setX(e.getX() * factor); n.setY(e.getY() * factor); n.setZ(e.getZ() * factor);
            copia.add(n);
        }
        return copia;
    }

    /**
     * Lee el DXF con la codificación que le corresponde: UTF-8 desde AutoCAD 2007 (AC1021) y Windows-1252 en versiones
     * anteriores. Así las capas con tilde o eñe («COLUMNAS_ÁREA») no se corrompen.
     */
    static String leerTexto(Path archivo) throws IOException {
        byte[] bytes = Files.readAllBytes(archivo);
        var m = java.util.regex.Pattern.compile("\\$ACADVER\\s+1\\s+(AC\\d+)").matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        String version = m.find() ? m.group(1) : "AC1009";
        var charset = version.compareTo("AC1021") >= 0 ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName("windows-1252");
        try {
            return charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException ex) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Busca el conversor DWG→DXF (dwgread.exe de LibreDWG) sin depender de la carpeta de compilación: la propiedad
     * {@code concretolab.tools}, la variable CONCRETOLAB_TOOLS, la carpeta «tools» junto a la aplicación o a la carpeta de
     * trabajo, {@code ~/.concretolab/tools}, {@code target/} (solo desarrollo) y el PATH.
     */
    public static Optional<Path> localizarDwgread() {
        var carpetas = new ArrayList<Path>();
        for (String ruta : new String[]{System.getProperty("concretolab.tools"), System.getenv("CONCRETOLAB_TOOLS")})
            if (ruta != null && !ruta.isBlank()) carpetas.add(Path.of(ruta));
        try {
            var codigo = Path.of(FernandoHuancaPlanoDxf.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path base = Files.isRegularFile(codigo) ? codigo.getParent() : codigo;
            if (base != null) { carpetas.add(base.resolve("tools")); carpetas.add(base.resolve("app").resolve("tools")); if (base.getParent() != null) carpetas.add(base.getParent().resolve("tools")); }
        } catch (Exception ignorada) { /* sin ubicación del código: se siguen probando las demás */ }
        carpetas.add(Path.of(System.getProperty("user.dir"), "tools"));
        carpetas.add(Path.of(System.getProperty("user.home"), ".concretolab", "tools"));
        carpetas.add(Path.of(System.getProperty("user.dir"), "target"));
        String entorno = System.getenv("PATH");
        if (entorno != null) for (String parte : entorno.split(java.io.File.pathSeparator)) if (!parte.isBlank()) carpetas.add(Path.of(parte));
        for (Path carpeta : carpetas) {
            try {
                for (String nombre : new String[]{"dwgread.exe", "dwgread"}) {
                    Path directo = carpeta.resolve(nombre);
                    if (Files.isRegularFile(directo)) return Optional.of(directo);
                }
                if (Files.isDirectory(carpeta)) try (var hijos = Files.list(carpeta)) {
                    for (Path hijo : (Iterable<Path>) hijos.filter(h -> h.getFileName().toString().toLowerCase().startsWith("libredwg"))::iterator) {
                        Path interno = hijo.resolve("dwgread.exe");
                        if (Files.isRegularFile(interno)) return Optional.of(interno);
                    }
                }
            } catch (IOException | java.nio.file.InvalidPathException ignorada) { /* carpeta ilegible: siguiente */ }
        }
        return Optional.empty();
    }

    public Plano fernandoHuancaLeerDwg(Path archivo, Path dwgread) throws IOException, InterruptedException {
        Path convertido = Files.createTempFile("concretolab-", ".dxf");
        try {
            Process proceso = new ProcessBuilder(dwgread.toAbsolutePath().toString(),
                    "-O", "DXF", "-o", convertido.toString(), archivo.toAbsolutePath().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (!proceso.waitFor(45, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IOException("La conversión DWG excedió 45 segundos.");
                if (proceso.exitValue() != 0 || Files.size(convertido) == 0)
                    throw new IOException("LibreDWG no pudo convertir el archivo. Exporta una copia en DXF ASCII desde AutoCAD.");
                return fernandoHuancaLeer(convertido);
            } finally {
                if (proceso.isAlive()) {
                    proceso.destroyForcibly();
                    proceso.waitFor();
                }
            }
        } finally {
            Files.deleteIfExists(convertido);
        }
    }

    public Plano fernandoHuancaLeer(Path archivo) throws IOException {
        if (Files.size(archivo) > 10_000_000) throw new IllegalArgumentException("El DXF supera 10 MB.");
        var entidades = new ArrayList<Entidad>();
        var caras = new LinkedHashMap<String,List<List<Punto>>>();
        String seccion = "", tipo = null, unidad = "";
        boolean esperaSeccion = false, esperaUnidad = false, eof = false;
        var grupos = new HashMap<Integer, String>();
        var verticesX = new ArrayList<String>();
        var verticesY = new ArrayList<String>();
        var curvas = new ArrayList<String>();
        // LibreDWG para Windows puede emitir CR CR LF. Conserva las líneas vacías de valores DXF.
        String contenido = leerTexto(archivo).replace("\r\r\n","\r\n");
        try (var lector = new java.io.BufferedReader(new java.io.StringReader(contenido))) {
            String codigo;
            while ((codigo = lector.readLine()) != null) {
                String valor = lector.readLine();
                if (valor == null) throw new IllegalArgumentException("DXF truncado.");
                int grupo = Integer.parseInt(codigo.strip());
                valor = valor.strip();
                if (grupo == 0) {
                    if (tipo != null) {
                        Entidad entidad;
                        try {
                            entidad = entidad(tipo, grupos, verticesX, verticesY, curvas);
                        } catch (IllegalArgumentException ex) {
                            // Inventaría la entidad, pero una capa estructural seleccionada la rechazará.
                            entidad = new Entidad(tipo + " inválida", grupos.getOrDefault(8,"0"), null, null);
                        }
                        entidades.add(entidad);
                        if ("3DFACE".equals(entidad.tipo) && entidad.capa.matches("CL3D_(COLUMNA|VIGA|ZAPATA|LOSA|MURO)_[1-9][0-9]*")) {
                            caras.computeIfAbsent(entidad.capa, clave -> new ArrayList<>())
                                    .add(List.of(punto(grupos,0),punto(grupos,1),punto(grupos,2),punto(grupos,3)));
                        }
                    }
                    grupos.clear();
                    verticesX.clear();
                    verticesY.clear();
                    curvas.clear();
                    esperaSeccion = "SECTION".equals(valor);
                    if ("ENDSEC".equals(valor)) seccion = "";
                    if ("EOF".equals(valor)) { eof = true; break; }
                    tipo = "ENTITIES".equals(seccion) ? valor : null;
                } else if (esperaSeccion && grupo == 2) {
                    seccion = valor;
                    esperaSeccion = false;
                } else if ("HEADER".equals(seccion)) {
                    if (grupo == 9) esperaUnidad = "$INSUNITS".equals(valor);
                    if (esperaUnidad && grupo == 70) { unidad = valor; esperaUnidad = false; }
                } else if (tipo != null) {
                    grupos.putIfAbsent(grupo, valor);
                    if ("LWPOLYLINE".equals(tipo)) {
                        switch (grupo) {
                            case 10 -> verticesX.add(valor);
                            case 20 -> verticesY.add(valor);
                            case 42 -> curvas.add(valor);
                            default -> { }
                        }
                    }
                }
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("DXF ASCII inválido. Exporta desde AutoCAD como DXF de texto.", ex);
        }
        if (!eof || entidades.isEmpty()) throw new IllegalArgumentException("El DXF no contiene entidades de dibujo completas.");
        var modelo = new ArrayList<Elemento>();
        for (var entrada : caras.entrySet()) {
            var vertices = entrada.getValue().stream().flatMap(List::stream).distinct().toList();
            double x = vertices.stream().mapToDouble(Punto::x).min().orElseThrow();
            double y = vertices.stream().mapToDouble(Punto::y).min().orElseThrow();
            double z = vertices.stream().mapToDouble(Punto::z).min().orElseThrow();
            double X = vertices.stream().mapToDouble(Punto::x).max().orElseThrow();
            double Y = vertices.stream().mapToDouble(Punto::y).max().orElseThrow();
            double Z = vertices.stream().mapToDouble(Punto::z).max().orElseThrow();
            var esperadas = carasPrisma(x,y,z,X,Y,Z).stream().map(Set::copyOf).toList();
            var recibidas = entrada.getValue().stream().map(Set::copyOf).toList();
            if (vertices.size()!=8 || recibidas.size()!=6 || !recibidas.containsAll(esperadas))
                throw new IllegalArgumentException("Prisma incompleto o deformado en "+entrada.getKey()+".");
            agregar(modelo,entrada.getKey().split("_")[1],x+(X-x)/2,y+(Y-y)/2,z+(Z-z)/2,X-x,Y-y,Z-z);
        }
        return new Plano(List.copyOf(entidades), unidad, List.copyOf(modelo));
    }

    public void fernandoHuancaExportar(Path destino, List<Elemento> elementos) throws IOException {
        if (elementos.isEmpty()) throw new IllegalArgumentException("El proyecto está vacío.");
        var dxf = new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1009\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        int indice=0;
        for(var e:elementos){
            double x=e.getX()-e.getLargo()/2, y=e.getY()-e.getAncho()/2, z=e.getZ()-e.getAltura()/2;
            String capa="CL3D_"+e.getTipo()+"_"+(++indice);
            for(var cara:carasPrisma(x,y,z,x+e.getLargo(),y+e.getAncho(),z+e.getAltura())){
                grupo(dxf,0,"3DFACE"); grupo(dxf,8,capa);
                grupo(dxf,62,switch(e.getTipo()){case "COLUMNA" -> 5; case "VIGA" -> 4; case "MURO" -> 30; default -> 8;});
                for(int i=0;i<4;i++){
                    var p=cara.get(i);
                    grupo(dxf,10+i,p.x); grupo(dxf,20+i,p.y); grupo(dxf,30+i,p.z);
                }
            }
        }
        dxf.append("0\nENDSEC\n0\nEOF\n");
        if(dxf.length()>10_000_000)throw new IllegalArgumentException("El modelo supera el límite de 10 MB del importador.");
        Files.writeString(destino,dxf,StandardCharsets.US_ASCII);
    }

    private static void grupo(StringBuilder dxf,int codigo,Object valor){
        dxf.append(codigo).append('\n').append(valor).append('\n');
    }
    private static List<List<Punto>> carasPrisma(double x,double y,double z,double X,double Y,double Z){
        var a=new Punto(x,y,z); var b=new Punto(X,y,z); var c=new Punto(X,Y,z); var d=new Punto(x,Y,z);
        var e=new Punto(x,y,Z); var f=new Punto(X,y,Z); var g=new Punto(X,Y,Z); var h=new Punto(x,Y,Z);
        return List.of(List.of(d,c,b,a),List.of(e,f,g,h),List.of(a,b,f,e),
                List.of(b,c,g,f),List.of(c,d,h,g),List.of(d,a,e,h));
    }

    private static Entidad entidad(String tipo, Map<Integer, String> grupos,
                                  List<String> xs, List<String> ys, List<String> curvas) {
        String capa = grupos.getOrDefault(8, "0");
        if ("1".equals(grupos.get(67)) || !"Model".equalsIgnoreCase(grupos.getOrDefault(410, "Model")))
            return new Entidad("PRESENTACIÓN", capa, null, null);
        return switch (tipo) {
            case "LINE" -> new Entidad(tipo, capa, punto(grupos, 0), punto(grupos, 1));
            case "POINT" -> new Entidad(tipo, capa, punto(grupos, 0), null);
            case "LWPOLYLINE" -> polilinea(capa, grupos, xs, ys, curvas);
            // Bloque insertado (columna dibujada como bloque): se usa su punto de inserción como centro.
            case "INSERT" -> new Entidad("BLOQUE", capa, punto(grupos, 0), null);
            case "CIRCLE" -> {
                double r = numero(grupos.get(40));
                if (r <= 0) throw new IllegalArgumentException("Círculo sin radio.");
                var c = punto(grupos, 0);
                yield new Entidad("CÍRCULO", capa, new Punto(c.x - r, c.y - r, c.z), new Punto(c.x + r, c.y + r, c.z));
            }
            default -> new Entidad(tipo, capa, null, null);
        };
    }

    /** Polilínea ligera: rectángulo (4 vértices, o 5 con el primero repetido) o viga (2 vértices abiertos, como LINE). */
    private static Entidad polilinea(String capa, Map<Integer, String> grupos,
                                    List<String> xs, List<String> ys, List<String> curvas) {
        var tx = new ArrayList<>(xs);
        var ty = new ArrayList<>(ys);
        boolean cerrada = (Integer.parseInt(grupos.getOrDefault(70, "0")) & 1) != 0;
        if (tx.size() == 5 && ty.size() == 5 && numero(tx.get(0)) == numero(tx.get(4)) && numero(ty.get(0)) == numero(ty.get(4))) {
            tx.remove(4);
            ty.remove(4);
            cerrada = true;
        }
        boolean recta = curvas.stream().mapToDouble(FernandoHuancaPlanoDxf::numero).allMatch(n -> n == 0);
        if (!cerrada && tx.size() == 2 && ty.size() == 2 && recta) {
            double z = numero(grupos.getOrDefault(38, "0"));
            return new Entidad("LINE", capa, new Punto(numero(tx.get(0)), numero(ty.get(0)), z),
                    new Punto(numero(tx.get(1)), numero(ty.get(1)), z));
        }
        return rectangulo(capa, grupos, tx, ty, curvas, cerrada);
    }

    private static Entidad rectangulo(String capa, Map<Integer, String> grupos,
                                     List<String> xs, List<String> ys, List<String> curvas, boolean cerrada) {
        var noCompatible = new Entidad("LWPOLYLINE", capa, null, null);
        if (xs.size()!=4 || ys.size()!=4 || !cerrada
                || curvas.stream().mapToDouble(FernandoHuancaPlanoDxf::numero).anyMatch(n->n!=0)
                || numero(grupos.getOrDefault(210,"0"))!=0 || numero(grupos.getOrDefault(220,"0"))!=0
                || numero(grupos.getOrDefault(230,"1"))!=1) return noCompatible;
        var puntos = new ArrayList<Punto>();
        double z=numero(grupos.getOrDefault(38,"0"));
        for(int i=0;i<4;i++) puntos.add(new Punto(numero(xs.get(i)),numero(ys.get(i)),z));
        if (new HashSet<>(puntos).size()!=4) return noCompatible;
        for(int i=0;i<4;i++){
            var a=puntos.get(i); var b=puntos.get((i+1)%4);
            if ((a.x==b.x)==(a.y==b.y)) return noCompatible;
        }
        double x=puntos.stream().mapToDouble(Punto::x).min().orElseThrow();
        double y=puntos.stream().mapToDouble(Punto::y).min().orElseThrow();
        double X=puntos.stream().mapToDouble(Punto::x).max().orElseThrow();
        double Y=puntos.stream().mapToDouble(Punto::y).max().orElseThrow();
        if(puntos.stream().anyMatch(p->(p.x!=x && p.x!=X)||(p.y!=y && p.y!=Y)))return noCompatible;
        return new Entidad("RECTÁNGULO",capa,new Punto(x,y,z),new Punto(X,Y,z));
    }

    private static Punto punto(Map<Integer, String> g, int desplazamiento) {
        return new Punto(numero(g.get(10 + desplazamiento)), numero(g.get(20 + desplazamiento)),
                numero(g.getOrDefault(30 + desplazamiento, "0")));
    }

    private static double numero(String valor) {
        if (valor == null) throw new IllegalArgumentException("Una entidad carece de coordenadas.");
        double n = Double.parseDouble(valor);
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Coordenada inválida.");
        return n;
    }

    public List<Elemento> fernandoHuancaConvertir(Plano plano, Datos d) {
        if (d.pisos < 1 || d.pisos > 12 || d.altoPiso <= d.altoViga ||
                Arrays.stream(new double[]{d.escala,d.altoPiso,d.anchoViga,d.altoViga,d.altoZapata})
                        .anyMatch(n -> !Double.isFinite(n) || n <= 0))
            throw new IllegalArgumentException("Usa dimensiones positivas en metros y entre 1 y 12 pisos.");
        var huellasColumnas = huellas(plano,d.columnas,d.escala,d.ladoColumna);
        var columnas = huellasColumnas.keySet().stream().toList();
        var zapatas = d.zapatas.isBlank() ? Map.<Punto,Huella>of() : huellas(plano,d.zapatas,d.escala,d.ladoZapata);
        if (columnas.size() < 4) throw new IllegalArgumentException("Se requieren al menos cuatro ejes de columna.");
        for (int i=0;i<columnas.size();i++) for(int j=0;j<i;j++)
            if(columnas.get(i).distancia(columnas.get(j)) <= 0.05)
                throw new IllegalArgumentException("Hay ejes de columna coincidentes o demasiado próximos.");
        var vigas = new ArrayList<List<Punto>>();
        var usados = new HashSet<Punto>();
        for (var e : capa(plano,d.vigas,"LINE")) {
            var a = columna(columnas,huellasColumnas,planta(e.inicio,d.escala));
            var b = columna(columnas,huellasColumnas,planta(e.fin,d.escala));
            if (a.equals(b)) throw new IllegalArgumentException("Una viga empieza y termina en la misma columna.");
            double dx = Math.abs(a.x-b.x), dy = Math.abs(a.y-b.y);
            if ((dx > 1e-6 && dy > 1e-6) || luz(a,b,huellasColumnas)<=d.anchoViga)
                throw new IllegalArgumentException("Las vigas deben ser paralelas a X o Y y su luz libre mayor que su ancho.");
            if (vigas.contains(List.of(a,b)) || vigas.contains(List.of(b,a)))
                throw new IllegalArgumentException("Hay vigas duplicadas.");
            vigas.add(List.of(a,b));
            usados.add(a);
            usados.add(b);
        }
        if (usados.size()!=columnas.size()) throw new IllegalArgumentException("Hay columnas sin vigas conectadas.");
        var conectados = new HashSet<Punto>();
        conectados.add(columnas.get(0));
        int anteriores;
        do {
            anteriores=conectados.size();
            for (var viga : vigas)
                if (conectados.contains(viga.get(0)) || conectados.contains(viga.get(1))) conectados.addAll(viga);
        } while (anteriores != conectados.size());
        if (conectados.size()!=columnas.size())
            throw new IllegalArgumentException("La planta contiene conjuntos de columnas desconectados entre sí.");
        var resultado = new ArrayList<Elemento>();
        for (var h : zapatas.values())
            agregar(resultado,"ZAPATA",h.centro.x,h.centro.y,-d.altoZapata/2,h.largo,h.ancho,d.altoZapata);
        for (int piso=1;piso<=d.pisos;piso++) {
            double techo=piso*d.altoPiso;
            for(var h:huellasColumnas.values())
                agregar(resultado,"COLUMNA",h.centro.x,h.centro.y,techo-d.altoPiso/2,h.largo,h.ancho,d.altoPiso);
            for(var par:vigas){
                var a=par.get(0); var b=par.get(1);
                boolean ejeX=Math.abs(a.x-b.x)>1e-6;
                double luz=luz(a,b,huellasColumnas);
                double ajuste=(anchoEnEje(huellasColumnas.get(a),ejeX)-anchoEnEje(huellasColumnas.get(b),ejeX))/4;
                double x=(a.x+b.x)/2+Math.signum(b.x-a.x)*ajuste;
                double y=(a.y+b.y)/2+Math.signum(b.y-a.y)*ajuste;
                agregar(resultado,"VIGA",x,y,techo-d.altoViga/2,
                        ejeX?luz:d.anchoViga,ejeX?d.anchoViga:luz,d.altoViga);
            }
        }
        return List.copyOf(resultado);
    }

    /** Capas opcionales de losas (rectángulos) y muros (líneas) con sus espesores en metros; una capa vacía no se importa. */
    public record Extra(String losas, String muros, double espesorLosa, double espesorMuro) {
        public static final Extra NINGUNA = new Extra("", "", Double.NaN, Double.NaN);
    }

    /**
     * Igual que {@link #fernandoHuancaConvertir(Plano, Datos)} y además agrega, en cada piso, las losas (rectángulos de la capa
     * de losas, con su cara superior al nivel del piso) y los muros (líneas paralelas a X o Y de la capa de muros, de piso a piso).
     */
    public List<Elemento> fernandoHuancaConvertir(Plano plano, Datos d, Extra extra) {
        var base = fernandoHuancaConvertir(plano, d);
        boolean hayLosas = extra != null && extra.losas() != null && !extra.losas().isBlank();
        boolean hayMuros = extra != null && extra.muros() != null && !extra.muros().isBlank();
        if (!hayLosas && !hayMuros) return base;
        var resultado = new ArrayList<>(base);
        if (hayLosas) {
            if (!Double.isFinite(extra.espesorLosa()) || extra.espesorLosa() <= 0 || extra.espesorLosa() > d.altoPiso)
                throw new IllegalArgumentException("Ingresa un espesor de losa positivo (menor que la altura de piso).");
            var contornos = new ArrayList<Huella>();
            for (var e : capa(plano, extra.losas(), "RECTÁNGULO")) {
                var a = planta(e.inicio, d.escala);
                var b = planta(e.fin, d.escala);
                if (b.x - a.x <= 0 || b.y - a.y <= 0) throw new IllegalArgumentException("Contorno de losa sin área en la capa " + extra.losas() + ".");
                contornos.add(new Huella(new Punto((a.x + b.x) / 2, (a.y + b.y) / 2, 0), b.x - a.x, b.y - a.y));
            }
            for (int piso = 1; piso <= d.pisos; piso++)
                for (var h : contornos)
                    agregar(resultado, "LOSA", h.centro.x, h.centro.y, piso * d.altoPiso - extra.espesorLosa() / 2, h.largo, h.ancho, extra.espesorLosa());
        }
        if (hayMuros) {
            if (!Double.isFinite(extra.espesorMuro()) || extra.espesorMuro() <= 0)
                throw new IllegalArgumentException("Ingresa un espesor de muro positivo.");
            var tramos = new ArrayList<Punto[]>();
            for (var e : capa(plano, extra.muros(), "LINE")) {
                var a = planta(e.inicio, d.escala);
                var b = planta(e.fin, d.escala);
                double dx = Math.abs(a.x - b.x), dy = Math.abs(a.y - b.y);
                if (dx > 1e-6 && dy > 1e-6)
                    throw new IllegalArgumentException("Los muros deben ser paralelos a X o Y (hay uno inclinado en la capa " + extra.muros() + ").");
                if (a.distancia(b) <= extra.espesorMuro()) throw new IllegalArgumentException("Hay un muro más corto que su espesor.");
                tramos.add(new Punto[] {a, b});
            }
            for (int piso = 1; piso <= d.pisos; piso++)
                for (var t : tramos) {
                    boolean ejeX = Math.abs(t[0].x - t[1].x) > 1e-6;
                    double largo = t[0].distancia(t[1]);
                    agregar(resultado, "MURO", (t[0].x + t[1].x) / 2, (t[0].y + t[1].y) / 2, piso * d.altoPiso - d.altoPiso / 2,
                            ejeX ? largo : extra.espesorMuro(), ejeX ? extra.espesorMuro() : largo, d.altoPiso);
                }
        }
        return List.copyOf(resultado);
    }

    private static double anchoEnEje(Huella h,boolean ejeX) { return ejeX?h.largo:h.ancho; }
    private static double luz(Punto a,Punto b,Map<Punto,Huella> huellas) {
        boolean ejeX=Math.abs(a.x-b.x)>1e-6;
        return a.distancia(b)-(anchoEnEje(huellas.get(a),ejeX)+anchoEnEje(huellas.get(b),ejeX))/2;
    }

    private static List<Entidad> capa(Plano plano,String capa,String... tipos) {
        var entidades=plano.entidades.stream().filter(e->e.capa.equals(capa)).toList();
        var admitidos=Set.of(tipos);
        if(entidades.isEmpty() || entidades.stream().anyMatch(e->!admitidos.contains(e.tipo)))
            throw new IllegalArgumentException("La capa "+capa+" debe contener exclusivamente "+String.join(" o ",tipos)+" del espacio modelo.");
        if(entidades.size()>200) throw new IllegalArgumentException("Máximo 200 entidades por capa estructural.");
        return entidades;
    }
    private static Map<Punto,Huella> huellas(Plano plano,String capa,double escala,double lado) {
        var huellas=new LinkedHashMap<Punto,Huella>();
        for(var e:capa(plano,capa,"POINT","BLOQUE","RECTÁNGULO","CÍRCULO")){
            var a=planta(e.inicio,escala);
            Huella h;
            if(e.fin==null){
                if(!Double.isFinite(lado)||lado<=0)throw new IllegalArgumentException("Ingresa el lado para los puntos de la capa "+capa+".");
                h=new Huella(a,lado,lado);
            } else {
                var b=planta(e.fin,escala);
                h=new Huella(new Punto((a.x+b.x)/2,(a.y+b.y)/2,0),b.x-a.x,b.y-a.y);
                if(h.largo<=0 || h.ancho<=0)throw new IllegalArgumentException("Contorno sin área en la capa "+capa+".");
            }
            if(huellas.putIfAbsent(h.centro,h)!=null)
                throw new IllegalArgumentException("Hay centros duplicados en la capa "+capa+".");
        }
        return huellas;
    }
    private static Punto planta(Punto p,double escala) {
        double x=p.x*escala,y=p.y*escala;
        if(Math.abs(p.z*escala)>1e-6 || !Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Usa una planta XY en Z=0 y coordenadas finitas.");
        return new Punto(x,y,0);
    }
    /**
     * Columna a la que se ancla el extremo de una viga: la que contiene el punto en su huella (con 5 cm de holgura) o,
     * si no, la de centro más cercano a menos de 30 cm cuando es claramente la más próxima.
     */
    private static Punto columna(List<Punto> columnas,Map<Punto,Huella> huellas,Punto p) {
        var dentro=columnas.stream().filter(c->{
            var h=huellas.get(c);
            return Math.abs(p.x-c.x)<=h.largo/2+0.05 && Math.abs(p.y-c.y)<=h.ancho/2+0.05;
        }).toList();
        if(dentro.size()==1) return dentro.get(0);
        var ordenadas=(dentro.isEmpty()?columnas:dentro).stream().sorted(Comparator.comparingDouble(c->c.distancia(p))).toList();
        if(!ordenadas.isEmpty() && ordenadas.get(0).distancia(p)<=0.30
                && (ordenadas.size()==1 || ordenadas.get(1).distancia(p)>=1.5*ordenadas.get(0).distancia(p)))
            return ordenadas.get(0);
        throw new IllegalArgumentException("Cada extremo de viga debe caer sobre una columna (o a menos de 30 cm de su centro).");
    }
    private static void agregar(List<Elemento> elementos,String tipo,double x,double y,double z,double largo,double ancho,double alto) {
        var e=new Elemento();
        e.setTipo(tipo); e.setNombre("DXF-"+tipo+"-"+(elementos.size()+1));
        e.setX(x); e.setY(y); e.setZ(z); e.setLargo(largo); e.setAncho(ancho); e.setAltura(alto);
        elementos.add(e);
    }
}
