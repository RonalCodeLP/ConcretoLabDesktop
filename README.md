# ConcretoLab Desktop

Primera base de escritorio local con JavaFX, Spring Boot, JPA y SQLite. Permite crear proyectos, registrar zapatas, columnas y vigas como prismas rectangulares, editar sus dimensiones y posición, consultar su volumen geométrico y visualizar un esquema 3D.

Las coordenadas son las del centro de cada prisma; X e Y son horizontales y Z es vertical. Todas las dimensiones y coordenadas se ingresan en metros.

La plantilla rectangular genera cuatro zapatas, cuatro columnas cuadradas y cuatro vigas a partir de las dimensiones ingresadas por el usuario. Las vigas llegan hasta las caras de las columnas. Es un modelo geométrico, no diseña ni valida el proyecto estructural.

En «Modelo 3D y metrado», la opción «Ejes, niveles y generación» permite guardar una cuadrícula por proyecto, incluso en proyectos existentes. Ingresa el origen, separaciones entre ejes y cotas de nivel en metros, separadas por punto y coma; actualiza la cuadrícula y selecciona intersecciones y tramos. La vista previa muestra los elementos existentes y propuestos en planta, sus dimensiones y posibles superposiciones. Las zapatas se ubican bajo el primer nivel, las columnas entre niveles consecutivos y las vigas seleccionadas en cada nivel superior, entre caras de apoyos con el lado indicado. La generación no modifica los elementos anteriores y no reemplaza el diseño estructural.

Para mover un elemento, selecciónalo en la tabla o el visor y pulsa «Mover en 3D». Elige X, Y o Z y arrastra horizontalmente el elemento seleccionado, o introduce las coordenadas exactas de su centro. El visor muestra los ejes y niveles guardados; las sugerencias de alineación son opcionales. Antes de guardar se presentan los cambios y posibles superposiciones o separaciones. Las vigas en contacto con una columna pueden vincularse de forma explícita y reajustarse cuando la columna se mueve sobre el eje de la viga; un movimiento perpendicular o vertical requiere quitar ese vínculo o revisar la geometría. «Deshacer movimiento» restaura la última operación de la sesión siempre que sus elementos no hayan cambiado desde entonces. Las sugerencias son geométricas, no una validación estructural.

«Cerchas 2D» es un módulo separado, inspirado en el flujo de geometría, apoyos, cargas y resultados de Robot. Analiza cerchas planas con barras articuladas, elasticidad lineal, pequeñas deformaciones, una condición de carga nodal, E en MPa, área en cm² y coordenadas en metros. Muestra desplazamientos, reacciones, fuerzas axiales y tensiones, y guarda el modelo en el proyecto local. No importa los prismas 3D ni modela vigas a flexión, pórticos, concreto armado, sismo o normas de diseño. Los resultados son educativos y preliminares, sujetos a validación profesional; no constituyen verificación para obra.

«Análisis sísmico E.030» toma las columnas y vigas del proyecto como un pórtico 3D de concreto armado (barras de Euler-Bernoulli, ejes a nudos, base empotrada, un diafragma rígido por piso agrupado por la cota superior de las vigas). El usuario elige zona, categoría, suelo S0 a S4 (con Vs30 opcional para interpolar S2 y S3), irregularidades y CT de la E.030 (selector de versión: texto 2026 o versión 2018), f'c y las cargas de losa de cada piso. Calcula el peso sísmico, los modos de vibración, el análisis estático (arts. 33 a 37, con excentricidad accidental), el modal espectral con combinación CQC y cortante mínimo (arts. 39 a 45), la combinación direccional 100 % + 30 % (arts. 28, 33.3 y 43) y las derivas inelásticas contra el límite de 0,007, y exporta un reporte PDF con referencias. Los datos de entrada no se guardan. No modela muros, zapatas, zonas rígidas ni diseño de elementos, y implementa el texto 2026 (R.M. 183-2026-VIVIENDA, contrastado con el PDF oficial guardado en `doc/`) y la versión 2018 (R.M. 355-2018-VIVIENDA); los proyectos con trámite iniciado pueden regirse por la anterior. La combinación 100 % + 30 %, los suelos S0 a S4 con Vs30 y el tramo corto del factor C solo existen en 2026. Resultados educativos y preliminares, sujetos a validación por un ingeniero civil colegiado.

La suma mostrada es bruta: no descuenta intersecciones. La vista es esquemática, no calcula esfuerzos ni verifica estructuras.

El menú «Mezclas y ensayos» permite registrar una dosificación por metro cúbico ingresada por el usuario, la f'c especificada, sus humedades y absorciones, y ensayos físicos asociados. Calcula cantidades de lote y corrección de humedad a partir de masas de agregados en condición SSS y humedad/absorción sobre masa seca; no incorpora aporte de aditivos. No diseña automáticamente una mezcla ACI, no determina f'c y no dictamina conformidad. Los resultados necesitan validación profesional y mezclas de prueba. El resumen PDF no es una memoria normativa.

La biblioteca «Materiales y ensayos» guarda la procedencia, referencia y fecha de ensayo junto con los datos medidos. Al crear una dosificación se puede seleccionar un agregado para copiar su humedad y absorción; el nombre, referencia y fecha quedan como una instantánea en la mezcla. El módulo de mezclas permite seleccionar varios elementos del proyecto para sumar su volumen bruto: sigue siendo necesario revisar manualmente los encuentros.

La calculadora de granulometría recibe masa inicial seca, masas retenidas por tamiz y masa del fondo; muestra porcentajes, balance de masas y gráfico categórico. Los ensayos se pueden guardar y consultar asociados a un agregado. No decide conformidad normativa y no calcula módulo de finura.

«Balance de volúmenes» estima la masa de cemento y del agregado fino SSS para un metro cúbico, con agua, relación a/c, aire, agregado grueso y densidades seleccionados por el usuario. Es un balance matemático sin aditivos ni materiales cementantes adicionales; no selecciona parámetros mediante ACI y no garantiza la resistencia ni las condiciones de obra.

## Arquitectura

Sigue la skill `javafx-spring-sqlite-desktop`: Spring Boot sin web como contenedor, vistas FXML en `src/main/resources/view` con un controlador `@Component` por vista (paquete `controller`), helpers reutilizables en `ui` (`Notificador`, `Formularios`, `Tablas`, `Paginador`, `Exportador`), tema AtlantaFX con estilos en `css/app.css` y menú lateral en `MainController`. El proyecto activo lo comparte `SesionProyecto`. `VistasFxmlTest` carga todas las vistas y llama a `refrescar()`.

## Datos de demostración

El proyecto «DEMO - Edificación de práctica» (plantilla rectangular de 12 elementos, 3 materiales, un tamizado, una mezcla con su ensayo y una cercha) se crea con los seeders de `com.concretolab.seeder`. Su carga se controla con `app.seeder.enabled` en `application.properties`; también puedes activarla al iniciar con `java -Dapp.seeder.enabled=true -jar target/concretolab-desktop-0.1.0.jar`. Es idempotente: no duplica datos, pero si borras el proyecto DEMO se vuelve a crear mientras la opción siga activa. Los valores son ficticios y no deben usarse en obra.

El seeder también crea «Práctica - Edificio de dos pisos incompleto»: una cuadrícula con ejes X en 0, 5 y 9 m, ejes Y en 0 y 4 m y niveles en 0, 3 y 6 m. Incluye las seis zapatas y la estructura entre X1 y X2; faltan las columnas sobre X3 y las vigas del tramo X2-X3 y del eje X3. Las secciones de práctica son zapata 1,5 × 1,5 × 0,5 m, columna cuadrada de 0,4 m y viga de 0,3 × 0,5 m.

Para completarlo desde «Ejes, niveles y generación», conserva las dimensiones indicadas y usa la vista previa antes de confirmar cada paso:

1. Selecciona las intersecciones 3-1 y 3-2 y genera solo columnas. Se crearán las de ambos pisos.
2. Selecciona 2-1, 3-1, 2-2 y 3-2 y genera solo vigas X. Se completarán los tramos entre X2 y X3 en ambos pisos.
3. Selecciona 3-1 y 3-2 y genera solo vigas Y. Se completará el tramo sobre X3 en ambos pisos.

El seeder omite el proyecto de práctica si ya existe, por lo que conserva los cambios hechos al completarlo. Si se elimina el proyecto, se volverá a crear al iniciar con los seeders activados.

## Compilar y ejecutar

Se requiere JDK 17 o superior y Maven con las dependencias disponibles:

```powershell
mvn test
mvn package
java -jar target/concretolab-desktop-0.1.0.jar
```

## Distribución portable para Windows

En una computadora de desarrollo con JDK 17 o superior y Maven, ejecuta:

```powershell
.\CREAR_DISTRIBUCION_WINDOWS.ps1
```

Para incluir los proyectos de demostración en la primera ejecución:

```powershell
.\CREAR_DISTRIBUCION_WINDOWS.ps1 -ConDatosDemostracion
```

El resultado queda en `dist\ConcretoLab`. Comparte toda esa carpeta y ejecuta `ConcretoLab.exe`. La distribución incluye un entorno Java privado y no requiere que el usuario final instale Java ni Maven. La base de datos queda en `%USERPROFILE%\.concretolab\concretolab.db`.

Al iniciar se crea `.concretolab/concretolab.db` en la carpeta del usuario. La aplicación no necesita un servidor externo ni conexión a internet durante su uso. El JAR requiere Java instalado; no es todavía un instalador con Java incluido.
