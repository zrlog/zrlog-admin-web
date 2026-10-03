import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { beforeEach, afterEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleEntry } from "../index.types";
import useArticleVersionSync from "./use-article-version-sync";

const environment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
const base: ArticleEntry = {
    logId: 7,
    version: 1,
    title: "Base",
    markdown: "First\nMiddle\nLast\n",
    typeId: 1,
    rubbish: true,
};
type Options = Parameters<typeof useArticleVersionSync>[0];

describe("article version synchronization decision layer", () => {
    let container: HTMLDivElement, root: Root, current: ReturnType<typeof useArticleVersionSync>;
    const Harness = (props: Options) => {
        current = useArticleVersionSync(props);
        return null;
    };
    const render = async (props: Options) => {
        await act(async () => root.render(<Harness {...props} />));
    };
    const options = (local: Partial<ArticleEntry>, server: Partial<ArticleEntry> = {}): Options => ({
        conflict: {
            source: "localEdit",
            localArticle: { ...base, ...local },
            localVersion: 1,
            serverVersion: 2,
            baseArticle: base,
        },
        serverArticle: { ...base, version: 2, ...server },
        axiosInstance: { get: jest.fn() } as never,
        offline: false,
        onResolve: jest.fn(async () => true),
    });
    beforeEach(() => {
        environment.IS_REACT_ACT_ENVIRONMENT = true;
        localStorage.clear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        environment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("automatically syncs independent changes once from a locally retained base", async () => {
        const props = options({ title: "Mine" }, { digest: "Theirs" });
        await render(props);
        expect(props.axiosInstance.get).not.toHaveBeenCalled();
        expect(props.onResolve).toHaveBeenCalledTimes(1);
        expect(props.onResolve).toHaveBeenCalledWith(
            expect.objectContaining({ title: "Mine", digest: "Theirs", version: 2 })
        );
        await render({ ...props });
        expect(props.onResolve).toHaveBeenCalledTimes(1);
    });

    it("requires every conflict, preserves choices through remount, and invalidates an edited choice", async () => {
        const props = options(
            { title: "Mine", markdown: "Mine\nMiddle\nLast\n" },
            { title: "Theirs", markdown: "Theirs\nMiddle\nLast\n" }
        );
        await render(props);
        expect(current.remaining).toBe(2);
        expect(props.onResolve).not.toHaveBeenCalled();
        act(() => current.choose("title", "Mine"));
        expect(current.merged).toBeUndefined();
        act(() => root.unmount());
        root = createRoot(container);
        await render(props);
        expect(current.remaining).toBe(1);
        const body = current.plan!.conflicts.find((item) => item.field === "markdown")!;
        act(() => current.choose(body.id, ""));
        expect(current.merged?.markdown).toBe("Middle\nLast\n");
        act(() => current.edit(body.id, "Combined\n"));
        expect(current.merged).toBeUndefined();
        act(() => current.choose(body.id, current.drafts[body.id]));
        await act(async () => {
            await current.save();
        });
        expect(props.onResolve).toHaveBeenCalledWith(
            expect.objectContaining({ title: "Mine", markdown: "Combined\nMiddle\nLast\n" })
        );
    });

    it("invalidates decisions when the remote snapshot changes and never submits an old plan", async () => {
        const props = options({ title: "Mine" }, { title: "Theirs" });
        await render(props);
        act(() => current.choose("title", "Combined"));
        await render({
            ...props,
            conflict: { ...props.conflict!, serverVersion: 3 },
            serverArticle: { ...props.serverArticle, version: 3, title: "New remote" },
        });
        expect(current.remaining).toBe(1);
        expect(current.merged).toBeUndefined();
        expect(props.onResolve).not.toHaveBeenCalled();
    });

    it("does not guess a base when history cannot be read", async () => {
        const props = options({ title: "Mine" });
        props.conflict!.baseArticle = undefined;
        jest.mocked(props.axiosInstance.get).mockRejectedValue(new Error("history unavailable"));
        await render(props);
        expect(current.plan?.baseAvailable).toBe(false);
        expect(current.remaining).toBe(1);
        expect(props.onResolve).not.toHaveBeenCalled();
    });

    it("does not retry a failed automatic merge in a loop", async () => {
        const props = options({ title: "Mine" });
        jest.mocked(props.onResolve).mockResolvedValue(false);
        await render(props);
        expect(current.saveError).toBe(true);
        await render({ ...props });
        expect(props.onResolve).toHaveBeenCalledTimes(1);
    });
});
