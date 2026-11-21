<#
 Crea la distribución de ConcretoLab para Windows.

 Resultado por defecto: dist\ConcretoLab\ConcretoLab.exe (carpeta portable). Incluye un entorno Java recortado con jlink
 (solo los módulos que la aplicación usa), por lo que el usuario final no necesita instalar JDK, Maven ni dependencias.

 Parámetros:
   -ConDatosDemostracion  activa los datos de demostración en la primera ejecución.
   -Instalador            además crea un instalador .exe (acceso directo y menú Inicio). Requiere WiX Toolset 3.x en el PATH.
   -Verificar             arranca la aplicación empaquetada con una carpeta de datos temporal (nunca la real) y confirma que crea la base
                          cifrada; abre la ventana unos segundos.
#>
[CmdletBinding()]
param(
    [switch]$ConDatosDemostracion,
    [switch]$Instalador,
    [switch]$Verificar
)

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $MyInvocation.MyCommand.Path

function Buscar-Jdk {
    $candidatos = @()
    if ($env:JAVA_HOME) { $candidatos += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Amazon Corretto", "$env:ProgramFiles\Zulu")) {
        if (Test-Path $base) {
            $candidatos += (Get-ChildItem $base -Directory -Filter 'jdk*' | Sort-Object Name -Descending | ForEach-Object FullName)
        }
    }
    foreach ($c in $candidatos) {
        if ((Test-Path (Join-Path $c 'bin\jpackage.exe')) -and (Test-Path (Join-Path $c 'bin\jlink.exe'))) { return $c }
    }
    throw 'No se encontró un JDK 17 o superior con jpackage y jlink. Instálalo y define JAVA_HOME.'
}

$jdk = Buscar-Jdk
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
Write-Host "JDK: $jdk"

# La versión sale del pom.xml: un solo lugar donde cambiarla.
[xml]$pom = Get-Content (Join-Path $raizProyecto 'pom.xml') -Encoding UTF8
$version = $pom.project.version
$jar = "concretolab-desktop-$version.jar"

Push-Location $raizProyecto
try {
    # Las pruebas se ejecutan aparte (mvn test); el empaquetado no necesita sesión gráfica.
    & mvn '-o' '-B' '-ntp' 'package' '-Dmaven.test.skip=true'
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Falló el empaquetado sin conexión; se reintenta con conexión.'
        & mvn '-B' '-ntp' 'package' '-Dmaven.test.skip=true'
        if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo construir ConcretoLab.' }
    }

    # Runtime recortado: los módulos de Java que usan JavaFX, Spring, Hibernate, Flyway, OpenPDF y el driver SQLite.
    $runtime = Join-Path $raizProyecto 'target\runtime'
    if (Test-Path $runtime) { Remove-Item -LiteralPath $runtime -Recurse -Force }
    $modulos = 'java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.scripting,java.sql,java.xml,' +
               'java.instrument,java.security.jgss,java.security.sasl,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.localedata'
    & jlink '--add-modules' $modulos '--strip-debug' '--no-header-files' '--no-man-pages' '--compress=2' '--output' $runtime
    if ($LASTEXITCODE -ne 0) { throw 'jlink no pudo crear el entorno Java recortado.' }

    $destino = Join-Path $raizProyecto 'dist'
    $imagen = Join-Path $destino 'ConcretoLab'
    if (Test-Path $imagen) { Remove-Item -LiteralPath $imagen -Recurse -Force }
    New-Item -ItemType Directory -Path $destino -Force | Out-Null

    # jpackage toma todo el contenido de --input: se usa una carpeta con solo el JAR ejecutable.
    $entrada = Join-Path $raizProyecto 'target\entrada-jpackage'
    if (Test-Path $entrada) { Remove-Item -LiteralPath $entrada -Recurse -Force }
    New-Item -ItemType Directory -Path $entrada | Out-Null
    Copy-Item (Join-Path $raizProyecto "target\$jar") $entrada

    $comunes = @(
        '--name', 'ConcretoLab',
        '--app-version', $version,
        '--input', $entrada,
        '--main-jar', $jar,
        '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
        '--runtime-image', $runtime,
        '--dest', $destino,
        '--vendor', 'Equipo ConcretoLab',
        '--description', 'Cálculo y ensayos de concreto: modelo 3D, análisis sísmico E.030 y diseño de mezclas.'
    )
    if ($ConDatosDemostracion) { $comunes += @('--java-options', '-Dapp.seeder.enabled=true') }

    & jpackage @comunes '--type' 'app-image'
    if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear la distribución.' }

    $mb = [math]::Round((Get-ChildItem $imagen -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
    Write-Host ''
    Write-Host "Distribución lista (${mb} MB):" -ForegroundColor Green
    Write-Host (Join-Path $imagen 'ConcretoLab.exe')
    Write-Host 'Comparte toda la carpeta dist\ConcretoLab, no solo el archivo EXE.'

    if ($Instalador) {
        $wix = (Get-Command candle.exe -ErrorAction SilentlyContinue)
        if (-not $wix) {
            Write-Warning 'No se creó el instalador: jpackage necesita WiX Toolset 3.x (candle.exe y light.exe en el PATH). La carpeta portable sí está lista.'
        } else {
            & jpackage @comunes '--type' 'exe' '--win-menu' '--win-shortcut' '--win-dir-chooser' '--win-menu-group' 'ConcretoLab'
            if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear el instalador.' }
            Write-Host "Instalador en $destino" -ForegroundColor Green
        }
    }

    if ($Verificar) {
        # Se verifica una COPIA de la distribución con la carpeta de datos redirigida a una carpeta temporal
        # (-Dconcretolab.datos), para no tocar nunca la base real del usuario.
        $temporal = Join-Path ([System.IO.Path]::GetTempPath()) ("concretolab-verificacion-" + [guid]::NewGuid().ToString('N'))
        $copia = Join-Path $temporal 'app-copia'
        $datos = Join-Path $temporal 'datos'
        New-Item -ItemType Directory -Path $datos -Force | Out-Null
        Copy-Item -LiteralPath $imagen -Destination $copia -Recurse
        $cfg = Join-Path $copia 'app\ConcretoLab.cfg'
        $contenido = Get-Content $cfg -Encoding UTF8
        $linea = 'java-options=-Dconcretolab.datos=' + $datos
        $salida = New-Object System.Collections.Generic.List[string]
        $puesto = $false
        foreach ($l in $contenido) {
            $salida.Add($l)
            if ($l -eq '[JavaOptions]' -and -not $puesto) { $salida.Add($linea); $puesto = $true }
        }
        if (-not $puesto) { $salida.Add('[JavaOptions]'); $salida.Add($linea) }
        [System.IO.File]::WriteAllLines($cfg, $salida, (New-Object System.Text.UTF8Encoding($false)))

        $info = New-Object System.Diagnostics.ProcessStartInfo (Join-Path $copia 'ConcretoLab.exe')
        $info.UseShellExecute = $false
        $info.EnvironmentVariables['CONCRETOLAB_DB_CLAVE'] = 'verificacion-2026'
        $proceso = [System.Diagnostics.Process]::Start($info)
        $base = Join-Path $datos 'concretolab.db'
        $limite = (Get-Date).AddSeconds(120)
        $ok = $false
        while ((Get-Date) -lt $limite -and -not $proceso.HasExited) {
            Start-Sleep -Seconds 2
            if ((Test-Path $base) -and ((Get-Item $base).Length -gt 50000)) { $ok = $true; Start-Sleep -Seconds 4; break }
        }
        $vivo = -not $proceso.HasExited
        & taskkill /PID $proceso.Id /T /F | Out-Null
        if (-not $ok) { throw "La aplicación empaquetada no creó la base de datos de prueba (seguía en ejecución: $vivo)." }
        $cabecera = [System.Text.Encoding]::ASCII.GetString((Get-Content $base -Encoding Byte -TotalCount 15))
        if ($cabecera -eq 'SQLite format 3') { throw 'La base se creó SIN cifrar.' }
        Write-Host "Verificación correcta: arrancó, creó la base cifrada y siguió en ejecución ($vivo)." -ForegroundColor Green
        Remove-Item -LiteralPath $temporal -Recurse -Force -ErrorAction SilentlyContinue
    }} finally {
    Pop-Location
}
