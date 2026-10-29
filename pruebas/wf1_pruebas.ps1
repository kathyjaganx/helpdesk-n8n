# =====================================================================
# Pruebas del WF1 - Ingesta de tickets
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Uso (desde la carpeta del proyecto):
#   $env:HELPDESK_TOKEN = "el-token-configurado-en-n8n"
#   .\pruebas\wf1_pruebas.ps1
#
# El token se lee de una variable de entorno: nunca se escribe en el script.
# Los resultados se guardan en pruebas\resultados\ como evidencia.
# =====================================================================
param(
    [string]$Url = "http://localhost:5678/webhook/tickets"
)

if (-not $env:HELPDESK_TOKEN) {
    Write-Host "Primero defina el token:  `$env:HELPDESK_TOKEN = 'su-token'" -ForegroundColor Red
    exit 1
}

$resultados = @()

function Probar {
    param(
        [string]$Caso,
        [string]$Cuerpo,
        [int]$Esperado,
        [string]$Token = $env:HELPDESK_TOKEN
    )
    $headers = @{}
    if ($Token) { $headers["X-API-Key"] = $Token }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Cuerpo)

    try {
        $r = Invoke-WebRequest -Uri $Url -Method Post -Headers $headers -Body $bytes `
             -ContentType "application/json; charset=utf-8" -UseBasicParsing
        $codigo = [int]$r.StatusCode
        $respuesta = $r.Content
    } catch {
        if ($_.Exception.Response) {
            $codigo = [int]$_.Exception.Response.StatusCode
            $respuesta = $_.ErrorDetails.Message
        } else {
            $codigo = 0
            $respuesta = $_.Exception.Message
        }
    }

    $paso = ($codigo -eq $Esperado)
    $color = if ($paso) { "Green" } else { "Red" }
    $marca = if ($paso) { "PASO" } else { "FALLO" }
    Write-Host ""
    Write-Host "[$marca] $Caso" -ForegroundColor $color
    Write-Host "   Esperado: $Esperado   Obtenido: $codigo"
    Write-Host "   Respuesta: $respuesta"

    $script:resultados += [pscustomobject]@{
        Fecha     = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        Caso      = $Caso
        Esperado  = $Esperado
        Obtenido  = $codigo
        Resultado = $marca
        Respuesta = $respuesta
    }
}

$valido = @{
    solicitante_nombre = "María García"
    solicitante_correo = "maria.garcia@empresa.com"
    departamento       = "Contabilidad"
    asunto             = "No tengo internet"
    descripcion        = "Desde esta mañana no hay conexión wifi en mi computadora"
}

function Json($tabla) { return ($tabla | ConvertTo-Json -Compress) }
function Copia($cambios) {
    $t = @{}
    foreach ($k in $valido.Keys) { $t[$k] = $valido[$k] }
    foreach ($k in $cambios.Keys) { $t[$k] = $cambios[$k] }
    return $t
}

Write-Host "=== Pruebas WF1 - Ingesta de tickets ===" -ForegroundColor Cyan

# --- Casos válidos ---
Probar -Caso "1. Ticket valido" -Esperado 201 -Cuerpo (Json $valido)

Probar -Caso "2. Codigo HTML/script en el asunto (se limpia y se acepta)" -Esperado 201 `
       -Cuerpo (Json (Copia @{ asunto = "<script>alert('hack')</script>Impresora atascada" }))

Probar -Caso "3. Intento de inyeccion SQL (se guarda como texto)" -Esperado 201 `
       -Cuerpo (Json (Copia @{ descripcion = "Error en sistema'); DROP TABLE tickets;--" }))

Probar -Caso "4. Campo extra no permitido (se ignora)" -Esperado 201 `
       -Cuerpo (Json (Copia @{ prioridad = "ALTA"; estado = "CERRADO" }))

# --- Casos inválidos ---
Probar -Caso "5. Correo con formato invalido" -Esperado 400 `
       -Cuerpo (Json (Copia @{ solicitante_correo = "correo-sin-arroba.com" }))

Probar -Caso "6. Campos obligatorios faltantes" -Esperado 400 `
       -Cuerpo (Json @{ solicitante_nombre = "Pedro" })

Probar -Caso "7. Descripcion demasiado corta" -Esperado 400 `
       -Cuerpo (Json (Copia @{ descripcion = "falla" }))

Probar -Caso "8. Cuerpo que no es un objeto JSON" -Esperado 400 `
       -Cuerpo '["esto","es","una","lista"]'

# --- Control de acceso ---
Probar -Caso "9. Sin token de acceso" -Esperado 403 -Token "" -Cuerpo (Json $valido)

Probar -Caso "10. Token incorrecto" -Esperado 403 -Token "token-falso-123" -Cuerpo (Json $valido)

# --- Resumen ---
$pasaron = ($resultados | Where-Object { $_.Resultado -eq "PASO" }).Count
Write-Host ""
Write-Host "=== Resultado: $pasaron de $($resultados.Count) pruebas pasaron ===" -ForegroundColor Cyan

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$archivo = Join-Path $carpeta ("wf1_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv")
$resultados | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
Write-Host "Evidencia guardada en: $archivo"
