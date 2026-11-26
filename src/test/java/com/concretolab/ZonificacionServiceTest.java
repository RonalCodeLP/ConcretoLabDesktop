package com.concretolab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.concretolab.service.ZonificacionService;
import com.concretolab.service.ZonificacionService.Ubicacion;

class ZonificacionServiceTest {
    private final ZonificacionService servicio = new ZonificacionService();

    @Test
    void conteosTotalesCoincidenConElAnexo() {
        assertThat(servicio.fernandoHuancaDepartamentos()).hasSize(25);
        int provincias = servicio.fernandoHuancaDepartamentos().stream().mapToInt(d -> servicio.fernandoHuancaProvincias(d).size()).sum();
        assertThat(provincias).isEqualTo(196);
        assertThat(servicio.fernandoHuancaTotalDistritos()).isEqualTo(1892);
    }

    @Test
    void conteosPorDepartamento() {
        assertThat(contarDistritos("AMAZONAS")).isEqualTo(84);
        assertThat(contarDistritos("ÁNCASH")).isEqualTo(166);
        assertThat(contarDistritos("AREQUIPA")).isEqualTo(109);
        assertThat(contarDistritos("CALLAO")).isEqualTo(7);
        assertThat(contarDistritos("LIMA")).isEqualTo(171);
        assertThat(contarDistritos("MADRE DE DIOS")).isEqualTo(11);
        assertThat(contarDistritos("TUMBES")).isEqualTo(13);
        assertThat(servicio.fernandoHuancaProvincias("LIMA")).hasSize(10);
        assertThat(servicio.fernandoHuancaProvincias("CALLAO")).containsExactly("CALLAO");
    }

    @Test
    void fuenteIndicaLaNorma() {
        assertThat(servicio.fernandoHuancaFuente()).isEqualTo("E.030 2026 (R.M. 183-2026-VIVIENDA), Anexo II");
    }

    @Test
    void zonasVerificadasContraElPdf() {
        assertZona("LIMA", "LIMA", "MIRAFLORES", 4);
        assertZona("CALLAO", "CALLAO", "CALLAO", 4);
        assertZona("CALLAO", "CALLAO", "VENTANILLA", 4);
        assertZona("AREQUIPA", "AREQUIPA", "AREQUIPA", 3);
        assertZona("AREQUIPA", "AREQUIPA", "VITOR", 4);
        assertZona("AREQUIPA", "ISLAY", "MOLLENDO", 4);
        assertZona("CUSCO", "CUSCO", "CUSCO", 2);
        assertZona("CUSCO", "LA CONVENCIÓN", "ECHARATE", 2);
        assertZona("CUSCO", "ESPINAR", "ESPINAR", 3);
        assertZona("CUSCO", "CHUMBIVILCAS", "COLQUEMARCA", 2);
        assertZona("CUSCO", "CHUMBIVILCAS", "LLUSCO", 3);
        assertZona("PIURA", "PIURA", "PIURA", 4);
        assertZona("PIURA", "HUANCABAMBA", "HUANCABAMBA", 3);
        assertZona("LORETO", "MAYNAS", "IQUITOS", 1);
        assertZona("LORETO", "REQUENA", "SAQUENA", 1);
        assertZona("LORETO", "LORETO", "NAUTA", 2);
        assertZona("LORETO", "ALTO AMAZONAS", "YURIMAGUAS", 3);
        assertZona("UCAYALI", "CORONEL PORTILLO", "CALLERIA", 2);
        assertZona("UCAYALI", "PURÚS", "PURÚS", 1);
        assertZona("MADRE DE DIOS", "TAMBOPATA", "TAMBOPATA", 1);
        assertZona("MADRE DE DIOS", "MANU", "MANU", 2);
        assertZona("MOQUEGUA", "ILO", "ILO", 4);
        assertZona("PUNO", "PUNO", "PUNO", 3);
        assertZona("PUNO", "SANDIA", "SANDIA", 2);
        assertZona("PUNO", "SANDIA", "ALTO INAMBARI", 1);
        assertZona("TACNA", "TACNA", "TACNA", 4);
        assertZona("TUMBES", "TUMBES", "TUMBES", 4);
        assertZona("ICA", "PISCO", "PISCO", 4);
        assertZona("ICA", "CHINCHA", "SAN PEDRO DE HUACARPANA", 3);
        assertZona("ÁNCASH", "SANTA", "CHIMBOTE", 4);
        assertZona("ÁNCASH", "HUARAZ", "HUARAZ", 3);
        assertZona("CAJAMARCA", "CAJAMARCA", "CAJAMARCA", 3);
        assertZona("CAJAMARCA", "CAJAMARCA", "ENCAÑADA", 2);
        assertZona("SAN MARTÍN", "MOYOBAMBA", "MOYOBAMBA", 3);
        assertZona("JUNÍN", "HUANCAYO", "HUANCAYO", 3);
        assertZona("JUNÍN", "HUANCAYO", "PARIAHUANCA", 2);
        assertZona("AYACUCHO", "HUAMANGA", "AYACUCHO", 2);
        assertZona("AYACUCHO", "HUAMANGA", "CARMEN ALTO", 3);
        assertZona("AMAZONAS", "CHACHAPOYAS", "ASUNCIÓN", 2);
    }

    @Test
    void consultaIgnoraTildesMayusculasYEspacios() {
        assertThat(servicio.fernandoHuancaZona("lima", "lima", "miraflores")).hasValue(4);
        assertThat(servicio.fernandoHuancaZona("ancash", "santa", "chimbote")).hasValue(4);
        assertThat(servicio.fernandoHuancaZona("San Martin", "Moyobamba", "  Moyobamba ")).hasValue(3);
        assertThat(servicio.fernandoHuancaZona("Lima", "Cañete", "San Vicente de Cañete")).hasValue(4);
        assertThat(servicio.fernandoHuancaZona("lima", "canete", "san vicente de canete")).hasValue(4);
        assertThat(servicio.fernandoHuancaProvincias("junin")).contains("HUANCAYO");
        assertThat(servicio.fernandoHuancaDistritos("cusco", "cusco")).contains("SAN SEBASTIÁN", "WANCHAQ");
    }

    @Test
    void distritoDeOtraProvinciaConElMismoNombreTieneSuPropiaZona() {
        assertZona("LIMA", "LIMA", "MIRAFLORES", 4);
        assertZona("AREQUIPA", "AREQUIPA", "MIRAFLORES", 3);
        assertZona("LIMA", "YAUYOS", "MIRAFLORES", 3);
    }

    @Test
    void entradasInexistentesDevuelvenVacio() {
        assertThat(servicio.fernandoHuancaProvincias("ATLANTIDA")).isEmpty();
        assertThat(servicio.fernandoHuancaProvincias(null)).isEmpty();
        assertThat(servicio.fernandoHuancaDistritos("LIMA", "NO EXISTE")).isEmpty();
        assertThat(servicio.fernandoHuancaDistritos("ATLANTIDA", "LIMA")).isEmpty();
        assertThat(servicio.fernandoHuancaZona("ATLANTIDA", "LIMA", "MIRAFLORES")).isEqualTo(OptionalInt.empty());
        assertThat(servicio.fernandoHuancaZona("LIMA", "NO EXISTE", "MIRAFLORES")).isEqualTo(OptionalInt.empty());
        assertThat(servicio.fernandoHuancaZona("LIMA", "LIMA", "NO EXISTE")).isEqualTo(OptionalInt.empty());
        assertThat(servicio.fernandoHuancaZona("LIMA", "LIMA", null)).isEqualTo(OptionalInt.empty());
        assertThat(servicio.fernandoHuancaZona("LIMA", "LIMA", "CHIMBOTE")).isEqualTo(OptionalInt.empty());
    }

    @Test
    void buscarSinTildesEncuentraCanete() {
        List<Ubicacion> r = servicio.fernandoHuancaBuscar("canete", 20);
        assertThat(r).isNotEmpty();
        assertThat(r).anyMatch(u -> u.provincia().equals("CAÑETE"));
        assertThat(r).anyMatch(u -> u.distrito().equals("SAN VICENTE DE CAÑETE"));
    }

    @Test
    void buscarOrdenaPrimeroLosQueEmpiezanPorElTexto() {
        List<Ubicacion> r = servicio.fernandoHuancaBuscar("mira", 50);
        assertThat(r).isNotEmpty();
        int ultimoQueEmpieza = -1;
        int primeroQueSoloContiene = Integer.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            String d = r.get(i).distrito();
            if (d.startsWith("MIRA")) {
                ultimoQueEmpieza = i;
            } else if (d.contains("MIRA") && primeroQueSoloContiene == Integer.MAX_VALUE) {
                primeroQueSoloContiene = i;
            }
        }
        assertThat(ultimoQueEmpieza).isGreaterThanOrEqualTo(0);
        assertThat(ultimoQueEmpieza).isLessThan(primeroQueSoloContiene);
        assertThat(r.get(0).distrito()).startsWith("MIRA");
    }

    @Test
    void buscarRespetaElLimiteYTextosInvalidos() {
        assertThat(servicio.fernandoHuancaBuscar("san", 5)).hasSize(5);
        assertThat(servicio.fernandoHuancaBuscar("san", 0)).isEmpty();
        assertThat(servicio.fernandoHuancaBuscar("san", -1)).isEmpty();
        assertThat(servicio.fernandoHuancaBuscar("", 10)).isEmpty();
        assertThat(servicio.fernandoHuancaBuscar("   ", 10)).isEmpty();
        assertThat(servicio.fernandoHuancaBuscar(null, 10)).isEmpty();
        assertThat(servicio.fernandoHuancaBuscar("zzzzzzzz", 10)).isEmpty();
    }

    @Test
    void buscarDevuelveUbicacionesCompletas() {
        List<Ubicacion> r = servicio.fernandoHuancaBuscar("iquitos", 3);
        assertThat(r).first().isEqualTo(new Ubicacion("LORETO", "MAYNAS", "IQUITOS", 1));
    }

    @Test
    void noHayDistritosDuplicadosDentroDeLaMismaProvincia() {
        for (String dep : servicio.fernandoHuancaDepartamentos()) {
            for (String prov : servicio.fernandoHuancaProvincias(dep)) {
                List<String> distritos = servicio.fernandoHuancaDistritos(dep, prov);
                Set<String> unicos = new HashSet<>(distritos);
                assertThat(unicos).as("%s / %s", dep, prov).hasSameSizeAs(distritos);
            }
        }
    }

    @Test
    void todasLasZonasEstanEntre1Y4() {
        int total = 0;
        for (String dep : servicio.fernandoHuancaDepartamentos()) {
            for (String prov : servicio.fernandoHuancaProvincias(dep)) {
                for (String dist : servicio.fernandoHuancaDistritos(dep, prov)) {
                    OptionalInt z = servicio.fernandoHuancaZona(dep, prov, dist);
                    assertThat(z).as("%s / %s / %s", dep, prov, dist).isPresent();
                    assertThat(z.getAsInt()).isBetween(1, 4);
                    total++;
                }
            }
        }
        assertThat(total).isEqualTo(servicio.fernandoHuancaTotalDistritos());
    }

    private int contarDistritos(String departamento) {
        return servicio.fernandoHuancaProvincias(departamento).stream()
                .mapToInt(p -> servicio.fernandoHuancaDistritos(departamento, p).size())
                .sum();
    }

    private void assertZona(String dep, String prov, String dist, int esperada) {
        assertThat(servicio.fernandoHuancaZona(dep, prov, dist))
                .as("%s / %s / %s", dep, prov, dist)
                .hasValue(esperada);
    }
}
