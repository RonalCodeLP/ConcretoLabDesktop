# ConcretoLab Desktop

Primera base de escritorio local con JavaFX, Spring Boot, JPA y SQLite. Permite crear proyectos, registrar zapatas, columnas y vigas como prismas rectangulares, editar sus dimensiones y posición, consultar su volumen geométrico y visualizar un esquema 3D.

Las coordenadas son las del centro de cada prisma; X e Y son horizontales y Z es vertical. Todas las dimensiones y coordenadas se ingresan en metros.

La plantilla rectangular genera cuatro zapatas, cuatro columnas cuadradas y cuatro vigas a partir de las dimensiones ingresadas por el usuario. Las vigas llegan hasta las caras de las columnas. Es un modelo geométrico, no diseña ni valida el proyecto estructural.

En «Modelo 3D y metrado», la opción «Ejes, niveles y generación» permite guardar una cuadrícula por proyecto, incluso en proyectos existentes. Ingresa el origen, separaciones entre ejes y cotas de nivel en metros, separadas por punto y coma; actualiza la cuadrícula y selecciona intersecciones y tramos. La vista previa muestra los elementos existentes y propuestos en planta, sus dimensiones y posibles superposiciones. Las zapatas se ubican bajo el primer nivel, las columnas entre niveles consecutivos y las vigas seleccionadas en cada nivel superior, entre caras de apoyos con el lado indicado. La generación no modifica los elementos anteriores y no reemplaza el diseño estructural.

Para mover un elemento, selecciónalo en la tabla o el visor y pulsa «Mover en 3D». Elige X, Y o Z y arrastra horizontalmente el elemento seleccionado, o introduce las coordenadas exactas de su centro. El visor muestra los ejes y niveles guardados; las sugerencias de alineación son opcionales. Antes de guardar se presentan los cambios y posibles superposiciones o separaciones. Las vigas en contacto con una columna pueden vincularse de forma explícita y reajustarse cuando la columna se mueve sobre el eje de la viga; un movimiento perpendicular o vertical requiere quitar ese vínculo o revisar la geometría. «Deshacer movimiento» restaura la última operación de la sesión siempre que sus elementos no hayan cambiado desde entonces. Las sugerencias son geométricas, no una validación estructural.

«Análisis sísmico E.030» toma las columnas y vigas del proyecto como un pórtico 3D de concreto armado (barras de Euler-Bernoulli, ejes a nudos, base empotrada, un diafragma rígido por piso agrupado por la cota superior de las vigas). El usuario elige zona, categoría, suelo S0 a S4 (con Vs30 opcional para interpolar S2 y S3), irregularidades y CT de la E.030 (selector de versión: texto 2026 o versión 2018), f'c y las cargas de losa de cada piso. Calcula el peso sísmico, los modos de vibración, el análisis estático (arts. 33 a 37, con excentricidad accidental), el modal espectral con combinación CQC y cortante mínimo (arts. 39 a 45), la combinación direccional 100 % + 30 % (arts. 28, 33.3 y 43) y las derivas inelásticas contra el límite de 0,007, y exporta un reporte PDF con referencias. Los datos de entrada no se guardan. No modela muros, zapatas, zonas rígidas ni diseño de elementos, y implementa el texto 2026 (R.M. 183-2026-VIVIENDA, contrastado con el PDF oficial guardado en `doc/`) y la versión 2018 (R.M. 355-2018-VIVIENDA); los proyectos con trámite iniciado pueden regirse por la anterior. La combinación 100 % + 30 %, los suelos S0 a S4 con Vs30 y el tramo corto del factor C solo existen en 2026. Resultados educativos y preliminares, sujetos a validación por un ingeniero civil colegiado.

La suma mostrada es bruta: no descuenta intersecciones. La vista es esquemática, no calcula esfuerzos ni verifica estructuras.

El menú «Mezclas y ensayos» permite registrar una dosificación por metro cúbico ingresada por el usuario, la f'c especificada, sus humedades y absorciones, y ensayos físicos asociados. Calcula cantidades de lote y corrección de humedad a partir de masas de agregados en condición SSS y humedad/absorción sobre masa seca; no incorpora aporte de aditivos. No diseña automáticamente una mezcla ACI, no determina f'c y no dictamina conformidad. Los resultados necesitan validación profesional y mezclas de prueba. El resumen PDF no es una memoria normativa.

La biblioteca «Materiales y ensayos» guarda la procedencia, referencia y fecha de ensayo junto con los datos medidos. Al crear una dosificación se puede seleccionar un agregado para copiar su humedad y absorción; el nombre, referencia y fecha quedan como una instantánea en la mezcla. El módulo de mezclas permite seleccionar varios elementos del proyecto para sumar su volumen bruto: sigue siendo necesario revisar manualmente los encuentros.

La calculadora de granulometría recibe masa inicial seca, masas retenidas por tamiz y masa del fondo; muestra porcentajes, balance de masas y gráfico categórico. Los ensayos se pueden guardar y consultar asociados a un agregado. No decide conformidad normativa y no calcula módulo de finura.

«Balance de volúmenes» estima la masa de cemento y del agregado fino SSS para un metro cúbico, con agua, relación a/c, aire, agregado grueso y densidades seleccionados por el usuario. Es un balance matemático sin aditivos ni materiales cementantes adicionales; no selecciona parámetros mediante ACI y no garantiza la resistencia ni las condiciones de obra.

## Arquitectura

Sigue la skill `javafx-spring-sqlite-desktop`: Spring Boot sin web como contenedor, vistas FXML en `src/main/resources/view` con un controlador `@Component` por vista (paquete `controller`), helpers reutilizables en `ui` (`Notificador`, `Formularios`, `Tablas`, `Paginador`, `Exportador`), tema AtlantaFX con estilos en `css/app.css` y menú lateral en `MainController`. El proyecto activo lo comparte `SesionProyecto`. `VistasFxmlTest` carga todas las vistas y llama a `refrescar()`.

## Importar una planta estructural de AutoCAD

En **Modelo 3D y metrado**, abre **+ Proyecto → Importar proyecto desde CAD**.

- **DXF ASCII** se lee directamente.
- **DWG** requiere seleccionar el ejecutable `dwgread.exe` de GNU LibreDWG. ConcretoLab convierte una copia temporal a DXF y muestra sus capas. También se puede usar `SAVEAS` en AutoCAD para exportar a DXF ASCII y abrir esa copia.
- **RVT** se prepara en Revit con **Archivo → Exportar → Formatos CAD → DXF**. En el DXF de planta identifica los centros de columnas y ejes de vigas según los pasos de abajo. La aplicación muestra esta ruta al seleccionar un RVT.

La conversión de formato no define el modelo analítico. El resultado debe conservar las capas y entidades compatibles indicadas abajo. Para intercambiar información BIM de Revit, Autodesk ofrece IFC. El importador actual procesa DXF.

Para importar un modelo completo sin llenar el formulario de planta, abre `doc/portico-completo-3d.dxf`, revisa el conteo y guarda el proyecto. Es un caso de demostración con tres niveles, 18 columnas, 21 vigas y 6 zapatas.

Para intercambiar un modelo propio, selecciona el proyecto y pulsa **Exportar DXF** en la cabecera del visor. El archivo contiene caras 3D en coordenadas expresadas en metros. En AutoCAD usa **Abrir** y una vista isométrica. ConcretoLab puede reconstruir los prismas a partir de las capas `CL3D_*` y sus seis caras, incluso si tienen dimensiones distintas. Conserva esas capas y la escala al editar el archivo. El DXF intercambia geometría. Los parámetros de análisis se revisan en el módulo sísmico.

1. En el espacio modelo de AutoCAD, usa una planta XY en cota Z = 0. Las capas de columnas y zapatas aceptan contornos rectangulares cerrados (`LWPOLYLINE` con cuatro esquinas y lados paralelos a X/Y) o centros marcados con `POINT`. Los contornos aportan ancho y largo. Para vigas se requiere `LINE` entre dos centros de columna consecutivos, paralela a X o Y, en su propia capa.
2. Usa `SAVEAS` y guarda una copia como **DXF ASCII**. Puedes empezar por `doc/planta-estructural-ejemplo.dxf`.
3. En **Modelo 3D y metrado → + Proyecto → Importar proyecto desde CAD**, asigna las capas y confirma los metros por unidad de dibujo. Para el ejemplo, usa 0,001 metros por unidad, 3 pisos, 3 m por piso, columna de 0,40 m, viga de 0,30 × 0,50 m y zapata de 1,50 × 1,50 × 0,50 m.
4. Revisa el conteo propuesto y guarda en un proyecto nuevo o vacío. Inspecciona en 3D los elementos generados.
5. Selecciona el proyecto en **Análisis sísmico E.030**, revisa las hipótesis e introduce las cargas y parámetros estructurales. Calcula y abre **Simulación 3D** para ver las formas modales.

La importación reproduce una planta de pórticos rectangulares con secciones uniformes y la misma distribución por piso. El inventario muestra las capas y tipos encontrados durante la importación. Los elementos confirmados quedan en SQLite. Bloques, polilíneas y anotaciones pueden aparecer en el inventario, pero se requiere preparar las capas estructurales con las entidades indicadas. El cálculo existente usa base empotrada y diafragma rígido por piso. Las zapatas importadas se usan para metrado y visualización.

### Importación DXF más tolerante

Al abrir un DXF/DWG la aplicación propone sola las capas por su nombre (COLUMNAS, VIGAS, ZAPATAS, CIMENTACIÓN, PLACAS, EJES, también en inglés
y con tildes), estima la unidad si el archivo no la declara y muestra un diagnóstico de lo que usa y de lo que ignora (textos, cotas, sombreados,
espacio papel). Acepta columnas como rectángulos (también con el vértice repetido), círculos (se importan como cuadrado de lado igual al diámetro),
bloques (solo el punto de inserción) o puntos, y vigas como líneas o polilíneas de dos puntos. Las vigas se anclan a la columna sobre la que
caen sus extremos (o a menos de 30 cm del centro). Las capas de losas (rectángulos cerrados) y de muros (líneas paralelas a X o Y) se importan en cada piso con el espesor que indique:
la cara superior de la losa queda a nivel del piso y los muros van de piso a piso. Los elementos LOSA y MURO también se pueden crear a mano
en «Modelo 3D y metrado» y se exportan/importan con el proyecto.

El resumen de metrado calcula el concreto **neto**: donde se cruzan elementos (columna con viga, viga con losa…) el volumen común se cuenta
una sola vez, con prioridad zapata › columna › viga › muro › losa. Incluye encofrado y acero referenciales (anteproyecto, no metrado de obra); las cuantías de acero por tipo se cambian con «Cuantías de acero…» y se guardan en `cuantias-acero.properties` dentro de la carpeta de datos.
El análisis sísmico sigue siendo de pórticos: los muros avisan que no entran en la rigidez y las losas solo se usan para el metrado.
El resumen completo se abre con el botón «Metrado · … m³» (junto a «Cuantías de acero…») para no tapar la tabla.

### Editar muchos elementos a la vez

Sobre la tabla, el filtro muestra solo zapatas, columnas, vigas, losas o muros, y «Todos» selecciona toda la lista (también sirven Ctrl/Shift + clic).
Con varios seleccionados, «Editar» abre un panel donde solo cambian los campos que se llenan: `0,50` fija la medida y `+0,05` / `-0,05` suma o resta a
cada elemento; se guardan todos juntos o ninguno. En el visor 3D, un clic selecciona, **Ctrl+clic** suma o quita elementos de la selección (de cualquier tipo)
y el **doble clic** abre «Editar elemento», que tiene el botón «Mover en 3D». El doble clic sobre uno de varios seleccionados, en la lista o en el 3D, abre la
edición de todos ellos.

**Deshacer:** el botón «Deshacer» (o Ctrl+Z) revierte, uno por uno, hasta 20 cambios de la sesión: editar un elemento, editar varios a la vez
y mover en 3D. Solo deshace si esos elementos siguen como quedaron (si se cambiaron después por otro camino, avisa y no toca nada); el historial
se vacía al cambiar de proyecto o cerrar la aplicación. Antes de guardar una edición que dejaría vigas sin unir dos columnas (por ejemplo,
una medida fija puesta a vigas en X y en Y a la vez: en una viga en X, «Largo eje X» es su longitud) se pide confirmación.

### Exportar a IFC y ETABS

En «Modelo 3D y metrado», junto a «Exportar DXF…»: **IFC2X3** (columnas, vigas, losas, muros y zapatas como sólidos extruidos con material Concreto; las vigas y losas se extruyen en vertical, la forma es exacta) y **ETABS .e2k** (pisos a partir de las cotas de vigas y losas, secciones rectangulares, base empotrada; las zapatas no se exportan). El IFC se probó solo con comprobaciones estructurales del archivo, sin un visor IFC; el .e2k es **experimental** y no se ha verificado en ETABS: revise el modelo al importarlo.

## Asistente sísmico guiado

La pantalla «Análisis sísmico» pide solo cuatro cosas: **dónde está la obra** (departamento, provincia y distrito, con buscador; la zona sale del Anexo II de la E.030 2026, 1 892 distritos), **para qué se usará** (fija la categoría, U y precarga las cargas vivas de la E.020), **cómo es el suelo** (en lenguaje de campo; se traduce a S0–S4) y **qué sistema estructural tiene** (R0 y CT de la Tabla 10 y el art. 36). Todo lo demás está en «Opciones avanzadas». Los resultados traen semáforo, verificación de cada artículo y la junta sísmica (art. 52 de 2026 y 5.3 de 2018).

Avisos que la pantalla da y conviene no ignorar: la **Tabla 9** no permite pórticos como único sistema en categoría A (zonas 2 a 4; en A1, en ninguna zona) y esta aplicación solo modela pórticos; el análisis es referencial y debe validarlo un ingeniero civil colegiado. Con la versión 2018 la ubicación por distrito se desactiva (el anexo de 2018 es otro) y se elige la zona a mano. Las cargas vivas de industria común, depósito y esenciales son valores sugeridos, no literales de la E.020, y la carga muerta adicional es un valor típico.

## Simulación temporal (tiempo-historia)

En la pestaña «Simulación temporal» del análisis sísmico se generan 7 acelerogramas sintéticos compatibles con el espectro
de la E.030 (o se importan registros propios) y se integra la respuesta lineal elástica con suelo y estructura moviéndose
juntos. La verificación sigue los arts. 46–49 (escalado 0,2T–1,5T, promedio de máximos, deriva ≤ 1,25 × 0,007). Es una
verificación complementaria y conservadora (R = 1); no sustituye el análisis modal espectral. No se incluyen acelerogramas reales.

## Diseñar mezcla (ACI 211.1 + E.060)

El botón «Diseñar mezcla» (pantalla Mezclas y ensayos) calcula una dosificación de partida por m³: f’cr según la E.060 (con o sin
registro de desviación estándar), a/c por resistencia y por durabilidad, agua y aire (ACI 211.1), volumen de grueso según el módulo
de finura y fino por volumen absoluto. Copia los datos de la biblioteca de materiales y permite pasar el resultado a «Registrar mezcla».
Límites: peso normal, f’cr ≤ 45 MPa (35 con aire incorporado), sin aditivos reductores de agua; debe validarse con mezclas de prueba.
Los límites de durabilidad (a/c máxima y f’c mínima por exposición) se tomaron de ACI 318 / E.060 cap. 4: verifíquelos contra su edición.
Además, cada mezcla evalúa sus ensayos de 28 días con el criterio de aceptación de la E.060, y la pantalla de granulometría
calcula el módulo de finura y verifica el huso NTP 400.037 / ASTM C33 (fino y gruesos Nº 57, 67 y 7).

La biblioteca de materiales admite cementos por tipo (Portland I, II y V según NTP 334.009; adicionados IP e ICo según NTP 334.090; MS y HS por desempeño según NTP 334.082), el «CEMENTO» genérico de versiones anteriores y aditivos (dosis en observaciones; el diseño ACI no los considera). En «Diseñar mezcla» se puede elegir el cemento: copia su densidad y avisa si su tipo no corresponde a la exposición a sulfatos (moderada → II o MS; severa → V o HS; muy severa → V o HS más puzolana). En «Balance de volúmenes», «Usar en una mezcla» abre «Registrar mezcla» con el cemento, el agua y los agregados SSS por m³ ya escritos.

## Datos de demostración

El proyecto «DEMO - Edificación de práctica» (plantilla rectangular de 12 elementos, 3 materiales, un tamizado, una mezcla con su ensayo) se crea con los seeders de `com.concretolab.seeder`. Su carga se controla con `app.seeder.enabled` en `application.properties`; también puedes activarla al iniciar con `java -Dapp.seeder.enabled=true -jar target/concretolab-desktop-0.1.0.jar`. Es idempotente: no duplica datos, pero si borras el proyecto DEMO se vuelve a crear mientras la opción siga activa. Los valores son ficticios y no deben usarse en obra.

El seeder también crea «Práctica - Edificio de dos pisos incompleto»: una cuadrícula con ejes X en 0, 5 y 9 m, ejes Y en 0 y 4 m y niveles en 0, 3 y 6 m. Incluye las seis zapatas y la estructura entre X1 y X2; faltan las columnas sobre X3 y las vigas del tramo X2-X3 y del eje X3. Las secciones de práctica son zapata 1,5 × 1,5 × 0,5 m, columna cuadrada de 0,4 m y viga de 0,3 × 0,5 m.

Para completarlo desde «Ejes, niveles y generación», conserva las dimensiones indicadas y usa la vista previa antes de confirmar cada paso:

1. Selecciona las intersecciones 3-1 y 3-2 y genera solo columnas. Se crearán las de ambos pisos.
2. Selecciona 2-1, 3-1, 2-2 y 3-2 y genera solo vigas X. Se completarán los tramos entre X2 y X3 en ambos pisos.
3. Selecciona 3-1 y 3-2 y genera solo vigas Y. Se completará el tramo sobre X3 en ambos pisos.

El seeder omite el proyecto de práctica si ya existe, por lo que conserva los cambios hechos al completarlo. Si se elimina el proyecto, se volverá a crear al iniciar con los seeders activados.

## Abrir sin contraseña (y bases de versiones anteriores)

ConcretoLab abre directamente, sin contraseña. Si encuentra una base creada por una versión anterior que estaba protegida con
contraseña, la pide **una sola vez** (hasta 3 intentos; con una incorrecta no se modifica nada) y la convierte: antes guarda una copia
exacta de la base protegida en las copias de seguridad («Base anterior con contraseña», no se borra sola), luego quita la contraseña y
comprueba la integridad y que cada tabla conserve sus registros; si algo falla, vuelve a poner la copia y la base queda como estaba.
Desde entonces abre sin preguntar. Para automatizar, la variable de entorno `CONCRETOLAB_DB_CLAVE` evita el diálogo.

## Datos y copias de seguridad

La base SQLite está en `%USERPROFILE%\.concretolab\concretolab.db`. Al abrir la aplicación se guarda una copia de seguridad **antes** de
que se migre el esquema, y otra al cerrarla (se conservan las últimas 10 de cada una en `.concretolab\backups`).

La pantalla «Datos y copias» está pensada para el cliente:
- **Copias de seguridad:** «Crear copia ahora» (las manuales nunca se borran solas), «Restaurar la seleccionada…» (pide confirmación,
  guarda antes una copia de lo actual y cierra ConcretoLab; al volver a abrirlo se ven los datos de la copia) y «Eliminar copia».
- **Llevar un proyecto a otra computadora:** exportar a un archivo `.concretolab` (opcionalmente protegido con contraseña; eso solo
  afecta al archivo exportado) e importarlo en otra computadora (siempre como proyecto nuevo).
- **Opciones avanzadas** (plegado): rutas de la base, de las copias y de los registros, y el historial de cambios.

El esquema lo gestiona Flyway (`src/main/resources/db/migration`): una base creada con versiones anteriores se completa sola (V1 tablas,
V2 bitácora, V3 columnas faltantes). Cada cambio en las entidades requiere una migración nueva. Los registros técnicos se escriben en
`.concretolab\logs\concretolab.log`.

### Modelo incompleto

Si en algún piso ninguna viga une dos columnas, el resumen dice **«MODELO INCOMPLETO»** y no «CUMPLE», aunque las columnas solas den derivas
bajas: sin vigas unidas a las columnas no hay pórtico. Las derivas se marcan «no válida» (también en la pestaña «Derivas», en la simulación
temporal y en el PDF) y el botón «Ver en el modelo 3D» abre el modelo con esas vigas seleccionadas. Si solo algunas vigas quedan sueltas,
se avisa como alerta (pueden ser voladizos).

### Cómo corregir lo que no cumple

En el resumen, cada verificación que no cumple trae «Cómo corregir». Para la **deriva**, la aplicación vuelve a analizar el edificio agrandando las
columnas de 5 en 5 cm en la dirección que falla (hasta +60 cm) y muestra la menor medida que hace cumplir, con las derivas antes y después; «Ir al modelo 3D
y precargar el cambio» deja esas columnas seleccionadas con el panel de edición ya lleno: se revisa, se pulsa «Aplicar» y se vuelve a calcular. Es una
sugerencia de anteproyecto: peraltar vigas o agregar placas también puede ser la solución. Torsión, sistema no permitido y demás avisos dan un consejo en texto.

## Compilar y ejecutar

En Windows, doble clic en **`INICIAR_CONCRETOLAB.bat`**: compila (sin pruebas) y abre la aplicación. Busca Java 17 en `JAVA_HOME` o en
`C:\Program Files\Eclipse Adoptium` y Maven en el PATH o en `%USERPROFILE%\.m2\wrapper`. Para usar otra carpeta de datos:
`set CONCRETOLAB_JAVA_OPTS=-Dconcretolab.datos=C:\ruta` antes de ejecutarlo.

A mano:

Se requiere JDK 17 o superior y Maven con las dependencias disponibles:

```powershell
mvn test
mvn package
java -jar target/concretolab-desktop-0.1.0.jar
```

## Distribución para Windows (instalador para el cliente)

En una computadora de desarrollo con JDK 17 o superior (`jpackage` y `jlink`), Maven y WiX Toolset 3.x:

```powershell
.\CREAR_DISTRIBUCION_WINDOWS.ps1 -Verificar
```

Genera en `dist\`:
- **`ConcretoLab-<versión>.exe`**: el instalador para el cliente (unos 106 MB) y su huella `…exe.sha256.txt`. Lleva su propio Java
  (entorno recortado con `jlink`): el cliente no instala Java ni Maven. Se instala **solo para el usuario** (sin permisos de
  administrador, en `%LOCALAPPDATA%\ConcretoLab`), crea siempre el acceso del **menú Inicio** y pregunta con una casilla si se quiere
  también en el **escritorio**. Los textos del instalador están en español (`src/main/jpackage`). Se desinstala desde «Aplicaciones
  instaladas» y desinstalar no borra los datos (`%USERPROFILE%\.concretolab`).
- `ConcretoLab\ConcretoLab.exe`: la misma aplicación como carpeta portable (hay que copiar la carpeta completa).

El script compila en una carpeta temporal aparte (no toca `target\` ni un ConcretoLab abierto desde `target\*.jar`), encuentra Maven en
el PATH o en `%USERPROFILE%\.m2\wrapper`, y WiX en el PATH, en `%LOCALAPPDATA%\ConcretoLab-herramientas\wix314` o en
`Archivos de programa (x86)\WiX Toolset v3.*`. Opciones: `-SinInstalador`, `-ConDatosDemostracion` y `-Verificar` (abre la versión
portable con una carpeta de datos temporal y comprueba que abre sin contraseña y crea la base; nunca toca la base real).

**Prueba del instalador** (instala, prueba con datos temporales y desinstala):

```powershell
.\PROBAR_INSTALADOR_WINDOWS.ps1
```

Con UI Automation de Windows comprueba que abre sin contraseña, que guarda un proyecto, que el proyecto sigue al reiniciar y que una copia
de seguridad se crea y se restaura; también revisa los accesos directos y la desinstalación. No se ejecuta si ConcretoLab ya está instalado.

**Compartir con el cliente:** enviar solo el archivo `ConcretoLab-<versión>.exe` (por ejemplo en una carpeta compartida de Google Drive u
OneDrive, o en una memoria USB; por correo suele ser demasiado grande) junto con la huella SHA-256 para comprobar que llegó completo
(`Get-FileHash .\ConcretoLab-0.1.0.exe`). El instalador **no está firmado digitalmente**, así que Windows SmartScreen mostrará «Windows
protegió su PC»: hay que pulsar «Más información» y «Ejecutar de todas formas». Para evitarlo hace falta un certificado de firma de código.

El arranque tarda varios segundos la primera vez (migraciones y carga de Spring/Hibernate).

## Acerca de

El botón «Acerca de» del menú lateral muestra la versión, la autoría del equipo (Yefri Ronaldiño Lerma Pacompia, Erick Mijael Delgado Lima, Fernando André Huanca Huallpa, Walter David Quispesayhua Quispe), las normas aplicadas, la carpeta de datos, los componentes de terceros con su licencia y el aviso de que los resultados no sustituyen la revisión de un profesional colegiado y de que el análisis sísmico es preliminar (no validado para diseñar ni construir obras).