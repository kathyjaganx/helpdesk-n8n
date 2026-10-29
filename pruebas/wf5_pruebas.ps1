# =====================================================================
# Pruebas del WF5 - API de consulta
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Uso (desde la carpeta del proyecto):
#   $env:HELPDESK_TOKEN = "el-token-configurado-en-n8n"
#   powershell -ExecutionPolicy Bypass -File .\pruebas\wf5_pruebas.ps1
# =====================================================================
param(
    [string]$Url = "http://localhost:5678/webhook/consulta"
)

if (-not $env:HELPDESK_TOKEN) {
    Write-Host "Primero defina el token:  `$env:HELPDESK_TOKEN = 'su-token'" -ForegroundColor Red
    exit 1
}

function Consultar($parametros, $token = $env:HELPDESK_TOKEN) {
    $headers = @{}
    if ($token) { $headers["X-API-Key"] = $token }
    $destino = if ($parametros) { "$($Url)?$parametros" } else { $Url }
    try {
        $r = Invoke-WebRequest -Uri $destino -Method Get -Headers $headers -UseBasicParsing
        $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return @{ Codigo = [int]$r.StatusCode; Texto = $texto; Json = ($texto | ConvertFrom-Json) }
    } catch {
        if ($_.Exception.Response) {
            return @{ Codigo = [int]$_.Exception.Response.StatusCode; Texto = $_.ErrorDetails.Message; Json = $null }
        }
        return @{ Codigo = 0; Texto = $_.Exception.Message; Json = $null }
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

Write-Host "=== Pruebas WF5 - API de consulta ===" -ForegroundColor Cyan

$r = Consultar ""
$n = @($r.Json.tickets).Count
Registrar "1. Listado sin parametros" "200 con tickets y resumen" "$($r.Codigo), $n ticket(s), total $($r.Json.resumen.total)" `
    ($r.Codigo -eq 200 -and $n -gt 0 -and $r.Json.resumen.total -gt 0)

$r = Consultar "limite=3"
$n = @($r.Json.tickets).Count
Registrar "2. Limite de resultados" "200 con 3 tickets como maximo" "$($r.Codigo), $n ticket(s)" ($r.Codigo -eq 200 -and $n -le 3 -and $n -gt 0)

$r = Consultar "codigo=tck-000001"
$t = @($r.Json.tickets)
Registrar "3. Buscar por codigo (en minusculas)" "200 con TCK-000001" "$($r.Codigo), $($t.Count) ticket(s): $($t[0].codigo)" `
    ($r.Codigo -eq 200 -and $t.Count -eq 1 -and $t[0].codigo -eq "TCK-000001")

$r = Consultar "codigo=TCK-999999"
$n = @($r.Json.tickets).Count
Registrar "4. Codigo inexistente" "200 sin tickets" "$($r.Codigo), $n ticket(s)" ($r.Codigo -eq 200 -and $n -eq 0)

$r = Consultar "codigo=TCK-1%27%20OR%201%3D1"
Registrar "5. Intento de inyeccion SQL en el codigo" "400" "$($r.Codigo)" ($r.Codigo -eq 400)

$r = Consultar "limite=5000"
Registrar "6. Limite fuera de rango" "400" "$($r.Codigo)" ($r.Codigo -eq 400)

$r = Consultar "" ""
Registrar "7. Sin token" "403" "$($r.Codigo)" ($r.Codigo -eq 403)

$r = Consultar "limite=200"
$expuesto = ($r.Texto -match "solicitante_correo") -or ($r.Texto -match "@")
Registrar "8. La respuesta no expone correos" "Sin correos en la respuesta" $(if ($expuesto) { "Se encontraron correos" } else { "Sin correos" }) `
    ($r.Codigo -eq 200 -and -not $expuesto)

$pasaron = ($resultados | Where-Object { $_.Resultado -eq "PASO" }).Count
Write-Host ""
Write-Host "=== Resultado: $pasaron de $($resultados.Count) pruebas pasaron ===" -ForegroundColor Cyan

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$archivo = Join-Path $carpeta ("wf5_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv")
$resultados | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
Write-Host "Evidencia guardada en: $archivo"
