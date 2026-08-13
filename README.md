# ontop-bootstrap-schema

Schema-selective `ontop bootstrap` **and** `ontop extract-db-metadata`, implemented **without forking or patching
Ontop** — it only depends on the released Ontop artifacts from Maven Central (`5.3.0` here).

## Why it works

`DirectMappingEngine` is a public class and its `getMapping(relation, baseIRI, bnodeTemplateMap, index)` method is
public; it is obtained from the Guice injector of an Ontop configuration. This is exactly how the Protégé plugin
bootstraps (`BootstrapAction`). So the schema filtering can be done outside of Ontop:

1. `JDBCMetadataProviderFactory.getMetadataProvider(connection)` → `MetadataProvider.getRelationIDs()`
2. filter the ids on their schema component (case-insensitive, `INFORMATION_SCHEMA` dropped) — `SchemaFilteredMetadata`
3. resolve **only** the retained ids with `CachingMetadataLookup` → no column/constraint metadata is fetched for the
   other schemas (the expensive part, and the part that fails when the connection cannot read them)
4. bootstrap: `DirectMappingEngine.getMapping(...)` per relation → `SQLPPMappingFactory.createSQLPreProcessedMapping`,
   `MappingOntologyUtils.extractAndInsertDeclarationAxioms` for the OWL file, `OntopNativeMappingSerializer` for the
   `.obda` file
5. metadata extraction: `JsonMetadata` + Jackson, i.e. the same JSON as `RDBMetadataExtractorAndSerializerImpl`

Only what the two Ontop CLI commands themselves do is re-implemented; the direct-mapping and serialization logic is
untouched.

| class | role |
|---|---|
| `SchemaFilteredMetadata` | the schema filtering, shared by both commands |
| `SchemaAwareBootstrapper` | `bootstrap` equivalent (`.obda` + `.owl`) |
| `SchemaAwareMetadataSerializer` | `extract-db-metadata` equivalent (JSON) |
| `OntopBootstrapSchema`, `OntopExtractDBMetadataSchema` | Airline commands (public `it.unibz.inf.ontop.cli.OntopCommand`) |
| `OntopSchemaCli` | `ontop-schema` CLI exposing both commands |

## Usage

```bash
mvn package

java -jar target/ontop-bootstrap-schema-1.0-SNAPSHOT.jar bootstrap-schema \
  -b http://example.org/ \
  -p db.properties \
  -m out.obda \
  -t out.owl \
  --schema sales --schema hr

java -jar target/ontop-bootstrap-schema-1.0-SNAPSHOT.jar extract-db-metadata-schema \
  -p db.properties \
  -o metadata.json \
  --schema sales
```

Without `--schema`, all visible schemas are processed (standard Ontop behaviour). Schema names are matched
case-insensitively. If none of the selected schemas holds a relation, it fails with the list of available schemas:

```
No relation found in the schema(s) [marketing]. Available schemas: [INFORMATION_SCHEMA, SALES, HR]
```

`Ontop.main` cannot be extended in place (its command list is hardcoded in `Ontop.getOntopCommandCLI()`), hence the
separate `ontop-schema` CLI; the standard Ontop commands can be added to `OntopSchemaCli` if you want a single binary.

## Tests

`mvn test` runs 11 tests against an in-memory H2 database with two schemas holding different tables and columns
(`src/test/resources/schemas.sql`):

```
SALES.CUSTOMER(CUSTOMER_ID pk, COMPANY_NAME, CONTACT_EMAIL, VAT_NUMBER unique)
SALES.INVOICE(INVOICE_ID pk, CUSTOMER_ID fk -> SALES.CUSTOMER, ISSUED_ON date, TOTAL_EUR decimal)
HR.DEPARTMENT(DEPARTMENT_ID pk, DEPARTMENT_NAME)
HR.EMPLOYEE(EMPLOYEE_ID pk, DEPARTMENT_ID fk -> HR.DEPARTMENT, LAST_NAME, HIRED_ON date, MONTHLY_WAGE decimal)
HR.EMPLOYEE_SKILL(EMPLOYEE_ID + SKILL_CODE composite pk, LEVEL)
HR.ACCOUNT_MANAGER(EMPLOYEE_ID + CUSTOMER_ID composite pk, fk -> HR.EMPLOYEE and fk -> SALES.CUSTOMER)
```

They check the mapped tables, the mapped columns and their XSD types, the composite-key subject templates, the
foreign-key object properties, the case-insensitivity, and the error on an unknown schema — for both commands.

### Cross-schema foreign keys

A foreign key whose target relation is outside the selection is silently dropped (`HR.ACCOUNT_MANAGER -> SALES.CUSTOMER`
disappears when only `hr` is selected, and reappears when both schemas are selected); foreign keys internal to the
selection are kept. Select all the schemas involved if you need those references.

## Caveat

`MetadataProvider.getRelationIDs()` still issues one `DatabaseMetaData.getTables(null, null, null, ...)` call, so the
*list* of tables of all visible schemas is still retrieved (cheap, one round-trip). Restricting that call itself needs
a change inside `AbstractDBMetadataProvider`, i.e. a patched Ontop.
