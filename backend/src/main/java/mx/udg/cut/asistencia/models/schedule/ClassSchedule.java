package mx.udg.cut.asistencia.models.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Día e intervalo regular de una clase. */
@Embeddable
public class ClassSchedule {
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    private LocalTime endTime;

    protected ClassSchedule() {
    }

    public ClassSchedule(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
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
}
