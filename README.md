# Help Desk Local con n8n

Sistema de mesa de ayuda que recibe tickets, los **clasifica automáticamente** por categoría, les **asigna prioridad** y **genera reportes**, todo en un entorno 100% local con n8n, Docker y PostgreSQL.

**Autora:** Irma Yaritza Katiuxca Jagan Xol, carné 4090-23-9748
**Curso:** Análisis de Sistemas II (037), Universidad Mariano Gálvez de Guatemala
**Docente:** Ing. Edgar Raúl Molina Rey

> Estado: en construcción (Avance 1, diseño). Este README se completará con uso y pruebas.

## Requisitos

- Docker Desktop (Windows/Mac) o Docker Engine + Docker Compose (Linux)
- Git
- 2 GB de RAM libres

## Instalación

```bash
git clone <url-del-repositorio>
cd helpdesk-n8n
cp .env.example .env        # en PowerShell: Copy-Item .env.example .env
```

Editar `.env` y cambiar todos los valores `CAMBIAR_...`. Luego:

```bash
docker compose up -d
docker compose ps           # ambos servicios deben aparecer "running"/"healthy"
```

Abrir http://localhost:5678 y crear la cuenta de propietario de n8n.

### Fijar la versión de n8n (reproducibilidad)

```bash
docker exec helpdesk-n8n n8n --version
```

Reemplazar `stable` en `docker-compose.yml` por la versión obtenida, por ejemplo `docker.n8n.io/n8nio/n8n:2.x.y`.

## Estructura del repositorio

```
helpdesk-n8n/
├── docker-compose.yml     # n8n + PostgreSQL
├── .env.example           # plantilla de credenciales (el .env real no se sube)
├── db/init/
│   ├── 01_init.sh         # crea BD, usuario con privilegios mínimos y tablas
│   └── sql/schema.sql     # modelo de datos
├── workflows/             # exportaciones JSON de n8n
├── docs/                  # documento técnico y diagramas
└── reportes/              # reportes CSV generados por el sistema
```

## Reiniciar la base de datos desde cero

El script de inicialización solo corre la primera vez. Para volver a ejecutarlo (se pierden los datos):

```bash
docker compose down -v
docker compose up -d
```
