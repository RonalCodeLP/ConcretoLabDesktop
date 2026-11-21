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
una sola vez, con prioridad zapata › columna › viga › muro › losa. Incluye encofrado y acero referenciales (anteproyecto, no metrado de obra).
El análisis sísmico sigue siendo de pórticos: los muros avisan que no entran en la rigidez y las losas solo se usan para el metrado.

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

## Datos de demostración

El proyecto «DEMO - Edificación de práctica» (plantilla rectangular de 12 elementos, 3 materiales, un tamizado, una mezcla con su ensayo) se crea con los seeders de `com.concretolab.seeder`. Su carga se controla con `app.seeder.enabled` en `application.properties`; también puedes activarla al iniciar con `java -Dapp.seeder.enabled=true -jar target/concretolab-desktop-0.1.0.jar`. Es idempotente: no duplica datos, pero si borras el proyecto DEMO se vuelve a crear mientras la opción siga activa. Los valores son ficticios y no deben usarse en obra.

El seeder también crea «Práctica - Edificio de dos pisos incompleto»: una cuadrícula con ejes X en 0, 5 y 9 m, ejes Y en 0 y 4 m y niveles en 0, 3 y 6 m. Incluye las seis zapatas y la estructura entre X1 y X2; faltan las columnas sobre X3 y las vigas del tramo X2-X3 y del eje X3. Las secciones de práctica son zapata 1,5 × 1,5 × 0,5 m, columna cuadrada de 0,4 m y viga de 0,3 × 0,5 m.

Para completarlo desde «Ejes, niveles y generación», conserva las dimensiones indicadas y usa la vista previa antes de confirmar cada paso:

1. Selecciona las intersecciones 3-1 y 3-2 y genera solo columnas. Se crearán las de ambos pisos.
2. Selecciona 2-1, 3-1, 2-2 y 3-2 y genera solo vigas X. Se completarán los tramos entre X2 y X3 en ambos pisos.
3. Selecciona 3-1 y 3-2 y genera solo vigas Y. Se completará el tramo sobre X3 en ambos pisos.

El seeder omite el proyecto de práctica si ya existe, por lo que conserva los cambios hechos al completarlo. Si se elimina el proyecto, se volverá a crear al iniciar con los seeders activados.

## Base de datos cifrada

La base SQLite se cifra en reposo con AES-256 (driver SQLite3MultipleCiphers, modo SQLCipher 4). Al abrir la aplicación se pide la contraseña; la primera vez se crea (mínimo 8 caracteres) y, si ya había datos sin cifrar, se cifran en el sitio con una copia previa que se borra cuando la base abre bien (también se ofrece borrar las copias de seguridad antiguas que sigan sin cifrar). **La contraseña no se guarda en ningún lado: si se olvida, los datos no se pueden recuperar.** Las copias de seguridad nuevas salen cifradas con la misma contraseña; una copia hecha antes de cambiarla se abre con la contraseña que tenía. En «Datos y copias» está «Cambiar contraseña…» (la aplicación se cierra al terminar). Para automatizar (pruebas, scripts) la variable de entorno `CONCRETOLAB_DB_CLAVE` evita el diálogo; no se recomienda para uso normal.

## Datos y copias de seguridad

La base SQLite está en `%USERPROFILE%\.concretolab\concretolab.db`. Al abrir la aplicación se guarda una copia de seguridad **antes** de que se migre el esquema, y otra al cerrarla (se conservan las últimas 10 de cada una en `.concretolab\backups`). El menú «Datos y copias» permite crear copias manuales (nunca se borran solas), restaurar una (se aplica al reiniciar y antes se guarda una copia de lo actual), exportar un proyecto a un archivo `.concretolab` (opcionalmente cifrado con AES-256-GCM y contraseña) e importarlo en otra computadora (siempre como proyecto nuevo), y ver el historial de cambios. La base completa también está cifrada (ver «Base de datos cifrada»).

El esquema lo gestiona Flyway (`src/main/resources/db/migration`): una base creada con versiones anteriores se completa sola (V1 tablas, V2 bitácora, V3 columnas faltantes). Cada cambio en las entidades requiere una migración nueva. Los registros técnicos se escriben en `.concretolab\logs\concretolab.log`.

## Compilar y ejecutar

Se requiere JDK 17 o superior y Maven con las dependencias disponibles:

```powershell
mvn test
mvn package
java -jar target/concretolab-desktop-0.1.0.jar
```

## Distribución para Windows

En una computadora de desarrollo con JDK 17 o superior (con `jpackage` y `jlink`) y Maven:

```powershell
.\CREAR_DISTRIBUCION_WINDOWS.ps1
```

Opciones: `-ConDatosDemostracion` (activa los datos de demostración), `-Instalador` (además crea un instalador `.exe` con acceso directo y menú Inicio; requiere WiX Toolset 3.x en el PATH, si no está avisa y deja la carpeta portable) y `-Verificar` (arranca una copia de la aplicación empaquetada con una carpeta de datos temporal y confirma que crea la base cifrada; abre la ventana unos segundos y nunca toca la base real).

El resultado queda en `dist\ConcretoLab` (unos 126 MB): comparte toda la carpeta y ejecuta `ConcretoLab.exe`. Incluye un entorno Java recortado con `jlink`, por lo que el usuario final no necesita instalar Java ni Maven. La versión sale del `pom.xml`. Los datos quedan en `%USERPROFILE%\.concretolab`; con `-Dconcretolab.datos=<carpeta>` se puede usar otra carpeta (base, copias y registros).

El arranque tarda varios segundos la primera vez (migraciones y carga de Spring/Hibernate); se midió que activar la inicialización perezosa de Spring ahorra menos del 10 % en caliente, así que no se activó.

## Acerca de

El botón «Acerca de» del menú lateral muestra la versión, la autoría del equipo (Yefri Lerma, Fernando Huanca, Miajeld Delgado, Walter Quispe Sayhuan), las normas aplicadas, la carpeta de datos y si la base está cifrada, los componentes de terceros con su licencia y el aviso de que los resultados no sustituyen la revisión de un profesional colegiado.