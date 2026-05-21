{{/*
Expand the name of the chart.
*/}}
{{- define "balance-dashboard-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "balance-dashboard-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart label.
*/}}
{{- define "balance-dashboard-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels.
*/}}
{{- define "balance-dashboard-service.labels" -}}
helm.sh/chart: {{ include "balance-dashboard-service.chart" . }}
{{ include "balance-dashboard-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: banking-platform
app.kubernetes.io/component: balance-dashboard
{{- end }}

{{/*
Selector labels — used by Deployment, Service, HPA, NetworkPolicy.
*/}}
{{- define "balance-dashboard-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "balance-dashboard-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: balance-dashboard-service
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "balance-dashboard-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "balance-dashboard-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
