@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem ==================================================================
rem  Chat Sakura - inicializador local para Windows (dois cliques)
rem
rem  Abre o servidor e o cliente NESTE computador (127.0.0.1:12345), no
rem  modo de desenvolvimento sem TLS. Nao cria certificados, nao pede
rem  senhas tecnicas, nao precisa de administrador e nao altera nenhuma
rem  configuracao permanente do Windows. Os dados ficam na pasta "dados".
rem
rem  Funciona de duas formas:
rem    - pacote portatil: usa o Java da pasta "runtime" (nada a instalar);
rem    - codigo-fonte (ZIP do GitHub): precisa do Java 17 ou superior e,
rem      na primeira vez, de internet para baixar as bibliotecas.
rem ==================================================================

rem Sempre a pasta deste arquivo (funciona com espacos e acentos no caminho).
cd /d "%~dp0"
if /i "%~1"==":servidor" goto :janelaServidor

title Chat Sakura - inicializador
echo.
echo  =====================================================
echo    CHAT SAKURA - inicializador local
echo  =====================================================
echo.

if not exist "dados\logs" mkdir "dados\logs" >nul 2>&1
if not exist "dados\logs" goto :pastaSemGravacao

set "JAVA="
set "JAR="
set "PORTATIL="
set "SERVIDOR_NOSSO="
if exist "runtime\bin\java.exe" if exist "chat-sakura.jar" goto :modoPortatil
goto :modoCodigo

rem ------------------------------------------------------------------
rem  1. Java
rem ------------------------------------------------------------------
:modoPortatil
set "PORTATIL=1"
set "JAVA=runtime\bin\java.exe"
set "JAR=chat-sakura.jar"
echo  [1/4] Pacote portatil: usando o Java incluido na pasta "runtime".
goto :verificarPorta

:modoCodigo
set "JAR=target\chat-sakura.jar"
if not exist "pom.xml" goto :pastaErrada
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA set "JAVA=%%J"
if not defined JAVA goto :semJava

"%JAVA%" -version 2> "dados\logs\java-versao.txt"
set "VERSAO="
for /f "usebackq tokens=2,3" %%A in ("dados\logs\java-versao.txt") do if /i "%%A"=="version" if not defined VERSAO set "VERSAO=%%~B"
if not defined VERSAO goto :javaDesconhecido
set "MAIOR="
for /f "delims=.-+_" %%M in ("%VERSAO%") do set "MAIOR=%%M"
set /a MAIOR_NUM=MAIOR 2>nul
if %MAIOR_NUM% LSS 17 goto :javaAntigo
echo  [1/4] Java %VERSAO% encontrado.

rem O Maven usa o mesmo Java verificado acima (somente nesta janela).
for %%D in ("%JAVA%\..\..") do if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"

rem ------------------------------------------------------------------
rem  2. Ja existe um servidor? Senao, prepara o programa (codigo-fonte)
rem ------------------------------------------------------------------
:verificarPorta
if not exist "%JAR%" goto :compilar
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal verificar >nul 2>&1
set "ESTADO=%errorlevel%"
if "%ESTADO%"=="0" goto :jaAberto
if "%ESTADO%"=="2" goto :portaOcupada
if defined PORTATIL if not "%ESTADO%"=="1" goto :falhaJava
if defined PORTATIL goto :iniciarServidor

:compilar
set "MVN=.\mvnw.cmd"
where mvn >nul 2>&1 && set "MVN=mvn"
echo  [2/4] Preparando o programa. Na primeira vez, as bibliotecas sao baixadas
echo        da internet e isso pode levar alguns minutos. Aguarde...
echo        (detalhes em dados\logs\compilacao.log)
call %MVN% -q -DskipTests package > "dados\logs\compilacao.log" 2>&1
if errorlevel 1 goto :falhaCompilacao
if not exist "%JAR%" goto :falhaCompilacao
echo        Pronto.

:verificarDeNovo
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal verificar >nul 2>&1
set "ESTADO=%errorlevel%"
if "%ESTADO%"=="0" goto :jaAberto
if "%ESTADO%"=="2" goto :portaOcupada
if not "%ESTADO%"=="1" goto :falhaJava

rem ------------------------------------------------------------------
rem  3. Servidor (nova janela) e espera ate ele responder
rem ------------------------------------------------------------------
:iniciarServidor
echo  [3/4] Abrindo o servidor na janela "Chat Sakura - Servidor"...
del "dados\logs\servidor-falhou.txt" >nul 2>&1
del "dados\logs\servidor-parado.txt" >nul 2>&1
rem Caminho com ".\": funciona mesmo quando o Windows nao procura programas na
rem pasta atual (NoDefaultCurrentDirectoryInExePath).
start "Chat Sakura - Servidor" cmd /c ".\%~nx0 :servidor"
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal aguardar 90
if errorlevel 1 goto :servidorNaoIniciou
set "SERVIDOR_NOSSO=1"
goto :abrirCliente

:jaAberto
echo  [3/4] Ja existe um servidor do Chat Sakura aberto neste computador.
echo        Abrindo mais uma janela de chat (por exemplo, para uma segunda conta).

rem ------------------------------------------------------------------
rem  4. Cliente (nesta janela, para as mensagens ficarem visiveis)
rem ------------------------------------------------------------------
:abrirCliente
echo  [4/4] Abrindo a janela do Chat Sakura...
echo.
echo  Deixe esta janela aberta enquanto usa o chat: ela mostra as mensagens
echo  do programa (copia em dados\logs\cliente.log).
echo.
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal cliente
echo.
echo  A janela do Chat Sakura foi fechada.
if not defined SERVIDOR_NOSSO goto :fimCliente
echo.
echo  O servidor continua aberto na janela "Chat Sakura - Servidor".
choice /c SN /n /m " Deseja encerrar o servidor tambem? Digite S (sim) ou N (nao): "
if errorlevel 2 goto :fimServidorAberto
rem Encerra somente o servidor aberto por este inicializador (PID conferido).
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal parar
echo  Ate a proxima!
goto :fim

:fimServidorAberto
echo  O servidor continua aberto. Para abrir outra janela de chat, execute
echo  INICIAR-CHAT-SAKURA.bat de novo. Para encerrar, feche a janela do servidor.
goto :fim

:fimCliente
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal verificar >nul 2>&1
if errorlevel 1 goto :fim
echo  O servidor continua na janela "Chat Sakura - Servidor".
goto :fim

rem ------------------------------------------------------------------
rem  Janela do servidor (aberta pelo passo 3)
rem ------------------------------------------------------------------
:janelaServidor
title Chat Sakura - Servidor
echo.
echo  SERVIDOR DO CHAT SAKURA (modo local, somente neste computador)
echo  Para encerrar o servidor, feche esta janela.
echo.
"%JAVA%" -cp "%JAR%" br.unicesumar.chat.local.InicioLocal servidor
rem Encerrado a pedido do inicializador: fecha a janela sem pedir tecla.
if exist "dados\logs\servidor-parado.txt" goto :servidorParado
echo.
echo  O servidor foi encerrado. Leia as mensagens acima
echo  (copia em dados\logs\servidor.log).
pause
exit /b 0

:servidorParado
del "dados\logs\servidor-parado.txt" >nul 2>&1
exit /b 0

rem ------------------------------------------------------------------
rem  Problemas (a janela fica aberta para leitura)
rem ------------------------------------------------------------------
:semJava
echo  ERRO: o Java nao foi encontrado neste computador.
echo.
echo  Opcao 1 (mais simples): use o pacote portatil do Chat Sakura, que ja traz
echo          o Java. Veja "Computador sem Java" no README.
echo  Opcao 2: instale o Java 17 ou superior (JDK). Por exemplo:
echo          https://adoptium.net/temurin/releases/?version=17
echo          Durante a instalacao, marque "Set JAVA_HOME" e "Add to PATH".
echo          Depois, feche esta janela e execute este arquivo de novo.
goto :erro

:javaDesconhecido
echo  ERRO: nao foi possivel identificar a versao do Java em:
echo        %JAVA%
echo  Veja dados\logs\java-versao.txt e confira se o Java 17 ou superior esta instalado.
goto :erro

:javaAntigo
echo  ERRO: o Chat Sakura precisa do Java 17 ou superior.
echo        Versao encontrada: %VERSAO%
echo        Local: %JAVA%
echo.
echo  Instale o JDK 17 ou superior (https://adoptium.net/temurin/releases/?version=17)
echo  ou use o pacote portatil, que ja traz o Java. Se o Java novo ja estiver
echo  instalado, a variavel JAVA_HOME pode estar apontando para o antigo.
goto :erro

:pastaErrada
echo  ERRO: este arquivo precisa ficar na pasta do Chat Sakura, junto com o
echo  arquivo pom.xml (codigo-fonte) ou com a pasta runtime (pacote portatil).
echo  Extraia o ZIP inteiro e execute o arquivo de dentro da pasta extraida.
goto :erro

:pastaSemGravacao
echo  ERRO: nao foi possivel criar a pasta "dados" aqui:
echo        %CD%
echo  Provavelmente o arquivo foi aberto de dentro do ZIP, sem extrair, ou a
echo  pasta e somente leitura. Extraia o ZIP (botao direito, "Extrair tudo")
echo  para a Area de Trabalho ou Documentos e execute o arquivo extraido.
goto :erro

:falhaCompilacao
if exist "%JAR%" goto :compilacaoAntiga
echo  ERRO: nao foi possivel preparar o programa. Mensagens do Maven:
echo  -----------------------------------------------------------------
findstr /i /c:"ERROR" /c:"error:" /c:"Exception" "dados\logs\compilacao.log"
echo  -----------------------------------------------------------------
echo  Causas comuns:
echo   - sem internet (ou rede que bloqueia downloads) na primeira execucao;
echo   - Java sem compilador: instale o JDK, nao apenas o JRE;
echo   - outra janela do Chat Sakura aberta usando os arquivos.
echo  Log completo: dados\logs\compilacao.log
echo  Alternativa: use o pacote portatil (veja o README).
goto :erro

:compilacaoAntiga
echo  AVISO: nao foi possivel atualizar o programa (talvez outra janela do
echo  Chat Sakura esteja aberta). Usando a versao ja preparada antes.
echo  Detalhes: dados\logs\compilacao.log
goto :verificarDeNovo

:falhaJava
echo  ERRO: o Java nao conseguiu executar o Chat Sakura (codigo %ESTADO%).
echo  Execute de novo; se continuar, apague a pasta "target" e tente outra vez.
goto :erro

:portaOcupada
echo  ERRO: a porta 12345 esta sendo usada por outro programa.
echo  Pode ser um servidor do Chat Sakura no modo seguro (TLS), aberto pelo
echo  terminal, ou outro aplicativo. Programas usando a porta (PID no final):
echo.
netstat -ano | findstr /r /c:":12345 .*LISTENING"
echo.
echo  Feche esse programa e execute este arquivo de novo. Para descobrir qual
echo  e, abra o Gerenciador de Tarefas, aba "Detalhes", e procure o PID.
echo  Este inicializador nao encerra programas desconhecidos.
goto :erro

:servidorNaoIniciou
echo.
echo  ERRO: o servidor nao ficou pronto.
echo  Leia as mensagens na janela "Chat Sakura - Servidor" (ou em
echo  dados\logs\servidor.log), feche-a e execute este arquivo de novo.
goto :erro

:erro
echo.
pause
exit /b 1

:fim
echo.
pause
exit /b 0
