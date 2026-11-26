package com.concretolab.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;

@Service
public class ZonificacionService {
    private static final String FERNANDO_HUANCA_RECURSO = "/data/zonificacion-e030-2026.csv";
    private static final String FERNANDO_HUANCA_FUENTE = "E.030 2026 (R.M. 183-2026-VIVIENDA), Anexo II";

    public record Ubicacion(String departamento, String provincia, String distrito, int zona) {
    }

    private record Fila(Ubicacion ubicacion, String distritoNorm, String provinciaNorm) {
    }

    private record Datos(
            List<Fila> filas,
            List<String> departamentos,
            Map<String, String> departamentoPorClave,
            Map<String, Map<String, String>> provinciaPorClave,
            Map<String, Map<String, List<Ubicacion>>> arbol) {
    }

    private volatile Datos datos;

    public List<String> fernandoHuancaDepartamentos() {
        return fernandoHuancaCargar().departamentos();
    }

    public List<String> fernandoHuancaProvincias(String departamento) {
        Datos d = fernandoHuancaCargar();
        String dep = d.departamentoPorClave().get(clave(departamento));
        if (dep == null) {
            return List.of();
        }
        return List.copyOf(d.arbol().get(dep).keySet());
    }

    public List<String> fernandoHuancaDistritos(String departamento, String provincia) {
        List<Ubicacion> lista = fernandoHuancaUbicaciones(departamento, provincia);
        List<String> nombres = new ArrayList<>(lista.size());
        for (Ubicacion u : lista) {
            nombres.add(u.distrito());
        }
        return Collections.unmodifiableList(nombres);
    }

    public OptionalInt fernandoHuancaZona(String departamento, String provincia, String distrito) {
        String buscado = clave(distrito);
        if (buscado.isEmpty()) {
            return OptionalInt.empty();
        }
        for (Ubicacion u : fernandoHuancaUbicaciones(departamento, provincia)) {
            if (clave(u.distrito()).equals(buscado)) {
                return OptionalInt.of(u.zona());
            }
        }
        return OptionalInt.empty();
    }

    public List<Ubicacion> fernandoHuancaBuscar(String texto, int limite) {
        String q = clave(texto);
        if (q.isEmpty() || limite <= 0) {
            return List.of();
        }
        List<List<Ubicacion>> grupos = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        for (Fila f : fernandoHuancaCargar().filas()) {
            if (f.distritoNorm().startsWith(q)) {
                grupos.get(0).add(f.ubicacion());
            } else if (f.provinciaNorm().startsWith(q)) {
                grupos.get(1).add(f.ubicacion());
            } else if (f.distritoNorm().contains(q)) {
                grupos.get(2).add(f.ubicacion());
            } else if (f.provinciaNorm().contains(q)) {
                grupos.get(3).add(f.ubicacion());
            }
        }
        List<Ubicacion> resultado = new ArrayList<>();
        for (List<Ubicacion> grupo : grupos) {
            for (Ubicacion u : grupo) {
                if (resultado.size() >= limite) {
                    return resultado;
                }
                resultado.add(u);
            }
        }
        return resultado;
    }

    public int fernandoHuancaTotalDistritos() {
        return fernandoHuancaCargar().filas().size();
    }

    public String fernandoHuancaFuente() {
        return FERNANDO_HUANCA_FUENTE;
    }

    private List<Ubicacion> fernandoHuancaUbicaciones(String departamento, String provincia) {
        Datos d = fernandoHuancaCargar();
        String dep = d.departamentoPorClave().get(clave(departamento));
        if (dep == null) {
            return List.of();
        }
        String prov = d.provinciaPorClave().get(dep).get(clave(provincia));
        if (prov == null) {
            return List.of();
        }
        return d.arbol().get(dep).get(prov);
    }

    private Datos fernandoHuancaCargar() {
        Datos d = datos;
        if (d == null) {
            synchronized (this) {
                d = datos;
                if (d == null) {
                    d = fernandoHuancaLeer();
                    datos = d;
                }
            }
        }
        return d;
    }

    private Datos fernandoHuancaLeer() {
        List<Fila> filas = new ArrayList<>();
        Map<String, String> depPorClave = new LinkedHashMap<>();
        Map<String, Map<String, String>> provPorClave = new LinkedHashMap<>();
        Map<String, Map<String, List<Ubicacion>>> arbol = new LinkedHashMap<>();

        try (InputStream in = ZonificacionService.class.getResourceAsStream(FERNANDO_HUANCA_RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro el recurso " + FERNANDO_HUANCA_RECURSO);
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linea;
            int numero = 0;
            while ((linea = br.readLine()) != null) {
                numero++;
                if (numero == 1 && !linea.isEmpty() && linea.charAt(0) == '﻿') {
                    linea = linea.substring(1);
                }
                if (linea.isBlank() || numero == 1) {
                    continue;
                }
                String[] c = linea.split(";", -1);
                if (c.length != 4) {
                    throw new IllegalStateException(FERNANDO_HUANCA_RECURSO + ", linea " + numero + ": se esperaban 4 columnas");
                }
                int zona;
                try {
                    zona = Integer.parseInt(c[3].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalStateException(FERNANDO_HUANCA_RECURSO + ", linea " + numero + ": zona invalida", e);
                }
                if (zona < 1 || zona > 4) {
                    throw new IllegalStateException(FERNANDO_HUANCA_RECURSO + ", linea " + numero + ": zona fuera de rango");
                }
                String dep = c[0].trim();
                String prov = c[1].trim();
                Ubicacion u = new Ubicacion(dep, prov, c[2].trim(), zona);

                depPorClave.putIfAbsent(clave(dep), dep);
                provPorClave.computeIfAbsent(dep, k -> new LinkedHashMap<>()).putIfAbsent(clave(prov), prov);
                arbol.computeIfAbsent(dep, k -> new LinkedHashMap<>())
                        .computeIfAbsent(prov, k -> new ArrayList<>())
                        .add(u);
                filas.add(new Fila(u, clave(u.distrito()), clave(prov)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error leyendo " + FERNANDO_HUANCA_RECURSO, e);
        }

        Map<String, Map<String, List<Ubicacion>>> congelado = new LinkedHashMap<>();
        arbol.forEach((dep, provs) -> {
            Map<String, List<Ubicacion>> m = new LinkedHashMap<>();
            provs.forEach((prov, lista) -> m.put(prov, List.copyOf(lista)));
            congelado.put(dep, Collections.unmodifiableMap(m));
        });
        return new Datos(List.copyOf(filas), List.copyOf(new ArrayList<>(arbol.keySet())),
                Collections.unmodifiableMap(depPorClave), Collections.unmodifiableMap(provPorClave),
                Collections.unmodifiableMap(congelado));
    }

    private static String clave(String s) {
        if (s == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
