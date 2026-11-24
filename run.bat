@echo off
REM Build first with `mvn clean package -DskipTests`, then run this script.
if not exist "target\researchmatch.jar" (
    echo target\researchmatch.jar not found. Run: mvn clean package -DskipTests
    exit /b 1
)
java -jar target/researchmatch.jar
