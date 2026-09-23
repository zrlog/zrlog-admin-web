import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { useTemplateConfig } from "./use-template-config";
import type { TemplateConfigData } from "./template-config-model";

const mockSave = jest.fn<Promise<{ error: number; message: string }>, any[]>();
const mockError = jest.fn();
const mockSuccess = jest.fn();
const mockMessages = { error: mockError, success: mockSuccess };
jest.mock("antd", () => ({ message: { useMessage: () => [mockMessages, null] } }));
jest.mock("../../utils/sse-utils", () => ({ postRefreshCacheSse: (...args: any[]) => mockSave(...args) }));

const data: TemplateConfigData = {
    name: "Test theme",
    template: "/include/templates/test",
    config: {
        title: { label: "Title", type: "text", value: "Original", htmlElementType: "input", contentType: "text" },
        enabled: { label: "Enabled", type: "boolean", value: true, htmlElementType: "switch", contentType: "text" },
        cover: { label: "Cover", type: "file", value: "/cover.png", htmlElementType: "input", contentType: "text" },
    },
};
let editor: ReturnType<typeof useTemplateConfig>;
const Harness = ({ disabled = false }: { disabled?: boolean }) => {
    editor = useTemplateConfig(data, disabled);
    return null;
};
let root: Root;
let container: HTMLDivElement;
const actEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };

beforeEach(async () => {
    actEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
    jest.clearAllMocks();
    mockSave.mockResolvedValue({ error: 0, message: "Saved" });
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(<Harness />));
});
afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    actEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
});

describe("theme configuration drafts", () => {
    it("saves booleans and raw asset paths for the theme returned by the backend", async () => {
        await act(async () =>
            editor.changeValues({ enabled: false, cover: "/upload/cover.png", template: "/wrong-theme" })
        );
        await act(async () => editor.save());
        expect(mockSave).toHaveBeenCalledWith(
            "/api/admin/template/config",
            expect.objectContaining({
                body: { title: "Original", enabled: false, cover: "/upload/cover.png", template: data.template },
            })
        );
        expect(mockSuccess).toHaveBeenCalledWith("Saved");
    });

    it.each(["business", "network"])("retains the draft after a %s error", async (failure) => {
        await act(async () => editor.changeValues({ title: "Unsaved" }));
        if (failure === "business") mockSave.mockResolvedValue({ error: 1, message: "Rejected" });
        else mockSave.mockRejectedValue(new Error("Network error"));
        await act(async () => editor.save());
        expect(editor.values.title).toBe("Unsaved");
        expect(editor.dirty).toBe(true);
        expect(editor.saving).toBe(false);
        expect(mockError).toHaveBeenCalledTimes(1);
        expect(mockSuccess).not.toHaveBeenCalled();
    });

    it("keeps edits made during a save dirty and prevents duplicate submissions", async () => {
        let finish!: (response: { error: number; message: string }) => void;
        mockSave.mockImplementation(
            () =>
                new Promise((resolve) => {
                    finish = resolve;
                })
        );
        await act(async () => editor.changeValues({ title: "Submitted" }));
        let saving!: Promise<void>;
        await act(async () => {
            saving = editor.save();
        });
        await act(async () => {
            editor.changeValues({ title: "Edited while saving" });
            void editor.save();
        });
        expect(mockSave).toHaveBeenCalledTimes(1);
        expect(editor.saving).toBe(true);
        await act(async () => {
            finish({ error: 0, message: "Saved" });
            await saving;
        });
        expect(editor.values.title).toBe("Edited while saving");
        expect(editor.dirty).toBe(true);
        await act(async () => editor.changeValues({ title: "Submitted" }));
        expect(editor.dirty).toBe(false);
    });

    it("clears the dirty state after saving and guards browser unload only while needed", async () => {
        const unload = () => {
            const event = new Event("beforeunload", { cancelable: true });
            window.dispatchEvent(event);
            return event.defaultPrevented;
        };
        expect(unload()).toBe(false);
        await act(async () => editor.changeValues({ title: "New title" }));
        expect(unload()).toBe(true);
        await act(async () => editor.save());
        expect(editor.dirty).toBe(false);
        expect(unload()).toBe(false);
    });

    it("does not submit offline configuration", async () => {
        await act(async () => root.render(<Harness disabled />));
        await act(async () => editor.changeValues({ title: "Offline draft" }));
        await act(async () => editor.save());
        expect(mockSave).not.toHaveBeenCalled();
        expect(editor.dirty).toBe(true);
    });
});
