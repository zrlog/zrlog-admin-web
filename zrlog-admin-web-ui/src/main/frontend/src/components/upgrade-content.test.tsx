import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";
import { UpgradeData } from "../type";
import UpgradeContent from "./upgrade-content";

jest.mock("@editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: require("@jest/globals").jest.fn(),
}));

jest.mock("@editor/dist/editor/html-preview-panel", () => ({
    __esModule: true,
    default: () => null,
}));

jest.mock("antd/es/divider", () => ({
    __esModule: true,
    default: () => null,
}));

jest.mock("../base/ConfigProviderApp", () => ({
    getAppState: () => ({ dark: false }),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("UpgradeContent", () => {
    let container: HTMLDivElement;
    let root: Root;
    let callbacks: Array<(html: string) => void>;
    const renderMarkdown = jest.mocked(markdownToHtmlSyncWithCallback);
    const data: UpgradeData = {
        upgrade: true,
        onlineUpgradable: true,
        disableUpgradeReason: "",
        version: {
            buildId: "100",
            changeLog: "# Changes",
            type: "standard",
            version: "3.9.0",
        },
    };

    const render = (nextData: UpgradeData) => {
        act(() => {
            root.render(<UpgradeContent data={nextData} />);
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        callbacks = [];
        renderMarkdown.mockImplementation((markdown, onSuccess) => {
            callbacks.push(onSuccess);
            return `sync:${markdown}`;
        });
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        renderMarkdown.mockReset();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("does not restart markdown rendering after async updates or equivalent rerenders", () => {
        render(data);
        expect(renderMarkdown).toHaveBeenCalledTimes(2);

        act(() => {
            callbacks.forEach((callback, index) => callback(`async:${index}`));
        });
        expect(renderMarkdown).toHaveBeenCalledTimes(2);

        render({ ...data, version: { ...data.version } });
        expect(renderMarkdown).toHaveBeenCalledTimes(2);

        render({
            ...data,
            version: { ...data.version, changeLog: "# New changes" },
        });
        expect(renderMarkdown).toHaveBeenCalledTimes(3);
    });
});
