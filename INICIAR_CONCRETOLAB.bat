@echo off
setlocal EnableExtensions
title ConcretoLab Desktop
cd /d "%~dp0"

echo ==================================================
echo   ConcretoLab Desktop - compilar y abrir
echo ==================================================
echo.

rem ---- Java 17 o superior -------------------------------------------------
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%~fD"
)
if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
    set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
) else (
    set "JAVA=java"
    set "JAVAW=javaw"
)
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No se encontro Java 17. Instale Eclipse Temurin 17 o defina JAVA_HOME.
    goto :fin_error
)

rem ---- Maven: el del PATH o el que dejo el wrapper en .m2 ------------------
set "MVN="
where mvn >nul 2>&1 && set "MVN=mvn"
if not defined MVN (
    for /f "delims=" %%M in ('dir /b /s "%USERPROFILE%\.m2\wrapper\dists\mvn.cmd" 2^>nul') do set "MVN=%%M"
)
if not defined MVN (
    echo [ERROR] No se encontro Maven. Instale Maven o agreguelo al PATH.
    goto :fin_error
)

rem ---- Compilar (sin pruebas; sin conexion si ya estan las dependencias) ---
echo Compilando...
call "%MVN%" -q -o -DskipTests package >nul 2>&1
if errorlevel 1 (
    echo Descargando lo que falte de internet; la primera vez puede tardar unos minutos...
    call "%MVN%" -q -DskipTests package
    if errorlevel 1 (
        echo [ERROR] La compilacion fallo. Revise los mensajes de arriba.
        goto :fin_error
    )
)

set "JAR="
for %%J in ("target\concretolab-desktop-*.jar") do set "JAR=%%~fJ"
if not defined JAR (
    echo [ERROR] No se encontro el .jar en la carpeta target.
    goto :fin_error
)

rem ---- Abrir la aplicacion -------------------------------------------------
rem Opciones extra de Java (por ejemplo otra carpeta de datos):
rem   set CONCRETOLAB_JAVA_OPTS=-Dconcretolab.datos=C:\ruta\de\prueba
echo Abriendo ConcretoLab...
start "ConcretoLab Desktop" "%JAVAW%" %CONCRETOLAB_JAVA_OPTS% -jar "%JAR%"
exit /b 0

:fin_error
echo.
pause
exit /b 1
