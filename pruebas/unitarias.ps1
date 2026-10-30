# =====================================================================
# Pruebas unitarias del Help Desk
# Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
#
# Ejecuta las pruebas de pruebas\unitarias\ dentro de un contenedor de
# Node.js, así que NO hace falta instalar Node.js en la computadora.
# Las pruebas leen el código de los nodos directamente de los workflows
# exportados (carpeta workflows\), es decir, prueban lo que corre en n8n.
#
# Uso (desde la carpeta del proyecto):
#   powershell -ExecutionPolicy Bypass -File .\pruebas\unitarias.ps1
# =====================================================================

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$raiz = Split-Path -Parent $PSScriptRoot

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$archivo = Join-Path $carpeta ("unitarias_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".txt")

Write-Host "=== Pruebas unitarias (Node.js test runner en Docker) ===" -ForegroundColor Cyan
Write-Host ""

# La carpeta del proyecto se monta en modo solo lectura (:ro):
# las pruebas no pueden modificar ningún archivo.
docker run --rm -e NO_COLOR=1 -v "${raiz}:/app:ro" -w /app node:22-alpine `
    node --test --test-reporter=spec "pruebas/unitarias/*.test.js" 2>&1 |
    Tee-Object -FilePath $archivo
$codigo = $LASTEXITCODE

Write-Host ""
if ($codigo -eq 0) {
    Write-Host "=== Todas las pruebas unitarias pasaron ===" -ForegroundColor Green
} else {
    Write-Host "=== Hay pruebas que fallaron: revise las marcadas con una X ===" -ForegroundColor Red
}
Write-Host "Evidencia guardada en: $archivo"
exit $codigo
