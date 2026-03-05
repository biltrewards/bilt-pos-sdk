#!/usr/bin/env node
'use strict';

// Generates an OpenAPI 3.0 YAML spec from the Nexo JSON Schema.
// Converts JSON Schema definitions to OpenAPI components/schemas using
// @openapi-contrib/json-schema-to-openapi-schema.
//
// Usage:  node generate-openapi.js
//    or:  npm run generate:openapi

const fs   = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const { convert } = require('@openapi-contrib/json-schema-to-openapi-schema');

const SCHEMA_SRC = path.join(__dirname, 'nexo_sale_to_poi_v3_0_schema.json');
const OUT_FILE   = path.join(__dirname, '..', 'docs', 'openapi.yaml');

async function main() {
    const jsonSchema = JSON.parse(fs.readFileSync(SCHEMA_SRC, 'utf8'));

    // Convert each definition from JSON Schema draft-07 to OpenAPI 3.0 schema.
    const components = {};
    for (const [name, def] of Object.entries(jsonSchema.definitions)) {
        components[name] = await convert(def);
    }

    // Rewrite internal $refs: #/definitions/Foo → #/components/schemas/Foo
    const raw = JSON.stringify(components);
    const fixed = raw.replaceAll('#/definitions/', '#/components/schemas/');
    const schemas = JSON.parse(fixed);

    const spec = {
        openapi: '3.0.3',
        info: {
            title: 'Nexo Sale to POI v3.0 — Schema Reference',
            description: 'Bilt POS terminal API schema reference based on the nexo EPAS Sale to POI protocol v3.0.',
            version: '3.0.0',
        },
        paths: {},
        components: { schemas },
    };

    fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
    fs.writeFileSync(OUT_FILE, yaml.dump(spec, { lineWidth: 120, noRefs: true }));
    console.log('Generated docs/openapi.yaml');
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});
