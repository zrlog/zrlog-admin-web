# 只读知识库与 MCP 首期契约

## 实现顺序与数据归属

1. 共用 `KnowledgeService` 查询现有文章，内置助手先接入真正的模型工具调用循环。
2. 写作助手增加知识库模式，展示调用进度和可点击来源。对话仅保留在当前页面内存，切换访问范围清空对话，不写入文章共享 AI 历史，也不写浏览器持久缓存。
3. `/mcp` 提供 OAuth 保护的远程 Streamable HTTP 接口，复用相同工具与权限服务；连接信息放在外部应用页面。

不增加文章表或向量数据库。首期不提供写文章、发布、删除、附件读取、任意 SQL、外部 URL 抓取或插件动作。

## 工具

- `search_articles({query?, offset?, limit?})`：标题、摘要、标签、正文的字面关键词检索；空查询按更新时间浏览。limit 默认 5，上限 10；offset 上限 1000。返回有权访问的标题、摘要、URL、状态、分页信息，不返回未授权文章的数量或元数据。
- `read_article({id, offset?, length?})`：读取 Markdown（无 Markdown 时读取 HTML 纯文本），默认 8000 字符，上限 12000；返回来源和下一段位置。不可读和不存在使用相同错误。

工具输入有长度与整数边界，文章是资料而非指令。模型只能调用这两个固定工具，最多 8 次调用，4 轮后要求总结；供应商不支持工具调用时显示错误。引用来自实际返回资料，来源列表由服务端生成。

## 授权

所有查询在数据库分页前应用角色、归属、草稿和私有条件，读取正文再次应用同样条件。权限是当前帐号权限与本次授权范围的交集。普通帐号只能读取自己的文章；editor 可读他人非私有文章；admin/owner 可读全部，但仍受本次 scope 限制。

内置助手沿用登录态与 `ARTICLE_ASSIST` action，默认只读本人公开文章；“其他成员文章”“草稿”“私有文章”分别开启。每次调用重新校验帐号启用状态与 authVersion。扩大范围不会扩大角色权限。

外部 MCP 使用独立 resource `站点 issuer/mcp`，只接受对应 audience 的 Bearer token。支持 `articles:read`、`articles:all`、`articles:read_drafts`、`articles:read_private`、`offline_access`，不接受写 scope。撤销和帐号变更立即生效。现有 `/api/oauth` resource 保持兼容，token 不跨 resource 使用。

## 外部兼容

首期提供 MCP 2025-03-26、2025-06-18、2025-11-25 的 initialize / initialized / ping / tools/list / tools/call。使用无会话、JSON 响应的 Streamable HTTP；GET SSE 与 DELETE 返回 405，不宣告订阅或其他未实现能力。后续协议版本按协商降级，不声称支持不同生命周期。

OAuth 使用已有预注册 public client + S256 PKCE；管理员登记客户端准确回调 URL，客户端配置 client ID。尚不提供动态注册或 Client ID Metadata Document，因此需要自动注册、不能填 client ID 的客户端暂不兼容。服务端校验 Origin；浏览器直接跨域调用不开放，桌面/服务端客户端无 Origin 正常使用。

规范参考：[MCP HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[OAuth](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)。本功能是独立工具契约，不从后台 OpenAPI 自动生成。

## 验证切片

H2/SQLite 权限矩阵、分页与字面检索、分段正文；模拟供应商真实 tool_calls 往返与循环上限、未知工具、帐号降权；前端范围重置与来源呈现；OAuth MCP audience / scope / 撤销；JSON-RPC 协商、错误、HTTP metadata 与 context path；native DTO、前端类型/构建和工程护栏。

内置模型适配使用 Chat Completions function tools，工具调用结果逐轮返回模型，UI 通过 SSE 显示进度及最终回答。Qwen 的知识库请求关闭 thinking 以兼容非流式工具轮次；保留 DeepSeek reasoning_content 与 Gemini thought_signature 供同一请求后续轮次使用，不返回界面或持久化。参考 [Qwen 非流式限制](https://www.alibabacloud.com/help/en/model-studio/deep-thinking)、[Gemini 工具签名](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures)。
