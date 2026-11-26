<#
 Compila la documentación de ConcretoLab con pdflatex (MiKTeX o TeX Live) y deja los PDF en la carpeta doc\:
   doc\Levantar_como_usuario_ConcretoLab.pdf
   doc\Levantar_como_desarrollador_ConcretoLab.pdf
   doc\Manual_de_usuario_ConcretoLab.pdf
   doc\Documentacion_tecnica_ConcretoLab.pdf
   doc\Codigo_fuente_ConcretoLab.docx y .pdf   (en Word, con herramientas\generar-codigo-word.py; requiere Python y Word)
 El manual también se copia a src\main\resources\ayuda\, de donde lo toma la ayuda integrada de la aplicación
 (vuelva a compilar la aplicación o el instalador después de cambiar el manual).
 Los archivos intermedios quedan en doc\latex\build (no se versionan).

 Uso: .\compilar.ps1            (los cinco)
      .\compilar.ps1 usuario       (solo uno: usuario, desarrollador, manual, tecnica o codigo)
#>
param([ValidateSet('todos', 'usuario', 'desarrollador', 'manual', 'tecnica', 'codigo')][string]$Documento = 'todos')

$ErrorActionPreference = 'Stop'
$aqui = $PSScriptRoot
$doc = Split-Path $aqui
$build = Join-Path $aqui 'build'
New-Item -ItemType Directory -Force -Path $build | Out-Null
if (-not (Get-Command pdflatex -ErrorAction SilentlyContinue)) { throw 'No se encontró pdflatex. Instale MiKTeX o TeX Live.' }

$documentos = [ordered]@{
    usuario       = @{ Fuente = 'levantar-usuario'; Pdf = 'Levantar_como_usuario_ConcretoLab.pdf' }
    desarrollador = @{ Fuente = 'levantar-desarrollador'; Pdf = 'Levantar_como_desarrollador_ConcretoLab.pdf' }
    manual  = @{ Fuente = 'manual-usuario'; Pdf = 'Manual_de_usuario_ConcretoLab.pdf' }
    tecnica = @{ Fuente = 'documentacion-tecnica'; Pdf = 'Documentacion_tecnica_ConcretoLab.pdf' }
    codigo  = @{ Fuente = 'codigo-fuente'; Pdf = 'Codigo_fuente_ConcretoLab.pdf' }
}

Push-Location $aqui
try {
    foreach ($clave in $documentos.Keys) {
        if ($Documento -ne 'todos' -and $Documento -ne $clave) { continue }
        $d = $documentos[$clave]
        if ($clave -eq 'codigo') {
            Write-Host 'Generando el código fuente en Word...'
            # El código fuente se arma en Word (fondo negro, como al copiar desde IntelliJ) y Word lo exporta a PDF.
            & python -I -X utf8 (Join-Path $aqui 'herramientas\generar-codigo-word.py')
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el documento del código fuente.' }
            Write-Host '  listo: doc\Codigo_fuente_ConcretoLab.docx y .pdf' -ForegroundColor Green
            continue
        }
        if (-not (Test-Path "$($d.Fuente).tex")) { Write-Warning "Falta $($d.Fuente).tex"; continue }
        Write-Host "Compilando $($d.Fuente)..."
        # Tres pasadas: índice, referencias cruzadas y números de página definitivos.
        for ($i = 1; $i -le 3; $i++) {
            # MiKTeX escribe avisos propios en stderr; no son errores de compilación (se revisa el código de salida).
            $ErrorActionPreference = 'Continue'
            & pdflatex -interaction=nonstopmode -halt-on-error "-output-directory=$build" "$($d.Fuente).tex" *> (Join-Path $build "$($d.Fuente).salida.txt")
            $ErrorActionPreference = 'Stop'
            if ($LASTEXITCODE -ne 0) {
                Select-String -Path (Join-Path $build "$($d.Fuente).log") -Pattern '^!' -Context 0, 4 | Select-Object -First 3 | ForEach-Object { $_.Line; $_.Context.PostContext }
                throw "pdflatex falló en $($d.Fuente) (ver $build\$($d.Fuente).log)."
            }
        }
        Copy-Item (Join-Path $build "$($d.Fuente).pdf") (Join-Path $doc $d.Pdf) -Force
        if ($clave -eq 'manual') {
            # La ayuda integrada de la aplicación muestra este mismo PDF (src\main\resources\ayuda).
            $ayuda = Join-Path (Split-Path $doc) 'src\main\resources\ayuda'
            New-Item -ItemType Directory -Force -Path $ayuda | Out-Null
            Copy-Item (Join-Path $build "$($d.Fuente).pdf") (Join-Path $ayuda $d.Pdf) -Force
        }
        $registro = (Get-Content (Join-Path $build "$($d.Fuente).log") -Raw) -replace "`r?`n", ''
        $paginas = ([regex]::Matches($registro, '\((\d+) pages') | Select-Object -Last 1).Groups[1].Value
        Write-Host ("  listo: doc\{0} ({1} páginas)" -f $d.Pdf, $paginas) -ForegroundColor Green
    }
} finally {
    Pop-Location
}
