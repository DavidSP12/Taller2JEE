package com.taller2jee.logic.persistence;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JpaEntityManagerFactoryProvider {
    private JpaEntityManagerFactoryProvider() {
    }

    public static EntityManagerFactory create(String dbPathOrJdbcUrl) {
        String jdbcUrl = resolveJdbcUrl(dbPathOrJdbcUrl);
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", jdbcUrl);
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.connection.pool_size", "4");
        properties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        return Persistence.createEntityManagerFactory("taller2jee-pu", properties);
    }

    private static String resolveJdbcUrl(String dbPathOrJdbcUrl) {
        if (dbPathOrJdbcUrl.startsWith("jdbc:")) {
            return dbPathOrJdbcUrl;
        }
        Path path = Path.of(dbPathOrJdbcUrl).toAbsolutePath();
        return "jdbc:sqlite:" + path;
    }
}
