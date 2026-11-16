# ConcretoLab Desktop

Primera base de escritorio local con JavaFX, Spring Boot, JPA y SQLite. Permite crear proyectos, registrar zapatas, columnas y vigas como prismas rectangulares, editar sus dimensiones y posición, consultar su volumen geométrico y visualizar un esquema 3D.

Las coordenadas son las del centro de cada prisma; X e Y son horizontales y Z es vertical. Todas las dimensiones y coordenadas se ingresan en metros.

La plantilla rectangular genera cuatro zapatas, cuatro columnas cuadradas y cuatro vigas a partir de las dimensiones ingresadas por el usuario. Las vigas llegan hasta las caras de las columnas. Es un modelo geométrico, no diseña ni valida el proyecto estructural.

«Cerchas 2D» es un módulo separado, inspirado en el flujo de geometría, apoyos, cargas y resultados de Robot. Analiza cerchas planas con barras articuladas, elasticidad lineal, pequeñas deformaciones, una condición de carga nodal, E en MPa, área en cm² y coordenadas en metros. Muestra desplazamientos, reacciones, fuerzas axiales y tensiones, y guarda el modelo en el proyecto local. No importa los prismas 3D ni modela vigas a flexión, pórticos, concreto armado, sismo o normas de diseño. Los resultados son educativos y preliminares, sujetos a validación profesional; no constituyen verificación para obra.

La suma mostrada es bruta: no descuenta intersecciones. La vista es esquemática, no calcula esfuerzos ni verifica estructuras.

El menú «Mezclas y ensayos» permite registrar una dosificación por metro cúbico ingresada por el usuario, la f'c especificada, sus humedades y absorciones, y ensayos físicos asociados. Calcula cantidades de lote y corrección de humedad a partir de masas de agregados en condición SSS y humedad/absorción sobre masa seca; no incorpora aporte de aditivos. No diseña automáticamente una mezcla ACI, no determina f'c y no dictamina conformidad. Los resultados necesitan validación profesional y mezclas de prueba. El resumen PDF no es una memoria normativa.

La biblioteca «Materiales y ensayos» guarda la procedencia, referencia y fecha de ensayo junto con los datos medidos. Al crear una dosificación se puede seleccionar un agregado para copiar su humedad y absorción; el nombre, referencia y fecha quedan como una instantánea en la mezcla. El módulo de mezclas permite seleccionar varios elementos del proyecto para sumar su volumen bruto: sigue siendo necesario revisar manualmente los encuentros.

La calculadora de granulometría recibe masa inicial seca, masas retenidas por tamiz y masa del fondo; muestra porcentajes, balance de masas y gráfico categórico. Los ensayos se pueden guardar y consultar asociados a un agregado. No decide conformidad normativa y no calcula módulo de finura.

«Balance de volúmenes» estima la masa de cemento y del agregado fino SSS para un metro cúbico, con agua, relación a/c, aire, agregado grueso y densidades seleccionados por el usuario. Es un balance matemático sin aditivos ni materiales cementantes adicionales; no selecciona parámetros mediante ACI y no garantiza la resistencia ni las condiciones de obra.

## Compilar y ejecutar

Se requiere JDK 17 o superior y Maven con las dependencias disponibles:

```powershell
mvn test
mvn package
java -jar target/concretolab-desktop-0.1.0.jar
```

Al iniciar se crea `.concretolab/concretolab.db` en la carpeta del usuario. La aplicación no necesita un servidor externo ni conexión a internet durante su uso. El JAR requiere Java instalado; no es todavía un instalador con Java incluido.
