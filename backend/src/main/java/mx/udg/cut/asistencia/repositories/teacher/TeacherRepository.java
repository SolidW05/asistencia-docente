package mx.udg.cut.asistencia.repositories.teacher;

import mx.udg.cut.asistencia.models.teacher.Teacher;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {
    Optional<Teacher> findByCode(String code);

    List<Teacher> findAllByOrderByCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.id = :id")
    Optional<Teacher> lockById(@Param("id") UUID id);
}
