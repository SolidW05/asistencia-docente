package mx.udg.cut.asistencia.models.schedule;

import mx.udg.cut.asistencia.models.geofence.Geofence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "teaching_classes")
public class TeachingClass {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "class_allowed_modules",
            joinColumns = @JoinColumn(name = "class_id"),
            inverseJoinColumns = @JoinColumn(name = "module_id"))
    private Set<Geofence> allowedModules = new LinkedHashSet<>();

    protected TeachingClass() {
    }

    public TeachingClass(
            UUID id,
            String name,
            UUID teacherId,
            UUID campusId,
            boolean active,
            Set<Geofence> modules) {
        this(id, name, teacherId, campusId, active, modules, null);
    }

    public TeachingClass(
            UUID id,
            String name,
            UUID teacherId,
            UUID campusId,
            boolean active,
            Set<Geofence> modules,
            UUID createdBy) {
        this.id = id;
        this.name = name;
        this.teacherId = teacherId;
        this.campusId = campusId;
        this.active = active;
        this.allowedModules = modules;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getCampusId() {
        return campusId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public Set<Geofence> getAllowedModules() {
        return allowedModules;
    }

    public void update(
            String name, UUID teacherId, UUID campusId, boolean active, Set<Geofence> modules) {
        this.name = name;
        this.teacherId = teacherId;
        this.campusId = campusId;
        this.active = active;
        this.allowedModules = modules;
        this.modifiedAt = Instant.now();
    }
}