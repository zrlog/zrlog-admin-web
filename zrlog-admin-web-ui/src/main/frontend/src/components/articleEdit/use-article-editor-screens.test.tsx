import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import useArticleEditorScreens from "./use-article-editor-screens";

let mockScreens: Record<string, boolean> = {};
let mockSmallBreakpoint = 576;

jest.mock("antd", () => ({ Grid: { useBreakpoint: () => mockScreens } }));
jest.mock("antd-style", () => ({
    useTheme: () => ({
        screenXSMax: mockSmallBreakpoint - 1,
        screenSM: mockSmallBreakpoint,
        screenMD: 768,
        screenLG: 992,
        screenXL: 1200,
        screenXXL: 1600,
    }),
}));

const environment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };

describe("article editor initial breakpoints", () => {
    let container: HTMLDivElement;
    let root: Root;
    const originalMatchMedia = window.matchMedia;

    beforeEach(() => {
        mockScreens = {};
        mockSmallBreakpoint = 576;
        environment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        window.matchMedia = originalMatchMedia;
        environment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    const renderAt = (width: number) => {
        window.matchMedia = ((query: string) => {
            const limit = Number(query.match(/([\d.]+)px/)?.[1]);
            return { matches: query.includes("min-width") ? width >= limit : width <= limit };
        }) as typeof window.matchMedia;
        const renders: ReturnType<typeof useArticleEditorScreens>[] = [];
        const Probe = () => {
            renders.push(useArticleEditorScreens());
            return null;
        };
        act(() => root.render(<Probe />));
        return renders[0];
    };

    it.each([
        [390, false, false],
        [575, false, false],
        [576, true, false],
        [1440, true, true],
    ])("uses the viewport on the first render at %ipx", (width, sm, lg) => {
        expect(renderAt(width as number)).toMatchObject({ sm, lg });
    });

    it("uses the configured theme breakpoint for initial button sizing", () => {
        mockSmallBreakpoint = 640;
        expect(renderAt(600).sm).toBe(false);
    });

    it("uses live breakpoint updates after Ant Design subscribes", () => {
        expect(renderAt(1440).sm).toBe(true);
        mockScreens = { sm: false, lg: false };
        expect(renderAt(390)).toEqual(mockScreens);
    });
});
