package com.example.ontop;

import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.help.Help;
import it.unibz.inf.ontop.cli.OntopCommand;

/**
 * Custom Airline CLI exposing the schema-selective commands.
 *
 * <p>The standard {@code ontop} CLI cannot be extended in place: its command list is hardcoded in
 * {@code Ontop.getOntopCommandCLI()}. Standard Ontop commands (e.g. {@code OntopQuery}) can nevertheless be added
 * to this builder, as they are all public {@link OntopCommand}s.</p>
 */
public class OntopSchemaCli {

    public static void main(String... args) {
        Cli<Runnable> cli = Cli.<Runnable>builder("ontop-schema")
                .withDescription("Schema-selective Ontop bootstrap and DB metadata extraction")
                .withDefaultCommand(Help.class)
                .withCommand(Help.class)
                .withCommand(OntopBootstrapSchema.class)
                .withCommand(OntopExtractDBMetadataSchema.class)
                .build();

        cli.parse(args).run();
    }
}
