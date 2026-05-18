# Observability — Dashboards & Alert Rules

Reference loaded on demand by `banking-devops-platform` skill. Defines the Grafana dashboard layout and Prometheus alert ruleset every banking service ships with. Companion to the Spring-side observability reference (`spring-boot-banking/references/observability.md`).

## Grafana Dashboard (per service)

Every service ships with a dashboard at `https://grafana.<env>.example.com/d/<service>`. The dashboard is committed as JSON under `infra/grafana/<service>.json` and provisioned via the Grafana operator (no clicking in the UI).

### Required Panels

| Panel | What it shows | Sample PromQL |
|---|---|---|
| Request rate (RPS) by endpoint | Traffic shape; spot drops or floods | `sum by (uri) (rate(http_server_requests_seconds_count{service="$service"}[1m]))` |
| Error rate by endpoint | 5xx / total per URI; gate for the HighErrorRate alert | `sum by (uri) (rate(http_server_requests_seconds_count{service="$service",status=~"5.."}[5m])) / sum by (uri) (rate(http_server_requests_seconds_count{service="$service"}[5m]))` |
| Latency p50 / p95 / p99 | Histogram quantiles per endpoint | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{service="$service"}[5m])))` |
| JVM heap | Used vs committed vs max | `jvm_memory_used_bytes{area="heap",service="$service"}` |
| GC pauses | Pause duration over time | `rate(jvm_gc_pause_seconds_sum{service="$service"}[1m]) / rate(jvm_gc_pause_seconds_count{service="$service"}[1m])` |
| DB pool usage | Active / idle / pending Hikari connections | `hikaricp_connections_active{service="$service"}` |
| Slow queries | Count of queries above threshold | `rate(hikaricp_connections_acquire_seconds_count{service="$service"}[5m])` (with histogram for distribution) |
| Kafka consumer lag (if consumer) | Per-partition lag, summed by topic | `sum by (topic) (kafka_consumer_lag{service="$service"})` |
| Business: transfers completed | Counter of successful financial ops | `sum by (currency) (rate(transfers_completed_total{service="$service"}[1m]))` |
| Business: transfer amount sum | Throughput in money terms | `sum by (currency) (rate(transfer_amount_thb_sum{service="$service"}[5m]))` |

### Dashboard Conventions

- **Variables** at the top: `$env`, `$service`, `$instance`. Default to "All" for instance.
- **Time range**: default `Last 1 hour`; alert links open at the firing time ± 30 min.
- **Annotations**: deploy events (from Argo CD / Helm release webhook) overlaid as vertical lines — instantly answers "did this start after the deploy?".
- **Panel links**: error-rate and latency panels link to the matching Tempo trace search (filter by service + status).
- **Owner field**: every dashboard JSON includes `meta.owner=<team-slack-channel>`.

---

## Prometheus Alert Rules

Rules committed under `infra/prometheus/rules/<service>.yaml` and loaded by the Prometheus operator. Severities map to Alertmanager routes: `critical` → PagerDuty; `warning` → Slack.

### Standard Rule Set

```yaml
groups:
- name: <service>.rules
  rules:
  - alert: HighErrorRate
    expr: |
      sum by (service) (rate(http_server_requests_seconds_count{service="<service>",status=~"5.."}[5m]))
        /
      sum by (service) (rate(http_server_requests_seconds_count{service="<service>"}[5m]))
        > 0.01
    for: 5m
    labels: { severity: critical }
    annotations:
      summary: "5xx error rate > 1% on {{ $labels.service }}"
      runbook: "https://runbooks.example.com/{{ $labels.service }}/high-error-rate"

  - alert: HighLatency
    expr: |
      histogram_quantile(0.95,
        sum by (le, service) (rate(http_server_requests_seconds_bucket{service="<service>"}[5m]))
      ) > 1
    for: 5m
    labels: { severity: critical }
    annotations:
      summary: "p95 latency > SLA on {{ $labels.service }}"

  - alert: HighGcPauses
    expr: |
      rate(jvm_gc_pause_seconds_sum{service="<service>"}[1m])
        /
      rate(jvm_gc_pause_seconds_count{service="<service>"}[1m])
        > 0.5
    for: 1m
    labels: { severity: warning }
    annotations:
      summary: "GC pause average > 500ms on {{ $labels.service }}"

  - alert: PodCrashLooping
    expr: |
      increase(kube_pod_container_status_restarts_total{namespace="<ns>",pod=~"<service>-.*"}[10m]) > 3
    for: 1m
    labels: { severity: critical }
    annotations:
      summary: "Pod {{ $labels.pod }} restarted > 3 times in 10m"

  - alert: KafkaLagHigh
    expr: sum by (topic) (kafka_consumer_lag{service="<service>"}) > 10000
    for: 5m
    labels: { severity: warning }
    annotations:
      summary: "Kafka consumer lag > 10k on topic {{ $labels.topic }}"

  - alert: TransfersFailedRatioHigh
    expr: |
      sum(rate(transfers_failed_total{service="<service>"}[5m]))
        /
      sum(rate(transfers_total{service="<service>"}[5m]))
        > 0.05
    for: 5m
    labels: { severity: critical }
    annotations:
      summary: "Transfer failure ratio > 5% on {{ $labels.service }}"
```

### Why each rule exists

- **HighErrorRate** — protects user trust; 1% 5xx for 5 min is well past noise.
- **HighLatency** — SLA defense; p95 chosen over p99 to avoid single-request flap.
- **HighGcPauses** — early warning for heap pressure / wrong GC choice before users see latency.
- **PodCrashLooping** — catches OOMKill / startup-failure storms quickly; pairs with the `kubectl describe` runbook.
- **KafkaLagHigh** — consumer health; lag means downstream eventual consistency is breaking.
- **TransfersFailedRatioHigh** — business-level signal; an outage with green infra metrics is still an outage if money stops moving.

### Alert Rule Conventions

- **Every alert has a `runbook` annotation** pointing to a `<service>/<alert-name>.md` page with: symptom, recent deploys, mitigation steps, escalation path, rollback command.
- **Every alert has a `severity` label** (`critical` or `warning`). No mid-severity. Critical pages on-call; warning notifies the team channel.
- **`for:` is mandatory** — no instantaneous alerts. Minimum `1m` to absorb scrape jitter.
- **Inhibition**: `HighErrorRate` inhibits per-endpoint variants while firing to avoid duplicate pages.
- **Test the rule** with `promtool test rules` in CI before merging.

---

## When to Use This Reference

- Adding a new service and provisioning its dashboard + alerts
- Tuning an alert that flaps or fires late
- Adding a new business metric and surfacing it on the dashboard
- Reviewing an observability PR against the platform standard
- Investigating an incident — match the firing alert back to its dashboard panel and runbook
