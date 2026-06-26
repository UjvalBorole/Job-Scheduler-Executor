@echo off
setlocal
echo Forwarding Grafana to http://127.0.0.1:3000
kubectl port-forward svc/grafana 3000:3000 -n observability
endlocal

