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

/**
 * Persiste campus y ofrece una lectura ordenada por nombre.
 * El bloqueo pesimista permite coordinar cambios relacionados durante una transacción.
 * Las búsquedas devuelven Optional vacío cuando no existe la fila solicitada.
 * No aplica por sí mismo reglas de autorización.
 */
public interface CampusRepository extends JpaRepository<Campus, UUID> {
    /**
     * Lista campus en orden ascendente de nombre.
     * La consulta no excluye campus inactivos.
     */
    List<Campus> findAllByOrderByNameAsc();

    /**
     * Busca el campus mediante JPQL con bloqueo pesimista de escritura.
     * El bloqueo requiere una transacción activa y Optional vacío indica ausencia.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campus c where c.id=:id")
    Optional<Campus> lockById(@Param("id") UUID id);
}
