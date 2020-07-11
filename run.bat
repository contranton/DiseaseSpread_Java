@echo off
set CP=mason.20.jar;.;commons-collections4-4.1.jar
javac -cp %CP% Agent.java Model.java
if %errorlevel% neq 0 goto end
rem                           
 java -cp %CP% Model       STATIC
 java -cp %CP% Model       STATIC_LOW_CONNECTIVITY
 java -cp %CP% Model       STATIC_HIGH_CONNECTIVITY
 java -cp %CP% Model           CITY
 java -cp %CP% Model           CITY_QUARANTINE
 java -cp %CP% Model           CITY_SOCIAL_DISTANCING
 java -cp %CP% Model           CITY_QUARANTINE_MARKET
 java -cp %CP% Model           CITY_MASKS

python graphics.py
:end
echo DONE
pause