<#
 Toma las capturas reales de la «Guía de instalación» (doc\latex\img\guia):
   - el aviso de Windows SmartScreen (con una copia del instalador marcada como descargada de internet);
     solo se captura y se cierra con «No ejecutar»: el aviso no se salta de forma automática;
   - cada pantalla del asistente de instalación, con números y recuadros sobre lo que hay que pulsar;
   - la primera apertura de ConcretoLab (con una carpeta de datos TEMPORAL: nunca la del usuario).
 Al terminar desinstala ConcretoLab. No se ejecuta si ya está instalado.
#>
[CmdletBinding()]
param([string]$Instalador)

$ErrorActionPreference = 'Stop'
$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
if (-not $Instalador) {
    [xml]$pom = Get-Content (Join-Path $raiz 'pom.xml') -Encoding UTF8
    $Instalador = Join-Path $raiz ("dist\ConcretoLab-" + $pom.project.version + '.exe')
}
$salida = Join-Path $PSScriptRoot '..\img\guia'
New-Item -ItemType Directory -Force -Path $salida | Out-Null

Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing, System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Pantalla {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
  [DllImport("user32.dll")] public static extern IntPtr SendMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr h, int atributo, out RECT r, int tam);
}
"@
[void][Pantalla]::SetProcessDPIAware()
$AE = [System.Windows.Automation.AutomationElement]
$TS = [System.Windows.Automation.TreeScope]

function Patron($el, $p) { $o = $null; if ($el.TryGetCurrentPattern($p, [ref]$o)) { return $o }; return $null }

function Ventana-De([scriptblock]$filtro, [int]$segundos = 60) {
    $limite = (Get-Date).AddSeconds($segundos)
    do {
        foreach ($v in $AE::RootElement.FindAll($TS::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
            if (& $filtro $v) { return $v }
        }
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $limite)
    return $null
}

function Asistente { Ventana-De { param($v) $v.Current.ClassName -eq 'MsiDialogCloseClass' -and $v.Current.Name -match 'ConcretoLab' } 60 }

function Hijo($ventana, [string]$nombre, [int]$segundos = 15) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::NameProperty, $nombre)
    $limite = (Get-Date).AddSeconds($segundos)
    do {
        $e = $ventana.FindFirst($TS::Descendants, $cond)
        if ($e) { return $e }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $limite)
    throw "No se encontró «$nombre» en «$($ventana.Current.Name)»."
}

function Pulsar($el) {
    $i = Patron $el ([System.Windows.Automation.InvokePattern]::Pattern)
    if ($i) { $i.Invoke(); Start-Sleep -Milliseconds 900; return }
    $t = Patron $el ([System.Windows.Automation.TogglePattern]::Pattern)
    if ($t) { $t.Toggle(); Start-Sleep -Milliseconds 500; return }
    # Los controles del instalador de Windows (MSI) son botones Win32 sin patrones de UI Automation: clic con BM_CLICK.
    $h = [IntPtr]$el.Current.NativeWindowHandle
    if ($h -ne [IntPtr]::Zero) { [void][Pantalla]::SendMessage($h, 0x00F5, [IntPtr]::Zero, [IntPtr]::Zero); Start-Sleep -Milliseconds 1200; return }
    throw "No se puede pulsar «$($el.Current.Name)»."
}

# Captura la ventana tal como se ve y dibuja marcas numeradas: @(@{ El = <elemento>; N = 1 }, ...).
# $tapar: @(@{ El = <elemento>; Texto = '...' }) reemplaza datos personales (por ejemplo, el usuario de Windows en una ruta).
function Capturar($ventana, [string]$archivo, $marcas = @(), $tapar = @()) {
    $h = [IntPtr]$ventana.Current.NativeWindowHandle
    if ($h -ne [IntPtr]::Zero) { [void][Pantalla]::SetForegroundWindow($h) }
    Start-Sleep -Milliseconds 700
    $r = $ventana.Current.BoundingRectangle
    # Borde visible real (sin el margen invisible de Windows 11, que captura lo que hay detrás de la ventana)
    $marco = New-Object Pantalla+RECT
    if ($h -ne [IntPtr]::Zero -and [Pantalla]::DwmGetWindowAttribute($h, 9, [ref]$marco, 16) -eq 0) {
        $r = New-Object System.Windows.Rect $marco.Left, $marco.Top, ($marco.Right - $marco.Left), ($marco.Bottom - $marco.Top)
    }
    $bmp = New-Object System.Drawing.Bitmap ([int]$r.Width), ([int]$r.Height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen([int]$r.X, [int]$r.Y, 0, 0, $bmp.Size)
    $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'ClearTypeGridFit'
    foreach ($t in $tapar) {
        $b = $t.El.Current.BoundingRectangle
        $rect = New-Object System.Drawing.RectangleF ([single]($b.X - $r.X + 2)), ([single]($b.Y - $r.Y + 2)), ([single]($b.Width - 4)), ([single]($b.Height - 4))
        $g.FillRectangle([System.Drawing.Brushes]::White, $rect)
        $sf = New-Object System.Drawing.StringFormat; $sf.LineAlignment = 'Center'
        $g.DrawString($t.Texto, (New-Object System.Drawing.Font('Segoe UI', ([single]([Math]::Max(11, $b.Height * 0.55))), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)), [System.Drawing.Brushes]::Black, $rect, $sf)
    }
    $escala = [Math]::Max(1.0, $r.Height / 400.0)
    foreach ($m in $marcas) {
        $b = $m.El.Current.BoundingRectangle
        $x = [single]($b.X - $r.X - 3); $y = [single]($b.Y - $r.Y - 3); $w = [single]($b.Width + 6); $hh = [single]($b.Height + 6)
        $lapiz = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 220, 38, 38)), ([single](3 * $escala))
        $g.DrawRectangle($lapiz, $x, $y, $w, $hh)
        $d = [single](26 * $escala)
        $cx = [single]([Math]::Max(2, $x - $d * 0.55)); $cy = [single]([Math]::Max(2, $y - $d * 0.55))
        $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 220, 38, 38))), $cx, $cy, $d, $d)
        $g.DrawEllipse((New-Object System.Drawing.Pen ([System.Drawing.Color]::White), ([single](2 * $escala))), $cx, $cy, $d, $d)
        $f = New-Object System.Drawing.Font('Segoe UI', ([single](13 * $escala)), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $sf = New-Object System.Drawing.StringFormat; $sf.Alignment = 'Center'; $sf.LineAlignment = 'Center'
        $g.DrawString([string]$m.N, $f, [System.Drawing.Brushes]::White, (New-Object System.Drawing.RectangleF $cx, $cy, $d, $d), $sf)
    }
    $g.Dispose()
    $bmp.Save((Join-Path $salida $archivo), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  captura: $archivo"
}

function Registrado {
    Get-ChildItem 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall' -ErrorAction SilentlyContinue |
        ForEach-Object { Get-ItemProperty $_.PSPath } | Where-Object { $_.DisplayName -eq 'ConcretoLab' } | Select-Object -First 1
}

if (Registrado) { throw 'ConcretoLab ya está instalado: desinstálelo antes de tomar las capturas.' }
$temporal = Join-Path ([System.IO.Path]::GetTempPath()) ('concretolab-capturas-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporal | Out-Null

# Ícono del archivo instalador (como se ve en el Explorador)
$ico = [System.Drawing.Icon]::ExtractAssociatedIcon($Instalador)
$bmpIco = New-Object System.Drawing.Bitmap 256, 256
$gi = [System.Drawing.Graphics]::FromImage($bmpIco); $gi.InterpolationMode = 'HighQualityBicubic'
$gi.DrawImage((New-Object System.Drawing.Icon($ico, 256, 256)).ToBitmap(), 0, 0, 256, 256); $gi.Dispose()
$bmpIco.Save((Join-Path $salida '00-icono-instalador.png'), [System.Drawing.Imaging.ImageFormat]::Png)

# 1) SmartScreen: copia marcada como descargada de internet
Write-Host '1) Aviso de SmartScreen'
$copia = Join-Path $temporal (Split-Path $Instalador -Leaf)
Copy-Item -LiteralPath $Instalador -Destination $copia
Set-Content -LiteralPath $copia -Stream 'Zone.Identifier' -Value "[ZoneTransfer]`r`nZoneId=3`r`nHostUrl=https://drive.google.com/" -Encoding ASCII
$procCopia = Start-Process -FilePath $copia -PassThru
$ss = Ventana-De { param($v) $v.Current.Name -match 'Windows protegi' -or $v.Current.ClassName -eq 'Windows.UI.Core.CoreWindow' -and $v.Current.Name -match 'SmartScreen' } 20
$smartScreen = $false
if ($ss) {
    $smartScreen = $true
    Capturar $ss '01-smartscreen.png' @(@{ El = (Hijo $ss 'Más información'); N = 1 })
    Pulsar (Hijo $ss 'Más información')
    Capturar $ss '02-smartscreen-ejecutar.png' @(@{ El = (Hijo $ss 'Ejecutar de todas formas'); N = 2 })
    Pulsar (Hijo $ss 'No ejecutar')
} else {
    Write-Warning 'No apareció SmartScreen (puede estar desactivado en esta PC). La guía lo explicará solo con texto.'
    # Sin aviso, la copia abrió el instalador: se cierra sin instalar nada.
    & taskkill /PID $procCopia.Id /T /F 2>$null | Out-Null
}
Start-Sleep -Seconds 2

# 2) Asistente de instalación
Write-Host '2) Asistente de instalación'
$procInst = Start-Process -FilePath $Instalador -PassThru
$asis = Ventana-De { param($v) $v.Current.Name -match 'ConcretoLab' -and $v.Current.ClassName -eq 'MsiDialogCloseClass' } 120
if (-not $asis) { throw 'No apareció el asistente de instalación.' }
Start-Sleep -Seconds 2
$siguiente = Hijo $asis 'Siguiente'
Capturar $asis '03-bienvenida.png' @(@{ El = $siguiente; N = 1 })
Pulsar $siguiente

$asis = Asistente
$cambiar = Hijo $asis 'Cambiar...' 5
$siguiente = Hijo $asis 'Siguiente'
$ruta = $asis.FindFirst($TS::Descendants, (New-Object System.Windows.Automation.OrCondition((New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, [System.Windows.Automation.ControlType]::Edit)), (New-Object System.Windows.Automation.PropertyCondition($AE::ClassNameProperty, 'RichEdit20W')))))
if (-not $ruta) { throw 'No se encontró la ruta de instalación para ocultar el nombre de usuario.' }
$taparRuta = @(); if ($ruta) { $taparRuta = @(@{ El = $ruta; Texto = 'C:\Users\<su usuario>\AppData\Local\ConcretoLab\' }) }
Capturar $asis '04-carpeta.png' @(@{ El = $siguiente; N = 1 }, @{ El = $cambiar; N = 2 }) $taparRuta
Pulsar $siguiente

$asis = Asistente
$casilla = Hijo $asis 'Crear un acceso directo en el escritorio'
$siguiente = Hijo $asis 'Siguiente'
Capturar $asis '05-accesos-directos.png' @(@{ El = $casilla; N = 1 }, @{ El = $siguiente; N = 2 })
Pulsar $siguiente

$asis = Asistente
$instalar = Hijo $asis 'Instalar'
Capturar $asis '06-listo-para-instalar.png' @(@{ El = $instalar; N = 1 })
Pulsar $instalar
Start-Sleep -Milliseconds 1500
$asis = Asistente
Capturar $asis '07-instalando.png'

$limite = (Get-Date).AddSeconds(300)
do { Start-Sleep -Seconds 1; $asis = Asistente; $finalizar = $asis.FindFirst($TS::Descendants, (New-Object System.Windows.Automation.PropertyCondition($AE::NameProperty, 'Finalizar'))) } while (-not $finalizar -and (Get-Date) -lt $limite)
Capturar $asis '08-finalizado.png' @(@{ El = $finalizar; N = 1 })
Pulsar $finalizar
Start-Sleep -Seconds 2

$inst = Registrado
if (-not $inst) { throw 'La instalación no quedó registrada.' }
$exe = Join-Path $inst.InstallLocation 'ConcretoLab.exe'

# 3) Primera apertura (datos temporales)
Write-Host '3) Primera apertura'
$info = New-Object System.Diagnostics.ProcessStartInfo $exe
$info.UseShellExecute = $false
$info.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = "-Dconcretolab.datos=$(Join-Path $temporal 'datos')"
$app = [System.Diagnostics.Process]::Start($info)
$principal = Ventana-De { param($v) $v.Current.Name -eq 'ConcretoLab Desktop' } 150
if ($principal) {
    $w = Patron $principal ([System.Windows.Automation.WindowPattern]::Pattern)
    if ($w) { $w.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal) }
    Start-Sleep -Seconds 3
    Capturar $principal '09-primera-apertura.png'
    if ($w) { $w.Close() }
}
Start-Sleep -Seconds 5
if (-not $app.HasExited) { try { & taskkill /PID $app.Id /T /F 2>$null | Out-Null } catch { } }

# 4) Desinstalar
Write-Host '4) Desinstalar'
Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/x', $inst.PSChildName, '/qn') -Wait
Write-Host ("Desinstalado: " + (-not (Registrado)))
Remove-Item -LiteralPath $temporal -Recurse -Force -ErrorAction SilentlyContinue
Write-Host ("SmartScreen capturado: $smartScreen")
