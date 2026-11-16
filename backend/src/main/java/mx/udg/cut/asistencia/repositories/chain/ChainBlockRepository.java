package mx.udg.cut.asistencia.repositories.chain;

import mx.udg.cut.asistencia.models.chain.ChainBlock;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Expone operaciones JPA sobre bloques y una búsqueda por docente.
 * La lista derivada mantiene el orden ascendente del índice global.
 * Los servicios de cadena también usan JDBC para recorridos y escrituras explícitas.
 * Este repositorio no verifica hashes ni integridad.
 */
public interface ChainBlockRepository extends JpaRepository<ChainBlock, UUID> {
    /**
     * Selecciona bloques asociados al docente por índice ascendente.
     * El génesis no coincide porque carece de docente.
     */
    List<ChainBlock> findByTeacherIdOrderByIndexAsc(UUID teacherId);
}
