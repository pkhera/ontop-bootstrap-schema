package com.example.ontop;

import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.restrictions.Required;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.cli.OntopCommand;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.spec.mapping.serializer.impl.OntopNativeMappingSerializer;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.File;
import java.util.List;

/**
 * Drop-in replacement of `ontop bootstrap` supporting a repeatable --schema option.
 *
 * <p>Implements the public {@link OntopCommand} interface, so it can also be registered in a custom
 * Airline {@code Cli} together with the standard Ontop commands.</p>
 */
@Command(name = "bootstrap-schema",
        description = "Bootstrap ontology and mapping from selected schemas of the database")
public class OntopBootstrapSchema implements OntopCommand {

    @Option(type = OptionType.COMMAND, name = {"-p", "--properties"}, title = "properties file",
            description = "Properties file")
    String propertiesFile;

    @Option(type = OptionType.COMMAND, name = {"--db-url"}, title = "DB URL", description = "DB URL")
    String dbUrl;

    @Option(type = OptionType.COMMAND, name = {"-u", "--db-user"}, title = "DB user", description = "DB user")
    String dbUser;

    @Option(type = OptionType.COMMAND, name = {"--db-password"}, title = "DB password", description = "DB password")
    String dbPassword;

    @Option(type = OptionType.COMMAND, name = {"--db-driver"}, title = "DB driver", description = "DB driver")
    String dbDriver;

    @Option(type = OptionType.COMMAND, name = {"-b", "--base-iri"}, title = "base IRI",
            description = "Base IRI of the generated mapping")
    @Required
    String baseIRI;

    @Option(type = OptionType.COMMAND, name = {"-t", "--ontology"}, title = "ontology file",
            description = "Output OWL ontology file")
    @Required
    String owlFile;

    @Option(type = OptionType.COMMAND, name = {"-m", "--mapping"}, title = "mapping file",
            description = "Output mapping file in the Ontop native format (.obda)")
    @Required
    String mappingFile;

    @Option(type = OptionType.COMMAND, name = {"-s", "--schema"}, title = "schema", arity = 1,
            description = "Schema to bootstrap. Can be repeated. By default, all the schemas are bootstrapped")
    List<String> schemas;

    @Override
    public void run() {
        try {
            if (baseIRI.contains("#"))
                throw new IllegalArgumentException("Base IRI cannot contain the character '#'!");

            OntopSQLOWLAPIConfiguration.Builder<?> builder = OntopSQLOWLAPIConfiguration.defaultBuilder();
            if (propertiesFile != null) builder.propertyFile(propertiesFile);
            if (dbUrl != null) builder.jdbcUrl(dbUrl);
            if (dbUser != null) builder.jdbcUser(dbUser);
            if (dbPassword != null) builder.jdbcPassword(dbPassword);
            if (dbDriver != null) builder.jdbcDriver(dbDriver);

            SchemaAwareBootstrapper.Results results = new SchemaAwareBootstrapper().bootstrap(
                    builder.build(),
                    baseIRI,
                    schemas == null ? ImmutableSet.of() : ImmutableSet.copyOf(schemas));

            new OntopNativeMappingSerializer().write(new File(mappingFile), results.getPPMapping());

            OWLOntology ontology = results.getOntology();
            ontology.getOWLOntologyManager().saveOntology(ontology, new FileDocumentTarget(new File(owlFile)));
        }
        catch (Exception e) {
            System.err.println("Error occurred during bootstrapping: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        SingleCommand.singleCommand(OntopBootstrapSchema.class).parse(args).run();
    }
}
