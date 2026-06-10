package it.gov.pagopa.gpd.technicalsupport.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "reconciliation.apd",
    name = "enabled",
    havingValue = "true")
public class ReconciliationApdConfig {

  private final ReconciliationApdProperties properties;

  @Bean
  DataSource apdDataSource() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(properties.getJdbcUrl());
    dataSource.setUsername(properties.getUsername());
    dataSource.setPassword(properties.getPassword());
    dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
    dataSource.setMinimumIdle(properties.getMinimumIdle());
    dataSource.setConnectionTimeout(properties.getConnectionTimeoutMs());
    dataSource.setPoolName("reconciliation-apd-pool");
    dataSource.setReadOnly(true);
    return dataSource;
  }

  @Bean
  JdbcTemplate apdJdbcTemplate(DataSource apdDataSource) {
    return new JdbcTemplate(apdDataSource);
  }
  
  @Bean
  NamedParameterJdbcTemplate apdReadReplicaNamedParameterJdbcTemplate(
      DataSource apdDataSource) {
    return new NamedParameterJdbcTemplate(apdDataSource);
  }
}