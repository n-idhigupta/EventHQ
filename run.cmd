@echo off
setlocal
rem Select an installed Java 17 or 21 without modifying system settings.
rem An explicit EVENTHQ_JAVA_HOME takes precedence over automatic selection.
if defined EVENTHQ_JAVA_HOME goto explicitJava
for /d %%J in ("%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Eclipse Adoptium\jdk-21*") do if exist "%%~fJ\bin\javac.exe" set "JAVA_HOME=%%~fJ"
goto launch
:explicitJava
set "JAVA_HOME=%EVENTHQ_JAVA_HOME%"
:launch
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo A JDK is required. Set EVENTHQ_JAVA_HOME to your Java 17 or 21 JDK directory. 1>&2
  exit /b 1
)
pushd "%~dp0"
if "%~1"=="" (
  call "%~dp0mvnw.cmd" spring-boot:run "-Dspring-boot.run.arguments=--eventhq.seed=true"
) else (
  call "%~dp0mvnw.cmd" %*
)
set "EVENTHQ_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EVENTHQ_EXIT_CODE%
