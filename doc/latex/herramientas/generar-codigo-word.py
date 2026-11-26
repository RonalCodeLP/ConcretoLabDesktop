"""
Genera doc/Codigo_fuente_ConcretoLab.docx (y, con Word instalado, el PDF) con el código fuente completo de ConcretoLab
Desktop, con el mismo formato que un copiado y pegado desde IntelliJ IDEA (tema oscuro): fondo negro (#191A1C) en cada
línea, letra Courier New y los colores del resaltado del IDE.

La plantilla es herramientas/plantilla-codigo.docx (estilos, tamaño de página y márgenes). El orden de los archivos,
los grupos y las descripciones se toman de generar-codigo-fuente.py.

Uso (desde la raíz del repositorio):  python doc/latex/herramientas/generar-codigo-word.py [--sin-pdf]
"""
import datetime
import html
import importlib.util
import pathlib
import re
import subprocess
import sys
import zipfile

AQUI = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("listado", AQUI / "generar-codigo-fuente.py")
listado = importlib.util.module_from_spec(spec)
spec.loader.exec_module(listado)

RAIZ = listado.RAIZ
PLANTILLA = AQUI / "plantilla-codigo.docx"
DOCX = RAIZ / "doc" / "Codigo_fuente_ConcretoLab.docx"
PDF = RAIZ / "doc" / "Codigo_fuente_ConcretoLab.pdf"

FONDO = "191a1c"
# Colores del tema oscuro de IntelliJ IDEA (los mismos del ejemplo)
C = {
    "texto": "bcbec4", "clave": "cf8e6d", "cadena": "6aab73", "numero": "2aacb8", "comentario": "7a7e85",
    "javadoc": "5f826b", "anotacion": "b3ae60", "campo": "c77dbb", "metodo": "56a8f5", "etiqueta": "d5b778",
    "atributo": "bababa",
}

JAVA_CLAVES = set("""abstract assert boolean break byte case catch char class const continue default do double else enum
extends final finally float for goto if implements import instanceof int interface long native new package private
protected public return short static strictfp super switch synchronized this throw throws transient try void volatile
while true false null var record yield sealed permits non-sealed""".split())
SQL_CLAVES = set("""create table if not exists primary key autoincrement integer text real not null default references on
delete cascade unique index alter add column insert into values select from where update set drop foreign check and
or as blob numeric""".split())
PS_CLAVES = set("""function param if elseif else foreach for while do try catch finally return throw switch break
continue begin process end exit in""".split())
BAT_CLAVES = set("""@echo echo off on set if else goto call exit not exist errorlevel setlocal endlocal pause start for
do in rem title cd pushd popd""".split())


# ---------------------------------------------------------------------------------------------- resaltado

def tokens_java(texto):
    """Divide un archivo Java en (texto, color), línea a línea, como lo colorea IntelliJ."""
    campos = set(re.findall(r"^\s{4}(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|protected|public|static|final|volatile|transient)\s+)+"
                            r"[\w<>\[\],.? ]+?\s+(\w+)\s*(?:=|;)", texto, re.M))
    patron = re.compile(r"""
        (?P<javadoc>/\*\*.*?\*/) | (?P<bloque>/\*.*?\*/) | (?P<linea>//[^\n]*) |
        (?P<texto>\"\"\"(?:.|\n)*?\"\"\") | (?P<cadena>"(?:\\.|[^"\\\n])*") | (?P<car>'(?:\\.|[^'\\\n])+') |
        (?P<anot>@\w+(?:\.\w+)*) | (?P<num>\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFdDlL]?\b) |
        (?P<id>[A-Za-z_$][\w$]*) | (?P<otro>\s+|.)""", re.S | re.X)
    salida = []
    ultimo_id = None
    for m in patron.finditer(texto):
        k, s = m.lastgroup, m.group()
        color = C["texto"]
        if k == "javadoc":
            color = C["javadoc"]
        elif k in ("bloque", "linea"):
            color = C["comentario"]
        elif k in ("texto", "cadena", "car"):
            color = C["cadena"]
        elif k == "anot":
            color = C["anotacion"]
        elif k == "num":
            color = C["numero"]
        elif k == "id":
            if s in JAVA_CLAVES:
                color = C["clave"]
            elif s in campos:
                color = C["campo"]
            else:
                resto = texto[m.end():m.end() + 2]
                antes = texto[max(0, m.start() - 1):m.start()]
                # declaración de método: tipo seguido del nombre y paréntesis (no una llamada con punto)
                if resto.startswith("(") and antes == " " and ultimo_id not in (None, "new", "return", "throw", "else"):
                    color = C["metodo"]
            ultimo_id = s if k == "id" else (ultimo_id if k == "otro" and s.isspace() else None)
        if k != "id" and not (k == "otro" and s.isspace()):
            ultimo_id = None
        salida.append((s, color))
    return salida


def tokens_xml(texto):
    patron = re.compile(r"""(?P<com><!--.*?-->) | (?P<prolog><\?.*?\?>) | (?P<tag></?[\w:.\-]+|/?>) |
        (?P<attr>[\w:.\-]+(?==)) | (?P<val>"[^"]*"|'[^']*') | (?P<otro>[^<"'\w]+|\w+|.)""", re.S | re.X)
    salida = []
    dentro = False
    for m in patron.finditer(texto):
        k, s = m.lastgroup, m.group()
        if k == "com":
            salida.append((s, C["comentario"]))
        elif k == "prolog":
            salida.append((s, C["comentario"]))
        elif k == "tag":
            dentro = not s.endswith(">")
            salida.append((s, C["etiqueta"]))
        elif k == "attr" and dentro:
            salida.append((s, C["atributo"]))
        elif k == "val" and dentro:
            salida.append((s, C["cadena"]))
        else:
            salida.append((s, C["texto"]))
    return salida


def tokens_css(texto):
    patron = re.compile(r"""(?P<com>/\*.*?\*/) | (?P<prop>-?[\w-]+(?=\s*:)(?=[^{}]*;)) | (?P<num>-?\b\d+(?:\.\d+)?(?:px|em|%|s|ms)?\b) |
        (?P<cad>"[^"]*"|'[^']*') | (?P<color>\#[0-9a-fA-F]{3,8}\b) | (?P<otro>\s+|[\w.\-#:]+|.)""", re.S | re.X)
    salida = []
    nivel = 0
    for m in patron.finditer(texto):
        k, s = m.lastgroup, m.group()
        if k == "com":
            salida.append((s, C["comentario"]))
        elif k == "prop" and nivel > 0:
            salida.append((s, C["campo"]))
        elif k in ("num", "color"):
            salida.append((s, C["numero"]))
        elif k == "cad":
            salida.append((s, C["cadena"]))
        else:
            if s == "{":
                nivel += 1
            elif s == "}":
                nivel = max(0, nivel - 1)
            salida.append((s, C["etiqueta"] if nivel == 0 and s.strip() not in ("", "{", "}", ",") else C["texto"]))
    return salida


def tokens_genericos(texto, claves, comentario, mayusculas=False):
    patron = re.compile(r"(?P<com>%s) | (?P<cad>\"(?:[^\"\n])*\"|'(?:[^'\n])*') | (?P<var>\$[\w:]+|%%[\w~]+%%?) |"
                        r" (?P<num>\b\d+(?:\.\d+)?\b) | (?P<id>[@\w-]+) | (?P<otro>\s+|.)" % comentario, re.X | re.S)
    salida = []
    for m in patron.finditer(texto):
        k, s = m.lastgroup, m.group()
        if k == "com":
            salida.append((s, C["comentario"]))
        elif k == "cad":
            salida.append((s, C["cadena"]))
        elif k == "var":
            salida.append((s, C["campo"]))
        elif k == "num":
            salida.append((s, C["numero"]))
        elif k == "id" and (s.lower() if not mayusculas else s.lower()) in claves:
            salida.append((s, C["clave"]))
        else:
            salida.append((s, C["texto"]))
    return salida


def tokens_properties(texto):
    salida = []
    for linea in texto.split("\n"):
        if linea.lstrip().startswith(("#", "!")):
            salida.append((linea, C["comentario"]))
        elif "=" in linea:
            clave, valor = linea.split("=", 1)
            salida += [(clave, C["campo"]), ("=", C["texto"]), (valor, C["cadena"])]
        else:
            salida.append((linea, C["texto"]))
        salida.append(("\n", C["texto"]))
    return salida


def resaltar(ruta, texto):
    ext = ruta.rsplit(".", 1)[-1].lower()
    if ext == "java":
        return tokens_java(texto)
    if ext in ("xml", "fxml", "wxs", "wxl"):
        return tokens_xml(texto)
    if ext == "css":
        return tokens_css(texto)
    if ext == "sql":
        return tokens_genericos(texto, SQL_CLAVES, r"--[^\n]*")
    if ext == "ps1":
        return tokens_genericos(texto, PS_CLAVES, r"<\#.*?\#>|\#[^\n]*")
    if ext == "bat":
        return tokens_genericos(texto, BAT_CLAVES, r"(?:^|(?<=\n))\s*(?:rem\b|::)[^\n]*")
    if ext == "properties":
        return tokens_properties(texto)
    return [(texto, C["texto"])]


# ---------------------------------------------------------------------------------------------- Word

FUENTE = '<w:rFonts w:ascii="Courier New" w:cs="Courier New" w:eastAsia="Courier New" w:hAnsi="Courier New"/>'


def run(texto, color):
    return ('<w:r><w:rPr>%s<w:color w:val="%s"/></w:rPr><w:t xml:space="preserve">%s</w:t></w:r>'
            % (FUENTE, color, html.escape(texto, quote=False)))


def parrafo_codigo(piezas):
    ppr = '<w:pPr><w:shd w:fill="%s" w:val="clear"/><w:rPr>%s<w:color w:val="%s"/></w:rPr></w:pPr>' % (FONDO, FUENTE, C["texto"])
    return "<w:p>" + ppr + "".join(run(t, c) for t, c in piezas if t) + "</w:p>"


def lineas_de(tokens):
    """Agrupa los tokens en líneas (un párrafo de Word por línea) y une los trozos del mismo color."""
    lineas, actual = [], []
    for texto, color in tokens:
        partes = texto.split("\n")
        for i, p in enumerate(partes):
            if i > 0:
                lineas.append(actual)
                actual = []
            if p:
                if actual and actual[-1][1] == color:
                    actual[-1] = (actual[-1][0] + p, color)
                else:
                    actual.append((p, color))
    if actual:
        lineas.append(actual)
    return lineas


def parrafo(texto, estilo=None, negrita=False, cursiva=False, centrado=False, tamano=None, salto_antes=False):
    ppr = ""
    if estilo or centrado or salto_antes:
        ppr = "<w:pPr>%s%s%s</w:pPr>" % ('<w:pStyle w:val="%s"/>' % estilo if estilo else "",
                                        '<w:pageBreakBefore w:val="1"/>' if salto_antes else "",
                                        '<w:jc w:val="center"/>' if centrado else "")
    rpr = "%s%s%s" % ("<w:b/>" if negrita else "", "<w:i/>" if cursiva else "",
                      '<w:sz w:val="%d"/><w:szCs w:val="%d"/>' % (tamano, tamano) if tamano else "")
    return '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (
        ppr, "<w:rPr>%s</w:rPr>" % rpr if rpr else "", html.escape(texto, quote=False))


def parrafo_mixto(trozos):
    """trozos: lista de (texto, negrita)."""
    runs = "".join('<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % ("<w:rPr><w:b/></w:rPr>" if b else "",
                                                                        html.escape(t, quote=False)) for t, b in trozos)
    return "<w:p>" + runs + "</w:p>"


def texto_plano(latex):
    """Pasa a texto plano las descripciones escritas en LaTeX para la documentación técnica."""
    s = latex
    s = re.sub(r"\\(?:ruta|textbf|emph|textit|texttt)\{([^{}]*)\}", r"\1", s)
    s = s.replace("\\_", "_").replace("~", " ").replace("\\%", "%").replace("\\&", "&").replace("\\#", "#")
    s = s.replace("\\ldots", "…").replace("\\allowbreak{}", "").replace("\\textbackslash{}", "\\").replace("--", "–")
    s = re.sub(r"\$([^$]*)\$", r"\1", s)
    s = re.sub(r"\\[a-zA-Z]+\{?([^{}]*)\}?", r"\1", s)
    return " ".join(s.replace("{", "").replace("}", "").split())


def indice():
    return ('<w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> TOC \\o "1-2" \\h \\z \\u </w:instrText></w:r>'
            '<w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>(El índice se actualiza al abrir o exportar el documento.)</w:t></w:r>'
            '<w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>')


def main():
    archivos = listado.archivos()
    desc = listado.descripciones()
    usados = set()
    cuerpo = []
    integrantes = ["Fernando Andre Huanca Huallpa", "Yefri Lerma", "Erick Delgado", "Walter Quispesayhua"]
    datos = (RAIZ / "doc" / "latex" / "comun" / "datos.tex").read_text(encoding="utf-8")
    m = re.search(r"\\newcommand\{\\equipoconcretolab\}\{(.*?)\}\s*$", datos, re.M | re.S)
    if m:
        nombres = [n.strip() for n in re.split(r"\\\\|,", texto_plano(m.group(1))) if n.strip()]
        if len(nombres) >= 2:
            integrantes = nombres
    version = re.search(r"\\def\\versionapp\s*\{([^}]*)\}", datos)
    version = version.group(1).strip() if version else "0.1.0"

    # Portada
    cuerpo += [parrafo("", tamano=22) for _ in range(6)]
    cuerpo.append(parrafo("ConcretoLab Desktop", centrado=True, negrita=True, tamano=48))
    cuerpo.append(parrafo("Código fuente", centrado=True, tamano=36))
    cuerpo.append(parrafo("Versión %s" % version, centrado=True, tamano=26))
    cuerpo += [parrafo("", tamano=22) for _ in range(8)]
    meses = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
             "noviembre", "diciembre"]
    hoy = datetime.date.today()
    cuerpo.append(parrafo("Perú, %s de %d" % (meses[hoy.month - 1], hoy.year), centrado=True))

    # Presentación e índice
    cuerpo.append(parrafo("Presentación", estilo="Heading1", salto_antes=True))
    resumen = []
    for _, titulo, _, filtro in listado.GRUPOS:
        grupo = [r for r in archivos if r not in usados and filtro(r)]
        usados.update(grupo)
        resumen.append((titulo, grupo))
    total_lineas = sum((RAIZ / r).read_text(encoding="utf-8-sig", errors="replace").count("\n") for _, g in resumen for r in g)
    cuerpo.append(parrafo(
        "Este documento contiene el código fuente completo de ConcretoLab Desktop %s, tal como se encuentra en el "
        "repositorio del proyecto: %d archivos y %d líneas. Incluye el archivo de construcción de Maven, la "
        "configuración, los scripts de Windows, todas las clases Java de la aplicación, las vistas FXML, la hoja de "
        "estilos, las migraciones de la base de datos, los recursos del instalador y las pruebas automáticas."
        % (version, sum(len(g) for _, g in resumen), total_lineas)))
    cuerpo.append(parrafo(
        "El código está organizado por capas. Cada sección corresponde a una parte de la aplicación y, dentro de ella, "
        "cada archivo aparece con su ruta en el repositorio, su número de líneas, una breve descripción y su contenido "
        "completo, con el formato del entorno de desarrollo IntelliJ IDEA. La arquitectura y los cálculos se explican en "
        "la Documentación técnica de ConcretoLab."))
    cuerpo.append(parrafo(
        "No se incluyen los archivos que no son código: imágenes, íconos, la tabla de zonificación sísmica por distrito "
        "(data/zonificacion-e030-2026.csv) y el plano DXF de prueba."))
    cuerpo.append(parrafo("Índice", estilo="Heading1", salto_antes=True))
    cuerpo.append(indice())

    # Código
    for (_, titulo, intro, _), (_, grupo) in zip(listado.GRUPOS, resumen):
        cuerpo.append(parrafo(titulo, estilo="Heading1", salto_antes=True))
        cuerpo.append(parrafo(texto_plano(intro)))
        for ruta in grupo:
            texto = (RAIZ / ruta).read_text(encoding="utf-8-sig", errors="replace").replace("\r\n", "\n").replace("\t", "    ")
            if texto.endswith("\n"):
                texto = texto[:-1]
            n = texto.count("\n") + 1
            nombre = pathlib.PurePosixPath(ruta).name
            base = nombre.rsplit(".", 1)[0]
            d = desc.get(base)
            d = texto_plano(d) if d else (texto_plano(listado.javadoc(texto) or "") if ruta.endswith(".java") else "")
            cuerpo.append(parrafo(nombre, estilo="Heading2"))
            cuerpo.append(parrafo_mixto([("Ruta: ", True), (ruta, False), ("     Líneas: ", True), (str(n), False)]))
            if d:
                cuerpo.append(parrafo(d))
            for linea in lineas_de(resaltar(ruta, texto)):
                cuerpo.append(parrafo_codigo(linea))
            cuerpo.append(parrafo(""))

    with zipfile.ZipFile(PLANTILLA) as z:
        partes = {n: z.read(n) for n in z.namelist()}
    documento = partes["word/document.xml"].decode("utf-8")
    inicio = documento.index("<w:body>") + len("<w:body>")
    sect = documento.rindex("<w:sectPr")
    documento = documento[:inicio] + "".join(cuerpo) + documento[sect:]
    partes["word/document.xml"] = documento.encode("utf-8")
    # Que Word actualice el índice al abrir
    ajustes = partes["word/settings.xml"].decode("utf-8")
    if "updateFields" not in ajustes:
        ajustes = ajustes.replace("</w:settings>", '<w:updateFields w:val="true"/></w:settings>')
    partes["word/settings.xml"] = ajustes.encode("utf-8")
    DOCX.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DOCX, "w", zipfile.ZIP_DEFLATED) as z:
        for n, b in partes.items():
            z.writestr(n, b)
    print("Word: %s (%d archivos, %d líneas)" % (DOCX, sum(len(g) for _, g in resumen), total_lineas))

    if "--sin-pdf" not in sys.argv:
        ps = ("$w = New-Object -ComObject Word.Application; $w.Visible = $false; $w.DisplayAlerts = 0; "
              "try { $d = $w.Documents.Open('%s', $false, $false); $d.TablesOfContents | ForEach-Object { $_.Update() }; "
              "$d.Save(); $d.ExportAsFixedFormat('%s', 17); $d.Close($false) } finally { $w.Quit() }"
              % (str(DOCX).replace("'", "''"), str(PDF).replace("'", "''")))
        r = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr)
            sys.exit("No se pudo exportar el PDF con Word.")
        print("PDF: %s" % PDF)


if __name__ == "__main__":
    main()
