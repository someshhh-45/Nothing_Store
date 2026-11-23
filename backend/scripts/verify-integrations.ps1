# Read-only diagnostics for a LOCAL Nothing Market Docker Compose deployment.
# Run from repository root:
# powershell -ExecutionPolicy Bypass -File backend/scripts/verify-integrations.ps1
param(
  [int]$ApiPort = 8090,
  [int]$MetricsPort = 8091,
  [int]$PrometheusPort = 9090,
  [int]$GrafanaPort = 3000
)
$ErrorActionPreference = 'Stop'
$compose = 'backend/compose.yml'
$envFile = 'backend/.env'
$counts = @{ PASS = 0; WARN = 0; FAIL = 0 }
function Report([string]$status,[string]$component,[string]$message) {
  $counts[$status]++
  Write-Host ('[{0}] {1}: {2}' -f $status,$component,$message)
}
function Query-Db([string]$sql) {
  $result = & docker compose -f $compose --env-file $envFile exec -T postgres psql -U nothing -d nothing -tAc $sql 2>&1
  if ($LASTEXITCODE -ne 0) { throw [string]::Join(' ',@($result)) }
  return ([string]::Join(' ',@($result))).Trim()
}
function Request([string]$url) {
  return Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 45
}
try {
  if (!(Test-Path $envFile)) { throw 'Missing backend/.env. Create it from backend/.env.example.' }
  $null = & docker compose -f $compose --env-file $envFile ps --quiet app
  if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is not responding.' }
  Report 'PASS' 'Docker Compose' 'Compose project reachable.'
} catch {
  Report 'FAIL' 'Docker Compose' $_.Exception.Message
  Write-Host 'Cannot continue without the local Docker Compose project.'
  exit 1
}
try {
  $rows=Query-Db 'SELECT count(*) FROM products;'
  $accounts=Query-Db 'SELECT count(*) FROM users;'
  $orders=Query-Db 'SELECT count(*) FROM orders;'
  Report 'PASS' 'PostgreSQL' ("SQL reachable; products={0}, customers={1}, orders={2}" -f $rows,$accounts,$orders)
} catch { Report 'FAIL' 'PostgreSQL' $_.Exception.Message }
try {
  $catalog=@(Request ("http://localhost:{0}/api/store/catalog" -f $ApiPort))
  Report 'PASS' 'Spring Boot API' ("Retail catalog returned {0} products." -f $catalog.Count)
} catch { Report 'FAIL' 'Spring Boot API' $_.Exception.Message }
try {
  $data=Request ("http://localhost:{0}/api/search/products?q=buds" -f $ApiPort)
  $names=@($data.hits.hits | ForEach-Object { $_._source.productName })
  $match=@($names | Where-Object { $_ -match 'earbuds|buds' })
  if ($match.Count -gt 0) {
    Report 'PASS' 'OpenSearch' ("'buds' search matched: {0}" -f ($match -join ', '))
  } else {
    Report 'WARN' 'OpenSearch' ("Search returned {0} hits, but no 'buds' product. Check catalog/index." -f $names.Count)
  }
} catch { Report 'FAIL' 'OpenSearch' $_.Exception.Message }
try {
  $topic=& docker compose -f $compose --env-file $envFile exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list 2>&1
  if ($LASTEXITCODE -ne 0) { throw [string]::Join(' ',@($topic)) }
  if (@($topic) -match '^nothing\.events$') {
    Report 'PASS' 'Apache Kafka' 'Broker reachable; nothing.events topic exists.'
  } else {
    Report 'WARN' 'Apache Kafka' 'Broker reachable, but nothing.events topic not found.'
  }
} catch { Report 'FAIL' 'Apache Kafka' $_.Exception.Message }
try {
  $outbox=Query-Db 'SELECT count(*) FROM nothing_outbox;'
  $published=Query-Db 'SELECT count(*) FROM nothing_outbox WHERE published_at IS NOT NULL;'
  $consumed=Query-Db 'SELECT count(*) FROM nothing_consumed;'
  if ([int]$outbox -eq 0) {
    Report 'WARN' 'Kafka outbox' 'No events recorded yet; place a test order and retry.'
  } elseif ([int]$published -gt 0 -and [int]$consumed -gt 0) {
    Report 'PASS' 'Kafka event pipeline' ("outbox={0}; marked published={1}; processed={2}" -f $outbox,$published,$consumed)
  } else {
    Report 'WARN' 'Kafka event pipeline' ("outbox={0}; marked published={1}; processed={2}. Inspect publisher/consumer logs." -f $outbox,$published,$consumed)
  }
} catch { Report 'WARN' 'Kafka event pipeline' $_.Exception.Message }
try {
  $output=& docker compose -f $compose --env-file $envFile exec -T rabbitmq rabbitmq-diagnostics -q ping 2>&1
  if ($LASTEXITCODE -ne 0) { throw [string]::Join(' ',@($output)) }
  Report 'PASS' 'RabbitMQ' 'Broker responded to diagnostics ping. Notification delivery still needs a test order.'
} catch { Report 'FAIL' 'RabbitMQ' $_.Exception.Message }
try {
  $metrics=Invoke-WebRequest -Uri ("http://localhost:{0}/actuator/prometheus" -f $MetricsPort) -UseBasicParsing -TimeoutSec 15
  if ($metrics.StatusCode -eq 200 -and $metrics.Content -match 'jvm_') {
    Report 'PASS' 'Spring metrics' '/actuator/prometheus returned JVM metrics.'
  } else { Report 'WARN' 'Spring metrics' 'Endpoint reachable but metrics were unexpected.' }
} catch { Report 'FAIL' 'Spring metrics' $_.Exception.Message }
try {
  $targets=Request ("http://localhost:{0}/api/v1/targets" -f $PrometheusPort)
  $active=@($targets.data.activeTargets | Where-Object { $_.labels.job -eq 'nothing-backend' })
  if (@($active | Where-Object { $_.health -eq 'up' }).Count -gt 0) {
    Report 'PASS' 'Prometheus' 'nothing-backend scraping target UP.'
  } else {
    Report 'WARN' 'Prometheus' 'No UP target for nothing-backend; inspect Prometheus targets.'
  }
} catch { Report 'FAIL' 'Prometheus' $_.Exception.Message }
try {
  $health=Request ("http://localhost:{0}/api/health" -f $GrafanaPort)
  if ($health.database -eq 'ok') {
    Report 'PASS' 'Grafana' 'Grafana HTTP API and internal database healthy. Dashboard panels still require visual validation.'
  } else { Report 'WARN' 'Grafana' 'API answered, internal database health not confirmed.' }
} catch { Report 'FAIL' 'Grafana' $_.Exception.Message }
if (Get-Command terraform -ErrorAction SilentlyContinue) {
  Report 'PASS' 'Terraform CLI' 'Installed. Run terraform init and terraform validate in backend/infrastructure/terraform.'
} else {
  Report 'WARN' 'Terraform CLI' 'Not installed locally; files exist but cannot be validated on this PC.'
}
Report 'WARN' 'Aiven cloud' 'Not tested: this script targets LOCAL Docker services, not managed Aiven endpoints.'
Write-Host ''
Write-Host ('Summary: {0} passed, {1} warnings, {2} failed.' -f $counts.PASS,$counts.WARN,$counts.FAIL)
Write-Host 'No PostgreSQL data or Docker volumes were deliberately changed; the first OpenSearch query may initialize its search index.'
if ($counts.FAIL -gt 0) { exit 1 }
