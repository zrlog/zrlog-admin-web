import fs from "node:fs";
import path from "node:path";
import {createI18nProgram, readLocales, root} from "./admin-i18n.mjs";

const locales = new Map();
for (const [locale, entries] of readLocales(createI18nProgram())) {
    locales.set(locale, new Map([...entries.values()]
        .filter(entry => entry.path[0] === "access" && entry.path[1] === "endpointDescriptions")
        .map(entry => [entry.path.slice(2).join("."), entry.value])));
}

function* javaFiles(directory) {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) yield* javaFiles(file);
        else if (entry.name.endsWith(".java")) yield file;
    }
}

const keys = new Set();
let methods = 0;
const controllers = fs.readdirSync(root).filter(name => name.startsWith("zrlog-admin-"))
    .map(name => path.join(root, name, "src/main/java/com/zrlog/admin/web/controller"))
    .filter(directory => fs.existsSync(directory));
for (const file of controllers.flatMap(directory => [...javaFiles(directory)])) {
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
