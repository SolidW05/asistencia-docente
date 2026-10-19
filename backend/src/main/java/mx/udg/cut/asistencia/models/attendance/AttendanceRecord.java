package mx.udg.cut.asistencia.models.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendances")
public class AttendanceRecord {
    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "occurrence_id", nullable = false)
    private UUID occurrenceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "spatial_result", nullable = false)
    private SpatialResult spatialResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "temporal_result", nullable = false)
    private TemporalResult temporalResult;

    private String reason;
    private String justification;
    private String location;

    @Column(name = "location_issue")
    private String locationIssue;

    @Column(name = "configuration_snapshot", nullable = false)
    private String configurationSnapshot;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "chain_payload", nullable = false)
    private String chainPayload;

    protected AttendanceRecord() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getOccurrenceId() {
        return occurrenceId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public SpatialResult getSpatialResult() {
        return spatialResult;
    }

    public TemporalResult getTemporalResult() {
        return temporalResult;
    }

    public String getReason() {
        return reason;
    }

    public String getJustification() {
        return justification;
    }

    public String getLocation() {
        return location;
    }

    public String getLocationIssue() {
        return locationIssue;
    }

    public String getConfigurationSnapshot() {
        return configurationSnapshot;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getChainPayload() {
        return chainPayload;
    }
}
