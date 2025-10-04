package com.bustracking.bustrack.DBConfig;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class Databasetest implements CommandLineRunner {

    private final DataSource dataSource;
    private static final Logger log = LoggerFactory.getLogger(Databasetest.class);

    public Databasetest(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @Override
    public void run(String... args) throws Exception {
        //System.out.println("Testing database connection...");
//        System.out.println(System.getenv("SUPABASE_DB_URL"));
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                log.info("Successfully connected to the database!");
            } else {
                log.info("❌ Connection failed.");
            }
        } catch (Exception e) {
            log.warn ("❌ Error connecting to database:");
            log.warn("Exception: ", e);
        }
    }
}