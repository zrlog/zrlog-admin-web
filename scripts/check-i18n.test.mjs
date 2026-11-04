import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {checkLocales, createI18nProgram, findI18nUsage, readLocales} from "./admin-i18n.mjs";

function fixture(t, chinese, english, consumer, extra = {}) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), "admin-i18n-test-"));
    t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
    const files = {
        "tsconfig.json": JSON.stringify({compilerOptions: {strict: true, target: "es2020", types: [], skipLibCheck: true}, include: ["src"]}),
        "src/i18n/types.ts": "export type Shape<T> = {[K in keyof T]: T[K] extends string ? string : Shape<T[K]>};",
        "src/i18n/resources/page.ts": `import type {Shape} from '../types';
            export const zhCN = ${chinese} as const;
            export const enUS: Shape<typeof zhCN> = ${english};`,
        "src/i18n/admin.ts": `import * as page from './resources/page';
            const zhCN = {...page.zhCN}; const enUS = {...page.enUS};
            export const getAdminI18n = () => zhCN;`,
        "src/page.ts": `import {getAdminI18n} from './i18n/admin'; ${consumer}`,
        ...extra,
    };
    for (const [name, text] of Object.entries(files)) {
        const file = path.join(directory, name);
        fs.mkdirSync(path.dirname(file), {recursive: true});
        fs.writeFileSync(file, text);
    }
    const program = createI18nProgram(directory);
    const locales = readLocales(program, path.join(directory, "src/i18n/admin.ts"));
    const usage = findI18nUsage(program, locales.get("zhCN"), {directory: path.join(directory, "src")});
    return {locales, used: usage.used, errors: checkLocales(locales, usage.used)};
}

test("aliases, destructuring and object copies retain real references; tests do not keep stale keys alive", t => {
    const result = fixture(t, '{page:{title:"标题",subtitle:"副标题",stale:"旧文案"}}',
        '{page:{title:"Title",subtitle:"Subtitle",stale:"Stale"}}',
        'const copy = {...getAdminI18n()}; const labels = copy.page; const {title: heading} = labels; console.log(heading, labels.subtitle);',
        {"src/page.test.ts": "import {getAdminI18n} from './i18n/admin'; getAdminI18n().page.stale;"});
    assert.deepEqual([...result.used].sort(), ["page.subtitle", "page.title"]);
    assert.deepEqual(result.errors, ["Unused i18n key: page.stale"]);
});

test("dynamic lookup, enumeration and string arrays preserve live entries", t => {
    const result = fixture(t, '{statuses:{done:"完成",failed:"失败"},days:["周一","周二"]}',
        '{statuses:{done:"Done",failed:"Failed"},days:["Mon","Tue"]}',
        `const res = getAdminI18n(); const status: 'done'|'failed' = Math.random() ? 'done' : 'failed';
         console.log(res.statuses[status], Object.values(res.statuses), res.days[0]);`);
    assert.deepEqual(result.errors, []);
    assert.equal(result.used.size, 3);
});

test("Record casts and typed consumers preserve only the dictionaries they consume", t => {
    const result = fixture(t, '{statuses:{done:"完成",failed:"失败"},worker:{message:"已部署"},unused:"旧文案"}',
        '{statuses:{done:"Done",failed:"Failed"},worker:{message:"Deployed"},unused:"Stale"}',
        `const res = getAdminI18n(); const labels: Record<'done'|'failed', string> = res.statuses;
         console.log((res.statuses as Record<string,string>)[String(Math.random())], labels.done);
         function buildWorker(messages: {message: string}) {return messages.message;}
         buildWorker(res.worker);`);
    assert.deepEqual([...result.used].sort(), ["statuses.done", "statuses.failed", "worker.message"]);
    assert.deepEqual(result.errors, ["Unused i18n key: unused"]);
});

test("literal dotted protocol keys and both languages survive namespace composition", t => {
    const result = fixture(t, '{endpoints:{"article.create":"创建文章"}}', '{endpoints:{"article.create":"Create article"}}',
        'console.log(getAdminI18n().endpoints["article.create"]);', {
            "src/i18n/admin.ts": `import * as page from './resources/page';
                const zhCN = {access: {descriptions: page.zhCN.endpoints}};
                const enUS = {access: {descriptions: page.enUS.endpoints}};
                export const getAdminI18n = () => page.zhCN;`,
        });
    const entry = result.locales.get("enUS").get("access.descriptions.article.create");
    assert.deepEqual(entry.path, ["access", "descriptions", "article.create"]);
    assert.equal(entry.value, "Create article");
    assert.deepEqual(result.errors, []);
});

test("missing language entries and interpolation mismatches fail the check", t => {
    const result = fixture(t, '{count:"共 {count} 条",missing:"缺少翻译"}', '{count:"Total {total}"}',
        'console.log(getAdminI18n().count, getAdminI18n().missing);');
    assert.deepEqual(result.errors, ["Different placeholders: count", "Missing enUS key: missing"]);
});

test("array length and placeholder mismatches cannot bypass locale validation", t => {
    const result = fixture(t, '{days:["周一","周二"],totals:["共 {count} 条"]}', '{days:["Mon"],totals:["Total {total}"]}',
        'console.log(getAdminI18n().days, getAdminI18n().totals);');
    assert.deepEqual(result.errors, ["Different locale shapes: days", "Different placeholders: totals"]);
});

test("new duplicate common actions fail while different English meanings remain independent", t => {
    const result = fixture(t, '{common:{close:"关闭"},panel:{close:"关闭",disable:"关闭"}}',
        '{common:{close:"Close"},panel:{close:"Close",disable:"Disable"}}',
        'const res=getAdminI18n(); console.log(res.common.close,res.panel.close,res.panel.disable);');
    assert.deepEqual(result.errors, ["Reuse common.close instead of duplicating: panel.close"]);
});

test("deprecated dictionary entries are preserved without an unused-key exemption list", t => {
    const result = fixture(t, '{page:{title:"标题",/** @deprecated compatibility */ legacy:"旧文案"}}',
        '{page:{title:"Title",legacy:"Legacy"}}', 'console.log(getAdminI18n().page.title);');
    assert.equal(result.locales.get("zhCN").get("page.legacy").deprecated, true);
    assert.deepEqual(result.errors, []);
});

test("duplicate dictionary properties are rejected before a spread can silently overwrite them", t => {
    assert.throws(() => fixture(t, '{page:{title:"标题"},page:{title:"覆盖"}}', '{page:{title:"Title"}}', ''),
        /Duplicate i18n key: page.title/);
});
