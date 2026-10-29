# =====================================================================
# Pruebas del WF4 - Manejo de errores
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Simula una falla real: quita temporalmente al usuario de la aplicación
# el permiso de insertar tickets, envía un ticket válido (el WF1 falla en
# el nodo "Guardar ticket") y verifica que el WF4 registró el error.
# Al final SIEMPRE devuelve el permiso y comprueba que el sistema se
# recupera.
#
# Uso (desde la carpeta del proyecto):
#   $env:HELPDESK_TOKEN = "el-token-configurado-en-n8n"
#   powershell -ExecutionPolicy Bypass -File .\pruebas\wf4_pruebas.ps1
# =====================================================================
param(
    [string]$Url = "http://localhost:5678/webhook/tickets"
)

if (-not $env:HELPDESK_TOKEN) {
    Write-Host "Primero defina el token:  `$env:HELPDESK_TOKEN = 'su-token'" -ForegroundColor Red
    exit 1
}

# Los nombres de usuario se leen del contenedor: no se escriben aquí
$admin = (docker exec helpdesk-postgres printenv POSTGRES_USER).Trim()
$app   = (docker exec helpdesk-postgres printenv HELPDESK_USER).Trim()

function Sql-Admin($consulta) {
    docker exec helpdesk-postgres psql -U $admin -d helpdesk -tA -c $consulta
}
function Sql-App($consulta) {
    docker exec helpdesk-postgres psql -U $app -d helpdesk -tA -F "|" -c $consulta
}
function Enviar-Ticket($asunto) {
    $cuerpo = @{
        solicitante_nombre = "Prueba WF4"
        solicitante_correo = "pruebas.wf4@empresa.com"
        departamento       = "Pruebas"
        asunto             = $asunto
        descripcion        = "Ticket de prueba para el manejo de errores"
    } | ConvertTo-Json -Compress
    try {
        $r = Invoke-WebRequest -Uri $Url -Method Post -Headers @{ "X-API-Key" = $env:HELPDESK_TOKEN } `
             -Body ([System.Text.Encoding]::UTF8.GetBytes($cuerpo)) `
             -ContentType "application/json; charset=utf-8" -UseBasicParsing
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

$resultados = @()
function Registrar($caso, $esperado, $obtenido, $paso) {
    $marca = if ($paso) { "PASO" } else { "FALLO" }
    $color = if ($paso) { "Green" } else { "Red" }
    Write-Host ""
    Write-Host "[$marca] $caso" -ForegroundColor $color
    Write-Host "   Esperado: $esperado"
    Write-Host "   Obtenido: $obtenido"
    $script:resultados += [pscustomobject]@{
        Fecha = (Get-Date -Format "yyyy-MM-dd HH:mm:ss"); Caso = $caso
        Esperado = $esperado; Obtenido = $obtenido; Resultado = $marca
    }
}

Write-Host "=== Pruebas WF4 - Manejo de errores ===" -ForegroundColor Cyan
$antes = [int](Sql-App "SELECT COUNT(*) FROM errores;")
Write-Host "Errores registrados antes de la prueba: $antes"

try {
    Write-Host ""
    Write-Host "Simulando falla: se quita el permiso INSERT sobre tickets a '$app'..." -ForegroundColor Yellow
    Sql-Admin "REVOKE INSERT ON tickets FROM $app;" | Out-Null

    $codigo = Enviar-Ticket "Prueba de error controlado"
    Registrar "1. El WF1 falla y no crea el ticket" "HTTP 500" "HTTP $codigo" ($codigo -ge 500)

    Write-Host ""
    Write-Host "Esperando a que el WF4 registre el error..."
    $despues = $antes
    for ($i = 0; $i -lt 10 -and $despues -le $antes; $i++) {
        Start-Sleep -Seconds 2
        $despues = [int](Sql-App "SELECT COUNT(*) FROM errores;")
    }
    Registrar "2. El WF4 registra el error en la tabla errores" "1 error nuevo" "$($despues - $antes) error(es) nuevo(s)" ($despues -gt $antes)

    $fila = Sql-App "SELECT workflow, nodo, mensaje FROM errores ORDER BY id DESC LIMIT 1;"
    $p = "$fila" -split "\|"
    Registrar "3. El error identifica workflow, nodo y causa" `
        "WF1 - Ingesta de tickets | Guardar ticket | permission denied" `
        "$($p[0]) | $($p[1]) | $($p[2])" `
        ($p[0] -like "WF1*" -and $p[1] -eq "Guardar ticket" -and $p[2] -like "*permission denied*")

    $audit = [int](Sql-App "SELECT COUNT(*) FROM auditoria WHERE workflow = 'WF4_errores' AND fecha > NOW() - INTERVAL '2 minutes';")
    Registrar "4. El error queda en la auditoria" "Al menos 1 registro WF4_errores" "$audit registro(s)" ($audit -ge 1)
}
finally {
    Write-Host ""
    Write-Host "Restaurando el permiso INSERT sobre tickets..." -ForegroundColor Yellow
    Sql-Admin "GRANT INSERT ON tickets TO $app;" | Out-Null
}

$codigo = Enviar-Ticket "Prueba de recuperacion"
Registrar "5. El sistema se recupera al corregir la causa" "HTTP 201" "HTTP $codigo" ($codigo -eq 201)

$pasaron = ($resultados | Where-Object { $_.Resultado -eq "PASO" }).Count
Write-Host ""
Write-Host "=== Resultado: $pasaron de $($resultados.Count) pruebas pasaron ===" -ForegroundColor Cyan

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$archivo = Join-Path $carpeta ("wf4_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv")
$resultados | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
Write-Host "Evidencia guardada en: $archivo"
