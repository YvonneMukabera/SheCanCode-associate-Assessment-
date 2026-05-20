$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$paymentUrl = "$baseUrl/process-payment"
$key = [guid]::NewGuid().ToString()
$body = '{"amount":100,"currency":"FRW"}'
$headers = @{ "Idempotency-Key" = $key }

Write-Host "Testing She Can Code Idempotency Gateway"
Write-Host "URL: $paymentUrl"
Write-Host "Idempotency-Key: $key"
Write-Host ""

try {
    Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/actuator/health" -Method GET | Out-Null
} catch {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $baseUrl -Method GET | Out-Null
    } catch {
        Write-Host "The app is not running on port 8080." -ForegroundColor Yellow
        Write-Host "Start it in another PowerShell window with:"
        Write-Host "  mvn spring-boot:run"
        exit 1
    }
}

Write-Host "First request, expected X-Cache-Hit: false"
$first = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri $paymentUrl `
    -Method POST `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

Write-Host "Status: $($first.StatusCode)"
Write-Host "X-Cache-Hit: $($first.Headers['X-Cache-Hit'])"
Write-Host "Body: $($first.Content)"
Write-Host ""

Write-Host "Second request with the same key, expected X-Cache-Hit: true"
$second = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri $paymentUrl `
    -Method POST `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

Write-Host "Status: $($second.StatusCode)"
Write-Host "X-Cache-Hit: $($second.Headers['X-Cache-Hit'])"
Write-Host "Body: $($second.Content)"
