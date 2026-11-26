package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V3__Completar_columnas extends BaseJavaMigration {
    @Override
    public void migrate(Context contexto) throws Exception {
        Connection conexion = contexto.getConnection();
        String sql;
        try (var in = getClass().getResourceAsStream("/db/migration/V1__esquema_base.sql")) {
            if (in == null) throw new IllegalStateException("Falta V1__esquema_base.sql");
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String sentencia : sql.split("\\R")) {
            String linea = sentencia.strip();
            if (!linea.toUpperCase().startsWith("CREATE TABLE")) continue;
            int abre = linea.indexOf('('), cierra = linea.lastIndexOf(')');
            String tabla = linea.substring(0, abre).replaceAll("(?i)create table( if not exists)?", "").strip();
            Set<String> existentes = columnasDe(conexion, tabla);
            for (String definicion : definiciones(linea.substring(abre + 1, cierra))) {
                if (definicion.toLowerCase().startsWith("primary key")) continue;
                String columna = definicion.split("\\s+")[0];
                if (existentes.contains(columna.toLowerCase())) continue;
                try (Statement st = conexion.createStatement()) {
                    st.execute("ALTER TABLE " + tabla + " ADD COLUMN " + conDefecto(definicion));
                }
            }
        }
    }

    private static Set<String> columnasDe(Connection conexion, String tabla) throws Exception {
        Set<String> columnas = new HashSet<>();
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) columnas.add(rs.getString("name").toLowerCase());
        }
        return columnas;
    }

    private static List<String> definiciones(String cuerpo) {
        List<String> partes = new ArrayList<>();
        int nivel = 0, inicio = 0;
        for (int i = 0; i < cuerpo.length(); i++) {
            char c = cuerpo.charAt(i);
            if (c == '(') nivel++;
            else if (c == ')') nivel--;
            else if (c == ',' && nivel == 0) { partes.add(cuerpo.substring(inicio, i).strip()); inicio = i + 1; }
        }
        partes.add(cuerpo.substring(inicio).strip());
        return partes;
    }

    private static String conDefecto(String definicion) {
        if (!definicion.toLowerCase().contains("not null")) return definicion;
        String tipo = definicion.split("\\s+")[1].toLowerCase();
        boolean texto = tipo.startsWith("varchar") || tipo.startsWith("text") || tipo.startsWith("char");
        return definicion + (texto ? " default ''" : " default 0");
    }
}