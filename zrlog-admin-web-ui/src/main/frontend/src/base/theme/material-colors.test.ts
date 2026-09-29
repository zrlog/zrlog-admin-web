import { FastColor } from "@ant-design/fast-color";
import { describe, expect, it } from "@jest/globals";
import { materialColors } from "./material-colors";

const contrast = (a: string, b: string) => {
    const values = [a, b].map((color) => new FastColor(color).getLuminance()).sort((x, y) => y - x);
    return (values[0] + 0.05) / (values[1] + 0.05);
};

// Brand customization must not turn filled actions or selected navigation unreadable.
describe.each([false, true])("Material role contrast (dark=%s)", (dark) => {
    it("keeps a neutral configured brand neutral across all roles", () => {
        for (const role of Object.values(materialColors("#808080", dark))) {
            const { r, g, b } = new FastColor(role);
            expect(r).toBe(g);
            expect(g).toBe(b);
        }
    });

    it.each(["#d32f2f", "#00875a", "#6750a4"])("uses the configured hue for tonal roles (%s)", (seed) => {
        const configuredHue = new FastColor(seed).getHue();
        const colors = materialColors(seed, dark);
        for (const role of [colors.primaryContainer, colors.onPrimaryContainer]) {
            const difference = Math.abs(new FastColor(role).getHue() - configuredHue);
            expect(Math.min(difference, 360 - difference)).toBeLessThan(5);
        }
    });

    it.each(["#1677ff", "#ffffff", "#000000", "#ffff00", "#00ff00", "#f00", "#6750a4", "#ff00ff"])(
        "keeps text readable for brand %s",
        (seed) => {
            const c = materialColors(seed, dark);
            for (const [fg, bg] of [
                [c.onPrimary, c.primary],
                [c.onPrimary, c.primaryHover],
                [c.onPrimary, c.primaryActive],
                [c.onPrimaryContainer, c.primaryContainer],
                [c.onSurface, c.surface],
                [c.onSurface, c.container],
                [c.onSurfaceVariant, c.containerHigh],
                [c.primary, c.container],
                [c.primary, c.primaryContainer],
                [c.primary, c.containerHighest],
            ])
                expect(contrast(fg, bg)).toBeGreaterThanOrEqual(4.5);
            expect(contrast(c.outline, c.container)).toBeGreaterThanOrEqual(3);
        }
    );
});
