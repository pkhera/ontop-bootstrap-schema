#!/usr/bin/env bash
# Runs both commands against the H2 demo database (schemas SALES and HR, see src/test/resources/schemas.sql).
# All the generated files are kept in demo/output/.
set -u

cd "$(dirname "$0")/.."

JAR=target/ontop-bootstrap-schema-1.0-SNAPSHOT.jar
OUT=demo/output
PROPS=demo/db.properties
BASE_IRI=http://example.org/

[ -f "$JAR" ] || mvn -q package -DskipTests

rm -rf "$OUT" demo/testdb.mv.db
mkdir -p "$OUT"

run() { java -jar "$JAR" "$@"; }

echo "== bootstrap: all schemas =="
run bootstrap-schema -b "$BASE_IRI" -p "$PROPS" -m "$OUT/all.obda" -t "$OUT/all.owl"

echo "== bootstrap: --schema sales =="
run bootstrap-schema -b "$BASE_IRI" -p "$PROPS" -m "$OUT/sales.obda" -t "$OUT/sales.owl" --schema sales

echo "== bootstrap: --schema HR (case-insensitive) =="
run bootstrap-schema -b "$BASE_IRI" -p "$PROPS" -m "$OUT/hr.obda" -t "$OUT/hr.owl" --schema HR

echo "== extract-db-metadata: all schemas =="
run extract-db-metadata-schema -p "$PROPS" -o "$OUT/metadata-all.json"

echo "== extract-db-metadata: --schema sales =="
run extract-db-metadata-schema -p "$PROPS" -o "$OUT/metadata-sales.json" --schema sales

echo "== extract-db-metadata: --schema hr =="
run extract-db-metadata-schema -p "$PROPS" -o "$OUT/metadata-hr.json" --schema hr

echo "== unknown schema (expected error) =="
run extract-db-metadata-schema -p "$PROPS" -o "$OUT/metadata-unknown.json" --schema marketing \
    2>&1 | grep "No relation found" | tee "$OUT/unknown-schema.log"

echo
echo "Generated files:"
ls -l "$OUT"
