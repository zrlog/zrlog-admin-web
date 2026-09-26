import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { MemoryRouter, NavigateFunction, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import PersonalAccessTokens, { PersonalAccessToken } from "./personal-access-tokens";
import { UserApplications, UserApplicationsData } from "./oauth";
import { getRes, setBackendServerUrl } from "../utils/constants";
import { BasicUserInfo } from "../type";
import { ComponentProps } from "react";
import { Grid } from "antd";
import UserSettingsLayout from "./common/UserSettingsLayout";
import { USER_ROUTES, USER_APPLICATION_PAGES } from "../utils/account-page-routes";

const mockPost = jest.fn<Promise<any>, any[]>();
const mockGet = jest.fn<Promise<any>, any[]>();
const mockApi = { post: mockPost, get: mockGet };
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockApi }));
jest.mock("../base/ConfigProviderApp", () => ({ getAppState: () => ({ compactMode: false }) }));

const ApplicationsPage = (props: ComponentProps<typeof UserApplications>) => (
    <Routes>
        {USER_APPLICATION_PAGES.flatMap((page) =>
            ["", ".html"].map((suffix) => (
                <Route
                    key={page + suffix}
                    path={USER_ROUTES[page] + suffix}
                    element={
                        <UserSettingsLayout activeKey={page} administrator={props.data.administrator}>
                            <UserApplications key={page} {...props} activePage={page} />
                        </UserSettingsLayout>
                    }
                />
            ))
        )}
    </Routes>
);

const info: PersonalAccessToken = {
    id: "personal-id",
    userId: 1,
    name: "Desktop assistant",
    scopes: ["articles:read"],
    permissionMode: "custom",
    permissions: ["article.read"],
    resource: "https://blog.example/sub",
    createdAt: 1,
    expiresAt: 9999999999999,
    revoked: false,
    expired: false,
    invalidated: false,
};
const scopes = ["articles:read", "articles:read_drafts", "articles:read_private", "articles:all"];
const permissions = ["article.read", "taxonomy.read", "notification.create"];

describe("personal access tokens", () => {
    let root: Root;
    let container: HTMLDivElement;
    const changed = jest.fn<Promise<void>, []>();
    beforeEach(() => {
        jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: true });
        setBackendServerUrl("");
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
        setBackendServerUrl("");
    });
    const button = (text: string) =>
        Array.from(document.querySelectorAll<HTMLButtonElement>("button")).find(
            (b) => b.textContent?.replace(/\s/g, "") === text.replace(/\s/g, "")
        )!;
    const render = (allowed = permissions, tokens: PersonalAccessToken[] = []) =>
        act(() => root.render(<PersonalAccessTokens tokens={tokens} permissions={allowed} onChange={changed} />));
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

    it.each(["zh_CN", "en_US"])("defaults to selected article reading and a 30-day lifetime in %s", (lang) => {
        window.__SS_DATA__!.resourceInfo = { lang: lang as "zh_CN" | "en_US" };
        render();
        open();
        expect(document.querySelector<HTMLInputElement>('input[value="custom"]')!.checked).toBe(true);
        expect(document.querySelectorAll('input[type="checkbox"]:checked')).toHaveLength(1);
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.days30);
        expect(document.body.textContent).toContain("writer");
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.customHelp);
    });
    it.each([
        [info.resource, info.resource],
        ["/blog", "https://backend.example/blog"],
    ])("displays the created token once with its server address %s", async (resource, expectedUrl) => {
        setBackendServerUrl("https://backend.example/blog/");
        render(permissions);
        open();
        name();
        expect(document.querySelector('input[value="all"]')).toBeNull();
        act(() => document.querySelector<HTMLInputElement>('input[value="taxonomy.read"]')!.click());
        mockPost.mockResolvedValue({
            data: { error: 0, data: { token: "zrpat_one-time-secret", info: { ...info, resource } } },
        });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/oauth/createPersonalToken", {
            name: "Desktop assistant",
            expiresInDays: 30,
            permissionMode: "custom",
            permissions: ["article.read", "taxonomy.read"],
        });
        expect(changed).toHaveBeenCalledTimes(1);
        expect(document.body.textContent).toContain("zrpat_one-time-secret");
        expect(document.body.textContent).toContain(expectedUrl);
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.once);
        act(() => button(getRes().oauth.personalTokens.close).click());
        expect(document.body.textContent).not.toContain("zrpat_one-time-secret");
        open();
        expect(document.body.textContent).not.toContain("zrpat_one-time-secret");
    });
    it("requires an explicit choice to inherit and sends no hidden selected permissions", async () => {
        render();
        open();
        name();
        act(() => document.querySelector<HTMLInputElement>('input[value="inherit"]')!.click());
        expect(document.body.textContent).toContain(getRes().oauth.personalTokens.inheritHelp);
        expect(document.querySelector('input[value="taxonomy.read"]')).toBeNull();
        mockPost.mockResolvedValue({
            data: { error: 0, data: { token: "zrpat_inherited", info: { ...info, permissionMode: "inherit" } } },
        });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/oauth/createPersonalToken", {
            name: "Desktop assistant",
            expiresInDays: 30,
            permissionMode: "inherit",
            permissions: [],
        });
    });
    it("keeps old MCP scope restrictions visible", () => {
        render(permissions, [
            { ...info, permissionMode: "legacy", scopes: ["articles:read", "articles:read_private"] },
        ]);
        expect(container.textContent).toContain(getRes().oauth.personalTokens.legacy);
        expect(container.textContent).toContain(getRes().oauth.scopeLabels["articles:read_private"]);
    });
    it("keeps the one-time token available when reloading the list fails", async () => {
        render();
        open();
        name();
        changed.mockRejectedValue(new Error("offline"));
        mockPost.mockResolvedValue({ data: { error: 0, data: { token: "zrpat_keep-this-secret", info } } });
        await submit();
        expect(document.body.textContent).toContain("zrpat_keep-this-secret");
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
        render(permissions, [info]);
        act(() => button(getRes().oauth.personalTokens.revoke).click());
        mockPost.mockResolvedValueOnce({ data: { error: 0, data: true } });
        await act(async () => document.querySelector<HTMLButtonElement>(".ant-popconfirm .ant-btn-primary")!.click());
        expect(mockPost).toHaveBeenLastCalledWith("/api/admin/oauth/revokePersonalToken", { id: info.id });
    });
    it("renders cached account connections, accepts refreshed data and hides mutations while offline", async () => {
        const data: UserApplicationsData = {
            personalTokens: [info],
            personalTokenScopes: scopes,
            personalTokenPermissions: permissions,
            clients: [],
            grants: [],
            administrator: false,
            issuer: "https://blog.example/sub",
            resource: "https://blog.example/sub/api/oauth",
            mcpResource: info.resource + "/mcp",
        };
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={[USER_ROUTES.tokens]}>
                    <ApplicationsPage data={data} offline />
                </MemoryRouter>
            )
        );
        expect(mockGet).not.toHaveBeenCalled();
        expect(container.textContent).toContain(getRes().oauth.offline);
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={[USER_ROUTES.tokens]}>
                    <ApplicationsPage data={data} offline={false} />
                </MemoryRouter>
            )
        );
        expect(mockGet).not.toHaveBeenCalled();
        expect(container.textContent).toContain(info.name);
        expect(container.textContent).not.toContain(getRes().oauth.applications);
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={[USER_ROUTES.tokens]}>
                    <ApplicationsPage
                        data={{ ...data, personalTokens: [{ ...info, name: "Updated token" }] }}
                        offline={false}
                    />
                </MemoryRouter>
            )
        );
        expect(container.textContent).toContain("Updated token");
        expect(container.textContent).not.toContain(info.name);
    });

    it("resolves every service address against the connected backend and preserves client callbacks", async () => {
        setBackendServerUrl("https://backend.example/blog/");
        const data: UserApplicationsData = {
            personalTokens: [],
            personalTokenScopes: scopes,
            personalTokenPermissions: permissions,
            grants: [],
            administrator: true,
            clients: [{ clientId: "client", name: "App", redirectUris: ["https://client.example/callback"] }],
            issuer: "/blog",
            resource: "/blog/api/oauth",
        };
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={["/user/applications/clients.html?v=test"]}>
                    <ApplicationsPage data={data} offline={false} />
                </MemoryRouter>
            )
        );
        expect(container.textContent).toContain("https://backend.example/blog");
        expect(container.textContent).toContain("https://backend.example/blog/api/oauth");
        expect(container.textContent).toContain("https://client.example/callback");
        await act(async () =>
            Array.from(container.querySelectorAll<HTMLElement>("nav a"))
                .find((tab) => tab.textContent === getRes().oauth.personalTokens.title)!
                .click()
        );
        expect(container.textContent).toContain("https://backend.example/blog/mcp");
    });

    it("refreshes the application page cache after creating a token", async () => {
        const data: UserApplicationsData = {
            personalTokens: [],
            personalTokenScopes: scopes,
            personalTokenPermissions: permissions,
            clients: [],
            grants: [],
            administrator: false,
            issuer: "https://blog.example/sub",
            resource: "https://blog.example/sub/api/oauth",
            mcpResource: info.resource + "/mcp",
        };
        const updateCache = jest.fn();
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={["/user/applications/tokens.html?v=test"]}>
                    <ApplicationsPage data={data} offline={false} updateCache={updateCache} />
                </MemoryRouter>
            )
        );
        expect(mockGet).not.toHaveBeenCalled();
        open();
        name();
        const refreshed = { ...data, personalTokens: [info] };
        mockPost.mockResolvedValue({ data: { error: 0, data: { token: "zrpat_one-time-secret", info } } });
        mockGet.mockResolvedValue({ data: { error: 0, data: refreshed } });
        await submit();
        expect(updateCache).toHaveBeenCalledWith(refreshed, USER_ROUTES.tokens);
        expect(JSON.stringify(updateCache.mock.calls)).not.toContain("zrpat_one-time-secret");
    });

    it.each([false, true])(
        "navigates between independent application pages (administrator=%s)",
        async (administrator) => {
            let navigate!: NavigateFunction;
            const Location = () => {
                navigate = useNavigate();
                const location = useLocation();
                return <output>{location.pathname + location.search + location.hash}</output>;
            };
            const data: UserApplicationsData = {
                personalTokens: [info],
                personalTokenScopes: scopes,
                personalTokenPermissions: permissions,
                clients: [],
                grants: [],
                administrator,
                issuer: "https://blog.example/sub",
                resource: "https://blog.example/sub/api/oauth",
                mcpResource: info.resource + "/mcp",
            };
            window.__SS_DATA__!.resourceInfo = { lang: "zh_CN", staticPage: true };
            const page = (administrator ? USER_ROUTES.clients : USER_ROUTES.tokens) + ".html?v=test";
            await act(async () =>
                root.render(
                    <MemoryRouter initialEntries={[page]}>
                        <Location />
                        <ApplicationsPage data={data} offline={false} />
                    </MemoryRouter>
                )
            );
            const selected = () => container.querySelector('nav a[aria-current="page"]')?.textContent;
            expect(selected()).toBe(administrator ? getRes().oauth.applications : getRes().oauth.personalTokens.title);
            expect(container.querySelectorAll('nav a[href*="/user/applications/"]')).toHaveLength(
                administrator ? 3 : 2
            );
            expect(container.querySelector('[role="tablist"]')).toBeNull();
            expect(container.textContent?.includes(getRes().oauth.register)).toBe(administrator);
            await act(async () =>
                Array.from(container.querySelectorAll<HTMLElement>("nav a"))
                    .find((tab) => tab.textContent === getRes().oauth.grants)!
                    .click()
            );
            expect(container.querySelector("output")?.textContent).toBe(USER_ROUTES.grants + ".html?v=test");
            expect(selected()).toBe(getRes().oauth.grants);
            expect(container.textContent).not.toContain(info.name);
            expect(container.querySelector('a[href*="#"]')).toBeNull();
            await act(async () => navigate(-1));
            expect(selected()).toBe(administrator ? getRes().oauth.applications : getRes().oauth.personalTokens.title);
            expect(mockGet).not.toHaveBeenCalled();
            expect(mockPost).not.toHaveBeenCalled();
        }
    );
});
