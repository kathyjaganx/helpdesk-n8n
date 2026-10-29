-- =====================================================================
-- Help Desk Local con n8n — Esquema de base de datos
-- Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
-- Curso: Análisis de Sistemas II — UMG
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. CATEGORIAS: reglas de clasificación automática.
--    Las palabras clave viven en la BD (no en el código del workflow),
--    así se pueden ajustar sin modificar n8n.
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(50)  NOT NULL UNIQUE,
    descripcion     VARCHAR(200),
    palabras_clave  TEXT[]       NOT NULL DEFAULT '{}',
    prioridad_base  VARCHAR(10)  NOT NULL
                    CHECK (prioridad_base IN ('ALTA', 'MEDIA', 'BAJA')),
    activa          BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- 2. TICKETS: la entidad principal del sistema.
-- ---------------------------------------------------------------------
CREATE TABLE tickets (
    id                   SERIAL PRIMARY KEY,
    codigo               VARCHAR(12) GENERATED ALWAYS AS
                         ('TCK-' || LPAD(id::TEXT, 6, '0')) STORED,
    solicitante_nombre   VARCHAR(100) NOT NULL,
    solicitante_correo   VARCHAR(150) NOT NULL,
    departamento         VARCHAR(100) NOT NULL,
    asunto               VARCHAR(150) NOT NULL,
    descripcion          VARCHAR(2000) NOT NULL,
    categoria_id         INTEGER REFERENCES categorias(id),
    prioridad            VARCHAR(10)
                         CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA')),
    estado               VARCHAR(15) NOT NULL DEFAULT 'NUEVO'
                         CHECK (estado IN ('NUEVO', 'CLASIFICADO',
                                           'EN_PROCESO', 'RESUELTO', 'CERRADO')),
    motivo_clasificacion VARCHAR(300),
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_clasificacion  TIMESTAMPTZ,
    fecha_actualizacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Consistencia: un ticket clasificado debe tener categoría y prioridad
    CONSTRAINT chk_clasificado_completo CHECK (
        estado = 'NUEVO'
        OR (categoria_id IS NOT NULL AND prioridad IS NOT NULL)
    ),
    CONSTRAINT chk_correo_formato CHECK (
        solicitante_correo ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'
    )
);

CREATE INDEX idx_tickets_estado    ON tickets (estado);
CREATE INDEX idx_tickets_prioridad ON tickets (prioridad);
CREATE INDEX idx_tickets_fecha     ON tickets (fecha_creacion);

-- Mantiene fecha_actualizacion al día en cada UPDATE
CREATE OR REPLACE FUNCTION fn_actualizar_fecha()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tickets_fecha
    BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION fn_actualizar_fecha();

-- ---------------------------------------------------------------------
-- 3. AUDITORIA: bitácora de ejecución de todos los workflows.
--    El usuario de la aplicación solo puede INSERTAR (append-only).
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
    id          BIGSERIAL PRIMARY KEY,
    fecha       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    workflow    VARCHAR(60)  NOT NULL,
    accion      VARCHAR(60)  NOT NULL,
    resultado   VARCHAR(10)  NOT NULL
                CHECK (resultado IN ('OK', 'RECHAZADO', 'ERROR')),
    ticket_id   INTEGER REFERENCES tickets(id),
    origen_ip   VARCHAR(45),
    detalle     JSONB
);

CREATE INDEX idx_auditoria_fecha ON auditoria (fecha);

-- ---------------------------------------------------------------------
-- 4. ERRORES: registro del workflow de manejo de errores (Error Trigger).
-- ---------------------------------------------------------------------
CREATE TABLE errores (
    id             BIGSERIAL PRIMARY KEY,
    fecha          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    workflow       VARCHAR(100) NOT NULL,
    nodo           VARCHAR(100),
    mensaje        TEXT NOT NULL,
    ejecucion_id   VARCHAR(50),
    url_ejecucion  VARCHAR(300),
    detalle        JSONB
);

-- ---------------------------------------------------------------------
-- 5. REPORTES: historial de reportes generados por el workflow de salida.
-- ---------------------------------------------------------------------
CREATE TABLE reportes (
    id                SERIAL PRIMARY KEY,
    fecha_generacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    periodo_inicio    TIMESTAMPTZ NOT NULL,
    periodo_fin       TIMESTAMPTZ NOT NULL,
    total_tickets     INTEGER     NOT NULL,
    archivo           VARCHAR(200),
    resumen           JSONB,
    CONSTRAINT chk_periodo CHECK (periodo_fin >= periodo_inicio)
);

-- ---------------------------------------------------------------------
-- 6. DATOS INICIALES: categorías y sus palabras clave
-- ---------------------------------------------------------------------
INSERT INTO categorias (nombre, descripcion, palabras_clave, prioridad_base) VALUES
('Red',      'Conectividad, internet, wifi, VPN',
    ARRAY['internet','wifi','red','conexion','conexión','vpn','cable','router','sin señal','lento'],
    'ALTA'),
('Accesos',  'Usuarios, contraseñas y permisos',
    ARRAY['contraseña','contrasena','usuario','acceso','bloqueado','bloqueada','cuenta','permiso','login','clave'],
    'MEDIA'),
('Hardware', 'Equipos físicos y periféricos',
    ARRAY['impresora','monitor','pantalla','teclado','mouse','computadora','cpu','disco','laptop','no enciende'],
    'MEDIA'),
('Software', 'Programas, instalaciones y licencias',
    ARRAY['instalar','programa','licencia','office','excel','word','actualizacion','actualización','aplicacion','aplicación','sistema'],
    'BAJA'),
('Otros',    'Solicitudes que no coinciden con ninguna categoría',
    ARRAY[]::TEXT[],
    'BAJA');

-- ---------------------------------------------------------------------
-- 7. VISTA para el reporte (simplifica el workflow de salida)
-- ---------------------------------------------------------------------
CREATE VIEW v_tickets_detalle AS
SELECT t.id, t.codigo, t.solicitante_nombre, t.departamento, t.asunto,
       c.nombre AS categoria, t.prioridad, t.estado,
       t.fecha_creacion, t.fecha_clasificacion
FROM tickets t
LEFT JOIN categorias c ON c.id = t.categoria_id;
