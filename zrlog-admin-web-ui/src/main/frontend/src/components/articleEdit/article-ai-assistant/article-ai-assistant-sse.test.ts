import {describe, expect, it, jest} from "@jest/globals";
import {parseSseResponse} from "./article-ai-assistant-sse";

jest.mock("../../../utils/constants", () => ({
    getRes: () => ({error: {unknown: "Request failed"}}),
}));

describe("AI public error messages", () => {
    it.each([true, false])("preserves server wording and metadata for SSE=%s", (stream) => {
        const payload = {
            error: 1,
            message: "Server: the provider response was incomplete",
            errorType: "incomplete_response",
            errorTypeLabel: "Server: partial response",
            suggestion: "Server: inspect the saved output before retrying",
        };
        const result = parseSseResponse(stream
            ? `event: ai-error\ndata: ${JSON.stringify(payload)}\n\n`
            : JSON.stringify(payload));
        expect(result.errorMessage).toBe(payload.message);
        expect(result.errorMeta).toEqual({
            errorType: payload.errorType,
            errorTypeLabel: payload.errorTypeLabel,
            suggestion: payload.suggestion,
        });
    });

    it("does not invent advice when an older response only reports an error code", () => {
        const result = parseSseResponse('event: ai-error\ndata: {"error":1,"errorType":"provider_request"}\n\n');
        expect(result.errorMessage).toBe("Request failed");
        expect(result.errorMeta?.suggestion).toBeUndefined();
        expect(result.errorMeta?.errorTypeLabel).toBeUndefined();
    });
});
