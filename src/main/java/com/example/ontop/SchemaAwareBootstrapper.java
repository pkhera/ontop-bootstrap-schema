package com.example.ontop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import it.unibz.inf.ontop.dbschema.ImmutableMetadata;
import it.unibz.inf.ontop.dbschema.MetadataProvider;
import it.unibz.inf.ontop.dbschema.NamedRelationDefinition;
import it.unibz.inf.ontop.dbschema.impl.JDBCMetadataProviderFactory;
import it.unibz.inf.ontop.injection.OntopSQLCredentialSettings;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.SQLPPMappingFactory;
import it.unibz.inf.ontop.injection.SpecificationFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.BnodeStringTemplateFunctionSymbol;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.spec.mapping.bootstrap.impl.DirectMappingEngine;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPTriplesMap;
import it.unibz.inf.ontop.spec.mapping.util.MappingOntologyUtils;
import it.unibz.inf.ontop.utils.LocalJDBCConnectionUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schema-selective direct mapping bootstrapper built on top of the released Ontop artifacts.
 *
 * <p>No Ontop class is modified: the public {@link DirectMappingEngine#getMapping} entry point is reused
 * (same approach as the Protégé plugin), while the relation ids are filtered by schema <em>before</em> the
 * expensive column/constraint metadata is extracted.</p>
 */
public class SchemaAwareBootstrapper {

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
        OntopSQLCredentialSettings settings = injector.getInstance(OntopSQLCredentialSettings.class);

        String baseIRI = DirectMappingEngine.fixBaseURI(baseIRI0);

        ImmutableMetadata metadata;
        try (Connection connection = LocalJDBCConnectionUtils.createConnection(settings)) {
            MetadataProvider metadataProvider = metadataProviderFactory.getMetadataProvider(connection);
            metadata = SchemaFilteredMetadata.extract(metadataProvider, schemas);
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
}
