# =====================================================================
# Demostración del cliente Java contra el sistema real
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Requiere: los contenedores encendidos y haber ejecutado antes
#           pruebas\java.ps1 (genera cliente-java\target\cliente-helpdesk.jar)
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 consultar
#   powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 buscar TCK-000015
#   powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 registrar "Ana López" "ana@empresa.com" "Ventas" "Monitor sin imagen" "El monitor no muestra nada desde hoy"
# =====================================================================

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$raiz = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $raiz "cliente-java\target"

if (-not (Test-Path (Join-Path $jar "cliente-helpdesk.jar"))) {
    Write-Host "Primero ejecute .\pruebas\java.ps1 para compilar el cliente." -ForegroundColor Red
    exit 1
}

# El token se lee del .env: nunca se escribe en el script
$token = (Select-String -Path (Join-Path $raiz ".env") -Pattern '^HELPDESK_API_TOKEN=(.+)$').Matches[0].Groups[1].Value

# El contenedor se une a la red de Docker del proyecto y llama a n8n por su nombre
docker run --rm --network helpdesk-n8n_default `
    -e HELPDESK_URL=http://n8n:5678/webhook -e HELPDESK_TOKEN=$token `
    -v "${jar}:/app:ro" eclipse-temurin:21-jre `
    java -jar /app/cliente-helpdesk.jar @args
