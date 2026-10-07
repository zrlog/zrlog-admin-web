import { act, useState } from "react";
import { expect, it } from "@jest/globals";
import { createRoot } from "react-dom/client";
import { createPortal } from "react-dom";
import { Simulate } from "react-dom/test-utils";
import { UiIconProvider } from "@zrlog/ui/icons";
import HomeIcon from "@zrlog/ui/icons/home";
import UserIcon from "@zrlog/ui/icons/user";
import { UI_THEMES } from "@zrlog/ui/themes";
import { useUiApp } from "@zrlog/ui/feedback";
import { App, ConfigProvider } from "antd";

it("switches all theme icon families and selected glyphs in place, including portal content", async () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const popup = document.createElement("div");
    document.body.append(container, popup);
    const root = createRoot(container);
    const Page = () => {
        const [value, setValue] = useState("");
        return <><input value={value} onChange={(e) => setValue(e.target.value)} />
            <HomeIcon /><HomeIcon selected />
            {createPortal(<UserIcon selected />, popup)}
        </>;
    };
    const render = async (theme: string) => act(async () => root.render(
        <UiIconProvider theme={theme}><Page /></UiIconProvider>,
    ));
    try {
        await render("default");
        const input = container.querySelector("input")!;
        act(() => Simulate.change(input, { target: { value: "unsaved" } } as any));
        const initialPaths = Array.from(container.querySelectorAll("[data-icon] path"), (node) => node.getAttribute("d"));
        expect(initialPaths[0]).not.toBe(initialPaths[1]);
        for (const { id, iconSet } of [...UI_THEMES, UI_THEMES[0]]) {
            await render(id);
            expect(container.querySelector("input")).toBe(input);
            expect(input.value).toBe("unsaved");
            for (const node of [...Array.from(container.querySelectorAll("[data-icon]")), popup.firstElementChild!]) {
                expect(node.getAttribute("data-icon-set")).toBe(iconSet);
                expect(node.querySelector("path")).not.toBeNull();
            }
            expect(popup.firstElementChild!.getAttribute("data-selected")).toBe("true");
        }
        expect(Array.from(container.querySelectorAll("[data-icon] path"), (node) => node.getAttribute("d"))).toEqual(initialPaths);
    } finally {
        await act(async () => root.unmount());
        container.remove();
        popup.remove();
    }
});

it("keeps the message API stable when theme-specific notification configuration changes", async () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    const instances: ReturnType<typeof useUiApp>["message"][] = [];
    const Probe = () => { instances.push(useUiApp().message); return null; };
    try {
        for (const theme of ["default", "desk", "antd", "default"]) {
            await act(async () => root.render(<UiIconProvider theme={theme}>
                <ConfigProvider notification={{ closeIcon: <span>{theme}</span> }}>
                    <App><Probe /></App>
                </ConfigProvider>
            </UiIconProvider>));
        }
        expect(instances.length).toBeGreaterThanOrEqual(4);
        expect(new Set(instances).size).toBe(1);
    } finally {
        await act(async () => root.unmount());
        container.remove();
    }
});
