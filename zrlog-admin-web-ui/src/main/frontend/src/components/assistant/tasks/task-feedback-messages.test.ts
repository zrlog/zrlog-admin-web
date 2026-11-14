import { expect, it } from "@jest/globals";
import { mergeTaskFeedback } from "./task-feedback-messages";
import type { AssistantMessage } from "../chat/assistant-chat.types";

const feedback = (version: number): AssistantMessage => ({
    role: "assistant",
    content: version === 1 ? "Submitted" : "Complete",
    thinking: false,
    messageId: "task-result:run-1",
    createdAt: 10,
    taskResult: { runId: "run-1", version, status: version === 1 ? "submitted" : "success" },
});
it("preserves a later task confirmation when a stream publishes its older conversation snapshot", () => {
    const reply: AssistantMessage = { role: "assistant", content: "Streaming reply", thinking: false, createdAt: 20 };
    const question: AssistantMessage = { role: "user", content: "Question", thinking: false, createdAt: 15 };
    const merged = mergeTaskFeedback([feedback(1), question, reply], [feedback(2)]);
    expect(merged).toEqual([feedback(2), question, reply]);
    expect(mergeTaskFeedback(merged, [feedback(1)])).toEqual(merged);
});
it("does not discard local messages without IDs or duplicate a refreshed result", () => {
    const local: AssistantMessage = { role: "user", content: "Local", thinking: false };
    const merged = mergeTaskFeedback([local, { ...local, content: "Another local" }], [feedback(1), feedback(1)]);
    expect(merged).toHaveLength(3);
    expect(merged.map((message) => message.content)).toEqual(["Submitted", "Local", "Another local"]);
});
