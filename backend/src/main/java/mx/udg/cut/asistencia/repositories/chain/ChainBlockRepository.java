package mx.udg.cut.asistencia.repositories.chain;

import mx.udg.cut.asistencia.models.chain.ChainBlock;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainBlockRepository extends JpaRepository<ChainBlock, UUID> {
    List<ChainBlock> findByTeacherIdOrderByIndexAsc(UUID teacherId);
}
