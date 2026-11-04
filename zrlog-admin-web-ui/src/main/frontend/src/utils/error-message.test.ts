import {expect, it} from "@jest/globals";
import {getErrorMessage, getResponseMessage, readResponseError} from "./error-message";

it("preserves server messages from JSON, Axios errors and rejected business responses", () => {
    const data = {error: 9016, message: "Ask an administrator to grant article access"};
    expect(getErrorMessage(data, "fallback")).toBe(data.message);
    expect(getErrorMessage({response: {status: 403, data}}, "fallback")).toBe(data.message);
    expect(getResponseMessage(JSON.stringify(data))).toBe(data.message);
});

it.each(["<html>proxy diagnostics</html>", {message: " "}, {message: 9001}, null])(
    "uses a fallback for responses without a display message: %s", (data) => {
        expect(getErrorMessage({response: {data}, message: "Axios diagnostic"}, "fallback")).toBe("fallback");
    }
);

it("uses a fallback for network failures while retaining local validation messages", () => {
    expect(getErrorMessage({isAxiosError: true, message: "Network Error"}, "offline")).toBe("offline");
    expect(getErrorMessage(new Error("Choose an image"), "fallback")).toBe("Choose an image");
});

it("reads server messages from failed fetch responses and handles non-JSON bodies", async () => {
    const response = {json: async () => ({message: "Update service is unavailable"})} as Response;
    expect((await readResponseError(response, "fallback")).message).toBe("Update service is unavailable");
    expect((await readResponseError({json: async () => {throw new SyntaxError();}} as unknown as Response, "fallback")).message).toBe("fallback");
});
