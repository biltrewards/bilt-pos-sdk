#!/usr/bin/env node
'use strict';

// Generates Java types with static inner Builder classes from the Nexo JSON Schema.
// Uses a custom quicktype renderer that owns the class emission — no private-member hacks.
//
// Usage: node generate-nexo-java.js <schema-file> <output-dir>

const {
    quicktypeMultiFile,
    InputData,
    JSONSchemaInput,
    JavaTargetLanguage,
    JacksonRenderer,
    javaOptions,
    getOptionValues,
} = require('quicktype-core');

const fs   = require('fs');
const path = require('path');

const [schemaFile, outputDir] = process.argv.slice(2);


if (!schemaFile || !outputDir) {
    console.error('Usage: node generate-nexo-java.js <schema-file> <output-dir>');
    process.exit(1);
}

const PACKAGE = 'com.bilt.pos.nexo.model';

const FILE_HEADER = `/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \\| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
`;

// ---------------------------------------------------------------------------
// Custom renderer
//
// Extends JacksonRenderer so all non-class emission (enums, unions, Converter,
// source structure) comes for free. We only override two methods:
//
//   makeNamesForPropertyGetterAndSetter — intercepts getter/setter Name objects
//     and stores them in our own public map so emitClassDefinition can use them
//     without touching any private state.
//
//   emitClassDefinition — copied from JacksonRenderer source, extended with a
//     static inner Builder class at the end of the class body.
// ---------------------------------------------------------------------------
class BuilderJavaRenderer extends JacksonRenderer {
    constructor(targetLanguage, renderContext, options) {
        super(targetLanguage, renderContext, options);
        // Our own map: property Name → [getterName, setterName]
        this.gettersAndSetters = new Map();
    }

    makeNamesForPropertyGetterAndSetter(c, className, p, jsonName, name) {
        const pair = super.makeNamesForPropertyGetterAndSetter(c, className, p, jsonName, name);
        this.gettersAndSetters.set(name, pair);
        return pair;
    }

    emitClassDefinition(c, className) {
        // Collect properties once upfront so the builder can reference them.
        const props = [];
        this.forEachClassProperty(c, 'none', (name, _jsonName, p) => {
            const [, setterName] = this.gettersAndSetters.get(name);
            props.push({ name, type: this.javaType(false, p.type), setterName });
        });

        // --- header (package + imports + Javadoc + annotations) ---
        const imports = [...this.importsForType(c), ...this.importsForClass(c)];
        this.emitFileHeader(className, imports);
        this.emitDescription(this.descriptionForType(c));
        this.emitClassAttributes(c, className);

        // --- class body (copied from JacksonRenderer.emitClassDefinition) ---
        this.emitBlock(['public class ', className], () => {
            // Fields
            this.forEachClassProperty(c, 'none', (name, _jsonName, p) => {
                this.emitLine('private ', this.javaType(false, p.type, true), ' ', name, ';');
            });

            // Getters + setters
            this.forEachClassProperty(c, 'leading-and-interposing', (name, jsonName, p) => {
                this.emitDescription(this.descriptionForClassProperty(c, jsonName));
                const [getterName, setterName] = this.gettersAndSetters.get(name);
                const rendered = this.javaType(false, p.type);
                this.annotationsForAccessor(c, className, name, jsonName, p, false)
                    .forEach(a => this.emitLine(a));
                this.emitLine('public ', rendered, ' ', getterName, '() { return ', name, '; }');
                this.annotationsForAccessor(c, className, name, jsonName, p, true)
                    .forEach(a => this.emitLine(a));
                this.emitLine('public void ', setterName, '(', rendered, ' value) { this.', name, ' = value; }');
            });

            // --- static inner Builder ---
            if (props.length === 0) return;

            this.emitLine('');
            this.emitLine('public static Builder builder() { return new Builder(); }');
            this.emitLine('');
            this.emitBlock('public static final class Builder', () => {
                for (const { name, type } of props) {
                    this.emitLine('private ', type, ' ', name, ';');
                }

                this.emitLine('');
                this.emitLine('private Builder() {}');

                for (const { name, type } of props) {
                    this.emitLine('');
                    this.emitBlock(['public Builder ', name, '(', type, ' ', name, ')'], () => {
                        this.emitLine('this.', name, ' = ', name, ';');
                        this.emitLine('return this;');
                    });
                }

                this.emitLine('');
                this.emitBlock(['public ', className, ' build()'], () => {
                    this.emitLine(className, ' result = new ', className, '();');
                    for (const { name, setterName } of props) {
                        this.emitLine('result.', setterName, '(this.', name, ');');
                    }
                    this.emitLine('return result;');
                });
            });
        });

        this.finishFile();
    }
}

class BuilderJavaTargetLanguage extends JavaTargetLanguage {
    makeRenderer(renderContext, untypedOptionValues) {
        const options = getOptionValues(javaOptions, untypedOptionValues);
        return new BuilderJavaRenderer(this, renderContext, options);
    }
}

// ---------------------------------------------------------------------------
// Generation
// ---------------------------------------------------------------------------
async function run() {
    const schemaContent = fs.readFileSync(schemaFile, 'utf8');

    const schemaInput = new JSONSchemaInput(undefined);
    await schemaInput.addSource({ name: 'NexoTerminalAPI', schema: schemaContent });

    const inputData = new InputData();
    inputData.addInput(schemaInput);

    const result = await quicktypeMultiFile({
        inputData,
        lang: new BuilderJavaTargetLanguage(),
        rendererOptions: { package: PACKAGE },
    });

    fs.mkdirSync(outputDir, { recursive: true });
    fs.readdirSync(outputDir)
        .filter(f => f.endsWith('.java'))
        .forEach(f => fs.rmSync(path.join(outputDir, f)));

    let count = 0;
    for (const [filename, renderResult] of result) {
        fs.writeFileSync(path.join(outputDir, filename), FILE_HEADER + renderResult.lines.join('\n'));
        count++;
    }

    console.log(`Generated ${count} Java files in ${outputDir}`);
}

run().catch(err => {
    console.error(err);
    process.exit(1);
});
