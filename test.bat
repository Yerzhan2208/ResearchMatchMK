@echo off
REM One-shot: run the whole test suite and generate the coverage dashboard.
REM Usage:
REM   test.bat            run tests, print summary, write + open the HTML dashboard
REM   test.bat --no-open  run tests and write the dashboard, but do not open a browser
REM Extra args are passed through to scripts\coverage.py (e.g. --fail-line 100).
setlocal
cd /d "%~dp0"

set "OPEN=--open"
set "ARGS="
:parse
if "%~1"=="" goto run
if /I "%~1"=="--no-open" (
  set "OPEN="
) else (
  set "ARGS=%ARGS% %~1"
)
shift
goto parse

:run
python "%~dp0scripts\coverage.py" --run --html %OPEN% %ARGS%
