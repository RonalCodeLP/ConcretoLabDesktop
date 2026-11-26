"""
Genera los capítulos del documento «Código fuente de ConcretoLab Desktop» (doc/latex/codigo-fuente.tex).

Recorre el código de la aplicación (src/main, src/test, pom.xml y los scripts de la raíz), y escribe en
doc/latex/contenido/codigo/ un capítulo por grupo con cada archivo completo en un entorno lstlisting, precedido de su
ruta, su número de líneas y una descripción. Las descripciones se toman de la documentación técnica (tablas de
03-estructura-del-codigo.tex y 09-pruebas.tex) o, si no están, del primer párrafo del Javadoc de la clase.

Los caracteres que no son ASCII (tildes, letras griegas, símbolos) se escriben con su equivalente LaTeX dentro de
(*@ ... @*), que listings interpreta como LaTeX; así el PDF muestra el código tal cual con pdflatex.

Uso (desde la raíz del repositorio):  python doc/latex/herramientas/generar-codigo-fuente.py
compilar.ps1 lo ejecuta automáticamente antes de compilar el documento de código fuente.
"""
import pathlib
import re
import subprocess

RAIZ = pathlib.Path(__file__).resolve().parents[3]
LATEX = RAIZ / "doc" / "latex"
SALIDA = LATEX / "contenido" / "codigo"

EXCLUIDAS = (".png", ".ico", ".bmp", ".pdf", ".jpg", ".jpeg", ".csv", ".dxf", ".db", ".gif", ".class", ".jar")

# Equivalentes LaTeX de los caracteres no ASCII del código
EQUIVALENTES = {
    "á": "\\'a", "é": "\\'e", "í": "\\'{\\i}", "ó": "\\'o", "ú": "\\'u", "Á": "\\'A", "É": "\\'E", "Í": "\\'I",
    "Ó": "\\'O", "Ú": "\\'U", "ñ": "\\~n", "Ñ": "\\~N", "ü": "\\\"u", "Ü": "\\\"U", "û": "\\^u", "¿": "?`",
    "¡": "!`", "«": "\\guillemotleft{}", "»": "\\guillemotright{}", "ª": "\\textordfeminine{}",
    "º": "\\textordmasculine{}", "°": "\\textdegree{}", "±": "$\\pm$", "²": "$^2$", "³": "$^3$", "⁴": "$^4$",
    "·": "$\\cdot$", "½": "\\textonehalf{}", "×": "$\\times$", "–": "--", "—": "---", "’": "'", "‘": "`",
    "“": "``", "”": "''", "•": "\\textbullet{}", "●": "\\textbullet{}", "…": "...", "→": "$\\rightarrow$",
    "←": "$\\leftarrow$", "−": "-", "∓": "$\\mp$", "√": "$\\surd$", "∝": "$\\propto$", "∫": "$\\int$",
    "≈": "$\\approx$", "≤": "$\\le$", "≥": "$\\ge$", "≠": "$\\neq$", "Δ": "$\\Delta$", "Σ": "$\\Sigma$",
    "α": "$\\alpha$", "β": "$\\beta$", "γ": "$\\gamma$", "ζ": "$\\zeta$", "η": "$\\eta$", "θ": "$\\theta$",
    "ξ": "$\\xi$", "π": "$\\pi$", "φ": "$\\phi$", "ω": "$\\omega$", "ᵀ": "$^T$", "₀": "$_0$", "₁": "$_1$",
    "₂": "$_2$", "₃": "$_3$", "₄": "$_4$", "€": "EUR", "\u00a0": " ",
    "\u0307": "", "\ufeff": "",
}

# Grupos del documento: (archivo .tex, título, introducción, filtro de rutas)
GRUPOS = [
    ("01-construccion", "Construcción, configuración y scripts",
     "Archivo de construcción de Maven, configuración de Spring Boot y scripts para ejecutar, empaquetar y probar la "
     "aplicación en Windows.",
     lambda r: r in ("pom.xml", "INICIAR_CONCRETOLAB.bat", "CREAR_DISTRIBUCION_WINDOWS.ps1", "PROBAR_INSTALADOR_WINDOWS.ps1")
     or r.startswith("src/main/resources/") and r.endswith((".properties", ".xml")) and "/view/" not in r),
    ("02-arranque", "Arranque de la aplicación",
     "Clases del paquete raíz \\ruta{com.concretolab}: punto de entrada, aplicación de Spring Boot y ciclo de vida de "
     "JavaFX.",
     lambda r: re.fullmatch(r"src/main/java/com/concretolab/[^/]+\.java", r) is not None),
    ("03-modelo", "Entidades (paquete model)",
     "Entidades JPA que se guardan en la base SQLite.",
     lambda r: r.startswith("src/main/java/com/concretolab/model/")),
    ("04-repositorios", "Repositorios (paquete repository)",
     "Interfaces de Spring Data JPA para consultar y guardar las entidades.",
     lambda r: r.startswith("src/main/java/com/concretolab/repository/")),
    ("05-servicios", "Servicios y cálculos (paquete service)",
     "Lógica de negocio y módulos de cálculo: modelo y metrado, análisis sísmico, concreto, datos e intercambio.",
     lambda r: r.startswith("src/main/java/com/concretolab/service/")),
    ("06-controladores", "Controladores de pantalla (paquete controller)",
     "Controladores de las vistas FXML, administrados por Spring.",
     lambda r: r.startswith("src/main/java/com/concretolab/controller/")),
    ("07-interfaz", "Componentes de interfaz (paquete ui)",
     "Visor 3D, diálogos, notificaciones, ayuda integrada y utilidades de la interfaz.",
     lambda r: r.startswith("src/main/java/com/concretolab/ui/")),
    ("08-otros", "Datos de demostración, excepciones y migración Java",
     "Seeders de demostración (apagados por defecto), excepción de negocio y la migración de esquema escrita en Java.",
     lambda r: r.startswith(("src/main/java/com/concretolab/seeder/", "src/main/java/com/concretolab/exception/",
                             "src/main/java/db/"))),
    ("09-vistas", "Vistas FXML",
     "Definición de las pantallas en FXML; cada una tiene su controlador en el paquete \\ruta{controller}.",
     lambda r: r.startswith("src/main/resources/view/")),
    ("10-estilos", "Hoja de estilos",
     "Estilos CSS de JavaFX que se aplican sobre el tema AtlantaFX Primer.",
     lambda r: r.startswith("src/main/resources/css/")),
    ("11-migraciones", "Migraciones de la base de datos",
     "Scripts SQL de Flyway que crean y actualizan el esquema de la base SQLite.",
     lambda r: r.startswith("src/main/resources/db/")),
    ("12-instalador", "Recursos del instalador",
     "Archivos de WiX que personalizan el instalador generado por jpackage.",
     lambda r: r.startswith("src/main/jpackage/")),
    ("13-pruebas", "Pruebas automáticas",
     "Pruebas de JUnit 5: unitarias, de integración con Spring y de interfaz gráfica.",
     lambda r: r.startswith("src/test/")),
]

LENGUAJES = {".java": "Java", ".xml": "XML", ".fxml": "XML", ".wxs": "XML", ".wxl": "XML", ".sql": "SQL",
             ".properties": "", ".css": "", ".bat": "", ".ps1": ""}


def archivos():
    salida = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "src", "pom.xml",
                             "INICIAR_CONCRETOLAB.bat", "CREAR_DISTRIBUCION_WINDOWS.ps1", "PROBAR_INSTALADOR_WINDOWS.ps1"],
                            cwd=RAIZ, capture_output=True, text=True, encoding="utf-8", check=True).stdout
    rutas = [r.strip() for r in salida.splitlines() if r.strip()]
    return sorted({r for r in rutas if not r.lower().endswith(EXCLUIDAS) and (RAIZ / r).is_file()})


def descripciones():
    """Descripciones de clases tomadas de las tablas de la documentación técnica."""
    d = {}
    for nombre in ("03-estructura-del-codigo.tex", "09-pruebas.tex"):
        texto = (LATEX / "contenido" / "tecnica" / nombre).read_text(encoding="utf-8")
        for fila in re.split(r"\\\\", texto):
            m = re.match(r"\s*(?:\\ruta\{[a-z]+/\}\s*)?\\ruta\{([A-Za-z0-9_]+)\}\s*&\s*(?:[UIG]\s*&\s*)?(.+?)(?:&\s*\d+\s*)?$",
                         fila.strip().split("\\hline")[-1].strip(), re.S)
            if m and m.group(1) not in d:
                d[m.group(1)] = " ".join(m.group(2).split()).rstrip(". ") + "."
    return d


def escapar_texto(s):
    s = s.replace("\\", "\\textbackslash{}")
    for a, b in (("{", "\\{"), ("}", "\\}"), ("_", "\\_"), ("%", "\\%"), ("#", "\\#"), ("&", "\\&"), ("$", "\\$"),
                 ("^", "\\^{}"), ("~", "\\~{}")):
        s = s.replace(a, b)
    return s


def javadoc(texto):
    m = re.search(r"/\*\*(.*?)\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public |final |abstract )*(?:class|interface|record|enum)",
                  texto, re.S)
    if not m:
        return None
    cuerpo = " ".join(l.strip().lstrip("*").strip() for l in m.group(1).splitlines())
    cuerpo = re.sub(r"\{@(?:code|link|linkplain)\s+([^}]*)\}", r"\1", cuerpo)
    cuerpo = re.sub(r"<[^>]+>", "", cuerpo)
    cuerpo = re.split(r"(?<=[.])\s", cuerpo.strip())[0].strip()
    return escapar_texto(cuerpo) if cuerpo else None


def codigo_latex(texto, sin_mapear):
    def cambiar(c):
        if ord(c) < 128:
            return c
        if c in EQUIVALENTES:
            e = EQUIVALENTES[c]
            return "(*@" + e + "@*)" if e else ""
        sin_mapear.add(c)
        return "?"
    return "".join(cambiar(c) for c in texto.replace("\t", "    "))


def main():
    SALIDA.mkdir(parents=True, exist_ok=True)
    lista = archivos()
    desc = descripciones()
    usados = set()
    sin_mapear = set()
    resumen = []
    for clave, titulo, intro, filtro in GRUPOS:
        grupo = [r for r in lista if r not in usados and filtro(r)]
        usados.update(grupo)
        partes = ["% Generado por doc/latex/herramientas/generar-codigo-fuente.py. No editar a mano.\n",
                  "\\section{%s}\n\n%s\n" % (titulo, intro)]
        lineas_grupo = 0
        for ruta in grupo:
            texto = (RAIZ / ruta).read_text(encoding="utf-8-sig", errors="replace").replace("\r\n", "\n")
            if not texto.endswith("\n"):
                texto += "\n"
            n = texto.count("\n")
            lineas_grupo += n
            nombre = pathlib.PurePosixPath(ruta).name
            base = nombre.rsplit(".", 1)[0]
            descripcion = desc.get(base) or (javadoc(texto) if ruta.endswith(".java") else None)
            ext = "." + nombre.rsplit(".", 1)[-1] if "." in nombre else ""
            lenguaje = LENGUAJES.get(ext, "")
            partes.append("\n\\subsection{%s}\n" % escapar_texto(nombre))
            partes.append("\\noindent\\textit{Ruta:} \\ruta{%s} \\quad \\textit{Líneas:} %d\n\n" % (escapar_texto(ruta), n))
            if descripcion:
                partes.append("%s\n\n" % descripcion)
            opciones = "[language=%s]" % lenguaje if lenguaje else ""
            partes.append("\\begin{lstlisting}%s\n%s\\end{lstlisting}\n" % (opciones, codigo_latex(texto, sin_mapear)))
        (SALIDA / (clave + ".tex")).write_text("".join(partes), encoding="utf-8", newline="\n")
        resumen.append((titulo, len(grupo), lineas_grupo))
    faltan = [r for r in lista if r not in usados]
    # Tabla de resumen para la presentación del documento
    filas = ["%s & %d & %d \\\\" % (t, a, l) for t, a, l in resumen]
    tabla = ("% Generado por generar-codigo-fuente.py\n"
             "\\begin{table}[H]\n\\centering\n\\caption{Contenido de este documento.}\n\\label{tab:cod-resumen}\n"
             "\\begin{tabular}{L{8.6cm}rr}\n\\hline\n\\textbf{Parte} & \\textbf{Archivos} & \\textbf{Líneas} \\\\ \\hline\n"
             + "\n".join(filas) + "\n\\hline\n\\textbf{Total} & \\textbf{%d} & \\textbf{%d} \\\\ \\hline\n\\end{tabular}\n\\end{table}\n"
             % (sum(a for _, a, _ in resumen), sum(l for _, _, l in resumen)))
    (SALIDA / "00-resumen-tabla.tex").write_text(tabla, encoding="utf-8", newline="\n")
    print("Archivos: %d, líneas: %d" % (sum(a for _, a, _ in resumen), sum(l for _, _, l in resumen)))
    if faltan:
        print("Sin grupo (no incluidos):", ", ".join(faltan))
    if sin_mapear:
        print("Caracteres sin equivalente (se muestran como ?):", "".join(sorted(sin_mapear)))


if __name__ == "__main__":
    main()
