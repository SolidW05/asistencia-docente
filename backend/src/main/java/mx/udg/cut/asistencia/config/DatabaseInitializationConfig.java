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
                                if (!initialized)
                                    jdbc.execute(schema);
                                genesis.initialize();
                            });
        };
    }
}
