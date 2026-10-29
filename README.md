# Help Desk Local con n8n

Sistema de mesa de ayuda que recibe tickets de soporte, los **clasifica automáticamente** por categoría, les **asigna prioridad** y **genera reportes diarios**. Funciona en un entorno 100% local con n8n, Docker y PostgreSQL.

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

En muchas organizaciones los tickets de soporte se clasifican y priorizan a mano, lo que genera retrasos y criterios inconsistentes. Este sistema automatiza ese proceso con cuatro workflows de n8n:

| Workflow | Tipo | Qué hace | Disparador |
|---|---|---|---|
| **WF1 - Ingesta de tickets** | Ingesta | Recibe el ticket, valida y limpia los datos, lo guarda y registra el resultado en la auditoría | Webhook `POST /webhook/tickets` |
| **WF2 - Clasificación y prioridad** | Procesamiento | Clasifica los tickets nuevos por palabras clave y les asigna prioridad | Cada minuto |
| **WF3 - Generación de reportes** | Salida | Genera un CSV con los tickets de las últimas 24 horas y guarda un resumen en la base de datos | Diario a las 18:00 |
| **WF4 - Manejo de errores** | Errores | Registra cualquier falla de los otros workflows | Error Trigger |

### Ciclo de vida de un ticket

```
NUEVO  ──(WF2)──►  CLASIFICADO  ──►  EN_PROCESO  ──►  RESUELTO  ──►  CERRADO
```

### Reglas de clasificación (WF2)

- El texto se normaliza (minúsculas, sin tildes) antes de comparar.
- Cada palabra clave encontrada suma **1 punto**, o **2** si está en el asunto.
- Gana la categoría con más puntos. Si no hay coincidencias, el ticket va a **Otros**.
- La prioridad es la **prioridad base** de la categoría, pero sube a **ALTA** si aparece una palabra de urgencia (*urgente*, *no funciona*, *caído*, *servidor*, *crítico*, etc.).
- El motivo de cada clasificación queda guardado en el ticket.

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
                 POST + X-API-Key
  Solicitante ───────────────────────┐
                                     ▼
 ┌──────────────── Docker (solo 127.0.0.1) ──────────────────┐
 │                                                           │
 │   n8n 2.45.6                                              │
 │   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐   │
 │   │   WF1   │──►│   WF2   │──►│   WF3   │   │   WF4   │   │
 │   │ Ingesta │   │ Proceso │   │ Salida  │   │ Errores │   │
 │   └─────────┘   └─────────┘   └─────────┘   └─────────┘   │
 │                       │                                   │
 │                       ▼  (red interna de Docker)          │
 │   PostgreSQL 16 · base de datos "helpdesk"                │
 │   tickets · categorias · auditoria · errores · reportes   │
 │                                                           │
 └───────────────────────────────────────────────────────────┘
                                     │
                                     ▼
                     Carpeta reportes/ (archivos CSV)
```

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
- **PowerShell 5.1 o superior** para ejecutar los scripts de pruebas (incluido en Windows)
- 2 GB de RAM libres
- Puerto **5678** libre (n8n) y **5432** libre (PostgreSQL)

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

Para generar la llave de cifrado (64 caracteres):

```powershell
-join ((1..32) | % { '{0:x2}' -f (Get-Random -Max 256) })
```

En Linux o Mac: `openssl rand -hex 32`

> Se recomienda usar solo letras y números en las contraseñas. Símbolos como `$`, `'` o `#` pueden romper la lectura del `.env`.

### 4.3 Levantar los contenedores

```powershell
docker compose up -d
docker compose ps
```

Ambos servicios deben aparecer como `Up`, y PostgreSQL además como `(healthy)`.

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
| Value | un token generado con el mismo comando de la llave de cifrado |

Guardar el token en un lugar seguro: lo necesitan los clientes que envían tickets y los scripts de pruebas.

### 5.3 Importar los workflows

Importar los 4 archivos de la carpeta `workflows/` en este orden: **WF4, WF1, WF2, WF3**.

Para cada uno: **Create → Workflow → menú ⋯ → Import from File**.

Después de importar cada workflow:

1. Abrir los nodos Postgres y Webhook y confirmar que tengan asignadas las credenciales `Postgres Help Desk` y `Header Auth Help Desk`.
2. Guardar con **Ctrl + S**.
3. Publicar con el botón **Publish**.

### 5.4 Asignar el workflow de errores

En una instalación nueva los identificadores internos cambian, así que hay que volver a asignar el WF4. En **WF1, WF2 y WF3**:

**Menú ⋯ → Settings → Error Workflow → WF4 - Manejo de errores → Save**

> El WF4 debe estar **publicado**; de lo contrario aparece en gris y no se puede seleccionar.

---

## 6. Uso

### 6.1 Registrar un ticket

**PowerShell:**

```powershell
$token = "su-token"
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

**curl (Linux o Mac):**

```bash
curl -X POST http://localhost:5678/webhook/tickets \
  -H "X-API-Key: su-token" \
  -H "Content-Type: application/json" \
  -d '{"solicitante_nombre":"María García","solicitante_correo":"maria.garcia@empresa.com","departamento":"Contabilidad","asunto":"No tengo internet","descripcion":"Desde esta mañana no hay conexión wifi en mi computadora"}'
```

### 6.2 Campos del ticket

| Campo | Obligatorio | Longitud |
|---|---|---|
| `solicitante_nombre` | Sí | 3 a 100 |
| `solicitante_correo` | Sí, con formato válido | 6 a 150 |
| `departamento` | Sí | 2 a 100 |
| `asunto` | Sí | 5 a 150 |
| `descripcion` | Sí | 10 a 2000 |

Cualquier otro campo enviado se ignora.

### 6.3 Respuestas

| Código | Significado | Ejemplo |
|---|---|---|
| **201** | Ticket creado | `{"ok":true,"mensaje":"Ticket registrado correctamente","codigo":"TCK-000001","estado":"NUEVO"}` |
| **400** | Datos inválidos | `{"ok":false,"mensaje":"Solicitud rechazada","errores":["solicitante_correo no tiene un formato válido"]}` |
| **403** | Token ausente o incorrecto | (sin cuerpo) |
| **500** | Falla interna (queda registrada por el WF4) | `{"message":"Error in workflow"}` |

### 6.4 Consultar los datos

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

Los scripts están en `pruebas/` y guardan sus resultados en `pruebas/resultados/` como evidencia. Antes de ejecutarlos hay que definir el token en la terminal:

```powershell
$env:HELPDESK_TOKEN = "su-token"
```

### WF1 - Ingesta (10 casos)

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\wf1_pruebas.ps1
```

| # | Caso | Esperado |
|---|---|---|
| 1 | Ticket válido | 201 |
| 2 | Código `<script>` en el asunto (se elimina) | 201 |
| 3 | Intento de inyección SQL (se guarda como texto) | 201 |
| 4 | Campos extra no permitidos (se ignoran) | 201 |
| 5 | Correo con formato inválido | 400 |
| 6 | Campos obligatorios faltantes | 400 |
| 7 | Descripción demasiado corta | 400 |
| 8 | Cuerpo que no es un objeto JSON | 400 |
| 9 | Sin token | 403 |
| 10 | Token incorrecto | 403 |

### WF2 - Clasificación (8 casos)

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\wf2_pruebas.ps1
```

Envía tickets de cada categoría y comprueba la clasificación. Incluye tres casos especiales: la palabra *urgente* eleva la prioridad, *Redactar* no se confunde con la categoría *Red*, y el asunto pesa más que la descripción. Tarda de 1 a 2 minutos porque espera al WF2.

### WF3 - Reportes

Ejecutar el WF3 manualmente en n8n y comprobar:

1. El archivo nuevo en `reportes/`.
2. El registro en la tabla `reportes`, cuyo `total_tickets` y resumen deben coincidir con las filas del CSV.

### WF4 - Manejo de errores (5 casos)

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\wf4_pruebas.ps1
```

Provoca una falla real y controlada: quita temporalmente al usuario de la aplicación el permiso de insertar tickets, verifica que el WF1 falla, que el WF4 registra el error con workflow, nodo y causa, y que queda en la auditoría. Al final **siempre** devuelve el permiso y comprueba que el sistema se recupera.

### Resultados obtenidos

| Workflow | Resultado |
|---|---|
| WF1 - Ingesta | 10 de 10 ✅ |
| WF2 - Clasificación | 8 de 8 ✅ |
| WF3 - Reportes | Archivo y resumen consistentes ✅ |
| WF4 - Errores | 5 de 5 ✅ |

---

## 8. Seguridad

| Medida | Dónde | Protege contra |
|---|---|---|
| Credenciales en variables de entorno (`.env`), excluido de Git | `docker-compose.yml`, `.gitignore` | Exposición de contraseñas en el repositorio |
| Credenciales de n8n cifradas con `N8N_ENCRYPTION_KEY` | n8n | Lectura de credenciales desde la base de datos de n8n |
| Webhook protegido con token (Header Auth) | WF1 | Registro de tickets por clientes no autorizados |
| Lista blanca de campos | WF1, nodo *Validar y sanitizar* | Manipulación de campos internos como `estado` o `prioridad` |
| Eliminación de HTML, `<script>` y caracteres de control | WF1 | Inyección de código (XSS) |
| Consultas parametrizadas (`$1, $2...`) | Todos los nodos Postgres | Inyección SQL |
| Restricciones `CHECK` y llaves foráneas | Base de datos | Datos inconsistentes aunque falle la validación del workflow |
| Usuario con privilegios mínimos: no puede borrar tickets ni modificar la auditoría | `db/init/01_init.sh` | Borrado de evidencia o datos en caso de compromiso |
| Neutralización de celdas que empiezan con `= + - @` | WF3, nodo *Armar reporte* | Inyección de fórmulas al abrir el CSV en Excel |
| Puertos publicados solo en `127.0.0.1` | `docker-compose.yml` | Acceso desde otros equipos de la red |
| Escritura de archivos restringida a `reportes/` | `N8N_RESTRICT_FILE_ACCESS_TO` | Escritura de archivos fuera de la carpeta permitida |
| Mensajes de error sin el *stack* técnico completo | WF4 | Exposición de detalles internos |

---

## 9. Estructura del repositorio

```
helpdesk-n8n/
├── docker-compose.yml          # n8n 2.45.6 + PostgreSQL 16
├── .env.example                # Plantilla de variables de entorno
├── .gitignore                  # Excluye .env y los reportes generados
├── .gitattributes              # Mantiene finales de línea LF en scripts .sh
├── README.md
├── db/
│   └── init/
│       ├── 01_init.sh          # Crea la BD, el usuario y asigna privilegios
│       └── sql/
│           └── schema.sql      # Tablas, restricciones, vista y datos iniciales
├── workflows/
│   ├── WF1_ingesta.json
│   ├── WF2_clasificacion.json
│   ├── WF3_reportes.json
│   └── WF4_errores.json
├── pruebas/
│   ├── wf1_pruebas.ps1
│   ├── wf2_pruebas.ps1
│   ├── wf4_pruebas.ps1
│   └── resultados/             # Evidencia de cada ejecución en CSV
├── reportes/                   # Reportes generados por el WF3
└── docs/                       # Documento técnico y diagramas
```

---

## 10. Solución de problemas

Problemas reales encontrados durante el desarrollo y su solución.

| Síntoma | Causa | Solución |
|---|---|---|
| `ports are not available ... 5432 ... forbidden by its access permissions` | Hay un PostgreSQL instalado en Windows usando el puerto 5432 | Detenerlo: `Stop-Service postgresql-x64-17` y `Set-Service postgresql-x64-17 -StartupType Manual` (como administrador). El nombre del servicio se consulta con `Get-Service *postgres*` |
| PostgreSQL queda en `Restarting` y los logs dicen `role names cannot begin with "pg_"` | `POSTGRES_USER` empieza con `pg_`, prefijo reservado | Cambiar el nombre en `.env` y ejecutar `docker compose down -v` y `docker compose up -d` |
| El script `01_init.sh` no se ejecuta | El archivo se guardó con finales de línea CRLF de Windows | El `.gitattributes` lo evita. Si ocurre, guardarlo con LF en VS Code (esquina inferior derecha) |
| `The requested webhook "POST tickets" is not registered` (404) | El WF1 no está publicado | Abrir el WF1 y presionar **Publish** |
| Todas las peticiones responden 403 | El token no coincide con el de la credencial `Header Auth Help Desk` | Revisar el valor de `$env:HELPDESK_TOKEN` y el de la credencial |
| La credencial Postgres dice `Couldn't connect` | Contraseña incorrecta o host `localhost` | Usar host `postgres` y copiar la contraseña con `docker exec helpdesk-postgres printenv HELPDESK_PASSWORD` |
| El WF4 aparece en gris al elegir el Error Workflow | El WF4 no está publicado | Publicar el WF4 primero |
| Una consulta muestra `(END)` y no regresa a la terminal | Paginador de psql | Presionar `q`, o agregar `-P pager=off` |

### Reiniciar todo desde cero

Borra la base de datos y los workflows de n8n:

```powershell
docker compose down -v
docker compose up -d
```

Después hay que repetir la [configuración de n8n](#5-configuración-de-n8n).
