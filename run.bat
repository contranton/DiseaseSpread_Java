@echo off
javac -cp mason.20.jar;. Agent.java Model.java
if %errorlevel%==1 goto end
java -cp mason.20.jar;. Model
python graphics.py
:end