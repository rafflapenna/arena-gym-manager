@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Apache Maven Wrapper startup batch script, version 3.3.2

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE (SET "BASE_DIR=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%BASE_DIR%

@SET WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

@IF NOT EXIST %WRAPPER_PROPERTIES% (
  ECHO Cannot find "%WRAPPER_PROPERTIES%"
  EXIT /B 1
)

@FOR /F "usebackq tokens=1,* delims==" %%A IN (%WRAPPER_PROPERTIES%) DO (
  IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
)

@IF "%JAVA_HOME%"=="" (
  SET JAVACMD=java.exe
) ELSE (
  SET JAVACMD=%JAVA_HOME%\bin\java.exe
)

@FOR /F "tokens=*" %%i IN ('"%JAVACMD%" -version 2^>^&1') DO (
  SET JAVA_VERSION_OUTPUT=%%i
  GOTO :break_java_check
)
:break_java_check

@SET M2_HOME=%USERPROFILE%\.m2\wrapper\dists
@SET DISTRIBUTION_ID=%DISTRIBUTION_URL:https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/=%
@SET DISTRIBUTION_ID=%DISTRIBUTION_ID:-bin.zip=%

@SET MAVEN_HOME=%M2_HOME%\apache-maven-%DISTRIBUTION_ID%

@IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  ECHO Downloading Maven %DISTRIBUTION_ID%...
  POWERSHELL -Command "& { Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TEMP%\maven.zip' }"
  POWERSHELL -Command "& { Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%M2_HOME%' -Force }"
  REN "%M2_HOME%\apache-maven-%DISTRIBUTION_ID%" "apache-maven-%DISTRIBUTION_ID%"
  ECHO Maven %DISTRIBUTION_ID% installed.
)

@SET "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"
@IF NOT EXIST "%MAVEN_BIN%" (
  ECHO Maven executable not found: %MAVEN_BIN%
  EXIT /B 1
)

@"%MAVEN_BIN%" -f "%MAVEN_PROJECTBASEDIR%\pom.xml" %*
