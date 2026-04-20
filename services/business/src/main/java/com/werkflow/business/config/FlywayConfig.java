package com.werkflow.business.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway hrFlyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas("hr_service")
            .locations("classpath:db/migration/hr")
            .baselineOnMigrate(true)
            .outOfOrder(true)
            .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway financeFlyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas("finance_service")
            .locations("classpath:db/migration/finance")
            .baselineOnMigrate(true)
            .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway procurementFlyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas("procurement_service")
            .locations("classpath:db/migration/procurement")
            .baselineOnMigrate(true)
            .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway inventoryFlyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas("inventory_service")
            .locations("classpath:db/migration/inventory")
            .baselineOnMigrate(true)
            .load();
    }
}
