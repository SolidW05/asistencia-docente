package mx.udg.cut.asistencia.services.geofence;

import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.models.geofence.GeofenceType;
import mx.udg.cut.asistencia.repositories.campus.CampusRepository;
import mx.udg.cut.asistencia.repositories.geofence.GeofenceRepository;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeofenceService {
    private final GeofenceRepository geofences;
    private final CampusRepository campuses;
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;

    public GeofenceService(
            GeofenceRepository geofences,
            CampusRepository campuses,
            JdbcTemplate jdbc,
            CanonicalJson json) {
        this.geofences = geofences;
        this.campuses = campuses;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Geofence> list() {
        return geofences.findAllByOrderByNameAsc();
    }

    @Transactional
    public Geofence create(
            UUID campusId,
            String name,
            GeofenceType type,
            UUID parentId,
            JsonNode geometry,
            boolean active) {
        campuses.lockById(campusId)
                .orElseThrow(
                        () ->
                                ApiException.notFound(
                                        "CAMPUS_NOT_FOUND", "No se encontró el campus indicado."));
        validateBase(campusId, name, type, parentId, geometry, null);
        Geofence saved =
                geofences.saveAndFlush(
                        new Geofence(
                                UUID.randomUUID(),
                                campusId,
                                name,
                                type,
                                parentId,
                                json.write(geometry),
                                active));
        snapshot(saved);
        return saved;
    }

    @Transactional
    public Geofence update(
            UUID id,
            UUID campusId,
            String name,
            GeofenceType type,
            UUID parentId,
            JsonNode geometry,
            boolean active,
            long expectedVersion) {
        Geofence current =
                geofences
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "GEOFENCE_NOT_FOUND",
                                                "No se encontró la geocerca"));
        if (current.getVersion() != expectedVersion)
            throw ApiException.conflict(
                    "STALE_VERSION",
                    "La geocerca cambió. Actualiza la página y vuelve a intentarlo.");
        campuses.lockById(current.getCampusId())
                .orElseThrow(
                        () ->
                                ApiException.notFound(
                                        "CAMPUS_NOT_FOUND", "No se encontró el campus indicado."));
        if (current.getType() != type
                || !current.getCampusId().equals(campusId)
                || !java.util.Objects.equals(current.getParentId(), parentId))
            throw ApiException.unprocessable(
                    "GEOFENCE_HIERARCHY_IMMUTABLE",
                    "El tipo, campus y relación padre se definen al crear la geocerca.");
        validateBase(campusId, name, type, parentId, geometry, id);
        if (current.getType() == GeofenceType.CAMPUS) {
            long invalidChildren =
                    jdbc.queryForObject(
                            "select count(*) from geofences child where child.parent_id=? and not ST_Covers(ST_SetSRID(ST_GeomFromGeoJSON(?),4326),child.geometry)",
                            Long.class,
                            id,
                            json.write(geometry));
            if (invalidChildren > 0)
                throw ApiException.unprocessable(
                        "CHILD_OUTSIDE_CAMPUS",
                        "La nueva geometría dejaría módulos existentes fuera del campus.");
        }
        current.update(campusId, name, type, parentId, json.write(geometry), active);
        Geofence saved = geofences.saveAndFlush(current);
        snapshot(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean covers(UUID geofenceId, double longitude, double latitude) {
        return jdbc.queryForObject(
                "select ST_Covers(geometry,ST_SetSRID(ST_MakePoint(?,?),4326)) from geofences where id=?",
                Boolean.class,
                longitude,
                latitude,
                geofenceId);
    }

    @Transactional(readOnly = true)
    public double nearestBoundaryMeters(
            UUID campusId, List<UUID> relevantModuleIds, double longitude, double latitude) {
        String moduleClause =
                relevantModuleIds.isEmpty()
                        ? ""
                        : " OR g.id IN ("
                                + String.join(
                                        ",",
                                        java.util.Collections.nCopies(
                                                relevantModuleIds.size(), "?"))
                                + ")";
        String sql =
                "select min(ST_Distance(ST_Boundary(g.geometry)::geography,ST_SetSRID(ST_MakePoint(?,?),4326)::geography)) "
                        + "from geofences g where g.active=true and ((g.campus_id=? and g.type='CAMPUS')"
                        + moduleClause
                        + ")";
        java.util.ArrayList<Object> parameters =
                new java.util.ArrayList<>(List.of(longitude, latitude, campusId));
        parameters.addAll(relevantModuleIds);
        Double distance = jdbc.queryForObject(sql, Double.class, parameters.toArray());
        return distance == null ? Double.POSITIVE_INFINITY : distance;
    }

    @Transactional(readOnly = true)
    public boolean hasCampusPolygon(UUID campusId) {
        return geofences
                .findFirstByCampusIdAndType(campusId, GeofenceType.CAMPUS)
                .filter(Geofence::isActive)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Geofence campusPolygon(UUID campusId) {
        return geofences.findFirstByCampusIdAndType(campusId, GeofenceType.CAMPUS).orElse(null);
    }

    private void validateBase(
            UUID campusId,
            String name,
            GeofenceType type,
            UUID parentId,
            JsonNode geometry,
            UUID editingId) {
        if (!campuses.existsById(campusId))
            throw ApiException.notFound("CAMPUS_NOT_FOUND", "No se encontró el campus indicado.");
        if (name == null || name.isBlank() || name.length() > 120)
            throw ApiException.badRequest(
                    "INVALID_NAME",
                    "El nombre de la geocerca es obligatorio y admite hasta 120 caracteres.");
        if (type == null)
            throw ApiException.badRequest("INVALID_TYPE", "El tipo de geocerca es obligatorio.");
        String geoJson = validatePolygon(geometry);
        if (type == GeofenceType.CAMPUS) {
            if (parentId != null)
                throw ApiException.unprocessable(
                        "INVALID_PARENT", "Una geocerca CAMPUS no puede tener padre.");
            geofences
                    .findFirstByCampusIdAndType(campusId, GeofenceType.CAMPUS)
                    .filter(g -> !g.getId().equals(editingId))
                    .ifPresent(
                            g -> {
                                throw ApiException.conflict(
                                        "CAMPUS_GEOFENCE_EXISTS",
                                        "El campus ya tiene una geocerca principal.");
                            });
        } else {
            Geofence parent = parentId == null ? null : geofences.findById(parentId).orElse(null);
            if (parent == null
                    || parent.getType() != GeofenceType.CAMPUS
                    || !parent.getCampusId().equals(campusId))
                throw ApiException.unprocessable(
                        "INVALID_PARENT",
                        "El módulo debe tener como padre la geocerca del campus indicado.");
            boolean valid =
                    jdbc.queryForObject(
                            "select ST_Covers(parent.geometry,ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) from geofences parent where parent.id=?",
                            Boolean.class,
                            geoJson,
                            parentId);
            if (!valid)
                throw ApiException.unprocessable(
                        "MODULE_OUTSIDE_CAMPUS",
                        "El polígono del módulo debe quedar completamente dentro del campus.");
        }
    }

    private String validatePolygon(JsonNode geometry) {
        if (geometry == null
                || !geometry.isObject()
                || !"Polygon".equals(geometry.path("type").asText())
                || !geometry.path("coordinates").isArray())
            throw ApiException.unprocessable(
                    "INVALID_GEOMETRY", "La geometría debe ser un GeoJSON Polygon.");
        validatePositions(geometry.path("coordinates"));
        String value = json.write(geometry);
        try {
            boolean valid =
                    jdbc.queryForObject(
                            "select ST_IsValid(g) and not ST_IsEmpty(g) and ST_GeometryType(g)='ST_Polygon' and ST_SRID(g)=4326 from (select ST_SetSRID(ST_GeomFromGeoJSON(?),4326) g) q",
                            Boolean.class,
                            value);
            if (!valid)
                throw ApiException.unprocessable(
                        "INVALID_GEOMETRY",
                        "El polígono está vacío, autointersectado o no es válido.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unprocessable(
                    "INVALID_GEOMETRY",
                    "No se pudo leer el GeoJSON; revisa el polígono y sus coordenadas.");
        }
        return value;
    }

    private void validatePositions(JsonNode node) {
        if (node.isArray()
                && node.size() >= 2
                && node.get(0).isNumber()
                && node.get(1).isNumber()) {
            if (node.size() != 2)
                throw ApiException.unprocessable(
                        "INVALID_GEOMETRY",
                        "Solo se permiten coordenadas 2D en orden longitud, latitud.");
            double lon = node.get(0).asDouble(), lat = node.get(1).asDouble();
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90)
                throw ApiException.unprocessable(
                        "INVALID_COORDINATES", "Las coordenadas están fuera de WGS84.");
            return;
        }
        if (node.isArray())
            node.forEach(this::validatePositions);
        else
            throw ApiException.unprocessable(
                    "INVALID_GEOMETRY", "El GeoJSON tiene una estructura de coordenadas inválida.");
    }

    private void snapshot(Geofence g) {
        jdbc.update(
                """
            insert into geofence_versions(geofence_id,version,campus_id,name,type,parent_id,geometry_json,active,changed_at)
            values(?,?,?,?,?,?,?,?,?) on conflict (geofence_id,version) do nothing
            """,
                g.getId(),
                g.getVersion(),
                g.getCampusId(),
                g.getName(),
                g.getType().name(),
                g.getParentId(),
                g.getGeometryJson(),
                g.isActive(),
                Timestamp.from(Instant.now()));
    }
}
