# Plantilla LaTeX

La documentación de ConcretoLab se compone con **Template-Informe** de **Pablo Pizarro R.** (Facultad de Ciencias
Físicas y Matemáticas, Universidad de Chile), versión 7.2.3, en la adaptación que recibió el equipo
(`template.tex` y la carpeta `src/`). Manual de la plantilla: <https://latex.ppizarror.com/informe>.

La plantilla se distribuye con licencia MIT (<https://opensource.org/licenses/MIT>):

> Se concede permiso, de forma gratuita, a cualquier persona que obtenga una copia de este software y de los archivos de
> documentación asociados, para utilizar el software sin restricción, incluidos los derechos de usar, copiar, modificar,
> fusionar, publicar, distribuir, sublicenciar o vender copias del software, sujeto a que el aviso de copyright y este
> aviso de permiso se incluyan en todas las copias o partes sustanciales del software. EL SOFTWARE SE PROPORCIONA «TAL
> CUAL», SIN GARANTÍA DE NINGÚN TIPO.

## Cambios hechos por el equipo ConcretoLab

- Se reemplazaron la portada y el logo de la UPeU por la portada y el logo de ConcretoLab (`comun/concretolab.tex`);
  el archivo `img/departamentos/logo_upeu.pdf` no se incluye.
- Se repararon textos con codificación dañada (UTF-8 codificado dos veces, por ejemplo «Ã³» en lugar de «ó») en
  `template.tex` y `src/`.
- `src/cfg/init.tex` y `src/env/environments.tex`: las opciones `bookmarksopen` y `numbered` del paquete `bookmark`
  se escriben con valores literales (`true`), porque las versiones actuales del paquete no aceptan macros ahí.
