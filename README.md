# Help Desk Local con n8n

Sistema de mesa de ayuda que recibe tickets de soporte, los **clasifica automáticamente** por categoría, les **asigna prioridad** y **genera reportes diarios**. Los workflows de n8n funcionan como **backend**, una **página web** como **frontend** y un **cliente Java** permite que otros sistemas se integren, todo en un entorno 100% local con Docker.

| | |
|---|---|
| **Autora** | Irma Yaritza Katiuxca Jagan Xol |
| **Carné** | 4090-23-9748 |
| **Curso** | Análisis de Sistemas II (037) |
| **Docente** | Ing. Edgar Raúl Molina Rey |
| **Universidad** | Universidad Mariano Gálvez de Guatemala |
| **Modalidad** | Individual |

---

## Contenido

1. [Descripción del sistema](#1-descripción-del-sistema)
2. [Arquitectura](#2-arquitectura)
3. [Requisitos](#3-requisitos)
4. [Instalación](#4-instalación)
5. [Configuración de n8n](#5-configuración-de-n8n)
6. [Uso](#6-uso)
7. [Pruebas](#7-pruebas)
8. [Seguridad](#8-seguridad)
9. [Estructura del repositorio](#9-estructura-del-repositorio)
10. [Solución de problemas](#10-solución-de-problemas)

---

## 1. Descripción del sistema

En muchas organizaciones los tickets de soporte se clasifican y priorizan a mano, lo que genera retrasos y criterios inconsistentes. Este sistema automatiza el proceso completo.

### Componentes

| Componente | Función |
|---|---|
| **Página web** (`http://localhost:8080`) | Formulario para reportar problemas y cola de tickets que se actualiza sola |
| **nginx** | Sirve la página y reenvía las peticiones a n8n agregando el token del lado del servidor |
| **n8n** (5 workflows) | Toda la lógica del sistema: recibir, validar, clasificar, reportar y registrar errores |
| **PostgreSQL** | Guarda tickets, categorías, auditoría, errores y reportes |
| **Cliente Java** (`cliente-java/`) | Librería y línea de comandos para que otros sistemas registren y consulten tickets. Probada con JUnit 5, Mockito y JaCoCo |

### Workflows (backend)

| Workflow | Tipo | Qué hace | Disparador |
|---|---|---|---|
| **WF1 - Ingesta de tickets** | Ingesta | Recibe el ticket, valida y limpia los datos, lo guarda y registra el resultado en la auditoría | Webhook `POST /webhook/tickets` |
| **WF2 - Clasificación y prioridad** | Procesamiento | Clasifica los tickets nuevos por palabras clave y les asigna prioridad | Cada minuto |
| **WF3 - Generación de reportes** | Salida | Genera un CSV con los tickets de las últimas 24 horas y guarda un resumen en la base de datos | Diario a las 18:00 |
| **WF4 - Manejo de errores** | Errores | Registra cualquier falla de los otros workflows | Error Trigger |
| **WF5 - API de consulta** | Salida | Devuelve los tickets y el resumen que muestra la página web | Webhook `GET /webhook/consulta` |

### Ciclo de vida de un ticket

```
NUEVO  ──(WF2)──►  CLASIFICADO  ──►  EN_PROCESO  ──►  RESUELTO  ──►  CERRADO
```

### Reglas de clasificación (WF2)

- El texto se normaliza (minúsculas, sin tildes) antes de comparar.
- Cada palabra clave encontrada suma **1 punto**, o **2** si está en el asunto.
- Gana la categoría con más puntos. Si no hay coincidencias, el ticket va a **Otros**.
- La prioridad es la **prioridad base** de la categoría, pero sube a **ALTA** si aparece una palabra de urgencia (*urgente*, *no funciona*, *caído*, *servidor*, *crítico*, etc.).
- El motivo de cada clasificación queda guardado en el ticket y se puede ver en la página.

| Categoría | Prioridad base | Ejemplos de palabras clave |
|---|---|---|
| Red | ALTA | internet, wifi, conexión, vpn, router |
| Accesos | MEDIA | contraseña, usuario, bloqueado, cuenta, permiso |
| Hardware | MEDIA | impresora, monitor, teclado, computadora, laptop |
| Software | BAJA | instalar, programa, licencia, office, excel |
| Otros | BAJA | (sin coincidencias) |

Las palabras clave están en la tabla `categorias`, no en el código, así que se pueden ajustar sin modificar los workflows.

---

## 2. Arquitectura

```
                    Navegador
                        │  http://localhost:8080
                        ▼
 ┌──────────────── Docker (solo 127.0.0.1) ─────────────────────┐
 │                                                              │
 │   nginx 1.27 (frontend)                                      │
 │   · sirve la página web                                      │
 │   · /api/tickets  ──► agrega X-API-Key ──►  WF1              │
 │   · /api/consulta ──► agrega X-API-Key ──►  WF5              │
 │                        │                                     │
 │                        ▼                                     │
 │   n8n 2.45.6 (backend)                                       │
 │   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐               │
 │   │ WF1  │ │ WF2  │ │ WF3  │ │ WF4  │ │ WF5  │               │
 │   └──────┘ └──────┘ └──────┘ └──────┘ └──────┘               │
 │                        │  (red interna de Docker)            │
 │                        ▼                                     │
 │   PostgreSQL 16 · base de datos "helpdesk"                   │
 │   tickets · categorias · auditoria · errores · reportes      │
 │                                                              │
 └──────────────────────────────────────────────────────────────┘
                        │
                        ▼
              Carpeta reportes/ (archivos CSV)
```

Los diagramas de arquitectura y UML (casos de uso, clases, secuencia, actividades y estados) están en la carpeta [`docs/`](docs/).

### Modelo de datos

| Tabla | Propósito |
|---|---|
| `categorias` | Categorías, palabras clave y prioridad base |
| `tickets` | Tickets con su estado, categoría, prioridad y motivo de clasificación |
| `auditoria` | Bitácora de todas las acciones de los workflows (solo inserción) |
| `errores` | Fallas registradas por el WF4 |
| `reportes` | Historial de reportes generados con su resumen |
| `v_tickets_detalle` | Vista de tickets con el nombre de su categoría |

El esquema completo está en [`db/init/sql/schema.sql`](db/init/sql/schema.sql).

### Nodos de n8n utilizados (9 tipos)

Webhook, Code, IF, Postgres, Respond to Webhook, Schedule Trigger, Convert to File, Read/Write Files from Disk y Error Trigger.

---

## 3. Requisitos

- **Docker Desktop** (Windows o Mac) o Docker Engine con Docker Compose (Linux)
- **Git**
- **PowerShell 5.1 o superior** para los scripts de pruebas (incluido en Windows)
- 2 GB de RAM libres
- Puertos libres: **8080** (página web), **5678** (n8n) y **5432** (PostgreSQL)

No hace falta instalar Node.js, Java ni Maven: todas las pruebas corren dentro de contenedores de Docker.

---

## 4. Instalación

### 4.1 Clonar el repositorio

```powershell
git clone https://github.com/kathyjaganx/helpdesk-n8n.git
cd helpdesk-n8n
```

### 4.2 Crear el archivo de variables de entorno

```powershell
Copy-Item .env.example .env
```

En Linux o Mac: `cp .env.example .env`

Abrir `.env` y cambiar **todos** los valores `CAMBIAR_...`:

| Variable | Descripción |
|---|---|
| `POSTGRES_USER` | Administrador de PostgreSQL. **No puede empezar con `pg_`** |
| `POSTGRES_PASSWORD` | Contraseña del administrador |
| `HELPDESK_USER` | Usuario de la aplicación (privilegios mínimos) |
| `HELPDESK_PASSWORD` | Contraseña del usuario de la aplicación |
| `N8N_ENCRYPTION_KEY` | Llave con la que n8n cifra sus credenciales |
| `HELPDESK_API_TOKEN` | Token de acceso a los webhooks. nginx lo agrega a las peticiones de la página |

Para generar la llave de cifrado y el token (64 caracteres cada uno) se usa el generador criptográfico de Windows. Ejecutar el comando **dos veces**, una para cada valor:

```powershell
$b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ($b | ForEach-Object { $_.ToString('x2') }) -join ''
```

En Linux o Mac: `openssl rand -hex 32`

> Se recomienda usar solo letras y números en las contraseñas. Símbolos como `$`, `'` o `#` pueden romper la lectura del `.env`.

### 4.3 Levantar los contenedores

```powershell
docker compose up -d
docker compose ps
```

Deben aparecer **3 contenedores** como `Up`: `helpdesk-postgres` (además `healthy`), `helpdesk-n8n` y `helpdesk-web`.

### 4.4 Verificar la base de datos

```powershell
docker logs helpdesk-postgres 2>&1 | Select-String ">>"
```

Deben aparecer 4 líneas, terminando en `>> Base de datos del Help Desk lista.`

---

## 5. Configuración de n8n

### 5.1 Crear la cuenta

Abrir http://localhost:5678 y crear la cuenta de propietario.

### 5.2 Crear las credenciales

**Overview → Create → Credential**

**Postgres** (nombre: `Postgres Help Desk`)

| Campo | Valor |
|---|---|
| Host | `postgres` (nombre del servicio en Docker, no `localhost`) |
| Database | `helpdesk` |
| User | el valor de `HELPDESK_USER` |
| Password | el valor de `HELPDESK_PASSWORD` |
| Port | `5432` |
| SSL | Disable |

**Header Auth** (nombre: `Header Auth Help Desk`)

| Campo | Valor |
|---|---|
| Name | `X-API-Key` |
| Value | **el mismo valor** de `HELPDESK_API_TOKEN` del `.env` |

Para copiar el token del `.env` al portapapeles sin mostrarlo en pantalla:

```powershell
(Select-String -Path .env -Pattern '^HELPDESK_API_TOKEN=(.+)$').Matches[0].Groups[1].Value | Set-Clipboard
```

### 5.3 Importar los workflows

Importar los 5 archivos de la carpeta `workflows/` en este orden: **WF4, WF1, WF2, WF3, WF5**.

Para cada uno: **Create → Workflow → menú ⋯ → Import from File**.

Después de importar cada workflow:

1. Abrir los nodos Postgres y Webhook y confirmar que tengan asignadas las credenciales `Postgres Help Desk` y `Header Auth Help Desk`.
2. Guardar con **Ctrl + S**.
3. Publicar con el botón **Publish**.

### 5.4 Asignar el workflow de errores

En una instalación nueva los identificadores internos cambian, así que hay que volver a asignar el WF4. En **WF1, WF2, WF3 y WF5**:

**Menú ⋯ → Settings → Error Workflow → WF4 - Manejo de errores → Save**

> El WF4 debe estar **publicado**; de lo contrario aparece en gris y no se puede seleccionar.

---

## 6. Uso

### 6.1 Página web

Abrir **http://localhost:8080**, o desde la terminal: `Start-Process http://localhost:8080`

- **Reportar un problema:** llenar el formulario y presionar **Enviar ticket**. El código del ticket (`TCK-000015`) aparece sellado en el talón. Si hay errores, se muestran debajo de cada campo.
- **Tickets recientes:** la lista se actualiza sola cada 15 segundos, y cada 5 segundos justo después de enviar un ticket, para ver cómo pasa de *Nuevo* a *Clasificado*.
- **Filtros y búsqueda:** por prioridad, por estado o por código.
- **Motivo de la clasificación:** al hacer clic en un código se ve por qué se clasificó así.

En la esquina superior derecha, un **punto verde** indica que la página está conectada con n8n.

La página se adapta a celular y al modo oscuro del sistema.

### 6.2 API (para integraciones o pruebas)

La página usa estas dos rutas a través de nginx, que agrega el token. Para llamar a n8n directamente hay que enviar el encabezado `X-API-Key`.

| Ruta en nginx | Webhook en n8n | Método | Workflow |
|---|---|---|---|
| `/api/tickets` | `/webhook/tickets` | POST | WF1 - registrar ticket |
| `/api/consulta` | `/webhook/consulta` | GET | WF5 - consultar tickets |

**Registrar un ticket** (PowerShell, directo a n8n):

```powershell
$token = (Select-String -Path .env -Pattern '^HELPDESK_API_TOKEN=(.+)$').Matches[0].Groups[1].Value
$ticket = @{
    solicitante_nombre = "María García"
    solicitante_correo = "maria.garcia@empresa.com"
    departamento       = "Contabilidad"
    asunto             = "No tengo internet"
    descripcion        = "Desde esta mañana no hay conexión wifi en mi computadora"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:5678/webhook/tickets" -Method Post `
    -Headers @{ "X-API-Key" = $token } `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($ticket)) `
    -ContentType "application/json; charset=utf-8"
```

**Campos del ticket:**

| Campo | Obligatorio | Longitud |
|---|---|---|
| `solicitante_nombre` | Sí | 3 a 100 |
| `solicitante_correo` | Sí, con formato válido | 6 a 150 |
| `departamento` | Sí | 2 a 100 |
| `asunto` | Sí | 5 a 150 |
| `descripcion` | Sí | 10 a 2000 |

Cualquier otro campo enviado se ignora.

**Consultar tickets:**

| Parámetro | Ejemplo | Descripción |
|---|---|---|
| (ninguno) | `/webhook/consulta` | Últimos 50 tickets y resumen |
| `codigo` | `?codigo=TCK-000001` | Un ticket específico |
| `limite` | `?limite=20` | Cantidad de tickets, de 1 a 200 |

La respuesta **no incluye el correo** de los solicitantes.

**Respuestas:**

| Código | Significado |
|---|---|
| **200 / 201** | Consulta correcta / ticket creado |
| **400** | Datos o parámetros inválidos; la respuesta lista los motivos |
| **403** | Token ausente o incorrecto, o método no permitido en nginx |
| **404** | Ruta inexistente |
| **413** | Cuerpo de la petición mayor a 16 KB (nginx) |
| **429** | Más de 10 tickets por minuto desde el mismo origen (nginx) |
| **500** | Falla interna; queda registrada por el WF4 |

### 6.3 Cliente Java

Con los contenedores encendidos y después de compilar el cliente (`pruebas\java.ps1`, sección 7.2):

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 consultar
powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 buscar TCK-000015
powershell -ExecutionPolicy Bypass -File .\pruebas\java_demo.ps1 registrar "Ana López" "ana@empresa.com" "Ventas" "Monitor sin imagen" "El monitor no muestra nada desde hoy"
```

`consultar` muestra los últimos tickets, el conteo por prioridad y el **siguiente ticket a atender** (mayor prioridad y, en empate, el más antiguo). El script lee el token del `.env` y conecta el contenedor a la red interna de Docker del proyecto. Detalles del diseño en [`cliente-java/README.md`](cliente-java/README.md).

### 6.4 Consultar los datos directamente

```powershell
# Tickets con su clasificación
docker exec -it helpdesk-postgres psql -P pager=off -U helpdesk_app -d helpdesk -c "SELECT codigo, asunto, categoria, prioridad, estado FROM v_tickets_detalle ORDER BY id;"

# Bitácora de auditoría
docker exec -it helpdesk-postgres psql -P pager=off -U helpdesk_app -d helpdesk -c "SELECT id, fecha, workflow, accion, resultado, ticket_id FROM auditoria ORDER BY id DESC LIMIT 20;"

# Errores registrados
docker exec -it helpdesk-postgres psql -P pager=off -U helpdesk_app -d helpdesk -c "SELECT id, fecha, workflow, nodo, mensaje FROM errores ORDER BY id;"

# Historial de reportes
docker exec -it helpdesk-postgres psql -P pager=off -U helpdesk_app -d helpdesk -c "SELECT id, archivo, total_tickets, resumen FROM reportes ORDER BY id;"
```

Si se usó otro nombre en `HELPDESK_USER`, reemplazar `helpdesk_app` en estos comandos.

### 6.5 Reportes

El WF3 genera cada día a las 18:00 un archivo en la carpeta `reportes/` con el nombre `reporte_tickets_AAAAMMDD_HHMM.csv`. Para generar uno en cualquier momento: abrir el WF3 en n8n y presionar **Execute workflow**.

---

## 7. Pruebas

El proyecto tiene tres grupos de pruebas. Todos los scripts guardan su resultado en `pruebas/resultados/` como evidencia.

| Grupo | Herramientas | Pruebas |
|---|---|---|
| Unitarias de los workflows, frontend y nginx | Node.js `node:test` | 93 |
| Unitarias del cliente Java | **JUnit 5 + Mockito**, cobertura con **JaCoCo** | 71 |
| Integración contra el sistema en funcionamiento | PowerShell | 31 |

### 7.1 Pruebas unitarias de los workflows (93 pruebas)

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\unitarias.ps1
```

Corren con el *test runner* nativo de Node.js dentro de Docker (imagen `node:22-alpine`), así que **no hace falta instalar Node.js**. El proyecto se monta en **solo lectura**.

Las pruebas **no copian** el código de los nodos: lo leen directamente de los workflows exportados en `workflows/`. Así prueban exactamente lo que corre en n8n. Las categorías se leen de `schema.sql`.

| Archivo | Qué prueba | Pruebas |
|---|---|---|
| `wf1_validacion.test.js` | Validación y limpieza: datos válidos e inválidos, `<script>`, HTML, caracteres de control, lista blanca | 19 |
| `wf2_clasificacion.test.js` | Las 5 categorías, tildes, palabras completas, peso del asunto, empates, urgencia y motivo | 15 |
| `wf3_reporte.test.js` | Periodo, CSV, comillas, BOM, reporte vacío e inyección de fórmulas | 12 |
| `wf4_errores.test.js` | Fallas de ejecución y de disparador, recorte del mensaje, sin *stack* técnico | 5 |
| `wf5_consulta.test.js` | Códigos y límites válidos e inválidos, intento de inyección | 14 |
| `seguridad_proyecto.test.js` | SQL parametrizado en los 5 workflows, webhooks con token, Error Workflow asignado, sin secretos en los JSON, frontend sin `innerHTML` ni token, configuración de nginx, `.env` fuera de Git | 28 |

> La primera vez, Windows PowerShell puede mostrar en rojo *"Unable to find image 'node:22-alpine' locally"*. Es solo un aviso: Docker descarga la imagen y continúa. Para evitarlo: `docker pull node:22-alpine`.

### 7.2 Pruebas unitarias del cliente Java: JUnit 5, Mockito y JaCoCo (71 pruebas)

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\java.ps1
```

Ejecuta `mvn verify` dentro de un contenedor de Maven con Java 21 (`maven:3.9.9-eclipse-temurin-21`); **no hace falta instalar Java ni Maven**. Las dependencias se guardan en el volumen `helpdesk-maven`, así que la primera ejecución tarda unos minutos y las siguientes unos 25 segundos.

| Herramienta | Uso en el proyecto |
|---|---|
| **JUnit 5** | 71 pruebas organizadas con `@Nested`, `@ParameterizedTest` y `@DisplayName` en español |
| **Mockito** | Simula la interfaz `TransporteHttp`, es decir, las respuestas de n8n (201, 400, 403, 429, 500, red caída), sin necesitar el sistema encendido. Con `verify` y `verifyNoMoreInteractions` comprueba que, si los datos son inválidos, el cliente no llama al sistema; con `ArgumentCaptor` revisa el JSON que se envía |
| **JaCoCo** | Mide la cobertura y **hace fallar la compilación** si baja del 80% de líneas o de ramas |

| Clase de prueba | Qué prueba | Pruebas |
|---|---|---|
| `ValidadorTicketTest` | Mismas reglas de validación que el WF1, códigos de ticket e intentos de inyección | 20 |
| `ClienteHelpDeskTest` | Registrar, consultar y buscar con **Mockito**: cada respuesta posible del sistema | 27 |
| `TransporteJavaHttpTest` | Cliente HTTP real contra un servidor local del JDK: método, ruta, token, UTF-8 y HTTP/1.1 | 6 |
| `AnalizadorColaTest` | Pendientes, conteo por prioridad y siguiente ticket a atender | 6 |
| `ModeloTest` | Prioridades, inmutabilidad y excepciones | 12 |

**Cobertura obtenida: 98.9% de líneas, 98.4% de ramas y 100% de métodos** (mínimo exigido: 80%).

Resultados:

- Reporte de pruebas JUnit: `cliente-java/target/surefire-reports/`
- Reporte de cobertura: `cliente-java/target/site/jacoco/index.html`
- Copia del reporte HTML de JaCoCo y del resultado como evidencia: `pruebas/resultados/`

### 7.3 Pruebas de integración (contra el sistema en funcionamiento)

Antes de ejecutarlas, cargar el token del `.env` en la terminal:

```powershell
$env:HELPDESK_TOKEN = (Select-String -Path .env -Pattern '^HELPDESK_API_TOKEN=(.+)$').Matches[0].Groups[1].Value
```

| Script | Qué prueba | Casos |
|---|---|---|
| `wf1_pruebas.ps1` | Entradas válidas e inválidas, `<script>`, inyección SQL, campos extra, sin token y token incorrecto | 10 |
| `wf2_pruebas.ps1` | Clasificación real de tickets de cada categoría, urgencia, palabra parecida y peso del asunto. Tarda de 1 a 2 minutos | 8 |
| `wf4_pruebas.ps1` | Falla real y controlada (quita un permiso de la BD), registro del error y recuperación | 5 |
| `wf5_pruebas.ps1` | Listado, límite, búsqueda por código, código inexistente, inyección, sin token y que no exponga correos | 8 |

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\wf1_pruebas.ps1
```

(igual para `wf2`, `wf4` y `wf5`)

**Cliente Java:** `java_demo.ps1 consultar` contra el sistema real (sección 6.3).

**WF3 y página web:** se verifican manualmente. Ejecutar el WF3 en n8n y comprobar que el archivo de `reportes/` coincide con el registro de la tabla `reportes`. En la página, enviar un ticket y comprobar que pasa de *Nuevo* a *Clasificado* sin recargar.

### 7.4 Resultados obtenidos

| Prueba | Resultado |
|---|---|
| Unitarias de workflows, frontend y nginx | 93 de 93 ✅ |
| Unitarias del cliente Java (JUnit 5 + Mockito) | 71 de 71 ✅ |
| Cobertura del cliente Java (JaCoCo) | 98.9% líneas, 98.4% ramas, 100% métodos ✅ |
| WF1 - Ingesta | 10 de 10 ✅ |
| WF2 - Clasificación | 8 de 8 ✅ |
| WF3 - Reportes | Archivo y resumen consistentes ✅ |
| WF4 - Errores | 5 de 5 ✅ |
| WF5 - Consulta | 8 de 8 ✅ |
| Página web de punta a punta | Ticket creado y clasificado como Red / Alta sin recargar ✅ |
| Cliente Java contra el sistema real | Consulta de 15 tickets y siguiente a atender correcto ✅ |

---

## 8. Seguridad

| Capa | Medida | Dónde | Protege contra |
|---|---|---|---|
| Credenciales | Contraseñas y token en `.env`, excluido de Git | `docker-compose.yml`, `.gitignore` | Exposición en el repositorio |
| Credenciales | Llaves y token generados con el generador criptográfico del sistema | Instalación | Tokens predecibles |
| Credenciales | Credenciales de n8n cifradas con `N8N_ENCRYPTION_KEY`; los JSON exportados solo guardan su nombre | n8n | Lectura de credenciales desde la BD o los workflows |
| Acceso | Webhooks protegidos con token (Header Auth) | WF1, WF5 | Uso por clientes no autorizados |
| Acceso | nginx agrega el token del lado del servidor; el navegador nunca lo ve y no puede reemplazarlo | `nginx/default.conf.template` | Robo del token desde la página |
| Acceso | Cada ruta acepta solo su método; cualquier otra ruta de `/api/` responde 404 | nginx | Uso indebido de la API |
| Abuso | Límite de 10 tickets por minuto y cuerpos de máximo 16 KB | nginx | Saturación del sistema |
| Entrada | Lista blanca de campos | WF1, WF5 | Manipulación de campos internos |
| Entrada | Eliminación de HTML, `<script>` y caracteres de control | WF1 | Inyección de código (XSS) |
| Base de datos | Consultas parametrizadas (`$1, $2...`) | Todos los nodos Postgres | Inyección SQL |
| Base de datos | Usuario con privilegios mínimos: no puede borrar tickets ni modificar la auditoría | `db/init/01_init.sh` | Borrado de evidencia o datos |
| Base de datos | Restricciones `CHECK` y llaves foráneas | `schema.sql` | Datos inconsistentes |
| Privacidad | La API de consulta no devuelve correos | WF5 | Exposición de datos personales |
| Navegador | Política de seguridad de contenido (CSP), `X-Frame-Options`, `nosniff`, sin *referrer* | nginx | XSS, *clickjacking*, detección de tipos |
| Navegador | Todos los datos se muestran con `textContent`, nunca como HTML | `frontend/app.js` | XSS almacenado |
| Navegador | Fuentes servidas desde el proyecto, sin recursos externos | `frontend/fuentes/` | Dependencia y rastreo de terceros |
| Salida | Neutralización de celdas que empiezan con `= + - @` | WF3 | Inyección de fórmulas en Excel |
| Red | Puertos publicados solo en `127.0.0.1` | `docker-compose.yml` | Acceso desde otros equipos |
| Archivos | Escritura restringida a `reportes/` | `N8N_RESTRICT_FILE_ACCESS_TO` | Escritura fuera de la carpeta permitida |
| Errores | Registro sin el *stack* técnico completo | WF4 | Exposición de detalles internos |
| Integración | El cliente Java nunca incluye el token en el código: lo recibe por variable de entorno, leída del `.env` | `cliente-java/`, `java_demo.ps1` | Exposición del token en el código fuente |
| Verificación | 28 pruebas automatizadas revisan estas medidas en cada ejecución | `pruebas/unitarias/` | Que un cambio futuro las rompa sin notarlo |

---

## 9. Estructura del repositorio

```
helpdesk-n8n/
├── docker-compose.yml          # PostgreSQL 16 + n8n 2.45.6 + nginx 1.27
├── .env.example                # Plantilla de variables de entorno
├── .gitignore                  # Excluye .env y los reportes generados
├── .gitattributes              # Mantiene finales de línea LF en scripts .sh
├── README.md
├── db/
│   └── init/
│       ├── 01_init.sh          # Crea la BD, el usuario y asigna privilegios
│       └── sql/
│           └── schema.sql      # Tablas, restricciones, vista y datos iniciales
├── workflows/                  # Backend: exportaciones de n8n
│   ├── WF1_ingesta.json
│   ├── WF2_clasificacion.json
│   ├── WF3_reportes.json
│   ├── WF4_errores.json
│   └── WF5_consulta.json
├── frontend/                   # Página web
│   ├── index.html
│   ├── app.css
│   ├── app.js
│   └── fuentes/                # Fraunces y Figtree (SIL OFL 1.1)
├── nginx/
│   └── default.conf.template   # Servidor web y proxy seguro hacia n8n
├── cliente-java/               # Cliente Java (Maven)
│   ├── pom.xml                 # JUnit 5, Mockito, JaCoCo (mínimo 80%)
│   └── src/
│       ├── main/java/          # Modelo, excepciones, HTTP y servicios
│       └── test/java/          # 71 pruebas
├── pruebas/
│   ├── unitarias.ps1           # Pruebas de workflows en Docker (Node.js)
│   ├── unitarias/              # 93 pruebas con node:test
│   ├── java.ps1                # Pruebas del cliente Java en Docker (Maven)
│   ├── java_demo.ps1           # Cliente Java contra el sistema real
│   ├── wf1_pruebas.ps1         # Pruebas de integración
│   ├── wf2_pruebas.ps1
│   ├── wf4_pruebas.ps1
│   ├── wf5_pruebas.ps1
│   └── resultados/             # Evidencia de cada ejecución
├── reportes/                   # Reportes CSV generados por el WF3
└── docs/                       # Diagramas de arquitectura y UML
```

---

## 10. Solución de problemas

Problemas reales encontrados durante el desarrollo y su solución.

| Síntoma | Causa | Solución |
|---|---|---|
| `ports are not available ... 5432 ... forbidden by its access permissions` | Hay un PostgreSQL instalado en Windows usando el puerto 5432 | Detenerlo como administrador: `Stop-Service postgresql-x64-17` y `Set-Service postgresql-x64-17 -StartupType Manual`. El nombre se consulta con `Get-Service *postgres*` |
| PostgreSQL queda en `Restarting` y los logs dicen `role names cannot begin with "pg_"` | `POSTGRES_USER` empieza con `pg_` | Cambiar el nombre en `.env` y ejecutar `docker compose down -v` y `docker compose up -d` |
| El script `01_init.sh` no se ejecuta | Se guardó con finales de línea CRLF de Windows | El `.gitattributes` lo evita. Si ocurre, guardarlo con LF en VS Code (esquina inferior derecha) |
| `The requested webhook "POST tickets" is not registered` (404) | El workflow no está publicado | Abrirlo en n8n y presionar **Publish** |
| Las pruebas o la página responden 403 | El token no coincide con el de la credencial `Header Auth Help Desk` | El valor de `HELPDESK_API_TOKEN` en `.env` y el de la credencial deben ser idénticos. Después de cambiar el `.env`: `docker compose up -d` |
| La página muestra un punto rojo: *"El sistema no respondió"* | n8n se está reiniciando o el workflow no está publicado | Esperar unos 20 segundos; la página reintenta sola. Si persiste, revisar que WF1 y WF5 estén publicados |
| La página muestra *"Sin conexión con el servidor"* | El contenedor `helpdesk-web` no está encendido | `docker compose up -d` y `docker compose ps` |
| La credencial Postgres dice `Couldn't connect` | Contraseña incorrecta o host `localhost` | Usar host `postgres` y copiar la contraseña con `docker exec helpdesk-postgres printenv HELPDESK_PASSWORD` |
| El WF4 aparece en gris al elegir el Error Workflow | El WF4 no está publicado | Publicar el WF4 primero |
| Aviso rojo *"Unable to find image 'node:22-alpine' locally"* | La imagen de Node.js aún no está descargada | Es solo un aviso. Para evitarlo: `docker pull node:22-alpine` |
| El cliente Java responde *"request timed out"* contra n8n | El cliente HTTP de Java intenta pasar a HTTP/2 con `Upgrade: h2c`, y n8n usa ese mecanismo para sus WebSockets internos y no responde | Ya corregido: el cliente fuerza HTTP/1.1, y la prueba `usa HTTP/1.1` de `TransporteJavaHttpTest` impide que vuelva a ocurrir |
| `java_demo.ps1` dice *"Primero ejecute .\pruebas\java.ps1"* | Todavía no se compiló el cliente | Ejecutar `pruebas\java.ps1`, que genera `cliente-java/target/cliente-helpdesk.jar` |
| JaCoCo detiene la compilación con *"Coverage checks have not been met"* | La cobertura bajó del 80% | Agregar pruebas para el código nuevo; el reporte HTML muestra en rojo las líneas sin cubrir |
| `git push` falla con `HTTP 408` | Conexión lenta al subir imágenes | `git config http.postBuffer 524288000` y repetir `git push` |
| Una consulta muestra `(END)` y no regresa a la terminal | Paginador de psql | Presionar `q`, o agregar `-P pager=off` |

### Reiniciar todo desde cero

Borra la base de datos y los workflows de n8n:

```powershell
docker compose down -v
docker compose up -d
```

Después hay que repetir la [configuración de n8n](#5-configuración-de-n8n).
