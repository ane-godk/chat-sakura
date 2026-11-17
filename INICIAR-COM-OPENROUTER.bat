@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem ==================================================================
rem  Chat Sakura - inicia com a Sakura IA pelo OpenRouter (Qwen pela
rem  internet, sem precisar do Ollama neste computador).
rem
rem  Pede a chave do OpenRouter SEM mostra-la na tela e a guarda somente
rem  na memoria desta janela (e das janelas abertas a partir dela). Nada
rem  e gravado em arquivo, no registro do Windows, no banco ou nos logs:
rem  ao fechar as janelas, a chave some. Em seguida chama o
rem  INICIAR-CHAT-SAKURA.bat de sempre.
rem
rem  Se OPENROUTER_API_KEY (ou OPENROUTER_MODEL) ja estiver definida no
rem  Windows, ela e usada e a pergunta correspondente e pulada.
rem ==================================================================
cd /d "%~dp0"
title Chat Sakura - OpenRouter
if not exist "INICIAR-CHAT-SAKURA.bat" goto :semInicializador

echo.
echo  =====================================================
echo    CHAT SAKURA - Sakura IA pelo OpenRouter
echo  =====================================================
echo.
if defined OPENROUTER_API_KEY goto :jaTemChave

echo  Cole a chave do OpenRouter (comeca com sk-or-) e tecle Enter.
echo  Para colar: clique com o botao direito dentro desta janela.
echo  A chave NAO aparece na tela e NAO e gravada em nenhum arquivo.
echo.
for /f "usebackq delims=" %%K in (`powershell -NoProfile -Command "$s = Read-Host ' Chave' -AsSecureString; $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }"`) do set "OPENROUTER_API_KEY=%%K"
if not defined OPENROUTER_API_KEY goto :semChave
goto :conferirChave

:jaTemChave
echo  Usando a chave OPENROUTER_API_KEY ja definida no Windows.

:conferirChave
if /i not "%OPENROUTER_API_KEY:~0,6%"=="sk-or-" echo  AVISO: a chave nao comeca com sk-or-. Confira se copiou a chave inteira.

if defined OPENROUTER_MODEL goto :jaTemModelo
echo.
echo  Modelo (opcional). Tecle Enter para o servidor procurar sozinho um Qwen
echo  gratuito (qwen/...:free) na lista do OpenRouter. Ou digite o
echo  identificador exato do modelo, copiado de https://openrouter.ai/models?q=qwen
set /p "OPENROUTER_MODEL= Modelo: "
goto :iniciar

:jaTemModelo
echo  Modelo: %OPENROUTER_MODEL%

:iniciar
echo.
echo  Se um servidor do Chat Sakura ja estiver aberto, feche-o antes: a chave
echo  so vale para um servidor iniciado a partir desta janela.
echo.
call "INICIAR-CHAT-SAKURA.bat"
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
