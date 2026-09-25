import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { MemoryRouter, useLocation } from "react-router-dom";
import PersonalAccessTokens, { PersonalAccessToken } from "./personal-access-tokens";
import OAuth, { UserApplications } from "./oauth";
import { getRes } from "../utils/constants";
import { BasicUserInfo } from "../type";

const mockPost = jest.fn<Promise<any>, any[]>();
const mockGet = jest.fn<Promise<any>, any[]>();
const mockApi = { post: mockPost, get: mockGet };
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockApi }));

const info: PersonalAccessToken = {
    id: "personal-id",
    userId: 1,
    name: "Desktop assistant",
    scopes: ["articles:read"],
    resource: "https://blog.example/sub/mcp",
    createdAt: 1,
    expiresAt: 9999999999999,
    revoked: false,
    expired: false,
    invalidated: false,
};
const scopes = ["articles:read", "articles:read_drafts", "articles:read_private", "articles:all"];

describe("personal MCP tokens", () => {
    let root: Root;
    let container: HTMLDivElement;
    const changed = jest.fn<Promise<void>, []>();
    beforeEach(() => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        (globalThis as any).MessageChannel = class {
            port1 = { onmessage: () => undefined };
            port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
        };
        global.ResizeObserver = class {
            observe() {
                return undefined;
            }
            unobserve() {
                return undefined;
            }
            disconnect() {
                return undefined;
            }
        };
        window.matchMedia = (() => ({
            matches: false,
            addListener: () => undefined,
            removeListener: () => undefined,
            addEventListener: () => undefined,
            removeEventListener: () => undefined,
            dispatchEvent: () => false,
        })) as any;
        const computedStyle = window.getComputedStyle;
        jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
        window.__SS_DATA__ = {
            key: "token-test",
            pageBuildId: "test",
            systemNotification: "",
            resourceInfo: { lang: "zh_CN" },
            user: { userId: 1, userName: "writer", role: "author", actions: ["oauth.grant.manage"] } as BasicUserInfo,
        };
        mockPost.mockReset();
        mockGet.mockReset();
        changed.mockReset().mockResolvedValue(undefined);
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
        delete (globalThis as any).MessageChannel;
    });
    const button = (text: string) =>
        Array.from(document.querySelectorAll<HTMLButtonElement>("button")).find(
            (b) => b.textContent?.replace(/\s/g, "") === text.replace(/\s/g, "")
        )!;
    const render = (allowed = scopes, tokens: PersonalAccessToken[] = []) =>
        act(() => root.render(<PersonalAccessTokens tokens={tokens} scopes={allowed} onChange={changed} />));
    const open = () => act(() => button(getRes().oauth.personalTokens.create).click());
    const name = () =>
        act(() =>
            Simulate.change(document.querySelector<HTMLInputElement>("input[placeholder]")!, {
                target: { value: "Desktop assistant" },
            } as any)
        );
    const submit = async () => {
        await act(async () => Simulate.submit(document.querySelector("form")!));
    };

    it.each(["zh_CN", "en_US"])("defaults to own public read and a 30-day lifetime in %s", (lang) => {
        window.__SS_DATA__!.resourceInfo = { lang: lang as "zh_CN" | "en_US" };
        render();
        open();
        expect(document.querySelector<HTMLInputElement>('input[value="own"]')!.checked).toBe(true);
        expect(document.querySelectorAll('input[type="checkbox"]:checked')).toHaveLength(0);
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.days30);
        expect(document.body.textContent).toContain("writer");
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.readHelp);
    });
    it("creates with explicit private access, displays the secret once and clears it on close", async () => {
        render(scopes.filter((s) => s !== "articles:all"));
        open();
        name();
        expect(document.querySelector('input[value="all"]')).toBeNull();
        act(() => document.querySelector<HTMLInputElement>('input[value="articles:read_private"]')!.click());
        mockPost.mockResolvedValue({ data: { error: 0, data: { token: "zrmcp_one-time-secret", info } } });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/oauth/createPersonalToken", {
            name: "Desktop assistant",
            expiresInDays: 30,
            scopes: ["articles:read", "articles:read_private"],
        });
        expect(changed).toHaveBeenCalledTimes(1);
        expect(document.body.textContent).toContain("zrmcp_one-time-secret");
        expect(document.body.textContent).toContain(info.resource);
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.once);
        act(() => button(getRes().oauth.personalTokens.close).click());
        expect(document.body.textContent).not.toContain("zrmcp_one-time-secret");
        open();
        expect(document.body.textContent).not.toContain("zrmcp_one-time-secret");
    });
    it("keeps the one-time token available when reloading the list fails", async () => {
        render();
        open();
        name();
        changed.mockRejectedValue(new Error("offline"));
        mockPost.mockResolvedValue({ data: { error: 0, data: { token: "zrmcp_keep-this-secret", info } } });
        await submit();
        expect(document.body.textContent).toContain("zrmcp_keep-this-secret");
        expect(document.querySelector("form")).toBeNull();
        expect(mockPost).toHaveBeenCalledTimes(1);
    });
    it("preserves the form on creation failure and revokes by metadata ID", async () => {
        render();
        open();
        name();
        mockPost.mockResolvedValueOnce({ data: { error: 1, message: "Rejected" } });
        await submit();
        expect(document.querySelector<HTMLInputElement>("input[placeholder]")!.value).toBe("Desktop assistant");
        expect(document.body.textContent).not.toContain(getRes().oauth.personalTokens.once);
        act(() => document.querySelector<HTMLButtonElement>(".ant-drawer-close")!.click());
        render(scopes, [info]);
        act(() => button(getRes().oauth.personalTokens.revoke).click());
        mockPost.mockResolvedValueOnce({ data: { error: 0, data: true } });
        await act(async () => document.querySelector<HTMLButtonElement>(".ant-popconfirm .ant-btn-primary")!.click());
        expect(mockPost).toHaveBeenLastCalledWith("/api/admin/oauth/revokePersonalToken", { id: info.id });
    });
    it("loads account connections in the personal tab and hides mutations while offline", async () => {
        mockGet.mockResolvedValue({
            data: {
                error: 0,
                data: {
                    personalTokens: [info],
                    personalTokenScopes: scopes,
                    clients: [],
                    grants: [],
                    administrator: false,
                    issuer: "https://blog.example/sub",
                    resource: "https://blog.example/sub/api/oauth",
                    mcpResource: info.resource,
                },
            },
        });
        await act(async () =>
            root.render(
                <MemoryRouter>
                    <UserApplications offline />
                </MemoryRouter>
            )
        );
        expect(mockGet).not.toHaveBeenCalled();
        expect(container.textContent).toContain(getRes().oauth.offline);
        await act(async () =>
            root.render(
                <MemoryRouter>
                    <UserApplications offline={false} />
                </MemoryRouter>
            )
        );
        expect(mockGet).toHaveBeenCalledWith("/api/admin/oauth");
        expect(container.textContent).toContain(info.name);
        expect(container.textContent).not.toContain(getRes().oauth.applications);
    });
    it("redirects old application bookmarks to the personal applications tab", async () => {
        function Location() {
            const location = useLocation();
            return <span>{location.pathname + location.search}</span>;
        }
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={["/oauth"]}>
                    <OAuth />
                    <Location />
                </MemoryRouter>
            )
        );
        expect(container.textContent).toMatch(/\/user(?:\.html)?\?tab=applications/);
    });
});
