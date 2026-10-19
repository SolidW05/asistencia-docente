package mx.udg.cut.asistencia.models.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "class_schedules")
public class ClassSchedule {
    @Id
    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected ClassSchedule() {
    }

    public ClassSchedule(UUID classId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.classId = classId;
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getClassId() {
        return classId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void update(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
