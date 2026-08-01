@echo off
set APP_HOME=%~dp0
set WRAPPER_DIR=%APP_HOME%gradle\wrapper
set CLASSPATH=%WRAPPER_DIR%\gradle-wrapper.jar
set JAR_URL=https://services.gradle.org/distributions/gradle-8.13-wrapper.jar

if not exist "%CLASSPATH%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$jar='%CLASSPATH%'; Invoke-WebRequest -UseBasicParsing '%JAR_URL%' -OutFile $jar; $expected=(Invoke-WebRequest -UseBasicParsing '%JAR_URL%.sha256').Content.Trim(); $actual=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLower(); if ($actual -ne $expected.ToLower()) { Remove-Item $jar; throw 'Gradle wrapper checksum verification failed.' }"
  if errorlevel 1 exit /b 1
)

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
