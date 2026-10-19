package mx.udg.cut.asistencia.models.geofence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "geofences")
public class Geofence {
    @Id
    private UUID id;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GeofenceType type;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "geometry_json", nullable = false, columnDefinition = "text")
    private String geometryJson;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    protected Geofence() {
    }

    public Geofence(
            UUID id,
            UUID campusId,
            String name,
            GeofenceType type,
            UUID parentId,
            String geometryJson,
            boolean active) {
        this.id = id;
        this.campusId = campusId;
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.geometryJson = geometryJson;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampusId() {
        return campusId;
    }

    public String getName() {
        return name;
    }

    public GeofenceType getType() {
        return type;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getGeometryJson() {
        return geometryJson;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public void update(
            UUID campusId,
            String name,
            GeofenceType type,
            UUID parentId,
            String geometryJson,
            boolean active) {
        this.campusId = campusId;
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.geometryJson = geometryJson;
        this.active = active;
    }
}
