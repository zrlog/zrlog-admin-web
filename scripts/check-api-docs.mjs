import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, "..");
const frontendPackage = path.join(rootDir, "src/main/frontend/package.json");
const require = createRequire(frontendPackage);
const { parseDocument } = require("yaml");

const openApiPath = path.join(rootDir, "docs/api/openapi.yaml");
const source = fs.readFileSync(openApiPath, "utf8");
const document = parseDocument(source, { prettyErrors: true, uniqueKeys: true });

if (document.errors.length > 0) {
    for (const error of document.errors) {
        process.stderr.write(`${error.message}\n`);
    }
    process.exit(1);
}

const spec = document.toJS();
if (spec.openapi !== "3.1.0") {
    throw new Error(`Expected OpenAPI 3.1.0, got ${spec.openapi || "missing"}`);
}
if (!spec.paths || Object.keys(spec.paths).length === 0) {
    throw new Error("OpenAPI paths must not be empty");
}

const httpMethods = new Set(["get", "post", "put", "patch", "delete", "options", "head", "trace"]);
const responseKinds = new Set(["page-hydration", "action", "stream", "download"]);
const operationIds = new Set();
let operationCount = 0;

const resolveLocalReference = (reference) => {
    if (!reference.startsWith("#/")) {
        throw new Error(`Only local OpenAPI references are supported: ${reference}`);
    }
    return reference
        .substring(2)
        .split("/")
        .map((part) => part.replaceAll("~1", "/").replaceAll("~0", "~"))
        .reduce((value, part) => value?.[part], spec);
};

const validateReferences = (value) => {
    if (Array.isArray(value)) {
        value.forEach(validateReferences);
        return;
    }
    if (!value || typeof value !== "object") {
        return;
    }
    if (typeof value.$ref === "string" && !resolveLocalReference(value.$ref)) {
        throw new Error(`Unresolved OpenAPI reference: ${value.$ref}`);
    }
    Object.values(value).forEach(validateReferences);
};

validateReferences(spec);

for (const [apiPath, pathItem] of Object.entries(spec.paths)) {
    if (!apiPath.startsWith("/")) {
        throw new Error(`API path must start with '/': ${apiPath}`);
    }
    for (const [method, operation] of Object.entries(pathItem)) {
        if (!httpMethods.has(method)) {
            continue;
        }
        operationCount += 1;
        const operationId = operation.operationId;
        if (!operationId) {
            throw new Error(`${method.toUpperCase()} ${apiPath} is missing operationId`);
        }
        if (operationIds.has(operationId)) {
            throw new Error(`Duplicate operationId: ${operationId}`);
        }
        operationIds.add(operationId);

        const responseKind = operation["x-zrlog-response-kind"];
        if (!responseKinds.has(responseKind)) {
            throw new Error(`${operationId} has invalid x-zrlog-response-kind: ${responseKind || "missing"}`);
        }
        if (!operation.responses || Object.keys(operation.responses).length === 0) {
            throw new Error(`${operationId} must declare responses`);
        }

        const controllerRef = operation["x-zrlog-controller"];
        const match = /^([A-Za-z0-9_.]+)#([A-Za-z0-9_]+)$/.exec(controllerRef || "");
        if (!match) {
            throw new Error(`${operationId} has invalid x-zrlog-controller`);
        }
        const [, className, methodName] = match;
        const controllerPath = path.join(rootDir, "src/main/java", `${className.replaceAll(".", "/")}.java`);
        if (!fs.existsSync(controllerPath)) {
            throw new Error(`${operationId} controller does not exist: ${controllerPath}`);
        }
        const controllerSource = fs.readFileSync(controllerPath, "utf8");
        const methodPattern = new RegExp(`\\b${methodName}\\s*\\(`);
        const methodMatch = methodPattern.exec(controllerSource);
        if (!methodMatch) {
            throw new Error(`${operationId} controller method does not exist: ${controllerRef}`);
        }
        const annotationBlock = controllerSource.substring(Math.max(0, methodMatch.index - 500), methodMatch.index);
        const methodAnnotation = `@RequestMethod(method = HttpMethod.${method.toUpperCase()})`;
        if (!annotationBlock.includes(methodAnnotation)) {
            throw new Error(`${operationId} must explicitly declare ${methodAnnotation}`);
        }
    }
}

process.stdout.write(`OpenAPI contract OK: ${operationCount} operation(s)\n`);
