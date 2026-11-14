export const buildWorkerExample = (messages: {
    workerRunning: string;
    workerConfigRequired: string;
    workerRequestFailed: string;
    workerResponseFailed: string;
}) => `export default {
  async scheduled(controller, env, ctx) {
    ctx.waitUntil(triggerAssistant(env));
  },

  async fetch() {
    return new Response(${JSON.stringify(messages.workerRunning)}, {
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  },
};

async function triggerAssistant(env) {
  if (!env.ZRLOG_ASSISTANT_TRIGGER_URL || !env.ZRLOG_ASSISTANT_TASK_TOKEN) {
    throw new Error(${JSON.stringify(messages.workerConfigRequired)});
  }
  const response = await fetch(env.ZRLOG_ASSISTANT_TRIGGER_URL, {
    method: "POST",
    redirect: "error",
    headers: {
      Authorization: \`Bearer \${env.ZRLOG_ASSISTANT_TASK_TOKEN}\`,
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(${JSON.stringify(messages.workerRequestFailed)}.replace("{status}", String(response.status)));
  }
  const result = await response.json();
  if (result.error !== 0) {
    throw new Error(${JSON.stringify(messages.workerResponseFailed)});
  }
}`;

export const buildWranglerExample = (endpoint: string) => `name = "zrlog-assistant-trigger"
main = "src/index.js"
compatibility_date = "2026-10-30"

[triggers]
crons = ["* * * * *"]

[vars]
ZRLOG_ASSISTANT_TRIGGER_URL = ${JSON.stringify(endpoint)}`;
