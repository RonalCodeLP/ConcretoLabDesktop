[CmdletBinding()]
param(
    [string]$Instalador,
    [switch]$DejarInstalado
)

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Instalador) {
    $Instalador = Join-Path $raizProyecto 'dist\ConcretoLab-Setup.exe'
}
if (-not (Test-Path -LiteralPath $Instalador)) { throw "No existe el instalador $Instalador. Créalo con CREAR_DISTRIBUCION_WINDOWS.ps1." }

Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
$AE = [System.Windows.Automation.AutomationElement]
$TS = [System.Windows.Automation.TreeScope]
$script:resultados = New-Object System.Collections.Generic.List[string]
$script:fallas = 0

function Anotar([bool]$ok, [string]$texto) {
    $linea = $(if ($ok) { '[OK]    ' } else { '[FALLA] ' }) + $texto
    $script:resultados.Add($linea)
    if ($ok) { Write-Host $linea -ForegroundColor Green } else { Write-Host $linea -ForegroundColor Red; $script:fallas++ }
}

function Exigir([bool]$ok, [string]$texto) {
    Anotar $ok $texto
    if (-not $ok) { throw "Falló: $texto" }
}

function Instalacion-Registrada {
    Get-ChildItem 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall' -ErrorAction SilentlyContinue |
        ForEach-Object { Get-ItemProperty $_.PSPath } | Where-Object { $_.DisplayName -eq 'ConcretoLab' } | Select-Object -First 1
}

function Ids-Proceso([int]$padre) {
    @($padre) + @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$padre" | ForEach-Object { [int]$_.ProcessId })
}

function Esperar-Ventana([int]$padre, [int]$segundos = 150) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        foreach ($id in (Ids-Proceso $padre)) {
            $q = Get-Process -Id $id -ErrorAction SilentlyContinue
            if ($q -and $q.MainWindowTitle) { return $q.MainWindowTitle }
        }
        Start-Sleep -Milliseconds 700
    }
    return ''
}

function Buscar([int]$padre, [string]$nombre, [string]$tipo = $null, [int]$segundos = 25, [switch]$Opcional) {
    $limite = (Get-Date).AddSeconds($segundos)
    $condNombre = New-Object System.Windows.Automation.PropertyCondition($AE::NameProperty, $nombre)
    do {
        foreach ($id in (Ids-Proceso $padre)) {
            $condProc = New-Object System.Windows.Automation.PropertyCondition($AE::ProcessIdProperty, $id)
            foreach ($v in $AE::RootElement.FindAll($TS::Children, $condProc)) {
                foreach ($h in $v.FindAll($TS::Descendants, $condNombre)) {
                    if ((-not $tipo -or $h.Current.ControlType.ProgrammaticName -eq "ControlType.$tipo") -and -not $h.Current.IsOffscreen) { return $h }
                }
            }
        }
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $limite)
    if ($Opcional) { return $null }
    throw "No se encontró «$nombre» en la ventana de ConcretoLab."
}

function Buscar-Campo([int]$padre, [string]$textoGuia) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, [System.Windows.Automation.ControlType]::Edit)
    foreach ($id in (Ids-Proceso $padre)) {
        $condProc = New-Object System.Windows.Automation.PropertyCondition($AE::ProcessIdProperty, $id)
        foreach ($v in $AE::RootElement.FindAll($TS::Children, $condProc)) {
            foreach ($e in $v.FindAll($TS::Descendants, $cond)) {
                $vp = Patron $e ([System.Windows.Automation.ValuePattern]::Pattern)
                if ($vp -and $vp.Current.Value -eq $textoGuia -and -not $e.Current.IsOffscreen) { return $e }
            }
        }
    }
    throw "No se encontró el campo «$textoGuia»."
}

function Patron($el, $patron) {
    $o = $null
    if ($el.TryGetCurrentPattern($patron, [ref]$o)) { return $o }
    return $null
}

function Pulsar($el) {
    foreach ($par in @(@([System.Windows.Automation.InvokePattern]::Pattern, 'Invoke'), @([System.Windows.Automation.TogglePattern]::Pattern, 'Toggle'),
                       @([System.Windows.Automation.SelectionItemPattern]::Pattern, 'Select'), @([System.Windows.Automation.ExpandCollapsePattern]::Pattern, 'Expand'))) {
        $p = Patron $el $par[0]
        if ($p) { $p.($par[1]).Invoke(); Start-Sleep -Milliseconds 600; return }
    }
    throw "No se puede pulsar «$($el.Current.Name)»."
}

function Escribir($el, [string]$texto) {
    (Patron $el ([System.Windows.Automation.ValuePattern]::Pattern)).SetValue($texto)
    Start-Sleep -Milliseconds 300
}

function Ir-A([int]$padre, [string]$vista) {
    Pulsar (Buscar $padre $vista 'TabItem')
    Start-Sleep -Seconds 1
}

function Combo-Proyecto([int]$padre) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, [System.Windows.Automation.ControlType]::ComboBox)
    foreach ($id in (Ids-Proceso $padre)) {
        $condProc = New-Object System.Windows.Automation.PropertyCondition($AE::ProcessIdProperty, $id)
        foreach ($v in $AE::RootElement.FindAll($TS::Children, $condProc)) {
            foreach ($c in $v.FindAll($TS::Descendants, $cond)) {
                $val = (Patron $c ([System.Windows.Automation.ValuePattern]::Pattern)).Current.Value
                if (-not $c.Current.IsOffscreen -and $val -ne 'Todos los tipos') { return $c }
            }
        }
    }
    throw 'No se encontró el selector de proyecto.'
}

function Proyectos-Listados([int]$padre) {
    $combo = Combo-Proyecto $padre
    $exp = Patron $combo ([System.Windows.Automation.ExpandCollapsePattern]::Pattern)
    $exp.Expand(); Start-Sleep -Seconds 1
    $nombres = @()
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, [System.Windows.Automation.ControlType]::ListItem)
    foreach ($id in (Ids-Proceso $padre)) {
        $condProc = New-Object System.Windows.Automation.PropertyCondition($AE::ProcessIdProperty, $id)
        foreach ($v in $AE::RootElement.FindAll($TS::Children, $condProc)) {
            foreach ($i in $v.FindAll($TS::Descendants, $cond)) { if ($i.Current.Name) { $nombres += $i.Current.Name } }
        }
    }
    $exp.Collapse(); Start-Sleep -Milliseconds 500
    return $nombres
}

function Crear-Proyecto([int]$padre, [string]$nombre) {
    Ir-A $padre 'Modelo 3D y metrado'
    Pulsar (Buscar $padre '+ Proyecto' 'Button')
    Pulsar (Buscar $padre 'Crear proyecto vacío…' 'MenuItem')
    Escribir (Buscar-Campo $padre 'Nombre del proyecto') $nombre
    Escribir (Buscar-Campo $padre 'Ubicación (opcional)') 'Lima'
    Pulsar (Buscar $padre 'Crear' 'Button')
    Start-Sleep -Seconds 2
    return (Patron (Combo-Proyecto $padre) ([System.Windows.Automation.ValuePattern]::Pattern)).Current.Value
}

function Abrir-App([string]$exe, [string]$datos) {
    $info = New-Object System.Diagnostics.ProcessStartInfo $exe
    $info.UseShellExecute = $false
    $info.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = "-Dconcretolab.datos=$datos"
    $p = [System.Diagnostics.Process]::Start($info)
    $titulo = Esperar-Ventana $p.Id
    return @{ Proceso = $p; Titulo = $titulo }
}

function Cerrar-App($app) {
    $id = $app.Proceso.Id
    foreach ($pid2 in (Ids-Proceso $id)) {
        $q = Get-Process -Id $pid2 -ErrorAction SilentlyContinue
        if ($q -and $q.MainWindowHandle -ne [IntPtr]::Zero) {
            $w = Patron ($AE::FromHandle($q.MainWindowHandle)) ([System.Windows.Automation.WindowPattern]::Pattern)
            if ($w) { $w.Close() }
        }
    }
    return (Esperar-Salida $app 60)
}

function Esperar-Salida($app, [int]$segundos) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        $vivos = Ids-Proceso $app.Proceso.Id | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }
        if (-not $vivos) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Matar-App($app) {
    if ($app -and $app.Proceso -and -not $app.Proceso.HasExited) { & taskkill /PID $app.Proceso.Id /T /F 2>$null | Out-Null }
}

function Cabecera([string]$ruta) {
    $fs = New-Object System.IO.FileStream($ruta, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try { $b = New-Object byte[] 15; [void]$fs.Read($b, 0, 15); return [System.Text.Encoding]::ASCII.GetString($b) } finally { $fs.Dispose() }
}

if (Instalacion-Registrada) { throw 'ConcretoLab ya está instalado en esta PC. Desinstálalo antes de ejecutar la prueba (así no se toca una instalación real).' }

$temporal = Join-Path ([System.IO.Path]::GetTempPath()) ('concretolab-prueba-instalador-' + [guid]::NewGuid().ToString('N'))
$datos = Join-Path $temporal 'datos'
New-Item -ItemType Directory -Path $datos -Force | Out-Null
$registroMsi = Join-Path $temporal 'instalacion.log'
$app = $null
$instalado = $null
$menuInicio = Join-Path ([Environment]::GetFolderPath('Programs')) 'ConcretoLab\ConcretoLab.lnk'
$escritorio = Join-Path ([Environment]::GetFolderPath('Desktop')) 'ConcretoLab.lnk'
$proyecto1 = 'Obra de prueba del instalador'
$proyecto2 = 'Proyecto creado después de la copia'
Write-Host "Instalador: $Instalador"
Write-Host "Datos de la prueba (temporales): $datos"

try {
    $inst = Start-Process -FilePath $Instalador -ArgumentList @('/qn', '/l*v', "`"$registroMsi`"") -PassThru
    if (-not $inst.WaitForExit(600000)) { $inst.Kill(); throw 'La instalación no terminó en 10 minutos.' }
    $instalado = Instalacion-Registrada
    Exigir ([bool]$instalado) "Instalación silenciosa terminada (código $($inst.ExitCode)) y registrada en «Aplicaciones instaladas»."
    $carpeta = $instalado.InstallLocation
    if (-not $carpeta) { $carpeta = Split-Path $instalado.DisplayIcon }
    $exe = Join-Path $carpeta 'ConcretoLab.exe'
    Exigir (Test-Path -LiteralPath $exe) "Programa instalado en $carpeta (sin permisos de administrador)."
    Anotar (Test-Path -LiteralPath (Join-Path $carpeta 'runtime\bin\java.dll')) 'Incluye su propio Java (carpeta runtime): el cliente no instala Java.'
    Anotar (Test-Path -LiteralPath $menuInicio) "Acceso en el menú Inicio: $menuInicio"
    Anotar (Test-Path -LiteralPath $escritorio) "Acceso en el escritorio (en modo silencioso se crea; en la instalación normal es una casilla): $escritorio"

    $app = Abrir-App $exe $datos
    Exigir ($app.Titulo -eq 'ConcretoLab Desktop') 'La aplicación instalada abre directamente en su ventana principal, sin pedir contraseña.'
    $base = Join-Path $datos 'concretolab.db'
    Exigir ((Test-Path $base) -and ((Cabecera $base) -eq 'SQLite format 3')) 'Creó la base de datos en la carpeta temporal, sin contraseña.'
    $casilla = Buscar $app.Proceso.Id 'No volver a mostrar al abrir' 'CheckBox' 20 -Opcional
    Exigir ($null -ne $casilla) 'En el primer uso se abrió el recorrido de bienvenida.'
    Pulsar $casilla
    Pulsar (Buscar $app.Proceso.Id 'Cerrar la bienvenida' 'Button')
    $preferencias = Join-Path $datos 'preferencias.properties'
    Exigir ((Test-Path $preferencias) -and ((Get-Content $preferencias -Raw) -match 'bienvenida\.mostrar=false')) 'Al cerrarla con «No volver a mostrar al abrir» marcado, la preferencia quedó guardada en la carpeta de datos.'
    $valor = Crear-Proyecto $app.Proceso.Id $proyecto1
    Exigir ($valor -eq $proyecto1) "Creó y seleccionó el proyecto «$proyecto1»."
    Exigir (Cerrar-App $app) 'Se cerró normalmente con el botón de cerrar de la ventana.'
    $app = $null

    $app = Abrir-App $exe $datos
    Exigir ($app.Titulo -eq 'ConcretoLab Desktop') 'Volvió a abrir sin pedir contraseña.'
    Exigir ($null -eq (Buscar $app.Proceso.Id 'No volver a mostrar al abrir' 'CheckBox' 6 -Opcional)) 'La bienvenida no volvió a abrirse sola.'
    Ir-A $app.Proceso.Id 'Modelo 3D y metrado'
    $lista = Proyectos-Listados $app.Proceso.Id
    Exigir ($lista -contains $proyecto1) "El proyecto se conservó al reiniciar (proyectos: $($lista -join ', '))."

    Ir-A $app.Proceso.Id 'Datos y copias'
    $antes = @(Get-ChildItem (Join-Path $datos 'backups') -Filter '*-manual.db' -ErrorAction SilentlyContinue).Count
    Pulsar (Buscar $app.Proceso.Id 'Crear copia ahora' 'Button')
    $limite = (Get-Date).AddSeconds(30)
    do { Start-Sleep -Milliseconds 500; $manuales = @(Get-ChildItem (Join-Path $datos 'backups') -Filter '*-manual.db' -ErrorAction SilentlyContinue) } while ($manuales.Count -le $antes -and (Get-Date) -lt $limite)
    Exigir ($manuales.Count -gt $antes) "Creó la copia de seguridad «$($manuales[-1].Name)»."

    $valor = Crear-Proyecto $app.Proceso.Id $proyecto2
    Exigir ($valor -eq $proyecto2) "Creó un segundo proyecto «$proyecto2» después de la copia."

    Ir-A $app.Proceso.Id 'Datos y copias'
    Pulsar (Buscar $app.Proceso.Id 'Manual' 'DataItem')
    Pulsar (Buscar $app.Proceso.Id 'Restaurar la seleccionada…' 'Button')
    Pulsar (Buscar $app.Proceso.Id 'Restaurar y cerrar' 'Button')
    Exigir (Esperar-Salida $app 60) 'Al confirmar la restauración, ConcretoLab se cerró para aplicarla.'
    $app = $null

    $app = Abrir-App $exe $datos
    Exigir ($app.Titulo -eq 'ConcretoLab Desktop') 'Abrió de nuevo tras la restauración.'
    Ir-A $app.Proceso.Id 'Modelo 3D y metrado'
    $lista = Proyectos-Listados $app.Proceso.Id
    Exigir (($lista -contains $proyecto1) -and -not ($lista -contains $proyecto2)) "La restauración devolvió los datos de la copia (proyectos: $($lista -join ', '))."
    $previas = @(Get-ChildItem (Join-Path $datos 'backups') -Filter '*-previa-restauracion.db' -ErrorAction SilentlyContinue)
    Anotar ($previas.Count -ge 1) 'Antes de restaurar se guardó una copia de lo que había («Antes de restaurar»).'
    Exigir (Cerrar-App $app) 'Se cerró normalmente.'
    $app = $null
} catch {
    Anotar $false $_.Exception.Message
} finally {
    Matar-App $app
    if ($instalado -and -not $DejarInstalado) {
        $codigo = $instalado.PSChildName
        $des = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/x', $codigo, '/qn') -PassThru -Wait
        $sigue = Instalacion-Registrada
        Anotar (-not $sigue) "Desinstalación silenciosa (código $($des.ExitCode))."
        Anotar (-not (Test-Path -LiteralPath $menuInicio) -and -not (Test-Path -LiteralPath $escritorio)) 'Se quitaron los accesos del menú Inicio y del escritorio.'
        Anotar (Test-Path (Join-Path $datos 'concretolab.db')) 'Desinstalar no borró los datos del usuario (solo el programa).'
    }
    Write-Host ''
    Write-Host ("Resultado: " + $(if ($script:fallas -eq 0) { 'TODO CORRECTO' } else { "$($script:fallas) falla(s)" })) -ForegroundColor $(if ($script:fallas -eq 0) { 'Green' } else { 'Red' })
    Write-Host "Registro de la instalación: $registroMsi"
    Write-Host "Datos de la prueba: $datos (se pueden borrar)"
}
if ($script:fallas -gt 0) { exit 1 }
