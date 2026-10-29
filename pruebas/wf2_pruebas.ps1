# =====================================================================
# Pruebas del WF2 - Clasificación y prioridad
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Envía tickets de distintos tipos por el WF1, espera a que el WF2 los
# clasifique (se ejecuta cada minuto) y compara categoría y prioridad
# con lo esperado.
#
# Uso (desde la carpeta del proyecto):
#   $env:HELPDESK_TOKEN = "el-token-configurado-en-n8n"
#   powershell -ExecutionPolicy Bypass -File .\pruebas\wf2_pruebas.ps1
# =====================================================================
param(
    [string]$Url = "http://localhost:5678/webhook/tickets",
    [int]$EsperaMaxima = 150
)

if (-not $env:HELPDESK_TOKEN) {
    Write-Host "Primero defina el token:  `$env:HELPDESK_TOKEN = 'su-token'" -ForegroundColor Red
    exit 1
}

$casos = @(
    @{ Caso = "1. Problema de red";              Asunto = "No tengo internet";        Descripcion = "El wifi de mi computadora no conecta desde hoy";         Categoria = "Red";      Prioridad = "ALTA"  }
    @{ Caso = "2. Problema de acceso";           Asunto = "Olvidé mi contraseña";     Descripcion = "No recuerdo la clave de mi usuario del sistema";        Categoria = "Accesos";  Prioridad = "MEDIA" }
    @{ Caso = "3. Problema de hardware";         Asunto = "Impresora atascada";       Descripcion = "La impresora del segundo nivel no saca las hojas";      Categoria = "Hardware"; Prioridad = "MEDIA" }
    @{ Caso = "4. Solicitud de software";        Asunto = "Necesito instalar Excel";  Descripcion = "Para el reporte del mes necesito ese programa";         Categoria = "Software"; Prioridad = "BAJA"  }
    @{ Caso = "5. Sin coincidencias";            Asunto = "Silla de escritorio";      Descripcion = "Mi silla está rota y necesito que la cambien";         Categoria = "Otros";    Prioridad = "BAJA"  }
    @{ Caso = "6. Urgencia eleva la prioridad";  Asunto = "Cuenta bloqueada";         Descripcion = "Es urgente, no puedo entrar a mi cuenta para facturar"; Categoria = "Accesos";  Prioridad = "ALTA"  }
    @{ Caso = "7. Palabra parecida no confunde"; Asunto = "Redactar informe";         Descripcion = "Necesito ayuda para redactar el informe anual";        Categoria = "Otros";    Prioridad = "BAJA"  }
    @{ Caso = "8. El asunto pesa más";           Asunto = "Impresora no responde";    Descripcion = "Creo que es por el wifi de la oficina";                 Categoria = "Hardware"; Prioridad = "MEDIA" }
)

Write-Host "=== Pruebas WF2 - Clasificación y prioridad ===" -ForegroundColor Cyan
Write-Host "Enviando $($casos.Count) tickets por el WF1..."

$headers = @{ "X-API-Key" = $env:HELPDESK_TOKEN }
foreach ($c in $casos) {
    $cuerpo = @{
        solicitante_nombre = "Prueba WF2"
        solicitante_correo = "pruebas.wf2@empresa.com"
        departamento       = "Pruebas"
        asunto             = $c.Asunto
        descripcion        = $c.Descripcion
    } | ConvertTo-Json -Compress
    try {
        $r = Invoke-RestMethod -Uri $Url -Method Post -Headers $headers `
             -Body ([System.Text.Encoding]::UTF8.GetBytes($cuerpo)) `
             -ContentType "application/json; charset=utf-8"
        $c.Codigo = $r.codigo
        Write-Host "   $($c.Codigo) creado: $($c.Asunto)"
    } catch {
        Write-Host "   No se pudo crear '$($c.Asunto)': $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

$codigos = ($casos | ForEach-Object { "'" + $_.Codigo + "'" }) -join ","
$consulta = "SELECT codigo, COALESCE(categoria,''), COALESCE(prioridad,''), estado FROM v_tickets_detalle WHERE codigo IN ($codigos);"

Write-Host ""
Write-Host "Esperando a que el WF2 los clasifique (máximo $EsperaMaxima segundos)..."
$inicio = Get-Date
do {
    Start-Sleep -Seconds 10
    $filas = docker exec helpdesk-postgres psql -U helpdesk_app -d helpdesk -tA -F "|" -c $consulta
    $pendientes = @($filas | Where-Object { $_ -match "\|NUEVO$" }).Count
    $segundos = [int]((Get-Date) - $inicio).TotalSeconds
    Write-Host "   $segundos s: $pendientes pendientes"
} while ($pendientes -gt 0 -and $segundos -lt $EsperaMaxima)

$tabla = @{}
foreach ($f in $filas) {
    $p = $f -split "\|"
    if ($p.Count -ge 4) { $tabla[$p[0]] = @{ Categoria = $p[1]; Prioridad = $p[2]; Estado = $p[3] } }
}

$resultados = @()
foreach ($c in $casos) {
    $real = $tabla[$c.Codigo]
    $paso = $real -and $real.Estado -eq "CLASIFICADO" -and
            $real.Categoria -eq $c.Categoria -and $real.Prioridad -eq $c.Prioridad
    $marca = if ($paso) { "PASO" } else { "FALLO" }
    $color = if ($paso) { "Green" } else { "Red" }
    Write-Host ""
    Write-Host "[$marca] $($c.Caso) - $($c.Codigo)" -ForegroundColor $color
    Write-Host "   Esperado: $($c.Categoria) / $($c.Prioridad)   Obtenido: $($real.Categoria) / $($real.Prioridad) ($($real.Estado))"

    $resultados += [pscustomobject]@{
        Fecha              = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        Caso               = $c.Caso
        Ticket             = $c.Codigo
        Asunto             = $c.Asunto
        CategoriaEsperada  = $c.Categoria
        CategoriaObtenida  = $real.Categoria
        PrioridadEsperada  = $c.Prioridad
        PrioridadObtenida  = $real.Prioridad
        Resultado          = $marca
    }
}

$pasaron = ($resultados | Where-Object { $_.Resultado -eq "PASO" }).Count
Write-Host ""
Write-Host "=== Resultado: $pasaron de $($resultados.Count) pruebas pasaron ===" -ForegroundColor Cyan

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$archivo = Join-Path $carpeta ("wf2_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv")
$resultados | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
Write-Host "Evidencia guardada en: $archivo"
