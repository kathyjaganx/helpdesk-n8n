# =====================================================================
# Pruebas del cliente Java: JUnit 5 + Mockito, cobertura con JaCoCo
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Compila el módulo cliente-java\ y ejecuta "mvn verify" dentro de un
# contenedor de Maven con Java 21, así que NO hace falta instalar Java ni
# Maven. Las dependencias se guardan en el volumen "helpdesk-maven" para
# que la siguiente ejecución sea rápida.
#
# Uso (desde la carpeta del proyecto):
#   powershell -ExecutionPolicy Bypass -File .\pruebas\java.ps1
# =====================================================================

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$raiz = Split-Path -Parent $PSScriptRoot
$modulo = Join-Path $raiz "cliente-java"
$imagen = "maven:3.9.9-eclipse-temurin-21"

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$sello = Get-Date -Format "yyyyMMdd_HHmmss"
$archivo = Join-Path $carpeta ("java_" + $sello + ".txt")

# Descargar la imagen antes, para que su aviso no se mezcle con el resultado
docker image inspect $imagen *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Descargando la imagen $imagen (solo la primera vez)..." -ForegroundColor Yellow
    docker pull -q $imagen | Out-Null
}

Write-Host "=== Cliente Java: JUnit 5 + Mockito + JaCoCo ===" -ForegroundColor Cyan
Write-Host "La primera vez Maven descarga las dependencias y tarda unos minutos."
Write-Host ""

docker run --rm -v "${modulo}:/app" -v helpdesk-maven:/root/.m2 -w /app $imagen `
    mvn -B -ntp verify 2>&1 | Tee-Object -FilePath $archivo
$codigo = $LASTEXITCODE

# Resumen de cobertura leído del CSV que genera JaCoCo
$csv = Join-Path $modulo "target\site\jacoco\jacoco.csv"
if (Test-Path $csv) {
    $filas = Import-Csv $csv
    $suma = { param($c) ($filas | Measure-Object -Property $c -Sum).Sum }
    $pct = { param($ok, $no) if (($ok + $no) -eq 0) { 100 } else { [math]::Round(100 * $ok / ($ok + $no), 1) } }
    $lineas = & $pct (& $suma "LINE_COVERED") (& $suma "LINE_MISSED")
    $ramas = & $pct (& $suma "BRANCH_COVERED") (& $suma "BRANCH_MISSED")
    $metodos = & $pct (& $suma "METHOD_COVERED") (& $suma "METHOD_MISSED")

    $resumen = @(
        "",
        "=== Cobertura JaCoCo (mínimo exigido: 80% de líneas y de ramas) ===",
        "   Líneas:  $lineas %",
        "   Ramas:   $ramas %",
        "   Métodos: $metodos %"
    )
    $resumen | ForEach-Object { Write-Host $_ -ForegroundColor Cyan }
    $resumen | Out-File -FilePath $archivo -Append -Encoding UTF8

    # Copia del reporte HTML de JaCoCo como evidencia
    $destino = Join-Path $carpeta ("jacoco_" + $sello)
    Copy-Item -Recurse -Force (Join-Path $modulo "target\site\jacoco") $destino
    Write-Host "Reporte HTML de cobertura: $destino\index.html"
}

Write-Host ""
if ($codigo -eq 0) {
    Write-Host "=== BUILD SUCCESS: todas las pruebas pasaron y la cobertura cumple el mínimo ===" -ForegroundColor Green
} else {
    Write-Host "=== Fallaron pruebas o la cobertura no alcanzó el mínimo: revise el detalle arriba ===" -ForegroundColor Red
}
Write-Host "Evidencia guardada en: $archivo"
exit $codigo
