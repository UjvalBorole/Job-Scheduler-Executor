@echo off
setlocal
set RELEASE_NAME=job-platform

echo Uninstalling %RELEASE_NAME%
helm uninstall %RELEASE_NAME%

echo.
echo Namespaces and PVCs are intentionally left for review/data safety.
echo To remove PVCs manually, inspect them first:
echo kubectl get pvc -A
endlocal

