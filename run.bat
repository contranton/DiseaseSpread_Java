@echo off
javac -cp mason.20.jar;. Agent.java Model.java
if %errorlevel% neq 0 goto end
rem                           m_id NEIGH_RADIUS 
java -cp mason.20.jar;. Model 0       > log0.txt
java -cp mason.20.jar;. Model 1 20    > log1.txt
java -cp mason.20.jar;. Model 2 100   > log2.txt

python graphics.py
:end