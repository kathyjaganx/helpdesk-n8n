#!/bin/bash
# =====================================================================
# Inicialización de PostgreSQL para el Help Desk
# Se ejecuta automáticamente SOLO la primera vez que se crea el volumen.
#
# Seguridad:
#   - Las contraseñas llegan desde variables de entorno (.env), nunca
#     escritas en este archivo.
#   - El usuario de la aplicación (helpdesk_app) tiene privilegios
#     mínimos: no puede borrar tickets ni modificar la auditoría.
# =====================================================================
set -e

echo ">> Creando base de datos '$HELPDESK_DB' y usuario '$HELPDESK_USER'..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_user="$HELPDESK_USER" -v app_pass="$HELPDESK_PASSWORD" -v app_db="$HELPDESK_DB" <<-'EOSQL'
    CREATE USER :"app_user" WITH PASSWORD :'app_pass';
    CREATE DATABASE :"app_db";
    REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
    GRANT CONNECT ON DATABASE :"app_db" TO :"app_user";
EOSQL

echo ">> Creando tablas..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$HELPDESK_DB" \
     -f /docker-entrypoint-initdb.d/sql/schema.sql

echo ">> Asignando privilegios mínimos a '$HELPDESK_USER'..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$HELPDESK_DB" \
     -v app_user="$HELPDESK_USER" <<-'EOSQL'
    REVOKE ALL ON SCHEMA public FROM PUBLIC;
    GRANT USAGE ON SCHEMA public TO :"app_user";

    -- Tickets: crear, leer y actualizar. NUNCA borrar.
    GRANT SELECT, INSERT, UPDATE ON tickets TO :"app_user";

    -- Categorías: solo lectura (las reglas las define el administrador)
    GRANT SELECT ON categorias TO :"app_user";

    -- Auditoría y errores: solo insertar y leer (append-only, no se alteran)
    GRANT SELECT, INSERT ON auditoria, errores TO :"app_user";

    -- Reportes: insertar y leer
    GRANT SELECT, INSERT ON reportes TO :"app_user";

    -- Vista del reporte
    GRANT SELECT ON v_tickets_detalle TO :"app_user";

    -- Secuencias de los ID autoincrementables
    GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO :"app_user";
EOSQL

echo ">> Base de datos del Help Desk lista."
