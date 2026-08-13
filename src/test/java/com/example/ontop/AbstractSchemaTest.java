package com.example.ontop;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * In-memory H2 database with two schemas holding different tables and columns:
 *
 * <pre>
 * SALES.CUSTOMER(CUSTOMER_ID pk, COMPANY_NAME, CONTACT_EMAIL, VAT_NUMBER unique)
 * SALES.INVOICE(INVOICE_ID pk, CUSTOMER_ID fk, ISSUED_ON, TOTAL_EUR)
 * HR.DEPARTMENT(DEPARTMENT_ID pk, DEPARTMENT_NAME)
 * HR.EMPLOYEE(EMPLOYEE_ID pk, DEPARTMENT_ID fk, LAST_NAME, HIRED_ON, MONTHLY_WAGE)
 * HR.EMPLOYEE_SKILL(EMPLOYEE_ID + SKILL_CODE composite pk, LEVEL)
 * </pre>
 */
public abstract class AbstractSchemaTest {

    protected static final String JDBC_URL = "jdbc:h2:mem:schemaTest;DB_CLOSE_DELAY=-1";
    protected static final Path OUTPUT_DIR = Paths.get("target", "schema-test");
    protected static Path propertiesFile;

    private static Connection keepAliveConnection;

    @BeforeClass
    public static void setUp() throws SQLException, IOException {
        keepAliveConnection = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement statement = keepAliveConnection.createStatement()) {
            statement.execute(loadScript());
        }

        Files.createDirectories(OUTPUT_DIR);
        propertiesFile = OUTPUT_DIR.resolve("db.properties");
        Files.write(propertiesFile, String.join("\n",
                "jdbc.url=" + JDBC_URL,
                "jdbc.user=sa",
                "jdbc.password=",
                "jdbc.driver=org.h2.Driver").getBytes(StandardCharsets.UTF_8));
    }

    @AfterClass
    public static void tearDown() throws SQLException {
        keepAliveConnection.close();
    }

    private static String loadScript() throws IOException {
        try (InputStream in = AbstractSchemaTest.class.getResourceAsStream("/schemas.sql");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new java.io.BufferedReader(reader).lines().collect(Collectors.joining("\n"));
        }
    }
}
