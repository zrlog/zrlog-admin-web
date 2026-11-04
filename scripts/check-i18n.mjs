import fs from "node:fs";
import path from "node:path";
import {execFile} from "node:child_process";
import {parseArgs, promisify} from "node:util";
import {checkLocales, createI18nProgram, findI18nUsage, frontend, readLocales, root, ts} from "./admin-i18n.mjs";

const {values} = parseArgs({options: {base: {type: "string"}, summary: {type: "string"}}});
const program = createI18nProgram();
const locales = readLocales(program);
const entries = locales.get("zhCN");
const {used} = findI18nUsage(program, entries);
const errors = checkLocales(locales, used);

const execute = promisify(execFile);
const git = async args => (await execute("git", args, {cwd: root, encoding: "utf8"})).stdout;

async function baseEntries(ref) {
    const sha = (await git(["rev-parse", "--verify", "--end-of-options", `${ref}^{commit}`])).trim();
    const relative = path.relative(root, path.join(frontend, "src/i18n"));
    const files = (await git(["ls-tree", "-r", "--name-only", sha, "--", relative]))
        .trim().split("\n").filter(file => file.endsWith(".ts"));
    const sources = new Map(await Promise.all(files.map(async file => [path.join(root, file),
        await git(["show", `${sha}:${file}`])])));
    const options = {...program.getCompilerOptions(), types: []};
    const host = ts.createCompilerHost(options);
    const readFile = host.readFile;
    const fileExists = host.fileExists;
    host.readFile = file => sources.get(path.resolve(file)) ?? readFile(file);
    host.fileExists = file => sources.has(path.resolve(file)) || fileExists(file);
    return readLocales(ts.createProgram([...sources.keys()], options, host)).get("zhCN");
}

const previous = values.base ? await baseEntries(values.base) : undefined;
const groups = new Map();
for (const [key, entry] of entries) {
    const group = entry.path[0];
    const stats = groups.get(group) || {count: 0, added: 0, removed: 0};
    stats.count++;
    if (previous && !previous.has(key)) stats.added++;
    groups.set(group, stats);
}
for (const [key, entry] of previous || []) {
    if (entries.has(key)) continue;
    const group = entry.path[0];
    const stats = groups.get(group) || {count: 0, added: 0, removed: 0};
    stats.removed++;
    groups.set(group, stats);
}
const added = [...groups.values()].reduce((total, group) => total + group.added, 0);
const removed = [...groups.values()].reduce((total, group) => total + group.removed, 0);
const message = `Admin i18n: ${entries.size} bilingual keys; ${errors.length} issues`
    + (previous ? `; +${added} / -${removed} keys against ${values.base}` : "");
console.log(message);
for (const error of errors) console.error(error);
if (values.summary) {
    const lines = ["## Admin i18n", "", message, "", "| Namespace | Keys | Added | Removed |", "| --- | ---: | ---: | ---: |"];
    for (const [group, stats] of [...groups].sort((a, b) => b[1].count - a[1].count)) {
        lines.push(`| ${group} | ${stats.count} | ${previous ? stats.added : "—"} | ${previous ? stats.removed : "—"} |`);
    }
    if (errors.length) lines.push("", ...errors.map(error => `- ${error}`));
    fs.appendFileSync(values.summary, lines.join("\n") + "\n\n");
}
if (errors.length) process.exitCode = 1;
