First generate and copy a token in Assistant settings, and check that "Allow Bearer token triggers" is on.

## cURL

Add this command to crontab or another external scheduler that supports shell commands. When the command uses an environment variable, set `ZRLOG_ASSISTANT_TASK_TOKEN` to the generated token.

{{curlExample}}

## Cloudflare Worker

Use Cloudflare to trigger assistant tasks on a schedule.

### 1. Create a Worker

Create a Worker in Cloudflare Workers & Pages, replace the default code with the example below, and deploy. Opening the Worker URL shows instructions; Cron triggers run the tasks.

`src/index.js`

```javascript
export default {
  async scheduled(controller, env, ctx) {
    ctx.waitUntil(triggerAssistant(env));
  },

  async fetch() {
    return new Response("ZrLog assistant trigger deployed. Configure Cron and check the last external trigger in Assistant settings.", {
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  },
};

async function triggerAssistant(env) {
  if (!env.ZRLOG_ASSISTANT_TRIGGER_URL || !env.ZRLOG_ASSISTANT_TASK_TOKEN) {
    throw new Error("Configure ZRLOG_ASSISTANT_TRIGGER_URL and ZRLOG_ASSISTANT_TASK_TOKEN");
  }
  const response = await fetch(env.ZRLOG_ASSISTANT_TRIGGER_URL, {
    method: "POST",
    redirect: "error",
    headers: {
      Authorization: `Bearer ${env.ZRLOG_ASSISTANT_TASK_TOKEN}`,
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error("Assistant trigger request failed (HTTP {status}); check the Worker configuration and ZrLog server logs".replace("{status}", String(response.status)));
  }
  const result = await response.json();
  if (result.error !== 0) {
    throw new Error("The assistant rejected the trigger; check the ZrLog server logs");
  }
}
```

### 2. Configure the URL and token

In the Worker settings, open Variables and Secrets and add this text variable

Use a public HTTPS URL that can be reached directly. If you access the admin through a private address, replace it with the public site address and keep the subpath and full trigger path.

`ZRLOG_ASSISTANT_TRIGGER_URL`

{{triggerEndpoint}}

Add a Secret named `ZRLOG_ASSISTANT_TASK_TOKEN` with the token generated on this page. Update this Secret whenever you generate a new token.

### 3. Add a Cron Trigger

In the Worker settings, open Triggers and add a Cron Trigger with the expression below to check due tasks every minute. Tasks follow their own schedules; Cloudflare Cron uses UTC.

```plaintext
* * * * *
```

After the next trigger, refresh this page to check “Last external trigger”, then open the corresponding task conversation for results. Check Worker logs if triggering fails. For HTTP 401, check the token and the external trigger switch on this page.

### Deploy with Wrangler

You can also save `src/index.js` and the `wrangler.toml` below in a local project. Set `name` to the Worker you created. Run the commands, enter this page’s token when prompted, then deploy the configuration.

`wrangler.toml`

{{wranglerExample}}

Set the Secret and deploy

```bash
npx wrangler secret put ZRLOG_ASSISTANT_TASK_TOKEN
npx wrangler deploy
```
