package com.bank.balancedashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Balance Dashboard Service — Spring Boot entry point.
 *
 * <p>DataSource exclusions (NO-RDBMS constraint — impl-notes §2):
 * BDS has no JPA/Flyway/JDBC. These three autoconfiguration classes are excluded
 * defensively because they may be transitively pulled by shared starters and would
 * fail context startup if no DataSource bean is found.
 *
 * <p>Architecture: hexagonal, domain-driven. Redis (Lettuce) + Kafka + OAuth2/OIDC.
 * See implementation-notes.md §1 for full module layout.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class BalanceDashboardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceDashboardServiceApplication.class, args);
    }
}
