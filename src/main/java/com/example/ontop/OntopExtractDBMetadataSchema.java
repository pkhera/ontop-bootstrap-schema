package com.example.ontop;

import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.restrictions.Required;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.cli.OntopCommand;
import it.unibz.inf.ontop.injection.OntopMappingSQLConfiguration;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Drop-in replacement of {@code ontop extract-db-metadata} supporting a repeatable --schema option.
 */
@Command(name = "extract-db-metadata-schema",
        description = "Extract the DB metadata of selected schemas and serialize it into an output JSON file")
public class OntopExtractDBMetadataSchema implements OntopCommand {

    @Option(type = OptionType.COMMAND, name = {"-p", "--properties"}, title = "properties file",
            description = "Properties file")
    @Required
    String propertiesFile;

    @Option(type = OptionType.COMMAND, name = {"-o", "--output"}, title = "output", description = "output file")
    String outputFile;

    @Option(type = OptionType.COMMAND, name = {"-s", "--schema"}, title = "schema", arity = 1,
            description = "Schema to extract. Can be repeated. By default, all the schemas are extracted")
    List<String> schemas;

    @Override
    public void run() {
        try {
            OntopMappingSQLConfiguration configuration = OntopMappingSQLConfiguration.defaultBuilder()
                    .propertyFile(propertiesFile)
                    .build();

            String payload = new SchemaAwareMetadataSerializer().extractAndSerialize(
                    configuration,
                    schemas == null ? ImmutableSet.of() : ImmutableSet.copyOf(schemas));

            OutputStream out = outputFile == null ? System.out : new FileOutputStream(outputFile);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write(payload);
            writer.flush();
            if (outputFile != null)
                writer.close();
        }
        catch (Exception e) {
            System.err.println("Error occurred during the metadata extraction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        SingleCommand.singleCommand(OntopExtractDBMetadataSchema.class).parse(args).run();
    }
}
