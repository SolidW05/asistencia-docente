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

/**
 * Accede a identidades y roles persistidos de docentes.
 * Las búsquedas por código y UUID también ofrecen bloqueo pesimista de escritura.
 * Un bloqueo se aplica a filas existentes dentro de la transacción activa.
 * Si el código no existe, no se bloquea una fila inexistente.
 */
public interface TeacherRepository extends JpaRepository<Teacher, UUID> {
    /**
     * Busca la identidad persistida asociada al código docente.
     * Devuelve Optional vacío cuando no existe.
     */
    Optional<Teacher> findByCode(String code);

    /**
     * Busca por código mediante JPQL y bloqueo pesimista de escritura.
     * Solo bloquea una fila existente; un resultado vacío no bloquea el código ausente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.code = :code")
    Optional<Teacher> lockByCode(@Param("code") String code);

    /**
     * Lista identidades en orden ascendente de código.
     * Incluye todos los roles persistidos.
     */
    List<Teacher> findAllByOrderByCodeAsc();

    /**
     * Busca por UUID con bloqueo pesimista de escritura durante la transacción.
     * Devuelve Optional vacío si no existe la fila.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.id = :id")
    Optional<Teacher> lockById(@Param("id") UUID id);
}
