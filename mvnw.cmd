@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_SAVE_ERRORLEVEL__=
@SET __MVNW_SAVE_LAST_ERRORLEVEL__=
@FOR /F "tokens=1* delims==" %%A IN ('SET') DO @(
  IF "%%A"=="__MVNW_SAVE_LAST_ERRORLEVEL__" SET __MVNW_SAVE_LAST_ERRORLEVEL__=%%B
  IF "%%A"=="__MVNW_SAVE_ERRORLEVEL__" SET __MVNW_SAVE_ERRORLEVEL__=%%B
)
@SET __MVNW_JAVA_HOME__=%JAVA_HOME%

@REM Fallback 1: resolve from registry
@IF "%__MVNW_JAVA_HOME__%"=="" @FOR /F "usebackq skip=2 tokens=2*" %%A IN (
  `reg query "HKLM\SOFTWARE\JavaSoft\JDK" /v CurrentVersion 2^>NUL`
) DO @FOR /F "usebackq skip=2 tokens=2*" %%C IN (
  `reg query "HKLM\SOFTWARE\JavaSoft\JDK\%%B" /v JavaHome 2^>NUL`
) DO @SET __MVNW_JAVA_HOME__=%%D

@REM Fallback 2: known installation path on this machine
@IF "%__MVNW_JAVA_HOME__%"=="" @IF EXIST "C:\Program Files\Java\jdk-21\bin\java.exe" (
  @SET __MVNW_JAVA_HOME__=C:\Program Files\Java\jdk-21
)

@SET MVNW_VERBOSE=false
@IF "%MVNW_VERBOSE%"=="true" (
  @ECHO __MVNW_JAVA_HOME__=%__MVNW_JAVA_HOME__%
)

@SET __MVNW_WRAPPER_PROPERTIES__=%~dp0.mvn\wrapper\maven-wrapper.properties
@SET __MVNW_DISTRIBUTION_URL__=
@SET __MVNW_DISTRIBUTION_TYPE__=
@SET __MVNW_WRAPPER_VERSION__=

@FOR /F "usebackq eol=# tokens=1,2 delims==" %%A IN ("%__MVNW_WRAPPER_PROPERTIES__%") DO @(
  IF "%%A"=="distributionUrl" SET __MVNW_DISTRIBUTION_URL__=%%B
  IF "%%A"=="distributionType" SET __MVNW_DISTRIBUTION_TYPE__=%%B
  IF "%%A"=="wrapperVersion" SET __MVNW_WRAPPER_VERSION__=%%B
)

@IF "%__MVNW_DISTRIBUTION_URL__%"=="" (
  ECHO ERROR: distributionUrl not set in maven-wrapper.properties
  EXIT /B 1
)

@SET __MVNW_DISTRIBUTION_FILENAME__=
@FOR %%F IN ("%__MVNW_DISTRIBUTION_URL__%") DO @SET __MVNW_DISTRIBUTION_FILENAME__=%%~nxF

@SET __MVNW_DISTRIBUTION_BASENAME__=%__MVNW_DISTRIBUTION_FILENAME__:-bin.zip=%
@SET __MVNW_DISTRIBUTION_BASENAME__=%__MVNW_DISTRIBUTION_BASENAME__:.zip=%

@SET __MVNW_USER_HOME__=%USERPROFILE%
@IF "%MAVEN_USER_HOME%"=="" (
  SET __MVNW_MAVEN_HOME__=%__MVNW_USER_HOME__%\.m2\wrapper\dists\%__MVNW_DISTRIBUTION_BASENAME__%
) ELSE (
  SET __MVNW_MAVEN_HOME__=%MAVEN_USER_HOME%\.m2\wrapper\dists\%__MVNW_DISTRIBUTION_BASENAME__%
)

@SET __MVNW_MAVEN_BIN__=%__MVNW_MAVEN_HOME__%\bin\mvn.cmd
@IF EXIST "%__MVNW_MAVEN_BIN__%" GOTO :mvn_exec

@ECHO Downloading Maven %__MVNW_DISTRIBUTION_BASENAME__% ...
@ECHO Distribution URL: %__MVNW_DISTRIBUTION_URL__%
@ECHO Target: %__MVNW_MAVEN_HOME__%

@IF NOT EXIST "%__MVNW_MAVEN_HOME__%" @MKDIR "%__MVNW_MAVEN_HOME__%"

@SET __MVNW_TEMP_ZIP__=%__MVNW_MAVEN_HOME__%\%__MVNW_DISTRIBUTION_FILENAME__%

@POWERSHELL -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%__MVNW_DISTRIBUTION_URL__%' -OutFile '%__MVNW_TEMP_ZIP__%' }" || (
  ECHO ERROR: Failed to download Maven distribution.
  EXIT /B 1
)

@POWERSHELL -Command "& { Expand-Archive -Path '%__MVNW_TEMP_ZIP__%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force }" || (
  ECHO ERROR: Failed to extract Maven distribution.
  EXIT /B 1
)

@DEL /Q "%__MVNW_TEMP_ZIP__%"

@REM Find the actual extracted directory (may have a version suffix)
@FOR /D %%D IN ("%USERPROFILE%\.m2\wrapper\dists\%__MVNW_DISTRIBUTION_BASENAME__%*") DO @(
  SET __MVNW_MAVEN_HOME__=%%D
)
@SET __MVNW_MAVEN_BIN__=%__MVNW_MAVEN_HOME__%\bin\mvn.cmd

:mvn_exec
@IF NOT EXIST "%__MVNW_MAVEN_BIN__%" (
  ECHO ERROR: Maven binary not found at %__MVNW_MAVEN_BIN__%
  EXIT /B 1
)

@IF "%MVNW_VERBOSE%"=="true" ECHO Using Maven at %__MVNW_MAVEN_BIN__%

@SET JAVA_HOME=%__MVNW_JAVA_HOME__%
@"%__MVNW_MAVEN_BIN__%" %*
