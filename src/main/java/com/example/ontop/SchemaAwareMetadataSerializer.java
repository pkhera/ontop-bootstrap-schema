package com.example.ontop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import it.unibz.inf.ontop.dbschema.ImmutableMetadata;
import it.unibz.inf.ontop.dbschema.MetadataProvider;
import it.unibz.inf.ontop.dbschema.NamedRelationDefinition;
import it.unibz.inf.ontop.dbschema.impl.JDBCMetadataProviderFactory;
import it.unibz.inf.ontop.dbschema.impl.json.JsonMetadata;
import it.unibz.inf.ontop.injection.OntopMappingSQLConfiguration;
import it.unibz.inf.ontop.injection.OntopSQLCredentialSettings;
import it.unibz.inf.ontop.utils.LocalJDBCConnectionUtils;

import java.sql.Connection;

/**
 * Schema-selective counterpart of {@code ontop extract-db-metadata}.
 *
 * <p>Same JSON output as {@code RDBMetadataExtractorAndSerializerImpl}, except that the relations are restricted
 * to the selected schemas before their metadata is extracted.</p>
 */
public class SchemaAwareMetadataSerializer {

    /**
     * @param schemas if empty, all the visible schemas are extracted (default Ontop behaviour)
     */
    public String extractAndSerialize(OntopMappingSQLConfiguration configuration, ImmutableSet<String> schemas)
            throws Exception {

        Injector injector = configuration.getInjector();
        JDBCMetadataProviderFactory metadataProviderFactory = injector.getInstance(JDBCMetadataProviderFactory.class);
        OntopSQLCredentialSettings settings = injector.getInstance(OntopSQLCredentialSettings.class);

        ImmutableMetadata metadata;
        try (Connection connection = LocalJDBCConnectionUtils.createConnection(settings)) {
            MetadataProvider metadataProvider = metadataProviderFactory.getMetadataProvider(connection);
            metadata = SchemaFilteredMetadata.extract(metadataProvider, schemas);
        }

        JsonMetadata jsonMetadata = new JsonMetadata(metadata, NamedRelationDefinition::getID);
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonMetadata);
    }
}
