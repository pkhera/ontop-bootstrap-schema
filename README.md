# ontop-bootstrap-schema

Schema-selective `ontop bootstrap`, implemented **without forking or patching Ontop** — it only depends on the
released Ontop artifacts from Maven Central (`5.3.0` here).

## Why it works

`DirectMappingEngine` is a public class and its `getMapping(relation, baseIRI, bnodeTemplateMap, index)` method is
public; it is obtained from the Guice injector of an `OntopSQLOWLAPIConfiguration`. This is exactly how the Protégé
plugin bootstraps (`BootstrapAction`). So the schema filtering can be done outside of Ontop:

1. `JDBCMetadataProviderFactory.getMetadataProvider(connection)` → `MetadataProvider.getRelationIDs()`
2. filter the ids on their schema component (case-insensitive, `INFORMATION_SCHEMA` dropped)
3. resolve **only** the retained ids with `CachingMetadataLookup` → no column/constraint metadata is fetched for the
   other schemas (the expensive part, and the part that fails when the connection cannot read them)
4. `DirectMappingEngine.getMapping(...)` per relation → `SQLPPMappingFactory.createSQLPreProcessedMapping(...)`
5. `MappingOntologyUtils.extractAndInsertDeclarationAxioms(...)` for the OWL file
6. `OntopNativeMappingSerializer.write(...)` for the `.obda` file

Only what `ontop bootstrap` itself does is re-implemented (~40 lines); the direct-mapping logic is untouched.

## Usage

```bash
mvn package
java -jar target/ontop-bootstrap-schema-1.0-SNAPSHOT.jar \
  -b http://example.org/ \
  -p db.properties \
  -m out.obda \
  -t out.owl \
  --schema schema_a --schema schema_b
```

Without `--schema`, all visible schemas are bootstrapped (standard Ontop behaviour). If none of the selected schemas
holds a relation, it fails with the list of available schemas.

`OntopBootstrapSchema` implements the public `it.unibz.inf.ontop.cli.OntopCommand` interface, so it can also be
registered in your own Airline `Cli` next to the standard Ontop commands (`Ontop.main` cannot be extended in place:
its command list is hardcoded in `Ontop.getOntopCommandCLI()`).

## Verified locally (H2, `SCHEMA_A.TABLE_A` + `SCHEMA_B.TABLE_B`)

| command | generated sources |
|---|---|
| no `--schema` | `SELECT * FROM "SCHEMA_A"."TABLE_A"`, `SELECT * FROM "SCHEMA_B"."TABLE_B"` |
| `--schema schema_a` | `SELECT * FROM "SCHEMA_A"."TABLE_A"` only |
| `--schema nope` | `No relation found in the schema(s) [nope]. Available schemas: [INFORMATION_SCHEMA, SCHEMA_A, SCHEMA_B]` |

## Caveat

`MetadataProvider.getRelationIDs()` still issues one `DatabaseMetaData.getTables(null, null, null, ...)` call, so the
list of tables of all visible schemas is still retrieved (cheap, one round-trip). Restricting that call itself needs
a change inside `AbstractDBMetadataProvider`, i.e. a patched Ontop (see the fork PR).
