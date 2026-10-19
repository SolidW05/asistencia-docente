package mx.udg.cut.asistencia.models.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "class_occurrences")
public class ClassOccurrence {
    @Id
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "class_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    protected ClassOccurrence() {
    }

    public ClassOccurrence(
            UUID id, UUID classId, UUID teacherId, LocalDate date, Instant startAt, Instant endAt) {
        this.id = id;
        this.classId = classId;
        this.teacherId = teacherId;
        this.date = date;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public LocalDate getDate() {
        return date;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }
}
