# Cliente Java del Help Desk

Módulo Java que usa los workflows de n8n como backend: registra tickets (WF1) y los consulta (WF5), como lo haría otro sistema de la organización que necesite integrarse con la mesa de ayuda.

Su objetivo en el proyecto es aplicar **pruebas unitarias con JUnit 5**, **objetos simulados con Mockito** y **medición de cobertura con JaCoCo**.

## Estructura

```
cliente-java/
├── pom.xml
└── src/
    ├── main/java/gt/edu/umg/helpdesk/
    │   ├── Aplicacion.java              # Línea de comandos (excluida de la cobertura)
    │   ├── modelo/                      # Ticket, SolicitudTicket, Resumen, Prioridad...
    │   ├── excepciones/                 # Una excepción por cada respuesta del sistema
    │   ├── http/
    │   │   ├── TransporteHttp.java      # Interfaz: lo que Mockito simula
    │   │   └── TransporteJavaHttp.java  # Implementación real con java.net.http
    │   └── servicio/
    │       ├── ClienteHelpDesk.java     # Registra, consulta y busca tickets
    │       ├── ValidadorTicket.java     # Mismas reglas que el WF1
    │       └── AnalizadorCola.java      # Pendientes, conteo y siguiente a atender
    └── test/java/gt/edu/umg/helpdesk/   # 70 pruebas
```

## Diseño para poder probar

`ClienteHelpDesk` no usa `java.net.http` directamente: depende de la interfaz `TransporteHttp` (inyección de dependencias). En producción recibe `TransporteJavaHttp`; en las pruebas recibe un **mock de Mockito** que devuelve la respuesta que cada prueba necesita. Así se prueban todas las respuestas de n8n sin tener el sistema encendido:

| Respuesta simulada | Comportamiento esperado |
|---|---|
| 201 | Devuelve el código del ticket |
| 400 | `DatosInvalidosException` con los errores del WF1 |
| 403 | `AccesoDenegadoException` |
| 429 | `LimiteExcedidoException` |
| 500 y otros | `ErrorServidorException` con el código HTTP |
| Red caída / interrupción | `ConexionException` |
| Cuerpo que no es JSON | `HelpDeskException` |

Con `verify` y `verifyNoMoreInteractions` se comprueba además que, si los datos son inválidos, el cliente **no llama** al sistema.

`TransporteJavaHttp` se prueba contra un servidor HTTP local del JDK (`com.sun.net.httpserver`) que comprueba el método, la ruta, el encabezado `X-API-Key` y el cuerpo en UTF-8.

## Pruebas

| Clase de prueba | Herramientas | Pruebas |
|---|---|---|
| `ValidadorTicketTest` | JUnit 5 (`@Nested`, `@ParameterizedTest`) | 20 |
| `ClienteHelpDeskTest` | JUnit 5 + **Mockito** (`mock`, `when`, `verify`, `ArgumentCaptor`) | 27 |
| `TransporteJavaHttpTest` | JUnit 5 + servidor HTTP local | 5 |
| `AnalizadorColaTest` | JUnit 5 | 6 |
| `ModeloTest` | JUnit 5 (`@ParameterizedTest`) | 12 |

## Ejecución

Desde la carpeta del proyecto (no requiere Java ni Maven, usa Docker):

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\java.ps1
```

Ejecuta `mvn verify`, que compila, corre las 70 pruebas, genera el reporte de JaCoCo y **falla si la cobertura baja del 80%** de líneas o de ramas.

Resultados:

- Reporte de pruebas JUnit: `cliente-java/target/surefire-reports/`
- Reporte de cobertura JaCoCo: `cliente-java/target/site/jacoco/index.html`
- Copia del reporte y del resultado como evidencia: `pruebas/resultados/`

Cobertura obtenida: **98.8% de líneas, 98.4% de ramas, 100% de métodos.**

## Uso contra el sistema real

Con los contenedores encendidos y después de ejecutar `java.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 consultar
powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 buscar TCK-000015
```

El script lee el token del `.env` y conecta el contenedor a la red interna de Docker del proyecto.
