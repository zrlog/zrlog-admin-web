import { describe, expect, it, jest } from "@jest/globals";
import { formatDateTime } from "./date-time";

let mockLanguage = "en_US";
jest.mock("./constants", () => ({ getRes: () => ({ lang: mockLanguage }) }));

describe("account date and time formatting", () => {
    it("uses the selected language and updates when the preference changes", () => {
        const date = new Date(2026, 8, 28, 15, 4, 5);
        mockLanguage = "en_US";
        expect(formatDateTime(date)).toBe("9/28/2026, 3:04:05 PM");
        mockLanguage = "zh_CN";
        expect(formatDateTime(date.getTime())).toBe("2026/9/28 15:04:05");
        mockLanguage = "en_US";
        expect(formatDateTime(date)).toBe("9/28/2026, 3:04:05 PM");
    });
});
