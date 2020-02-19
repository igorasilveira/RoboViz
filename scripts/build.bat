cd ..

set BIN=bin\

IF EXIST %BIN% GOTO COMPILE
mkdir %BIN%

:COMPILE
call gradlew.bat clean shadowJar

copy viewer\build\libs\RoboViz.jar %BIN%\
copy viewer\config.txt %BIN%\
copy scripts\roboviz.sh %BIN%\
copy scripts\config.sh %BIN%\
copy scripts\roboviz.bat %BIN%\
copy scripts\config.bat %BIN%\
copy LICENSE.md %BIN%\
copy NOTICE.md %BIN%\
copy CHANGELOG.md %BIN%\

gradlew.bat clean
pause
