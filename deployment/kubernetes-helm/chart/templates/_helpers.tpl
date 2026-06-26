{{- define "job-platform.labels" -}}
app.kubernetes.io/name: {{ .name | quote }}
app.kubernetes.io/instance: {{ $.Release.Name | quote }}
app.kubernetes.io/part-of: {{ $.Values.global.partOf | quote }}
app.kubernetes.io/managed-by: {{ $.Release.Service | quote }}
helm.sh/chart: {{ printf "%s-%s" $.Chart.Name $.Chart.Version | quote }}
{{- end -}}

{{- define "job-platform.selectorLabels" -}}
app: {{ .name | quote }}
{{- end -}}

{{- define "job-platform.image" -}}
{{- printf "%s:%s" .image .tag -}}
{{- end -}}

{{- define "job-platform.commonSpringEnv" -}}
- name: TZ
  value: {{ $.Values.global.timezone | quote }}
- name: JAVA_TOOL_OPTIONS
  value: {{ printf "-Duser.timezone=%s" $.Values.global.javaTimezone | quote }}
- name: SPRING_PROFILES_ACTIVE
  value: {{ $.Values.global.profile | quote }}
{{- end -}}

{{- define "job-platform.tracingEnv" -}}
- name: MANAGEMENT_TRACING_ENABLED
  value: "true"
- name: MANAGEMENT_ZIPKIN_TRACING_ENDPOINT
  value: {{ printf "http://%s.%s.svc.cluster.local:%v/api/v2/spans" $.Values.observability.tempo.name $.Values.namespaces.observability $.Values.observability.tempo.zipkinPort | quote }}
{{- end -}}

{{- define "job-platform.kafkaAddress" -}}
{{- printf "%s.%s.svc.cluster.local:%v" .Values.data.kafka.name .Values.namespaces.data .Values.data.kafka.clientPort -}}
{{- end -}}

{{- define "job-platform.redisHost" -}}
{{- printf "%s.%s.svc.cluster.local" .Values.data.redis.name .Values.namespaces.data -}}
{{- end -}}

{{- define "job-platform.consumerUrl" -}}
{{- printf "http://%s.%s.svc.cluster.local:%v" .Values.apps.jobConsumer.name .Values.namespaces.app .Values.apps.jobConsumer.port -}}
{{- end -}}

{{- define "job-platform.minioEndpoint" -}}
{{- printf "http://%s.%s.svc.cluster.local:%v" .Values.data.minio.name .Values.namespaces.data .Values.data.minio.apiPort -}}
{{- end -}}

{{- define "job-platform.pvcStorageClass" -}}
{{- if .storageClassName }}
storageClassName: {{ .storageClassName | quote }}
{{- end }}
{{- end -}}

