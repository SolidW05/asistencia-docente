package mx.udg.cut.asistencia.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import mx.udg.cut.asistencia.services.chain.GenesisService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Crea el esquema vacío y después el Génesis, antes de que Hibernate valide los modelos. */
@Configuration
public class DatabaseInitializationConfig {
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor databaseInitializationDependency() {
        return new EntityManagerFactoryDependsOnPostProcessor("databaseInitializer");
    }

    @Bean
    InitializingBean databaseInitializer(DataSource dataSource, GenesisService genesis) {
        return () -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            String schema =
                    new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(
                            status -> {
                                // También serializa el primer arranque de varias instancias contra
                                // la misma base.
                                jdbc.execute("select pg_advisory_xact_lock(18471002)");
                                boolean initialized =
                                        Boolean.TRUE.equals(
                                                jdbc.queryForObject(
                                                        "select to_regclass('global_chain_head') is not null",
                                                        Boolean.class));
                                if (!initialized) jdbc.execute(schema);
                                upgradeExistingSchema(jdbc);
                                genesis.initialize();
                            });
        };
    }

    private void upgradeExistingSchema(JdbcTemplate jdbc) {
        jdbc.execute("alter table teachers drop constraint if exists teachers_role_check");
        jdbc.execute(
                "alter table teachers add constraint teachers_role_check "
                        + "check (role in ('TEACHER', 'JEFE', 'ADMIN'))");
        jdbc.execute(
                "alter table teaching_classes add column if not exists "
                        + "created_by uuid references teachers (id)");
        jdbc.execute(
                "create index if not exists teaching_classes_created_by_idx "
                        + "on teaching_classes (created_by, name)");

        jdbc.execute(
                "alter table global_chain_blocks drop constraint if exists global_chain_blocks_check");
        jdbc.execute(
                "alter table global_chain_blocks drop constraint if exists global_chain_blocks_block_event_check");
        jdbc.execute(
                "alter table global_chain_blocks drop constraint if exists "
                        + "global_chain_blocks_attendance_id_key");
        jdbc.execute(
                "alter table global_chain_blocks add constraint global_chain_blocks_block_event_check "
                        + "check ("
                        + "(block_index = 0 and event_type = 'GENESIS' and teacher_id is null "
                        + "and attendance_id is null and server_at is null) "
                        + "or (block_index > 0 and event_type in "
                        + "('ATTENDANCE_RECORDED', 'ATTENDANCE_STATUS_CHANGED') "
                        + "and teacher_id is not null and attendance_id is not null "
                        + "and server_at is not null))");
        jdbc.execute(
                "create index if not exists global_chain_blocks_attendance_idx "
                        + "on global_chain_blocks (attendance_id) where attendance_id is not null");
    }
}