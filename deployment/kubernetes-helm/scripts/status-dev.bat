@echo off
setlocal
set RELEASE_NAME=job-platform

helm status %RELEASE_NAME%
echo.
kubectl get pods,svc,ingress,hpa,pdb -n job-scheduler
echo.
kubectl get pods,svc,pvc -n data
echo.
kubectl get pods,svc,pvc -n observability
endlocal

