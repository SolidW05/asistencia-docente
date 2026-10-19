package mx.udg.cut.asistencia.models.campus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "campuses")
public class Campus {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    protected Campus() {
    }

    public Campus(UUID id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }
}
