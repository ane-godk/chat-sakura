@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem ==================================================================
rem  Chat Sakura - inicia com a Sakura IA pela Groq (Qwen pela internet,
rem  sem precisar do Ollama neste computador e sem cartao de credito).
rem
rem  Pede a chave da Groq SEM mostra-la na tela e a guarda somente na
rem  memoria desta janela (e das janelas abertas a partir dela). Nada e
rem  gravado em arquivo, no registro do Windows, no banco ou nos logs:
rem  ao fechar as janelas, a chave some. Em seguida chama o
rem  INICIAR-CHAT-SAKURA.bat de sempre.
rem
rem  CHAT_SAKURA_IA=groq (somente nesta janela) faz o servidor usar a Groq
rem  mesmo que exista uma chave antiga do OpenRouter no Windows.
rem  Se GROQ_API_KEY (ou GROQ_MODEL) ja estiver definida no Windows, ela e
rem  usada e a pergunta correspondente e pulada.
rem ==================================================================
cd /d "%~dp0"
title Chat Sakura - Groq
if not exist "INICIAR-CHAT-SAKURA.bat" goto :semInicializador

echo.
echo  =====================================================
echo    CHAT SAKURA - Sakura IA pela Groq
echo  =====================================================
echo.
set "CHAT_SAKURA_IA=groq"
set "OPENROUTER_API_KEY="
set "OPENROUTER_MODEL="
if defined GROQ_API_KEY goto :jaTemChave

echo  Cole a chave da Groq (comeca com gsk_) e tecle Enter.
echo  Para colar: clique com o botao direito dentro desta janela.
echo  A chave NAO aparece na tela e NAO e gravada em nenhum arquivo.
echo.
for /f "usebackq delims=" %%K in (`powershell -NoProfile -Command "$s = Read-Host ' Chave' -AsSecureString; $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }"`) do set "GROQ_API_KEY=%%K"
if not defined GROQ_API_KEY goto :semChave
goto :conferirChave

:jaTemChave
echo  Usando a chave GROQ_API_KEY ja definida no Windows.

:conferirChave
if /i not "%GROQ_API_KEY:~0,4%"=="gsk_" echo  AVISO: a chave nao comeca com gsk_. Confira se copiou a chave inteira.

echo.
if defined GROQ_MODEL goto :jaTemModelo
echo  Modelo: qwen/qwen3.8-27b - padrao. Para trocar, defina GROQ_MODEL.
goto :iniciar

:jaTemModelo
echo  Modelo: %GROQ_MODEL%

:iniciar
echo.
echo  Se um servidor do Chat Sakura ja estiver aberto, feche-o antes: a chave
echo  so vale para um servidor iniciado a partir desta janela.
echo.
rem Caminho com ".\": funciona mesmo quando o Windows nao procura programas na
rem pasta atual (NoDefaultCurrentDirectoryInExePath).
call ".\INICIAR-CHAT-SAKURA.bat"
exit /b %errorlevel%

:semInicializador
echo  ERRO: este arquivo precisa ficar na mesma pasta do INICIAR-CHAT-SAKURA.bat.
echo.
pause
exit /b 1

:semChave
echo.
echo  Nenhuma chave informada. Para usar sem IA pela internet, execute o
echo  INICIAR-CHAT-SAKURA.bat (o chat funciona normalmente sem a IA).
echo.
pause
exit /b 1
