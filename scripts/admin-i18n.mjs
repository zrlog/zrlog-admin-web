import path from "node:path";
import {createRequire} from "node:module";
import {fileURLToPath} from "node:url";

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
export const frontend = path.join(root, "zrlog-admin-web-ui/src/main/frontend");
const require = createRequire(path.join(frontend, "package.json"));
export const ts = require("typescript");

export function createI18nProgram(directory = frontend) {
    const config = ts.readConfigFile(path.join(directory, "tsconfig.json"), ts.sys.readFile);
    if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, "\n"));
    const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, directory);
    return ts.createProgram(parsed.fileNames, parsed.options);
}

// Read literal dictionaries and their local imports/spreads without executing application code.
export function readLocales(program, entry = path.join(frontend, "src/i18n/admin.ts")) {
    const checker = program.getTypeChecker();
    const source = program.getSourceFile(entry);
    if (!source) throw new Error(`Missing dictionary entry: ${entry}`);

    function resolve(node) {
        while (ts.isAsExpression(node) || ts.isSatisfiesExpression(node) || ts.isParenthesizedExpression(node)) {
            node = node.expression;
        }
        if (ts.isPropertyAccessExpression(node)) {
            const object = resolve(node.expression);
            if (ts.isObjectLiteralExpression(object)) {
                const property = object.properties.find(property => property.name?.text === node.name.text);
                if (property?.initializer) return resolve(property.initializer);
            }
        }
        if (ts.isIdentifier(node) || ts.isPropertyAccessExpression(node)) {
            let symbol = checker.getSymbolAtLocation(ts.isPropertyAccessExpression(node) ? node.name : node);
            if (symbol?.flags & ts.SymbolFlags.Alias) symbol = checker.getAliasedSymbol(symbol);
            const declaration = symbol?.valueDeclaration;
            if (declaration?.initializer) return resolve(declaration.initializer);
        }
        return node;
    }

    function flatten(expression, prefix, result, declaration, deprecated = false) {
        const node = resolve(expression);
        deprecated ||= Boolean(declaration && (ts.getJSDocDeprecatedTag(declaration)
            || /@deprecated\b/.test(declaration.getSourceFile().text.slice(declaration.pos, declaration.getStart()))));
        if (ts.isObjectLiteralExpression(node)) {
            for (const property of node.properties) {
                if (ts.isSpreadAssignment(property)) {
                    flatten(property.expression, prefix, result, property, deprecated);
                } else if (ts.isPropertyAssignment(property)) {
                    flatten(property.initializer, [...prefix, property.name.text], result, property, deprecated);
                } else {
                    throw new Error(`Expected a literal i18n property: ${property.getText()}`);
                }
            }
            return;
        }
        const value = ts.isStringLiteralLike(node) ? node.text
            : ts.isArrayLiteralExpression(node) && node.elements.every(ts.isStringLiteralLike)
                ? node.elements.map(element => element.text) : undefined;
        if (value === undefined) throw new Error(`Expected i18n text at ${prefix.join(".")}`);
        const key = prefix.join(".");
        if (result.has(key)) throw new Error(`Duplicate i18n key: ${key}`);
        result.set(key, {value, path: prefix, declaration, deprecated});
    }

    const locales = new Map();
    for (const statement of source.statements) {
        if (!ts.isVariableStatement(statement)) continue;
        for (const declaration of statement.declarationList.declarations) {
            if (!["zhCN", "enUS"].includes(declaration.name.text)) continue;
            const entries = new Map();
            flatten(declaration.initializer, [], entries, declaration);
            locales.set(declaration.name.text, entries);
        }
    }
    if (locales.size !== 2) throw new Error("Both zhCN and enUS dictionaries are required");
    return locales;
}

export function findI18nUsage(program, entries, {directory = path.join(frontend, "src"), includeTests = false} = {}) {
    const checker = program.getTypeChecker();
    const declarations = new Map([...entries].map(([key, entry]) => [entry.declaration, key]));
    const used = new Set();
    const references = new Map();

    function mark(symbol, reference) {
        for (const declaration of symbol?.getDeclarations() || []) {
            const key = declarations.get(declaration);
            if (!key) continue;
            used.add(key);
            if (reference) references.set(reference, key);
        }
    }

    function markType(type, seen = new Set()) {
        if (seen.has(type)) return;
        seen.add(type);
        if (type.isUnion()) return type.types.forEach(member => markType(member, seen));
        for (const symbol of type.getProperties()) {
            mark(symbol);
            // Only descend through dictionary properties, never through arbitrary application types.
            if (symbol.getDeclarations()?.some(declaration =>
                declaration.getSourceFile().fileName.includes("/src/i18n/"))) {
                markType(checker.getTypeOfSymbolAtLocation(symbol, symbol.valueDeclaration || symbol.declarations[0]), seen);
            }
        }
    }

    function markShape(actual, expected, seen = new Set()) {
        actual = checker.getNonNullableType(actual);
        expected = checker.getNonNullableType(expected);
        if (seen.has(actual)) return;
        seen.add(actual);
        if (expected.getStringIndexType()) return markType(actual);
        for (const property of expected.getProperties()) {
            const original = actual.getProperty(property.name);
            if (!original?.getDeclarations()?.some(declaration =>
                declaration.getSourceFile().fileName.includes("/src/i18n/"))) continue;
            // An explicit UI/library contract can erase the dictionary's property declarations.
            // Types derived from the dictionary keep them, so their reads are checked normally.
            if (!property.getDeclarations()?.some(declaration => declarations.has(declaration))) mark(original);
            const location = original.valueDeclaration || original.declarations[0];
            markShape(checker.getTypeOfSymbolAtLocation(original, location),
                checker.getTypeOfSymbolAtLocation(property, location), seen);
        }
    }

    function visit(node) {
        if (ts.isPropertyAccessExpression(node)) mark(checker.getSymbolAtLocation(node.name), node);
        if (ts.isElementAccessExpression(node) && node.argumentExpression) {
            const object = checker.getTypeAtLocation(node.expression);
            const index = checker.getTypeAtLocation(node.argumentExpression);
            const keys = index.isUnion() ? index.types : [index];
            if (keys.every(type => type.isStringLiteral() || type.isNumberLiteral())) {
                for (const key of keys) mark(object.getProperty(String(key.value)), node);
            } else {
                markType(object);
            }
        }
        if (ts.isBindingElement(node) && ts.isObjectBindingPattern(node.parent)) {
            const property = node.propertyName || node.name;
            if (ts.isIdentifier(property) || ts.isStringLiteralLike(property)) {
                mark(checker.getTypeAtLocation(node.parent).getProperty(property.text), node);
            }
        }
        if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
            && node.expression.expression.getText() === "Object"
            && ["entries", "values", "keys"].includes(node.expression.name.text) && node.arguments[0]) {
            markType(checker.getTypeAtLocation(node.arguments[0]));
        }
        if (ts.isAsExpression(node)) {
            markShape(checker.getTypeAtLocation(node.expression), checker.getTypeAtLocation(node));
        }
        if (ts.isVariableDeclaration(node) && node.type && node.initializer) {
            markShape(checker.getTypeAtLocation(node.initializer), checker.getTypeAtLocation(node.name));
        }
        if (ts.isCallExpression(node)) {
            for (const argument of node.arguments) {
                const context = checker.getContextualType(argument);
                if (context) markShape(checker.getTypeAtLocation(argument), context);
            }
        }
        if (ts.isJsxExpression(node) && node.expression) {
            const context = checker.getContextualType(node.expression);
            if (context) markShape(checker.getTypeAtLocation(node.expression), context);
        }
        ts.forEachChild(node, visit);
    }

    for (const source of program.getSourceFiles()) {
        const relative = path.relative(directory, source.fileName).split(path.sep).join("/");
        if (relative.startsWith("..") || source.isDeclarationFile || relative.startsWith("i18n/")) continue;
        if (!includeTests && (/(^|\/)(__tests__|test|tests)\//.test(relative)
            || /\.(test|spec)\.[cm]?[jt]sx?$/.test(relative))) continue;
        visit(source);
    }
    return {used, references};
}

export function checkLocales(locales, used) {
    const zh = locales.get("zhCN");
    const en = locales.get("enUS");
    const errors = [];
    const placeholders = value => [...new Set(value.match(/\{[A-Za-z][A-Za-z0-9_]*\}/g) || [])].sort().join(",");
    for (const key of new Set([...zh.keys(), ...en.keys()])) {
        const chinese = zh.get(key);
        const english = en.get(key);
        if (!chinese || !english) {
            errors.push(`Missing ${chinese ? "enUS" : "zhCN"} key: ${key}`);
            continue;
        }
        const left = chinese.value;
        const right = english.value;
        if (Array.isArray(left) !== Array.isArray(right)
            || (Array.isArray(left) && left.length !== right.length)) {
            errors.push(`Different locale shapes: ${key}`);
            continue;
        }
        const pairs = Array.isArray(left) ? left.map((value, index) => [value, right[index]]) : [[left, right]];
        if (pairs.some(([a, b]) => placeholders(a) !== placeholders(b))) errors.push(`Different placeholders: ${key}`);
        if (!chinese.deprecated && !english.deprecated && !used.has(key)) errors.push(`Unused i18n key: ${key}`);
    }
    // These button labels have a single stable meaning; business-specific messages stay local.
    for (const action of ["close", "cancel", "save", "delete", "refresh", "retry"]) {
        const canonical = `common.${action}`;
        if (!zh.has(canonical) || !en.has(canonical)) continue;
        for (const [key, entry] of zh) {
            if (key === canonical || entry.deprecated || en.get(key)?.deprecated) continue;
            if (entry.value === zh.get(canonical).value && en.get(key)?.value === en.get(canonical).value) {
                errors.push(`Reuse ${canonical} instead of duplicating: ${key}`);
            }
        }
    }
    return errors;
}
