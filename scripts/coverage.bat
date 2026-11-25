@echo off
REM Convenience wrapper: run the coverage dashboard (see scripts\coverage.py for all options).
REM Examples:
REM   scripts\coverage.bat               summarise the last JaCoCo run
REM   scripts\coverage.bat --run --html  run tests + write target\site\jacoco-dashboard.html
REM   scripts\coverage.bat --open        open the JaCoCo HTML report
python "%~dp0coverage.py" %*
