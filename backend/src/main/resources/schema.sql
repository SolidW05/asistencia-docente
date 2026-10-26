-- Esquema inicial vacío; se ejecuta una sola vez desde DatabaseInitializationConfig.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE teachers (
    id uuid PRIMARY KEY,
    code varchar(7) NOT NULL UNIQUE CHECK (code ~ '^[0-9]{7}$'),
    name varchar(160) NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('TEACHER', 'JEFE', 'ADMIN'))
);

CREATE TABLE campuses (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    active boolean NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE geofences (
    id uuid PRIMARY KEY,
    campus_id uuid NOT NULL REFERENCES campuses (id),
    name varchar(120) NOT NULL,
    type varchar(16) NOT NULL CHECK (type IN ('CAMPUS', 'MODULE')),
    parent_id uuid REFERENCES geofences (id),
    geometry_json text NOT NULL,
    geometry geometry(Polygon, 4326) NOT NULL,
    active boolean NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT geofence_parent_type CHECK (
        (type = 'CAMPUS' AND parent_id IS NULL)
        OR (type = 'MODULE' AND parent_id IS NOT NULL)
    )
);

CREATE INDEX geofences_geometry_gist
    ON geofences USING gist (geometry);

CREATE INDEX geofences_campus_type_idx
    ON geofences (campus_id, type, active);

CREATE UNIQUE INDEX geofences_one_campus_polygon_unique
    ON geofences (campus_id)
    WHERE type = 'CAMPUS';

CREATE FUNCTION geofence_geometry_from_geojson()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parsed geometry;
BEGIN
    parsed := ST_SetSRID(ST_GeomFromGeoJSON(NEW.geometry_json), 4326);
    IF GeometryType(parsed) <> 'POLYGON'
        OR ST_IsEmpty(parsed)
        OR NOT ST_IsValid(parsed)
    THEN
        RAISE EXCEPTION 'La geometría debe ser un polígono válido y no vacío';
    END IF;
    NEW.geometry := parsed::geometry(Polygon, 4326);
    RETURN NEW;
END
$$;

CREATE TRIGGER geofence_geometry_sync
    BEFORE INSERT OR UPDATE OF geometry_json ON geofences
    FOR EACH ROW
    EXECUTE FUNCTION geofence_geometry_from_geojson();

CREATE TABLE geofence_versions (
    geofence_id uuid NOT NULL REFERENCES geofences (id),
    version bigint NOT NULL,
    campus_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    type varchar(16) NOT NULL,
    parent_id uuid,
    geometry_json text NOT NULL,
    active boolean NOT NULL,
    changed_at timestamptz NOT NULL,
    PRIMARY KEY (geofence_id, version)
);

CREATE TABLE teaching_classes (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    teacher_id uuid NOT NULL REFERENCES teachers (id),
    campus_id uuid NOT NULL REFERENCES campuses (id),
    created_by uuid REFERENCES teachers (id),
    active boolean NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    modified_at timestamptz
);

CREATE INDEX teaching_classes_teacher_idx
    ON teaching_classes (teacher_id, active);

CREATE INDEX teaching_classes_created_by_idx
    ON teaching_classes (created_by, name);

CREATE TABLE class_schedules (
    class_id uuid PRIMARY KEY REFERENCES teaching_classes (id),
    day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CHECK (start_time < end_time)
);

CREATE TABLE class_allowed_modules (
    class_id uuid NOT NULL REFERENCES teaching_classes (id),
    module_id uuid NOT NULL REFERENCES geofences (id),
    PRIMARY KEY (class_id, module_id)
);

CREATE TABLE class_occurrences (
    id uuid PRIMARY KEY,
    class_id uuid NOT NULL REFERENCES teaching_classes (id),
    teacher_id uuid NOT NULL REFERENCES teachers (id),
    class_date date NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    UNIQUE (class_id, class_date),
    CHECK (start_at < end_at)
);

CREATE INDEX class_occurrences_teacher_date_idx
    ON class_occurrences (teacher_id, class_date);

CREATE TABLE attendances (
    id uuid PRIMARY KEY,
    teacher_id uuid NOT NULL REFERENCES teachers (id),
    occurrence_id uuid NOT NULL REFERENCES class_occurrences (id),
    received_at timestamptz NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('CONFIRMED', 'PENDING_REVIEW')),
    spatial_result varchar(32) NOT NULL,
    temporal_result varchar(24) NOT NULL,
    reason varchar(80),
    justification text,
    location text,
    location_issue varchar(32),
    configuration_snapshot text NOT NULL,
    idempotency_key uuid NOT NULL,
    request_fingerprint varchar(64) NOT NULL CHECK (length(request_fingerprint) = 64),
    chain_payload text NOT NULL,
    UNIQUE (teacher_id, idempotency_key),
    UNIQUE (teacher_id, occurrence_id)
);

CREATE INDEX attendances_teacher_received_idx
    ON attendances (teacher_id, received_at DESC);

CREATE TABLE global_chain_blocks (
    id uuid PRIMARY KEY,
    chain_id varchar(32) NOT NULL CHECK (chain_id = 'GLOBAL_ATTENDANCE'),
    block_index bigint NOT NULL UNIQUE CHECK (block_index >= 0),
    event_type varchar(32) NOT NULL,
    teacher_id uuid REFERENCES teachers (id),
    attendance_id uuid REFERENCES attendances (id),
    server_at timestamptz,
    payload text NOT NULL,
    previous_hash varchar(64) NOT NULL CHECK (length(previous_hash) = 64),
    hash varchar(64) NOT NULL CHECK (length(hash) = 64),
    format_version integer NOT NULL CHECK (format_version = 2),
    CHECK (
        (
            block_index = 0
            AND event_type = 'GENESIS'
            AND teacher_id IS NULL
            AND attendance_id IS NULL
            AND server_at IS NULL
        )
        OR
        (
            block_index > 0
            AND event_type IN ('ATTENDANCE_RECORDED', 'ATTENDANCE_STATUS_CHANGED')
            AND teacher_id IS NOT NULL
            AND attendance_id IS NOT NULL
            AND server_at IS NOT NULL
        )
    )
);

CREATE TABLE global_chain_head (
    id smallint PRIMARY KEY CHECK (id = 1),
    chain_id varchar(32) NOT NULL CHECK (chain_id = 'GLOBAL_ATTENDANCE'),
    last_index bigint NOT NULL CHECK (last_index >= 0),
    last_hash varchar(64) NOT NULL CHECK (length(last_hash) = 64),
    total_blocks bigint NOT NULL CHECK (total_blocks > 0),
    updated_at timestamptz NOT NULL,
    CHECK (total_blocks = last_index + 1)
);

CREATE INDEX global_chain_blocks_teacher_index_idx
    ON global_chain_blocks (teacher_id, block_index)
    WHERE teacher_id IS NOT NULL;
