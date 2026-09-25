import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { Grid } from "antd";
import { MemoryRouter } from "react-router-dom";
import Access from "./access";
import Members from "./members";
import WebsiteSettingsLayout from "./common/WebsiteSettingsLayout";
import OAuthConsent from "./oauth-consent";
import { getRes } from "../utils/constants";
import { BasicUserInfo } from "../type";

jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => ({}) }));
jest.mock("../base/ConfigProviderApp", () => ({ getAppState: () => ({ compactMode: false }) }));
jest.mock("./my-loading-component", () => () => null);

describe("account management pages", () => {
    let root: Root;
    let container: HTMLDivElement;
    beforeEach(() => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        const computedStyle = window.getComputedStyle;
        jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
        window.matchMedia = (() => ({
            matches: false,
            addListener: () => undefined,
            removeListener: () => undefined,
            addEventListener: () => undefined,
            removeEventListener: () => undefined,
            dispatchEvent: () => false,
        })) as any;
        window.__SS_DATA__ = {
            key: "ui-test",
            pageBuildId: "test",
            systemNotification: "",
            user: {
                userId: 1,
                role: "owner",
                actions: ["member.manage", "member.appoint_admin", "ownership.transfer"],
            } as BasicUserInfo,
        };
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    });
    const page = {
        currentRole: "author" as const,
        roles: ["owner", "admin", "editor", "author", "contributor"] as (
            | "owner"
            | "admin"
            | "editor"
            | "author"
            | "contributor"
        )[],
        actions: [
            {
                id: "article.publish",
                roles: ["owner", "admin", "editor", "author"] as ("owner" | "admin" | "editor" | "author")[],
                routes: ["/api/admin/article/create"],
                routeDetails: [{ path: "/api/admin/article/create", descriptionKey: "article.create" }],
                scope: "articles:publish",
            },
        ],
    };
    it("shows the full matrix on desktop and one selected role on narrow screens", () => {
        const breakpoint = jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ lg: true });
        act(() => root.render(<Access data={page} />));
        expect(container.querySelectorAll("thead th").length).toBe(7); // expand, action, five roles
        breakpoint.mockReturnValue({ lg: false });
        act(() => root.render(<Access data={page} />));
        expect(container.querySelectorAll("thead th").length).toBe(3);
        expect(container.textContent).toContain(getRes().access.ranges.author);
    });
    it("does not offer editing the owner or a peer admin to an admin", () => {
        jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: true });
        window.__SS_DATA__!.user = { userId: 2, role: "admin", actions: ["member.manage"] } as BasicUserInfo;
        act(() =>
            root.render(
                <MemoryRouter>
                    <Members
                        data={{
                            currentRole: "admin",
                            members: [
                                { userId: 1, userName: "owner", role: "owner", enabled: true, email: "" },
                                { userId: 3, userName: "peer", role: "admin", enabled: true, email: "" },
                                { userId: 4, userName: "writer", role: "author", enabled: true, email: "" },
                            ],
                        }}
                    />
                </MemoryRouter>
            )
        );
        expect(
            Array.from(container.querySelectorAll("button")).filter(
                (button) => button.textContent === getRes().members.edit
            )
        ).toHaveLength(1);
        expect(container.textContent).not.toContain(getRes().members.transfer);
        expect(container.querySelector('nav a[aria-current="page"]')?.getAttribute("href")).toContain(
            "/website/members"
        );
    });
    it("shows site member navigation only to accounts with member management permission", () => {
        jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: true });
        window.__SS_DATA__!.user!.actions = ["site.configure"];
        const render = () =>
            act(() =>
                root.render(
                    <MemoryRouter>
                        <WebsiteSettingsLayout activeKey="basic">
                            <div />
                        </WebsiteSettingsLayout>
                    </MemoryRouter>
                )
            );
        render();
        expect(container.querySelector('nav a[href*="/website/members"]')).toBeNull();
        expect(container.querySelector('nav a[aria-current="page"]')).not.toBeNull();
        window.__SS_DATA__!.user!.actions = ["site.configure", "member.manage"];
        render();
        expect(container.querySelector('nav a[href*="/website/members"]')?.textContent).toBe(getRes().members.title);
    });
    it.each(["zh_CN", "en_US"])("describes endpoint purposes in %s while keeping the exact path", (lang) => {
        window.__SS_DATA__!.resourceInfo = { lang: lang as "zh_CN" | "en_US" };
        act(() => root.render(<Access data={page} />));
        act(() => container.querySelector<HTMLButtonElement>(".ant-table-row-expand-icon")!.click());
        expect(container.textContent).toContain(getRes().access.endpointDescriptions["article.create"]);
        expect(container.textContent).toContain("/api/admin/article/create");
        expect(container.textContent).not.toContain(getRes().access.unknownEndpoint);
    });
    it("defaults consent to own public read and disables unavailable full-site access", () => {
        act(() =>
            root.render(
                <OAuthConsent
                    data={{
                        requestId: "request",
                        csrf: "csrf",
                        clientName: "Test application",
                        redirectUri: "https://client.example/callback",
                        resource: "https://blog.example/api/oauth",
                        scopes: ["articles:read", "articles:write", "articles:read_private", "articles:all"],
                        availableScopes: ["articles:read", "articles:write", "articles:read_private"],
                    }}
                />
            )
        );
        const inputs = Array.from(container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
        expect(inputs.filter((input) => input.checked).map((input) => input.value)).toEqual(["articles:read"]);
        expect(container.querySelector<HTMLInputElement>('input[type="radio"][value="own"]')!.checked).toBe(true);
        expect(container.querySelector<HTMLInputElement>('input[type="radio"][value="all"]')!.disabled).toBe(true);
    });
});
