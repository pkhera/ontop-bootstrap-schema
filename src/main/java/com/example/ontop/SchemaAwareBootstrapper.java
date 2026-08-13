package com.example.ontop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import it.unibz.inf.ontop.dbschema.ImmutableMetadata;
import it.unibz.inf.ontop.dbschema.MetadataProvider;
import it.unibz.inf.ontop.dbschema.NamedRelationDefinition;
import it.unibz.inf.ontop.dbschema.QuotedID;
import it.unibz.inf.ontop.dbschema.RelationID;
import it.unibz.inf.ontop.dbschema.impl.CachingMetadataLookup;
import it.unibz.inf.ontop.dbschema.impl.JDBCMetadataProviderFactory;
import it.unibz.inf.ontop.exception.MetadataExtractionException;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.SQLPPMappingFactory;
import it.unibz.inf.ontop.injection.SpecificationFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.BnodeStringTemplateFunctionSymbol;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.spec.mapping.bootstrap.impl.DirectMappingEngine;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPTriplesMap;
import it.unibz.inf.ontop.spec.mapping.util.MappingOntologyUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schema-selective direct mapping bootstrapper built on top of the released Ontop artifacts.
 *
 * <p>No Ontop class is modified: the public {@link DirectMappingEngine#getMapping} entry point is reused
 * (same approach as the Protégé plugin), while the relation ids are filtered by schema <em>before</em> the
 * (expensive) column/constraint metadata is extracted.</p>
 */
public class SchemaAwareBootstrapper {

    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    public static class Results {
        private final SQLPPMapping ppMapping;
        private final OWLOntology ontology;

        Results(SQLPPMapping ppMapping, OWLOntology ontology) {
            this.ppMapping = ppMapping;
            this.ontology = ontology;
        }

        public SQLPPMapping getPPMapping() { return ppMapping; }
        public OWLOntology getOntology() { return ontology; }
    }

    /**
     * @param schemas if empty, all the visible schemas are bootstrapped (default Ontop behaviour)
     */
    public Results bootstrap(OntopSQLOWLAPIConfiguration configuration, String baseIRI0, ImmutableSet<String> schemas)
            throws Exception {

        Injector injector = configuration.getInjector();
        DirectMappingEngine engine = injector.getInstance(DirectMappingEngine.class);
        JDBCMetadataProviderFactory metadataProviderFactory = injector.getInstance(JDBCMetadataProviderFactory.class);
        SQLPPMappingFactory ppMappingFactory = injector.getInstance(SQLPPMappingFactory.class);
        SpecificationFactory specificationFactory = injector.getInstance(SpecificationFactory.class);
        TypeFactory typeFactory = injector.getInstance(TypeFactory.class);

        String baseIRI = DirectMappingEngine.fixBaseURI(baseIRI0);

        ImmutableMetadata metadata;
        try (Connection connection = createConnection(configuration)) {
            MetadataProvider metadataProvider = metadataProviderFactory.getMetadataProvider(connection);
            metadata = extractMetadata(metadataProvider, schemas);
        }

        Map<NamedRelationDefinition, BnodeStringTemplateFunctionSymbol> bnodeTemplateMap = new HashMap<>();
        AtomicInteger mappingIndex = new AtomicInteger(1);

        ImmutableList.Builder<SQLPPTriplesMap> triplesMaps = ImmutableList.builder();
        for (NamedRelationDefinition relation : metadata.getAllRelations())
            triplesMaps.addAll(engine.getMapping(relation, baseIRI, bnodeTemplateMap, mappingIndex));

        SQLPPMapping ppMapping = ppMappingFactory.createSQLPreProcessedMapping(
                triplesMaps.build(),
                specificationFactory.createPrefixManager(ImmutableMap.of()));

        OWLOntology ontology = OWLManager.createOWLOntologyManager().createOntology(IRI.create(baseIRI));
        MappingOntologyUtils.extractAndInsertDeclarationAxioms(ontology, ppMapping.getTripleMaps(), typeFactory, true);

        return new Results(ppMapping, ontology);
    }

    private Connection createConnection(OntopSQLOWLAPIConfiguration configuration) throws Exception {
        var settings = configuration.getSettings();
        return DriverManager.getConnection(settings.getJdbcUrl(), settings.getJdbcUser().orElse(null),
                settings.getJdbcPassword().orElse(null));
    }

    /**
     * Filters the relation ids on their schema, so that the metadata of the irrelevant relations is never extracted.
     */
    private static ImmutableMetadata extractMetadata(MetadataProvider metadataProvider, ImmutableSet<String> schemas)
            throws MetadataExtractionException {

        ImmutableSet<String> normalizedSchemas = schemas.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(ImmutableSet.toImmutableSet());

        ImmutableList<RelationID> relationIds = metadataProvider.getRelationIDs().stream()
                .filter(id -> !getSchema(id).filter(INFORMATION_SCHEMA::equals).isPresent())
                .filter(id -> normalizedSchemas.isEmpty()
                        || getSchema(id).map(s -> normalizedSchemas.contains(s.toUpperCase(Locale.ROOT))).orElse(false))
                .collect(ImmutableList.toImmutableList());

        if (relationIds.isEmpty() && !normalizedSchemas.isEmpty())
            throw new MetadataExtractionException("No relation found in the schema(s) " + schemas
                    + ". Available schemas: " + extractSchemas(metadataProvider));

        CachingMetadataLookup lookup = new CachingMetadataLookup(metadataProvider);
        for (RelationID id : relationIds)
            lookup.getRelation(id);

        return lookup.extractImmutableMetadata();
    }

    private static ImmutableSet<String> extractSchemas(MetadataProvider metadataProvider)
            throws MetadataExtractionException {
        return metadataProvider.getRelationIDs().stream()
                .map(SchemaAwareBootstrapper::getSchema)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(ImmutableSet.toImmutableSet());
    }

    private static Optional<String> getSchema(RelationID id) {
        ImmutableList<QuotedID> components = id.getComponents();
        return components.size() > 1
                ? Optional.of(components.get(1).getName())
                : Optional.empty();
    }
}
