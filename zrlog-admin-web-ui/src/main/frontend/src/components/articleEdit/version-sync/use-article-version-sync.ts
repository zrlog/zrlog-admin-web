import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AxiosInstance } from "axios";
import { articleTabStorageKey } from "../../../utils/article-draft-storage";
import { ApiResponse } from "../../../type";
import { ArticleEditState, ArticleEntry } from "../index.types";
import {
    ArticleMergePlan,
    buildArticleMerge,
    MergeResolutions,
    MergeValue,
    resolveArticleMerge,
} from "./article-merge";

type Options = {
    conflict: ArticleEditState["contentConflict"];
    serverArticle: ArticleEntry;
    axiosInstance: AxiosInstance;
    offline: boolean;
    onResolve: (article: ArticleEntry) => Promise<boolean>;
};
type StoredMerge = { signature: string; resolutions: MergeResolutions; drafts: Record<string, string> };
const readProgress = (key: string): StoredMerge | undefined => {
    try {
        return JSON.parse(localStorage.getItem(key) || "null") || undefined;
    } catch {
        return undefined;
    }
};

// Part of the save coordinator: the UI submits decisions, never reads versions or writes articles.
const useArticleVersionSync = ({ conflict, serverArticle, axiosInstance, offline, onResolve }: Options) => {
    const [prepared, setPrepared] = useState<{ signature: string; plan: ArticleMergePlan }>();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState(false);
    const [resolutions, setResolutions] = useState<MergeResolutions>({});
    const [drafts, setDrafts] = useState<Record<string, string>>({});
    const context = useRef({ axiosInstance, onResolve, conflict, serverArticle });
    context.current = { axiosInstance, onResolve, conflict, serverArticle };
    const automaticAttempt = useRef<string>();
    const savingRef = useRef(false);
    const signature = conflict
        ? JSON.stringify([conflict?.localVersion, conflict?.serverVersion, conflict?.localArticle, serverArticle])
        : "";
    const plan = prepared?.signature === signature ? prepared.plan : undefined;
    const cacheKey = articleTabStorageKey(`merge-${serverArticle.logId}`);

    useEffect(() => {
        let cancelled = false;
        const { conflict, serverArticle } = context.current;
        setLoading(true);
        setPrepared(undefined);
        setSaveError(false);
        if (!conflict || conflict.loading || conflict.loadError || offline) return;
        const load = async () => {
            const cachedBase = conflict.baseArticle;
            let base =
                cachedBase?.logId === serverArticle.logId &&
                cachedBase?.version === conflict.localVersion &&
                conflict.localVersion <= conflict.serverVersion
                    ? cachedBase
                    : undefined;
            try {
                if (!base && conflict.localVersion <= conflict.serverVersion) {
                    const { data } = await context.current.axiosInstance.get<
                        ApiResponse<{ fromArticle: ArticleEntry; toArticle: ArticleEntry }>
                    >("/api/admin/article-version/compare", {
                        params: {
                            id: serverArticle.logId,
                            fromVersion: conflict.localVersion,
                            toVersion: conflict.serverVersion,
                        },
                        showError: false,
                    } as any);
                    if (
                        !data.error &&
                        data.data?.fromArticle?.logId === serverArticle.logId &&
                        data.data.fromArticle.version === conflict.localVersion &&
                        data.data.toArticle?.logId === serverArticle.logId &&
                        data.data.toArticle.version === conflict.serverVersion
                    )
                        base = data.data.fromArticle;
                }
            } catch {
                // Missing history cannot justify an automatic choice. Fall back to explicit differences.
            }
            if (cancelled) return;
            const nextPlan = buildArticleMerge(base, conflict.localArticle, serverArticle);
            const stored = readProgress(cacheKey);
            const restored: MergeResolutions = {};
            if (stored?.signature === signature)
                nextPlan.conflicts.forEach((item) => {
                    const value = stored.resolutions?.[item.id];
                    if (typeof value === typeof item.local) restored[item.id] = value;
                });
            setResolutions(restored);
            setDrafts(stored?.signature === signature ? stored.drafts || {} : {});
            setPrepared({ signature, plan: nextPlan });
            setLoading(false);
        };
        void load();
        return () => {
            cancelled = true;
        };
        // Signature binds the comparison and all decisions to these exact snapshots.
    }, [signature, conflict?.loading, conflict?.loadError, offline, cacheKey]);

    const merged = useMemo(() => plan && resolveArticleMerge(plan, resolutions), [plan, resolutions]);
    const remaining =
        plan?.conflicts.filter((item) => !Object.prototype.hasOwnProperty.call(resolutions, item.id)).length || 0;
    const save = useCallback(
        async (article: ArticleEntry) => {
            if (savingRef.current || !context.current.conflict || offline) return;
            savingRef.current = true;
            const savedSignature = signature;
            setSaving(true);
            setSaveError(false);
            try {
                if (await context.current.onResolve(article)) {
                    if (readProgress(cacheKey)?.signature === savedSignature) localStorage.removeItem(cacheKey);
                } else setSaveError(true);
            } catch {
                setSaveError(true);
            } finally {
                savingRef.current = false;
                setSaving(false);
            }
        },
        [cacheKey, signature, offline]
    );

    useEffect(() => {
        if (
            !conflict ||
            saving ||
            !plan ||
            !plan.baseAvailable ||
            plan.conflicts.length ||
            !merged ||
            offline ||
            automaticAttempt.current === signature
        )
            return;
        automaticAttempt.current = signature;
        void save(merged);
        // A snapshot gets one automatic attempt; repeated remote updates are checked by the save coordinator.
    }, [conflict, saving, plan, merged, offline, signature, save]);

    const persist = (nextResolutions: MergeResolutions, nextDrafts: Record<string, string>) => {
        setResolutions(nextResolutions);
        setDrafts(nextDrafts);
        const stored: StoredMerge = { signature, resolutions: nextResolutions, drafts: nextDrafts };
        try {
            localStorage.setItem(cacheKey, JSON.stringify(stored));
        } catch (error) {
            console.error(error);
        }
    };
    const choose = (id: string, value: MergeValue) => {
        const item = plan?.conflicts.find((item) => item.id === id);
        if (!item || typeof value !== typeof item.local || savingRef.current) return;
        persist({ ...resolutions, [id]: value }, drafts);
    };
    const edit = (id: string, value: string) => {
        if (!plan?.conflicts.some((item) => item.id === id && typeof item.local === "string") || savingRef.current)
            return;
        const next = { ...resolutions };
        delete next[id];
        persist(next, { ...drafts, [id]: value });
    };
    return {
        plan,
        loading,
        saving,
        saveError,
        resolutions,
        drafts,
        merged,
        remaining,
        choose,
        edit,
        save: () => merged && save(merged),
    };
};
export type ArticleVersionSync = ReturnType<typeof useArticleVersionSync>;
export default useArticleVersionSync;
