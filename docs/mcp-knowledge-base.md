# 只读知识库与 MCP 首期契约

## 实现顺序与数据归属

1. 共用 `KnowledgeService` 查询现有文章，内置助手先接入真正的模型工具调用循环。
2. 普通助手对话默认挂载知识库工具，展示调用进度和可点击来源，不再单独切换知识库模式。普通对话与结构化写作技能共用持久记录，按账号和文章隔离，重新打开文章后恢复；旧文章记录兼容读取。知识范围统一在个人设置选择。
3. `/mcp` 提供 OAuth 保护的远程 Streamable HTTP 接口，复用相同工具与权限服务；连接信息放在个人资料的「外部应用」页签。手动配置客户端可以创建账号专属的个人访问令牌；MCP 使用相同的账号与范围校验。

不增加文章表或向量数据库。首期不提供写文章、发布、删除、附件读取、任意 SQL、外部 URL 抓取或插件动作。

## 工具

- `search_articles({query?, offset?, limit?})`：标题、摘要、标签、正文的字面关键词检索；空查询按更新时间浏览。limit 默认 5，上限 10；offset 上限 1000。返回有权访问的标题、摘要、URL、状态、分页信息，不返回未授权文章的数量或元数据。
- `read_article({id, offset?, length?})`：读取 Markdown（无 Markdown 时读取 HTML 纯文本），默认 8000 字符，上限 12000；返回来源和下一段位置。不可读和不存在使用相同错误。

工具输入有长度与整数边界，文章是资料而非指令。模型只能调用这两个固定工具，最多 8 次调用，4 轮后要求总结；供应商不支持工具调用时显示错误。引用来自实际返回资料，来源列表由服务端生成。

## 授权

所有查询在数据库分页前应用角色、归属、草稿和私有条件，读取正文再次应用同样条件。权限是当前帐号权限与本次授权范围的交集。普通帐号只能读取自己的文章；editor 可读他人非私有文章；admin/owner 可读全部，但仍受本次 scope 限制。

内置助手沿用登录态与 `ARTICLE_ASSIST` action。个人设置的 `assistant.knowledgeScope` 用单个下拉选择：关闭、我的公开文章（默认）、我的全部文章、所有可访问的公开文章、所有可访问文章；全部包含草稿和私密文章。普通账号只展示关闭与本人范围。配置由服务端加载，请求体不能扩大范围；每次工具调用、模型轮次、流式内容转发及最终返回核对账号状态、authVersion 和设置快照。扩大范围不会扩大角色权限；关闭时移除模型工具但仍可普通对话。

外部 MCP 可使用 OAuth 访问令牌或有限期个人访问令牌。个人令牌仅接受读取相关 scope，不支持刷新，管理说明见 [个人令牌](mcp-personal-tokens.md)。OAuth 使用独立 resource `站点 issuer/mcp`，只接受对应 audience 的 Bearer token。支持 `articles:read`、`articles:all`、`articles:read_drafts`、`articles:read_private`、`offline_access`，不接受写 scope。撤销和帐号变更立即生效。现有 `/api/oauth` resource 保持兼容，token 不跨 resource 使用。

## 外部兼容

首期提供 MCP 2025-03-26、2025-06-18、2025-11-25 的 initialize / initialized / ping / tools/list / tools/call。使用无会话、JSON 响应的 Streamable HTTP；GET SSE 与 DELETE 返回 405，不宣告订阅或其他未实现能力。后续协议版本按协商降级，不声称支持不同生命周期。

协商到 2025-06-18 及以上版本时，initialize 的 `serverInfo.title` 使用当前博客名称（空名称回退为 `ZrLog`），`serverInfo.name` 保持稳定的 `zrlog-knowledge`。博客名称直接沿用站点配置，无需为 MCP 重复设置或翻译；修改后在客户端重新连接时获取。客户端自行保存的连接名称可能优先于服务端标题，例如 Codex 的 `mcp_servers.<name>`；这类名称仍需在客户端修改。

OAuth 使用已有预注册 public client + S256 PKCE；管理员登记客户端准确回调 URL，客户端配置 client ID。尚不提供动态注册或 Client ID Metadata Document，因此需要自动注册、不能填 client ID 的客户端暂不兼容。服务端校验 Origin；浏览器直接跨域调用不开放，桌面/服务端客户端无 Origin 正常使用。

规范参考：[MCP HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[OAuth](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)。本功能是独立工具契约，不从后台 OpenAPI 自动生成。

## 验证切片

H2/SQLite 权限矩阵、分页与字面检索、分段正文；模拟供应商真实 tool_calls 往返与循环上限、未知工具、帐号降权；前端统一对话/单一范围选择、持久记录恢复、账号隔离与来源呈现；OAuth MCP audience / scope / 撤销；JSON-RPC 协商、错误、HTTP metadata 与 context path；native DTO、前端类型/构建和工程护栏。

内置模型适配使用 Chat Completions function tools，每轮请求 `stream=true`，工具调用结果逐轮返回模型。
SSE 解析器按 index 拼接分段 tool_calls，完整接收并校验参数后才执行工具；保留 DeepSeek reasoning_content
与 Gemini thought_signature 供同一请求后续轮次使用。不透明签名不返回界面。

## 流式正文与思考内容

服务端逐段转发 `delta` 正文与 `reasoning_delta` 思考文本；每轮完成时的 `reasoning` 事件确认该轮完整思考。
前端累积当前轮正文并在请求结束前显示，下一轮清理工具调用前的过渡正文；已完成轮次的思考单独累计，
避免将完整思考事件重复追加。XHR progress 使用累计响应，重复通知不重复显示文字。

普通助手和知识库工具调用共用这条流式路径，知识范围关闭时仍流式回答。Qwen 的 thinking 开关遵循站点设置，
不再为了非流式请求强制关闭。仅显示供应商返回的文本 reasoning_content / reasoningContent / 字符串 reasoning，
未返回或关闭思考时不显示空面板。供应商忽略 stream、返回 JSON 时兼容完整响应，此时无法提前显示上游尚未发送的文字。

最终完整回答经权限复核、聊天记录保存成功后，才发送带稳定 messageId 的 `answer` 和 `done`；
前端用已保存记录替换临时片段。失败、停止和切换文章时清理当前未完成内容，不把部分响应当成已保存聊天。
供应商响应仍有大小和时间限制，断流、错误和无完成标记的响应不会保存为成功结果；客户端断开时关闭上游流。

参考 [Qwen 流式思考](https://www.alibabacloud.com/help/en/model-studio/deep-thinking)、
[Gemini 工具签名](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures)。

## 按需检索

默认挂载只表示在模型请求里提供 tools，并使用 `tool_choice=auto`；服务端不会在用户每次发消息时主动检索。模型直接返回正文时一轮结束，不调用 KnowledgeService，也不产生 tool 事件。系统提示明确：问候、一般问题、对已提供文字的润色/翻译/总结、可由当前上下文回答的追问直接回答；只有需要博客文章信息时才检索，已有来源可以复用。默认状态用「正在思考」，只有实际 tool_calls 才显示搜索/读取状态。
