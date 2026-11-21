# Guía de desarrollo: ConcretoLab y simulación sísmica en Perú

**Fecha de investigación:** 16 de noviembre de 2026.  
**Estado:** propuesta técnica; no certifica cumplimiento normativo ni sustituye la revisión de un ingeniero estructural colegiado y habilitado.

## 1. Qué se quiere construir

Una aplicación de escritorio para registrar una edificación, construir su modelo analítico y estudiar su respuesta frente a acciones sísmicas; presentar resultados trazables y compararlos con criterios normativos aplicables, bajo la revisión del profesional responsable. Debe operar localmente después de su instalación.

El proyecto conserva JavaFX para la interfaz, Spring Boot para los servicios y SQLite para datos locales. Un motor especializado podrá ejecutarse localmente si se comprueban su distribución, licencia y funcionamiento en Windows. Una animación debe representar resultados calculados; no demuestra por sí sola que una edificación sea segura.

## 2. Situación actual del proyecto

- **Modelo 3D:** prismas rectangulares para representación y volumen bruto; no definen conectividad, rigidez, masas ni un modelo apto para análisis de edificios. Las intersecciones no se descuentan.
- **Respuesta sísmica idealizada:** sistema de un grado de libertad con valores ingresados; no está conectado a la edificación ni implementa la E.030.
- **Concreto:** materiales, granulometría y balance de cantidades; no es una implementación completa ni validada de ACI 211.1.

Por tanto, no debe mostrarse un botón que declare que el modelo 3D existente «resiste» un sismo. Primero hay que construir y validar el modelo analítico y sus comprobaciones.

## 3. Marco peruano a considerar

El [Reglamento Nacional de Edificaciones (RNE)](https://www.gob.pe/institucion/vivienda/informes-publicaciones/2309793-reglamento-nacional-de-edificaciones-rne?trk=public_post_comment-text) establece normas de cumplimiento obligatorio para quienes proyectan o ejecutan edificaciones dentro de su ámbito. El programa es una herramienta de apoyo: la elección de criterios, el expediente y la responsabilidad profesional requieren revisión humana.

| Referencia | Relación con el sistema | Cómo se aplicaría |
|---|---|---|
| [E.030 Diseño Sismorresistente, RM 183-2026-VIVIENDA](https://www.gob.pe/institucion/vivienda/normas-legales/8081915-183-2026-vivienda) | Requisitos para el diseño sismorresistente dentro del RNE. | Registrar edición y parámetros justificados; implementar cada regla con su artículo, condiciones de aplicación y pruebas verificables. |
| [RM 217-2026-VIVIENDA](https://www.gob.pe/institucion/vivienda/normas-legales/8219609-217-2026-vivienda) | Modifica la disposición transitoria de la RM 183-2026 para proyectos en curso. | Preguntar fecha y situación del proyecto; el profesional debe confirmar qué versión corresponde. No aplicar automáticamente la versión nueva a todos los expedientes existentes. |
| E.020 Cargas | Determinación de acciones pertinentes. | Registrar casos y combinaciones con fuentes y unidades; contrastar cada regla con el texto aplicable. |
| E.050 Suelos y Cimentaciones | Información y criterios de suelos y cimentaciones. | Registrar el estudio de mecánica de suelos, su fecha, procedencia y parámetros facilitados por el responsable; no deducir el suelo de la ubicación. |
| E.060 Concreto Armado | Requisitos para estructuras de concreto armado. | Módulo independiente de verificaciones cuando existan geometría, materiales, refuerzo y solicitaciones suficientes; no inferir aprobación a partir del f’c. |
| Otras normas del RNE | Pueden ser aplicables según el material, uso y sistema, incluidas E.031 para aislamiento y normas específicas de acero o albañilería. | Incorporar solo cuando el alcance del modelo lo requiera y se haya verificado la versión pertinente. |

El [índice oficial del RNE](https://www.gob.pe/institucion/sencico/informes-publicaciones/887225-normas-del-reglamento-nacional-de-edificaciones-rne) sirve para localizar estas normas. También deben comprobarse modificaciones y vigencia a la fecha del proyecto y del informe. La RM 279-2025 difundía una **propuesta**, no debe tratarse como la versión aprobada.

### Límite de esta investigación

Se verificaron las páginas oficiales que identifican la modificación de 2026 y la disposición transitoria, pero en esta consulta no se pudo recuperar el texto íntegro de ambas desde el portal. **Antes de programar parámetros, límites, ecuaciones o fechas de transición debe obtenerse el texto oficial completo, leer sus disposiciones y verificar su vigencia y alcance con el profesional responsable.** No se fijan aquí tablas, coeficientes, límites de deriva ni reglas numéricas no contrastadas.

Las NTP de ensayos de concreto o agregados y ACI 211.1 pueden ser relevantes para el módulo de mezclas, pero no reemplazan el RNE ni convierten una dosificación en verificación sísmica. Cada ensayo debe registrar su norma y edición aplicable.

## 4. Qué información necesitaría el usuario

1. **Proyecto y normativa:** ubicación exacta, uso, fechas, situación del proyecto, profesional responsable y edición normativa justificada.
2. **Geometría:** niveles y cotas; ejes; nudos y conectividad; vigas, columnas, placas y losas de acuerdo con el alcance soportado; dimensiones y unidades.
3. **Materiales y secciones:** propiedades mecánicas apropiadas para el análisis, secciones, rigideces y justificación; cuando corresponda, parámetros de concreto y refuerzo.
4. **Apoyos y suelo:** restricciones, cimentación y datos de estudio de suelos. El programa debe distinguir entre dato respaldado, supuesto y dato faltante.
5. **Cargas y masas:** cargas permanentes, variables y otras aplicables; combinaciones y fuente de masas definidas según el modelo y normativa seleccionada.
6. **Acción sísmica:** dirección, espectro o registro de movimiento, fuente, unidades, amortiguamiento y escala, según el método aplicable. Un acelerograma real no equivale automáticamente al sismo de diseño del proyecto.

No debe asignarse una resistencia de concreto, un perfil de suelo o una seguridad sísmica solo por seleccionar «columna», «zapata» o un distrito.

## 5. Flujo funcional propuesto

1. **Crear proyecto:** seleccionar edición normativa y adjuntar referencias del expediente.
2. **Modelar:** editar en planta y elevación, crear nudos y barras conectadas; visualizar 3D y comprobar unidades.
3. **Definir propiedades:** materiales, secciones, apoyos, cargas, masas y fuente de cada dato.
4. **Validar modelo:** detectar nudos sin conexión, apoyos ausentes, elementos duplicados, propiedades faltantes y combinaciones sin datos; informar advertencias y errores antes de ejecutar.
5. **Análisis estático y modal:** resolver un caso lineal y comprobar equilibrio; después obtener períodos, modos y participación de masa. Verificar cantidad de modos y criterios con la norma elegida.
6. **Acción sísmica:** permitir análisis modal espectral cuando el método sea aplicable; una fase posterior podrá añadir historia en el tiempo con registros y parámetros justificados.
7. **Resultados:** mostrar tabla y visualización de desplazamientos, derivas, fuerzas y reacciones; indicar unidades, dirección, caso, escala visual y condiciones del análisis.
8. **Evaluación e informe:** comparar cada resultado con un criterio explícito y respaldado. Mostrar «no evaluado» cuando falten comprobaciones. La memoria debe incluir versión normativa, datos, supuestos, motor, advertencias y resultados.

La guía de [Autodesk Robot](https://help.autodesk.com/cloudhelp/2026/ENU/Robot-GSG/files/An_Introduction_to_RSA/Robot_GSG_An_Introduction_to_RSA_RSA_Process_html.html) describe un flujo de modelado, cargas, cálculo y resultados. No debe afirmarse equivalencia con Robot sin validación independiente.

## 6. Propuesta de interfaz

Menú lateral: **Inicio**, **Proyectos**, **Modelo estructural**, **Materiales y secciones**, **Apoyos**, **Cargas y masas**, **Acción sísmica**, **Análisis**, **Resultados** y **Reportes**. El módulo de diseño de mezclas permanecería separado y conectado solo por datos expresamente asignados.

La pantalla de modelo debería reunir planta o elevación, vista 3D, árbol de niveles y tabla de propiedades. El profesional debe poder seleccionar una barra, ver su sección y sus conexiones, y corregir errores sin editar coordenadas como texto libre. Los resultados deben estar asociados a una revisión concreta del modelo; si cambia la geometría, las cargas o los materiales, los resultados anteriores quedan marcados como desactualizados.

## 7. Motor de simulación y funcionamiento offline

[OpenSees](https://opensees.github.io/OpenSeesDocumentation/) es un candidato especializado para simular respuesta dinámica mediante modelos de elementos finitos. Berkeley publica un [ejemplo de pórtico 2D de concreto sometido a excitación sísmica](https://opensees.berkeley.edu/wiki/index.php/Example_3%3A_2D_RC_frame_subjected_to_earthquake_base_excitation). Su capacidad no garantiza que nuestro modelo, sus datos o su conexión a Java sean correctos.

Diseño propuesto: JavaFX → servicios Spring Boot → modelo de dominio validado → adaptador local del motor → proceso local aislado → resultados normalizados en SQLite/archivos del proyecto. Cada ejecución guarda modelo, unidades, versión del motor, entrada sísmica, opciones, fecha, errores, convergencia y resultados. Ejecutar sin red y no aceptar comandos arbitrarios provenientes de archivos de proyecto.

**Pendientes antes de elegirlo:** verificar binarios Windows, instalación reproducible sin internet, dependencias, versiones y comunicación con Java; revisar la [licencia oficial de OpenSees](https://opensees.github.io/OpenSeesDocumentation/developer/license.html), que distingue usos y contempla autorización para incorporación en productos comerciales. No asumir que cualquier distribución comercial es libre.

SQLite es local por computadora; transportar un proyecto requerirá una exportación/importación explícita, con versión de formato y copia de seguridad. No promete sincronización automática entre equipos.

## 8. Etapas de desarrollo

1. **Especificación validada:** elegir un caso de uso y estudiar el texto oficial E.030 y la disposición transitoria. Elaborar una matriz norma–artículo–dato–cálculo–prueba junto al ingeniero.
2. **Modelo estructural mínimo:** pórtico 2D lineal, nudos, barras con flexión, apoyos, cargas y unidades. Mantener separado el modelo 3D de metrados hasta definir una conversión comprobable.
3. **Motor y pruebas estáticas:** prototipo local; comprobar desplazamientos, reacciones y momentos en casos resueltos. Revisar errores, mecanismos e integridad de datos.
4. **Masas y análisis modal:** períodos, formas modales, participación y comprobaciones contra ejemplos de referencia independientes.
5. **Espectro y evaluación peruana:** implementar únicamente los casos de E.030 acordados, con sus condiciones y ejemplos; permitir indicar datos no evaluados. Verificación por especialista y comparación con resultados independientes.
6. **Historia en el tiempo y animación:** importar registros documentados, comprobar paso temporal y unidades, ejecutar análisis y mostrar deformada con escala declarada; estudiar convergencia y reproducibilidad.
7. **Reportes y distribución:** memorias trazables, gestión de versiones, instalación local, copia de seguridad y pruebas sin conexión en otra computadora.

## 9. Plan mínimo de validación

- Casos analíticos independientes: barra axial, viga simplemente apoyada y voladizo; pruebas de unidades, reacciones y equilibrio.
- Pórticos de referencia publicados: estático y modal; comparar períodos, desplazamientos y fuerzas con tolerancias justificadas y resultados de una herramienta independiente.
- Modelos inestables, mal conectados o con rigidez/masa insuficiente: deben producir errores claros, nunca un resultado de seguridad.
- Señales: unidades, dirección, tiempo, datos vacíos, errores de convergencia e importación. La animación debe coincidir con resultados numéricos y declarar su escala.
- Normativa: por cada regla, ejemplo de aplicación, excepción y caso fuera de alcance; revisión del texto vigente y de su disposición transitoria.
- Persistencia: pruebas con base temporal, datos anteriores y apertura sin internet; no usar la base real para ensayos automáticos.
- Revisión profesional documentada y pruebas de usuarios: registrar hallazgos y cerrar defectos antes de afirmar validez para uso real.

## 10. Decisiones pendientes

- Conseguir los textos oficiales completos de E.030 2026 y RM 217-2026 y definir la normativa correspondiente para cada caso.
- Elegir el primer tipo de edificación, número de niveles y sistemas estructurales dentro del alcance; definir qué información suministra el ingeniero.
- Decidir el motor tras una prueba técnica y revisión de licencia; no incluir dependencias externas sin evaluar su distribución.
- Acordar con el ingeniero casos de referencia, criterios de aceptación y límites de uso. El sistema no debe ofrecer una conclusión global de «seguro» sin cobertura completa y revisada.
