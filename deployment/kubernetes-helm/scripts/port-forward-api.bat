@echo off
setlocal
echo Forwarding API Gateway to http://127.0.0.1:8091
kubectl port-forward svc/api-gateway 8091:8091 -n job-scheduler
endlocal

