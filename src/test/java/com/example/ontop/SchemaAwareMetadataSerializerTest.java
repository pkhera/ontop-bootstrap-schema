package com.example.ontop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.exception.MetadataExtractionException;
import it.unibz.inf.ontop.injection.OntopMappingSQLConfiguration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SchemaAwareMetadataSerializerTest extends AbstractSchemaTest {

    private JsonNode extract(String... schemas) throws Exception {
        OntopMappingSQLConfiguration configuration = OntopMappingSQLConfiguration.defaultBuilder()
                .propertyFile(propertiesFile.toString())
                .build();

        String json = new SchemaAwareMetadataSerializer()
                .extractAndSerialize(configuration, ImmutableSet.copyOf(schemas));

        return new ObjectMapper().readTree(json);
    }

    private static List<String> getTableNames(JsonNode metadata) {
        List<String> names = new ArrayList<>();
        for (JsonNode relation : metadata.get("relations"))
            names.add(String.join(".", toList(relation.get("name"))));
        return names;
    }

    private static List<String> toList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText().replace("\"", "")));
        return values;
    }

    @Test
    public void testAllSchemasByDefault() throws Exception {
        List<String> tables = getTableNames(extract());

        assertEquals(ImmutableSet.of("SALES.CUSTOMER", "SALES.INVOICE", "HR.DEPARTMENT", "HR.EMPLOYEE",
                "HR.EMPLOYEE_SKILL", "HR.ACCOUNT_MANAGER"), ImmutableSet.copyOf(tables));
    }

    @Test
    public void testSalesSchemaOnly() throws Exception {
        JsonNode metadata = extract("sales");
        List<String> tables = getTableNames(metadata);

        assertEquals(ImmutableSet.of("SALES.CUSTOMER", "SALES.INVOICE"), ImmutableSet.copyOf(tables));
        assertFalse(metadata.toString().contains("MONTHLY_WAGE"));
    }

    @Test
    public void testHrSchemaOnly() throws Exception {
        JsonNode metadata = extract("HR");
        List<String> tables = getTableNames(metadata);

        assertEquals(ImmutableSet.of("HR.DEPARTMENT", "HR.EMPLOYEE", "HR.EMPLOYEE_SKILL", "HR.ACCOUNT_MANAGER"),
                ImmutableSet.copyOf(tables));
        assertFalse(metadata.toString().contains("VAT_NUMBER"));
        // the cross-schema foreign key HR.ACCOUNT_MANAGER -> SALES.CUSTOMER is dropped, the internal ones are kept
        assertFalse(metadata.toString().contains("SALES"));
        assertTrue(metadata.toString().contains("\"foreignKeys\""));
    }

    @Test
    public void testUnknownSchema() {
        try {
            extract("marketing");
            fail("A MetadataExtractionException was expected");
        }
        catch (Exception e) {
            assertTrue(e instanceof MetadataExtractionException);
            assertTrue(e.getMessage().contains("Available schemas"));
        }
    }
}
