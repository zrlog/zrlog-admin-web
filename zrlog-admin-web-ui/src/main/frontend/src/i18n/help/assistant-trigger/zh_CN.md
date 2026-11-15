先在助手设置中生成并复制令牌，确认“允许 Bearer 令牌触发”已开启。

## cURL

将以下命令添加到 crontab 或其他支持命令调用的外部调度器中。命令使用环境变量时，请将 `ZRLOG_ASSISTANT_TASK_TOKEN` 设为生成的令牌。

{{curlExample}}

## Cloudflare Worker

使用 Cloudflare 定时触发助手任务。

### 1. 创建 Worker

在 Cloudflare 的 Workers & Pages 中创建 Worker，用下方代码替换默认代码并部署。访问 Worker 地址可查看说明，任务由定时触发器执行。

`src/index.js`

```javascript
export default {
  async scheduled(controller, env, ctx) {
    ctx.waitUntil(triggerAssistant(env));
  },

  async fetch() {
    return new Response("ZrLog 助手触发器已部署，请配置 Cron 并在助手设置中查看最近外部触发", {
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  },
};

async function triggerAssistant(env) {
  if (!env.ZRLOG_ASSISTANT_TRIGGER_URL || !env.ZRLOG_ASSISTANT_TASK_TOKEN) {
    throw new Error("请配置 ZRLOG_ASSISTANT_TRIGGER_URL 和 ZRLOG_ASSISTANT_TASK_TOKEN");
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
    throw new Error("助手触发请求失败（HTTP {status}），请检查 Worker 配置和 ZrLog 服务日志".replace("{status}", String(response.status)));
  }
  const result = await response.json();
  if (result.error !== 0) {
    throw new Error("助手未接受触发请求，请检查 ZrLog 服务日志");
  }
}
```

### 2. 配置地址和令牌

在 Worker 设置的 Variables and Secrets 中添加以下文本变量

地址需能从公网通过 HTTPS 直接访问。若当前从内网访问后台，请换成站点公开地址，保留子路径和完整触发路径。

`ZRLOG_ASSISTANT_TRIGGER_URL`

{{triggerEndpoint}}

添加名为 `ZRLOG_ASSISTANT_TASK_TOKEN` 的 Secret，值为本页生成的令牌。生成新令牌后，请同步更新此 Secret。

### 3. 添加定时触发器

在 Worker 设置的 Triggers 中添加 Cron Trigger，填入下方表达式，每分钟检查到期任务。任务按各自计划执行；Cloudflare Cron 使用 UTC。

```plaintext
* * * * *
```

配置后等待一次触发，刷新本页查看“最近外部触发”，再打开对应任务的会话查看结果。触发失败时查看 Worker 日志；HTTP 401 请检查令牌和本页外部触发开关。

### 使用 Wrangler 部署

也可在本地项目中保存 `src/index.js` 和下方 `wrangler.toml`，将 `name` 改为已创建的 Worker 名称。运行命令时按提示输入本页令牌，再部署配置。

`wrangler.toml`

{{wranglerExample}}

配置 Secret 并部署

```bash
npx wrangler secret put ZRLOG_ASSISTANT_TASK_TOKEN
npx wrangler deploy
```
