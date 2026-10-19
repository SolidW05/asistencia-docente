package mx.udg.cut.asistencia.repositories.campus;

import mx.udg.cut.asistencia.models.campus.Campus;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusRepository extends JpaRepository<Campus, UUID> {
    List<Campus> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campus c where c.id=:id")
    Optional<Campus> lockById(@Param("id") UUID id);
}
