@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem ==================================================================
rem  Gera o pacote portatil do Chat Sakura (para computadores sem Java
rem  e sem Maven, como o da faculdade).
rem
rem  Precisa ser executado em um computador com JDK 17 ou superior
rem  (inclui o jlink). Resultado:
rem    dist\ChatSakura-Portatil\      (pasta pronta para usar)
rem    dist\ChatSakura-Portatil.zip   (a mesma pasta compactada)
rem
rem  O pacote leva: um Java reduzido (pasta runtime), o chat-sakura.jar,
rem  o INICIAR-CHAT-SAKURA.bat e o LEIA-ME.txt. Nenhum banco, certificado,
rem  senha ou configuracao pessoal entra no pacote.
rem ==================================================================
cd /d "%~dp0"
title Chat Sakura - gerar pacote portatil
echo.
echo  Gerando o pacote portatil do Chat Sakura...
echo.

set "JLINK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jlink.exe" set "JLINK=%JAVA_HOME%\bin\jlink.exe"
if not defined JLINK for /f "delims=" %%J in ('where jlink 2^>nul') do if not defined JLINK set "JLINK=%%J"
if not defined JLINK goto :semJdk
for %%D in ("%JLINK%\..\..") do set "JDK=%%~fD"
if not exist "%JDK%\jmods" goto :semJmods
set "JAVA_HOME=%JDK%"
echo  [1/4] JDK: %JDK%

if not exist "dist" mkdir "dist"
set "MVN=.\mvnw.cmd"
where mvn >nul 2>&1 && set "MVN=mvn"
echo  [2/4] Compilando e testando (mvn clean package)...
call %MVN% -q clean package > "dist\compilacao.log" 2>&1
if errorlevel 1 goto :falhaCompilacao
if not exist "target\chat-sakura.jar" goto :falhaCompilacao

set "PACOTE=dist\ChatSakura-Portatil"
if exist "%PACOTE%" rmdir /s /q "%PACOTE%"
if exist "%PACOTE%" goto :pacoteEmUso
if exist "dist\ChatSakura-Portatil.zip" del "dist\ChatSakura-Portatil.zip"
mkdir "%PACOTE%"

echo  [3/4] Criando o Java reduzido (jlink)...
rem Modulos: java.base, java.compiler, java.desktop, java.net.http e java.sql
rem (jdeps); jdk.charsets (acentos no console), jdk.crypto.ec (TLS) e
rem jdk.localedata (portugues).
"%JLINK%" --add-modules java.base,java.compiler,java.desktop,java.net.http,java.sql,jdk.charsets,jdk.crypto.ec,jdk.localedata --include-locales=en,pt --strip-debug --no-header-files --no-man-pages --compress=2 --output "%PACOTE%\runtime" > "dist\jlink.log" 2>&1
if errorlevel 1 goto :falhaJlink

copy /y "target\chat-sakura.jar" "%PACOTE%\" >nul
copy /y "INICIAR-CHAT-SAKURA.bat" "%PACOTE%\" >nul
copy /y "pacote-portatil\LEIA-ME.txt" "%PACOTE%\" >nul

echo  [4/4] Compactando...
tar -a -c -f "dist\ChatSakura-Portatil.zip" -C "dist" "ChatSakura-Portatil" >nul 2>&1
if errorlevel 1 powershell -NoProfile -Command "Compress-Archive -Path 'dist\ChatSakura-Portatil' -DestinationPath 'dist\ChatSakura-Portatil.zip' -Force"

echo.
echo  Pacote pronto:
echo    pasta: %CD%\%PACOTE%
if exist "dist\ChatSakura-Portatil.zip" echo    ZIP:   %CD%\dist\ChatSakura-Portatil.zip
echo.
echo  Copie o ZIP para um pendrive, extraia no outro computador e execute
echo  INICIAR-CHAT-SAKURA.bat de dentro da pasta extraida.
goto :fim

:semJdk
echo  ERRO: jlink nao encontrado. E preciso um JDK 17 ou superior (nao apenas o
echo  JRE) neste computador, com JAVA_HOME apontando para ele.
goto :erro

:semJmods
echo  ERRO: o JDK em %JDK% nao tem a pasta "jmods", necessaria ao jlink.
echo  Instale um JDK completo (por exemplo, Temurin 17) e tente de novo.
goto :erro

:falhaCompilacao
echo  ERRO: a compilacao ou os testes falharam. Veja dist\compilacao.log.
goto :erro

:pacoteEmUso
echo  ERRO: nao foi possivel apagar o pacote anterior em %PACOTE%.
echo  Feche o Chat Sakura que esteja aberto a partir dessa pasta e tente de novo.
goto :erro

:falhaJlink
echo  ERRO: o jlink falhou. Veja dist\jlink.log.
goto :erro

:erro
echo.
pause
exit /b 1

:fim
echo.
pause
exit /b 0
