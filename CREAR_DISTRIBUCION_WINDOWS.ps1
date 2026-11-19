<#
 Crea una carpeta portable de ConcretoLab para Windows.
 El resultado es dist\ConcretoLab\ConcretoLab.exe. Incluye su propio Java,
 por lo que el usuario final no necesita instalar JDK, Maven ni dependencias.
#>
[CmdletBinding()]
param(
    [switch]$ConDatosDemostracion
)

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $env:JAVA_HOME
} else {
    'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
}

$jpackage = Join-Path $jdk 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
    throw 'No se encontró un JDK con jpackage. Instala JDK 17 o superior y define JAVA_HOME.'
}
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path

Push-Location $raizProyecto
try {
    # Las pruebas se ejecutan en la integración continua. El empaquetado evita
    # iniciar pruebas JavaFX que requieren una sesión gráfica del desarrollador.
    & mvn '-o' '-B' '-ntp' 'package' '-Dmaven.test.skip=true'
    if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo construir ConcretoLab.' }

    $destino = Join-Path $raizProyecto 'dist'
    $imagen = Join-Path $destino 'ConcretoLab'
    if (Test-Path $imagen) { Remove-Item -LiteralPath $imagen -Recurse -Force }
    New-Item -ItemType Directory -Path $destino -Force | Out-Null

    $argumentos = @(
        '--type', 'app-image',
        '--name', 'ConcretoLab',
        '--app-version', '0.1.0',
        '--input', (Join-Path $raizProyecto 'target'),
        '--main-jar', 'concretolab-desktop-0.1.0.jar',
        '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
        '--dest', $destino,
        '--vendor', 'Equipo ConcretoLab'
    )
    if ($ConDatosDemostracion) {
        $argumentos += @('--java-options', '-Dapp.seeder.enabled=true')
    }
    & $jpackage @argumentos
    if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear la distribución.' }

    Write-Host ''
    Write-Host 'Distribución lista:' -ForegroundColor Green
    Write-Host (Join-Path $imagen 'ConcretoLab.exe')
    Write-Host 'Comparte toda la carpeta dist\ConcretoLab, no solo el archivo EXE.'
} finally {
    Pop-Location
}
