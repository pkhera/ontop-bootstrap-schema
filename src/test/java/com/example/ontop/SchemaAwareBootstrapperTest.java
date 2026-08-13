package com.example.ontop;

import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.exception.MetadataExtractionException;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.spec.mapping.serializer.impl.OntopNativeMappingSerializer;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SchemaAwareBootstrapperTest extends AbstractSchemaTest {

    private static final Pattern TABLE_PATTERN = Pattern.compile("SELECT \\* FROM \"(\\w+)\"\\.\"(\\w+)\"");

    private String bootstrap(String name, String... schemas) throws Exception {
        OntopSQLOWLAPIConfiguration configuration = OntopSQLOWLAPIConfiguration.defaultBuilder()
                .propertyFile(propertiesFile.toString())
                .build();

        SchemaAwareBootstrapper.Results results = new SchemaAwareBootstrapper()
                .bootstrap(configuration, "http://example.org/", ImmutableSet.copyOf(schemas));

        Path mappingFile = OUTPUT_DIR.resolve(name + ".obda");
        new OntopNativeMappingSerializer().write(new File(mappingFile.toString()), results.getPPMapping());
        return new String(Files.readAllBytes(mappingFile), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
    }

    /**
     * The tables having their own triples map (foreign keys generate additional triples maps).
     */
    private static ImmutableSet<String> getMappedTables(String mapping) {
        ImmutableSet.Builder<String> tables = ImmutableSet.builder();
        Matcher matcher = TABLE_PATTERN.matcher(mapping);
        while (matcher.find())
            tables.add(matcher.group(1) + "." + matcher.group(2));
        return tables.build();
    }

    @Test
    public void testAllSchemasByDefault() throws Exception {
        String mapping = bootstrap("all");

        assertEquals(
                ImmutableSet.of("SALES.CUSTOMER", "SALES.INVOICE",
                        "HR.DEPARTMENT", "HR.EMPLOYEE", "HR.EMPLOYEE_SKILL", "HR.ACCOUNT_MANAGER"),
                getMappedTables(mapping));
    }

    @Test
    public void testSalesSchemaOnly() throws Exception {
        String mapping = bootstrap("sales", "sales");

        assertEquals(ImmutableSet.of("SALES.CUSTOMER", "SALES.INVOICE"), getMappedTables(mapping));

        // the columns of the selected schema are mapped, with their types
        assertTrue(mapping.contains("CUSTOMER#COMPANY_NAME> {COMPANY_NAME}^^XSD:STRING"));
        assertTrue(mapping.contains("CUSTOMER#CONTACT_EMAIL"));
        assertTrue(mapping.contains("INVOICE#TOTAL_EUR> {TOTAL_EUR}^^XSD:DECIMAL"));
        assertTrue(mapping.contains("INVOICE#ISSUED_ON> {ISSUED_ON}^^XSD:DATE"));
        // the SALES.INVOICE -> SALES.CUSTOMER foreign key becomes an object property
        assertTrue(mapping.contains("INVOICE#REF-CUSTOMER_ID"));

        // nothing from the other schema
        assertFalse(mapping.contains("\"HR\""));
        assertFalse(mapping.contains("MONTHLY_WAGE"));
        assertFalse(mapping.contains("DEPARTMENT_NAME"));
    }

    @Test
    public void testHrSchemaOnly() throws Exception {
        String mapping = bootstrap("hr", "hr");

        assertEquals(ImmutableSet.of("HR.DEPARTMENT", "HR.EMPLOYEE", "HR.EMPLOYEE_SKILL", "HR.ACCOUNT_MANAGER"),
                getMappedTables(mapping));

        assertTrue(mapping.contains("EMPLOYEE#MONTHLY_WAGE> {MONTHLY_WAGE}^^XSD:DECIMAL"));
        assertTrue(mapping.contains("EMPLOYEE#HIRED_ON> {HIRED_ON}^^XSD:DATE"));
        // the composite primary key is used in the subject template
        assertTrue(mapping.contains("EMPLOYEE_SKILL/EMPLOYEE_ID={EMPLOYEE_ID};SKILL_CODE={SKILL_CODE}"));

        // no triples map for the tables of the other schema
        assertFalse(mapping.contains("SELECT * FROM \"SALES\""));
        assertFalse(mapping.contains("VAT_NUMBER"));
        assertFalse(mapping.contains("COMPANY_NAME"));
    }

    /**
     * A foreign key pointing to a non-selected schema (HR.ACCOUNT_MANAGER -> SALES.CUSTOMER) is dropped, since its
     * target relation is not part of the extracted metadata. The foreign keys internal to the selection are kept.
     */
    @Test
    public void testCrossSchemaForeignKeyIsDropped() throws Exception {
        String mapping = bootstrap("hr-fk", "hr");

        assertFalse(mapping.contains("ACCOUNT_MANAGER#REF-CUSTOMER_ID"));
        assertTrue(mapping.contains("ACCOUNT_MANAGER#REF-EMPLOYEE_ID"));

        // ... while it is mapped when both schemas are selected
        assertTrue(bootstrap("both-fk", "hr", "sales").contains("ACCOUNT_MANAGER#REF-CUSTOMER_ID"));
    }

    @Test
    public void testSeveralSchemas() throws Exception {
        assertEquals(getMappedTables(bootstrap("all2")), getMappedTables(bootstrap("both", "hr", "sales")));
    }

    @Test
    public void testSchemaNameIsCaseInsensitive() throws Exception {
        assertEquals(bootstrap("lower", "sales"), bootstrap("upper", "SALES"));
    }

    @Test
    public void testUnknownSchema() {
        try {
            bootstrap("unknown", "marketing");
            fail("A MetadataExtractionException was expected");
        }
        catch (Exception e) {
            assertTrue(e instanceof MetadataExtractionException);
            assertTrue(e.getMessage().contains("[marketing]"));
            assertTrue(e.getMessage().contains("SALES"));
            assertTrue(e.getMessage().contains("HR"));
        }
    }
}
