import { ArticleEntry } from "../index.types";

const scalarFields = [
    "title",
    "digest",
    "keywords",
    "alias",
    "thumbnail",
    "typeId",
    "canComment",
    "recommended",
    "privacy",
    "rubbish",
] as const;
export type ArticleMergeField = typeof scalarFields[number] | "markdown" | "content";
export type MergeValue = string | number | boolean;
export type MergeResolutions = Record<string, MergeValue>;
export type ArticleMergeConflict = {
    id: string;
    field: ArticleMergeField;
    base?: MergeValue;
    local: MergeValue;
    server: MergeValue;
};
type BodyPart = { text: string } | { conflictId: string };
export type ArticleMergePlan = {
    article: ArticleEntry;
    conflicts: ArticleMergeConflict[];
    bodyField: "markdown" | "content";
    body: BodyPart[];
    baseAvailable: boolean;
};
type TextEdit = { start: number; end: number; text: string; side: "local" | "server" };
const lines = (text: string) => text.match(/[^\n]*\n|[^\n]+$/g) || [];

// Bound memory for long articles. A coarse edit is conservative: it can add a conflict,
// but cannot silently discard changes that a more detailed diff would have found.
const edits = (before: string[], after: string[], side: TextEdit["side"]): TextEdit[] => {
    let prefix = 0;
    while (prefix < before.length && prefix < after.length && before[prefix] === after[prefix]) prefix++;
    let suffix = 0;
    while (
        suffix < before.length - prefix &&
        suffix < after.length - prefix &&
        before[before.length - 1 - suffix] === after[after.length - 1 - suffix]
    )
        suffix++;
    const a = before.slice(prefix, before.length - suffix),
        b = after.slice(prefix, after.length - suffix);
    if (!a.length && !b.length) return [];
    if ((a.length + 1) * (b.length + 1) > 1_000_000) {
        return [{ start: prefix, end: before.length - suffix, text: b.join(""), side }];
    }
    const table = Array.from({ length: a.length + 1 }, () => new Uint32Array(b.length + 1));
    for (let i = a.length - 1; i >= 0; i--)
        for (let j = b.length - 1; j >= 0; j--) {
            table[i][j] = a[i] === b[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
        }
    const result: TextEdit[] = [];
    let i = 0,
        j = 0,
        pending: TextEdit | undefined;
    const flush = () => {
        if (pending) result.push(pending);
        pending = undefined;
    };
    while (i < a.length || j < b.length) {
        if (i < a.length && j < b.length && a[i] === b[j]) {
            flush();
            i++;
            j++;
            continue;
        }
        pending ||= { start: prefix + i, end: prefix + i, text: "", side };
        if (j < b.length && (i === a.length || table[i][j + 1] > table[i + 1][j])) pending.text += b[j++];
        else {
            i++;
            pending.end = prefix + i;
        }
    }
    flush();
    return result;
};

const overlaps = (a: TextEdit, b: TextEdit) => {
    if (a.start === a.end) return a.start >= b.start && a.start <= b.end;
    if (b.start === b.end) return b.start >= a.start && b.start <= a.end;
    return a.start < b.end && b.start < a.end;
};

const applyEdits = (base: string[], start: number, end: number, changes: TextEdit[]) => {
    let cursor = start,
        text = "";
    changes.forEach((change) => {
        text += base.slice(cursor, change.start).join("") + change.text;
        cursor = change.end;
    });
    return text + base.slice(cursor, end).join("");
};

const mergeText = (
    baseText: string | undefined,
    local: string,
    server: string,
    field: "markdown" | "content",
    conflicts: ArticleMergeConflict[]
): BodyPart[] => {
    if (local === server || baseText === local) return [{ text: server }];
    if (baseText === server) return [{ text: local }];
    if (baseText === undefined) {
        conflicts.push({ id: field, field, local, server });
        return [{ conflictId: field }];
    }
    const base = lines(baseText);
    const changes = [...edits(base, lines(local), "local"), ...edits(base, lines(server), "server")].sort(
        (a, b) => a.start - b.start || a.end - b.end
    );
    const groups: TextEdit[][] = [];
    for (const change of changes) {
        const group = groups[groups.length - 1];
        if (group?.some((item) => overlaps(item, change))) group.push(change);
        else groups.push([change]);
    }
    const parts: BodyPart[] = [];
    let cursor = 0;
    groups.forEach((group, index) => {
        const start = group[0].start,
            end = Math.max(...group.map((change) => change.end));
        parts.push({ text: base.slice(cursor, start).join("") });
        const original = base.slice(start, end).join("");
        const localText = applyEdits(
            base,
            start,
            end,
            group.filter((change) => change.side === "local")
        );
        const serverText = applyEdits(
            base,
            start,
            end,
            group.filter((change) => change.side === "server")
        );
        if (localText === serverText || localText === original) parts.push({ text: serverText });
        else if (serverText === original) parts.push({ text: localText });
        else {
            const id = `${field}:${index}`;
            conflicts.push({ id, field, base: original, local: localText, server: serverText });
            parts.push({ conflictId: id });
        }
        cursor = end;
    });
    parts.push({ text: base.slice(cursor).join("") });
    return parts;
};

const valueOf = (article: ArticleEntry, field: typeof scalarFields[number]): MergeValue => {
    if (field === "typeId") return article.typeId ?? 0;
    if (["canComment", "recommended", "privacy", "rubbish"].includes(field)) return Boolean(article[field]);
    return String(article[field] ?? "");
};

export const buildArticleMerge = (
    base: ArticleEntry | undefined,
    local: ArticleEntry,
    server: ArticleEntry
): ArticleMergePlan => {
    const article = { ...server };
    const conflicts: ArticleMergeConflict[] = [];
    scalarFields.forEach((field) => {
        const mine = valueOf(local, field),
            theirs = valueOf(server, field),
            original = base && valueOf(base, field);
        if (mine === theirs || mine === original) Object.assign(article, { [field]: theirs });
        else if (theirs === original) Object.assign(article, { [field]: mine });
        else conflicts.push({ id: field, field, base: original, local: mine, server: theirs });
    });
    const bodyField = [base, local, server].some((value) => value?.markdown !== undefined && value.markdown !== null)
        ? "markdown"
        : "content";
    const bodyText = (value: ArticleEntry) =>
        bodyField === "markdown" ? value.markdown ?? value.content ?? "" : value.content || "";
    const body = mergeText(base ? bodyText(base) : undefined, bodyText(local), bodyText(server), bodyField, conflicts);
    return { article, conflicts, bodyField, body, baseAvailable: Boolean(base) };
};

export const resolveArticleMerge = (
    plan: ArticleMergePlan,
    resolutions: MergeResolutions
): ArticleEntry | undefined => {
    if (plan.conflicts.some((conflict) => !Object.prototype.hasOwnProperty.call(resolutions, conflict.id)))
        return undefined;
    const article = { ...plan.article };
    plan.conflicts
        .filter((conflict) => conflict.field !== plan.bodyField)
        .forEach((conflict) => {
            Object.assign(article, { [conflict.field]: resolutions[conflict.id] });
        });
    article[plan.bodyField] = plan.body
        .map((part) => ("text" in part ? part.text : String(resolutions[part.conflictId])))
        .join("");
    if (plan.bodyField === "markdown") {
        article.content = undefined;
        article.editorType = "markdown";
    }
    return article;
};
