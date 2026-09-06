import { describe, expect, it } from "@jest/globals";
import { createDraftAiSaveGate } from "./draft-ai-save-gate";

describe("draft AI/save gate", () => {
    it("tracks overlapping draft AI requests and releases each request exactly once", () => {
        const gate = createDraftAiSaveGate();
        const counts: number[] = [];
        gate.subscribe(() => counts.push(gate.getPendingAiCount()));

        const releaseFirst = gate.tryBeginAiRequest(0);
        const releaseSecond = gate.tryBeginAiRequest(undefined);

        expect(releaseFirst).toBeDefined();
        expect(releaseSecond).toBeDefined();
        expect(gate.getPendingAiCount()).toBe(2);
        releaseFirst?.();
        releaseFirst?.();
        expect(gate.getPendingAiCount()).toBe(1);
        releaseSecond?.();

        expect(gate.getPendingAiCount()).toBe(0);
        expect(counts).toEqual([1, 2, 1, 0]);
    });

    it("prevents a first create while draft AI is pending", () => {
        const gate = createDraftAiSaveGate();
        const releaseAi = gate.tryBeginAiRequest(0);

        expect(gate.tryBeginCreate(0)).toBeUndefined();

        releaseAi?.();
        expect(gate.tryBeginCreate(0)).toBeDefined();
    });

    it("prevents a draft AI request after first create has synchronously started", () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(undefined);

        expect(releaseCreate).toBeDefined();
        expect(gate.tryBeginAiRequest(0)).toBeUndefined();

        releaseCreate?.();
        expect(gate.tryBeginAiRequest(0)).toBeDefined();
    });

    it("does not serialize operations for an existing article", () => {
        const gate = createDraftAiSaveGate();
        const releaseDraftCreate = gate.tryBeginCreate(0);

        expect(gate.tryBeginAiRequest(7)).toBeDefined();
        expect(gate.tryBeginCreate(7)).toBeDefined();
        expect(gate.getPendingAiCount()).toBe(0);

        releaseDraftCreate?.();
    });
});
