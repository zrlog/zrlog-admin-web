import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "src/main/frontend/package.json"));
const ts = require("typescript");
const resourcePath = path.join(root, "src/main/frontend/src/i18n/admin.ts");
const source = ts.createSourceFile(resourcePath, fs.readFileSync(resourcePath, "utf8"), ts.ScriptTarget.Latest, true);
const property = (object, name) => object?.properties?.find(item => item.name?.text === name)?.initializer;
const locales = new Map();
for (const statement of source.statements) {
    if (!ts.isVariableStatement(statement)) continue;
    for (const declaration of statement.declarationList.declarations) {
        if (!["zhCN", "enUS"].includes(declaration.name.text)) continue;
        const initializer = ts.isAsExpression(declaration.initializer) ? declaration.initializer.expression : declaration.initializer;
        const descriptions = property(property(initializer, "access"), "endpointDescriptions");
        if (!descriptions || !ts.isObjectLiteralExpression(descriptions)) throw new Error(`Missing descriptions: ${declaration.name.text}`);
        locales.set(declaration.name.text, new Map(descriptions.properties.map(item => [item.name.text, item.initializer?.text])));
    }
}
if (locales.size !== 2) throw new Error("Both Chinese and English descriptions are required");
function* javaFiles(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) yield* javaFiles(file);
        else if (entry.name.endsWith(".java")) yield file;
    }
}
const keys = new Set();
let methods = 0;
for (const file of javaFiles(path.join(root, "src/main/java/com/zrlog/admin/web/controller"))) {
    for (const binding of fs.readFileSync(file, "utf8").matchAll(/@RequiresAction\(([^)]*)\)/g)) {
        const key = binding[1].match(/descriptionKey\s*=\s*"([^"]+)"/)?.[1];
        if (!key) throw new Error(`Missing endpoint description key: ${file}`);
        for (const [locale, descriptions] of locales) {
            if (!descriptions.get(key)?.trim()) throw new Error(`${locale} missing endpoint description: ${key}`);
        }
        keys.add(key);
        methods++;
    }
}
for (const [locale, descriptions] of locales) {
    for (const key of descriptions.keys()) {
        if (!keys.has(key)) throw new Error(`${locale} has an unused endpoint description: ${key}`);
    }
}
console.log(`Endpoint descriptions OK: ${methods} methods, ${keys.size} bilingual descriptions`);
