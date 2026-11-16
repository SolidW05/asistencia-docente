package mx.udg.cut.asistencia.repositories.chain;

import mx.udg.cut.asistencia.models.chain.ChainHead;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Expone las operaciones JPA heredadas sobre la cabecera global.
 * La cabecera resume índice, hash y cantidad del último estado conocido.
 * Los servicios usan JDBC para bloquearla y contrastarla con los bloques.
 * El repositorio no recalcula ni valida la cadena.
 */
public interface ChainHeadRepository extends JpaRepository<ChainHead, Short> {
}
