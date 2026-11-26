[CmdletBinding()]
param(
    [switch]$SinInstalador,
    [switch]$ConDatosDemostracion,
    [switch]$Verificar
)

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $MyInvocation.MyCommand.Path
$uuidActualizacion = '9b8e382a-8824-4473-a7a5-6689aaeee51b'

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

function Buscar-Maven {
    foreach ($nombre in 'mvn.cmd', 'mvn') {
        $cmd = Get-Command $nombre -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    $wrapper = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    if (Test-Path $wrapper) {
        $encontrado = Get-ChildItem $wrapper -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
        if ($encontrado) { return $encontrado.FullName }
    }
    throw 'No se encontró Maven. Instálalo o agrégalo al PATH.'
}

function Buscar-Wix {
    $cmd = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path $cmd.Source) }
    $candidatos = @(Join-Path $env:LOCALAPPDATA 'ConcretoLab-herramientas\wix314')
    $x86 = [Environment]::GetFolderPath('ProgramFilesX86')
    if ($x86 -and (Test-Path $x86)) {
        $candidatos += (Get-ChildItem $x86 -Directory -Filter 'WiX Toolset v3*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
    }
    foreach ($c in $candidatos) {
        if ((Test-Path (Join-Path $c 'candle.exe')) -and (Test-Path (Join-Path $c 'light.exe'))) { return $c }
    }
    return $null
}

function Limpiar-Carpeta([string]$ruta) {
    if (Test-Path -LiteralPath $ruta) { Remove-Item -LiteralPath $ruta -Recurse -Force }
    New-Item -ItemType Directory -Path $ruta -Force | Out-Null
}

$jdk = Buscar-Jdk
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
$mvn = Buscar-Maven
Write-Host "JDK:   $jdk"
Write-Host "Maven: $mvn"

[xml]$pom = Get-Content (Join-Path $raizProyecto 'pom.xml') -Encoding UTF8
$version = $pom.project.version
$jar = "concretolab-desktop-$version.jar"

$construccion = Join-Path ([System.IO.Path]::GetTempPath()) 'concretolab-construccion'
Limpiar-Carpeta $construccion
Copy-Item -LiteralPath (Join-Path $raizProyecto 'pom.xml') -Destination $construccion
Copy-Item -LiteralPath (Join-Path $raizProyecto 'src') -Destination $construccion -Recurse

Push-Location $construccion
try {
    & $mvn '-o' '-B' '-q' '-ntp' 'package' '-Dmaven.test.skip=true'
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Falló el empaquetado sin conexión; se reintenta descargando lo que falte.'
        & $mvn '-B' '-q' '-ntp' 'package' '-Dmaven.test.skip=true'
        if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo construir ConcretoLab.' }
    }
} finally {
    Pop-Location
}

$runtime = Join-Path $construccion 'runtime'
$modulos = 'java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.scripting,java.sql,java.xml,' +
           'java.instrument,java.security.jgss,java.security.sasl,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.localedata'
& jlink '--add-modules' $modulos '--strip-debug' '--no-header-files' '--no-man-pages' '--compress=2' '--output' $runtime
if ($LASTEXITCODE -ne 0) { throw 'jlink no pudo crear el entorno Java recortado.' }

$entrada = Join-Path $construccion 'entrada-jpackage'
Limpiar-Carpeta $entrada
Copy-Item (Join-Path $construccion "target\$jar") $entrada

$destino = Join-Path $raizProyecto 'dist'
New-Item -ItemType Directory -Path $destino -Force | Out-Null
$imagen = Join-Path $destino 'ConcretoLab'
if (Test-Path -LiteralPath $imagen) { Remove-Item -LiteralPath $imagen -Recurse -Force }

$icono = Join-Path $raizProyecto 'src\main\jpackage\ConcretoLab.ico'
$comunes = @(
    '--name', 'ConcretoLab',
    '--app-version', $version,
    '--input', $entrada,
    '--main-jar', $jar,
    '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
    '--runtime-image', $runtime,
    '--dest', $destino,
    '--icon', $icono,
    '--vendor', 'Equipo ConcretoLab',
    '--copyright', 'Equipo ConcretoLab',
    '--description', 'ConcretoLab: concreto, modelo 3D y análisis sísmico preliminar'
)
if ($ConDatosDemostracion) { $comunes += @('--java-options', '-Dapp.seeder.enabled=true') }

& jpackage @comunes '--type' 'app-image' '--temp' (Join-Path $construccion 'jpackage-imagen')
if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear la carpeta portable.' }
$mb = [math]::Round((Get-ChildItem $imagen -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
Write-Host ''
Write-Host "Carpeta portable lista (${mb} MB): $(Join-Path $imagen 'ConcretoLab.exe')" -ForegroundColor Green

if (-not $SinInstalador) {
    $wix = Buscar-Wix
    if (-not $wix) {
        Write-Warning 'No se creó el instalador: falta WiX Toolset 3.x (candle.exe y light.exe). La carpeta portable sí está lista.'
    } else {
        Write-Host "WiX:   $wix"
        $env:Path = $wix + ';' + $env:Path
        $env:CONCRETOLAB_RECURSOS_INSTALADOR = Join-Path $raizProyecto 'src\main\jpackage'
        $generado = Join-Path $destino "ConcretoLab-$version.exe"
        $instalador = Join-Path $destino 'ConcretoLab-Setup.exe'
        $detalle = @(); if ($PSBoundParameters['Verbose']) { $detalle = @('--verbose') }
        foreach ($viejo in @($generado, $instalador, "$generado.sha256.txt", "$instalador.sha256.txt")) {
            if (Test-Path -LiteralPath $viejo) { Remove-Item -LiteralPath $viejo -Force }
        }
        & jpackage @comunes '--type' 'exe' '--temp' (Join-Path $construccion 'jpackage-instalador') `
            '--win-per-user-install' '--win-dir-chooser' `
            '--win-menu' '--win-menu-group' 'ConcretoLab' `
            '--win-shortcut' '--win-shortcut-prompt' `
            '--win-upgrade-uuid' $uuidActualizacion `
            '--resource-dir' (Join-Path $raizProyecto 'src\main\jpackage') @detalle
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $generado)) { throw 'jpackage no pudo crear el instalador.' }
        Move-Item -LiteralPath $generado -Destination $instalador -Force
        $hash = (Get-FileHash -LiteralPath $instalador -Algorithm SHA256).Hash
        Set-Content -LiteralPath "$instalador.sha256.txt" -Value "$hash  ConcretoLab-Setup.exe" -Encoding ASCII
        $mbInst = [math]::Round((Get-Item -LiteralPath $instalador).Length / 1MB, 1)
        Write-Host "Instalador listo (${mbInst} MB): $instalador" -ForegroundColor Green
        Write-Host "SHA-256: $hash"
    }
}

if ($Verificar) {
    $temporal = Join-Path ([System.IO.Path]::GetTempPath()) ('concretolab-verificacion-' + [guid]::NewGuid().ToString('N'))
    $datos = Join-Path $temporal 'datos'
    New-Item -ItemType Directory -Path $datos -Force | Out-Null
    $info = New-Object System.Diagnostics.ProcessStartInfo (Join-Path $imagen 'ConcretoLab.exe')
    $info.UseShellExecute = $false
    $info.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = "-Dconcretolab.datos=$datos"
    $proceso = [System.Diagnostics.Process]::Start($info)
    $base = Join-Path $datos 'concretolab.db'
    $limite = (Get-Date).AddSeconds(120)
    $ventana = ''
    while ((Get-Date) -lt $limite -and -not $proceso.HasExited) {
        Start-Sleep -Seconds 2
        $ids = @($proceso.Id) + @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($proceso.Id)" | ForEach-Object { [int]$_.ProcessId })
        $ventana = ($ids | ForEach-Object { (Get-Process -Id $_ -ErrorAction SilentlyContinue).MainWindowTitle } | Where-Object { $_ } | Select-Object -First 1)
        if (-not $ventana) { $ventana = '' }
        if ($ventana -eq 'ConcretoLab Desktop' -and (Test-Path $base)) { Start-Sleep -Seconds 3; break }
    }
    $vivo = -not $proceso.HasExited
    & taskkill /PID $proceso.Id /T /F | Out-Null
    Start-Sleep -Seconds 2
    if ($ventana -ne 'ConcretoLab Desktop') { throw "La aplicación no mostró su ventana principal (ventana: '$ventana', en ejecución: $vivo)." }
    if (-not (Test-Path $base)) { throw 'La aplicación no creó la base de datos.' }
    $cabecera = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($base)[0..14])
    if ($cabecera -ne 'SQLite format 3') { throw 'La base nueva no es una base SQLite normal (¿quedó protegida con contraseña?).' }
    Write-Host 'Verificación correcta: abrió la ventana principal sin pedir contraseña y creó la base de datos.' -ForegroundColor Green
    Remove-Item -LiteralPath $temporal -Recurse -Force -ErrorAction SilentlyContinue
}
