package com.example.ontop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.dbschema.ImmutableMetadata;
import it.unibz.inf.ontop.dbschema.MetadataProvider;
import it.unibz.inf.ontop.dbschema.QuotedID;
import it.unibz.inf.ontop.dbschema.RelationID;
import it.unibz.inf.ontop.dbschema.impl.CachingMetadataLookup;
import it.unibz.inf.ontop.exception.MetadataExtractionException;

import java.util.Locale;
import java.util.Optional;

/**
 * Extracts the DB metadata of the selected schemas only.
 *
 * <p>The selection is made on the relation ids, so the (expensive) column, primary key, unique constraint and
 * foreign key metadata of the other schemas is never requested. This matters both for performance and for
 * connections that are not allowed to read the metadata of every visible schema.</p>
 */
public class SchemaFilteredMetadata {

    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    /**
     * @param schemas if empty, all the visible schemas are extracted (default Ontop behaviour)
     */
    public static ImmutableMetadata extract(MetadataProvider metadataProvider, ImmutableSet<String> schemas)
            throws MetadataExtractionException {

        ImmutableSet<String> normalizedSchemas = schemas.stream()
                .map(SchemaFilteredMetadata::normalize)
                .collect(ImmutableSet.toImmutableSet());

        ImmutableList<RelationID> relationIds = metadataProvider.getRelationIDs().stream()
                .filter(id -> !getSchema(id).filter(INFORMATION_SCHEMA::equals).isPresent())
                .filter(id -> normalizedSchemas.isEmpty()
                        || getSchema(id).map(s -> normalizedSchemas.contains(normalize(s))).orElse(false))
                .collect(ImmutableList.toImmutableList());

        if (relationIds.isEmpty() && !normalizedSchemas.isEmpty())
            throw new MetadataExtractionException("No relation found in the schema(s) " + schemas
                    + ". Available schemas: " + getAvailableSchemas(metadataProvider));

        CachingMetadataLookup lookup = new CachingMetadataLookup(metadataProvider);
        for (RelationID id : relationIds)
            lookup.getRelation(id);

        return lookup.extractImmutableMetadata();
    }

    public static ImmutableSet<String> getAvailableSchemas(MetadataProvider metadataProvider)
            throws MetadataExtractionException {
        return metadataProvider.getRelationIDs().stream()
                .map(SchemaFilteredMetadata::getSchema)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * In a relation id, the component 0 is the table and the component 1 (when present) is the schema.
     */
    private static Optional<String> getSchema(RelationID id) {
        ImmutableList<QuotedID> components = id.getComponents();
        return components.size() > 1
                ? Optional.of(components.get(1).getName())
                : Optional.empty();
    }

    private static String normalize(String schema) {
        return schema.toUpperCase(Locale.ROOT);
    }
}
