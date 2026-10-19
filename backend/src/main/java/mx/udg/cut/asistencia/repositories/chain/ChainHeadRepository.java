package mx.udg.cut.asistencia.repositories.chain;

import mx.udg.cut.asistencia.models.chain.ChainHead;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainHeadRepository extends JpaRepository<ChainHead, Short> {
}
