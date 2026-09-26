import { describe, expect, it } from "@jest/globals";
import { resolveApplicationServerUrl } from "./application-server-url";

const backend = "https://backend.example/blog/";
const frontend = "https://static-admin.example";

describe("external application service URLs", () => {
    it.each([
        ["https://canonical.example/sub", ""],
        ["https://canonical.example/sub/", ""],
        ["https://canonical.example/sub/api/oauth", "api/oauth"],
        ["https://canonical.example/sub/mcp", "mcp"],
        ["http://localhost:8080/sub/mcp", "mcp"],
    ] as const)("keeps the server's canonical identifier %s", (value, path) => {
        expect(resolveApplicationServerUrl(value, path, backend, frontend)).toBe(value);
    });

    it.each([
        [undefined, "", backend, "https://backend.example/blog"],
        ["/blog", "", backend, "https://backend.example/blog"],
        ["/blog/", "", backend, "https://backend.example/blog"],
        [undefined, "api/oauth", backend, backend + "api/oauth"],
        ["", "mcp", backend, backend + "mcp"],
        ["mcp", "mcp", backend, backend + "mcp"],
        ["/mcp", "mcp", backend, backend + "mcp"],
        ["/blog/mcp", "mcp", backend, backend + "mcp"],
        ["blog/mcp", "mcp", backend, backend + "mcp"],
        ["/blog/api/oauth", "api/oauth", backend, backend + "api/oauth"],
        [undefined, "mcp", "https://backend.example/blog", backend + "mcp"],
        [undefined, "mcp", "http://localhost:8080/", "http://localhost:8080/mcp"],
        [undefined, "mcp", "/blog/", frontend + "/blog/mcp"],
        ["//service.example/blog/mcp", "mcp", backend, "https://service.example/blog/mcp"],
    ] as const)("resolves %s with %s on %s", (value, path, base, expected) => {
        expect(resolveApplicationServerUrl(value, path, base, frontend)).toBe(expected);
    });

    it.each([
        "javascript:alert(1)",
        "data:text/plain,mcp",
        "file:///mcp",
        "https://",
        "https:example.com/mcp",
        "https://user:secret@example.com/mcp",
        "../mcp",
        "/blog/../mcp",
        "%2e%2e/mcp",
        "%",
        "\\\\other.example/mcp",
    ])("does not offer an invalid service address for copying: %s", (value) => {
        expect(resolveApplicationServerUrl(value, "mcp", backend, frontend)).toBe("");
    });

    it.each([
        "javascript:alert(1)",
        "https://user:secret@example.com",
        "https://backend.example?token=secret",
        "https://backend.example#fragment",
    ])("does not use an invalid backend base: %s", (base) => {
        expect(resolveApplicationServerUrl(undefined, "mcp", base, frontend)).toBe("");
    });
});
