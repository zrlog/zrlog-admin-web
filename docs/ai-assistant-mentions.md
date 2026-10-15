# AI 助手内容引用

文章编辑助手使用 `/` 选择写作技能、`@` 引用内容，`＋` 添加附件。`@` 候选包含当前消息已添加的附件和助手有权读取的已有文章；不支持引用任意本地路径、未添加的文件或其他会话的附件。

## 输入交互

- 在输入开头、空白或左括号后输入 `@关键词`，或点击输入栏的 `@` 按钮。邮箱和 URL 中的 `@` 不触发候选。
- 方向键切换候选，Enter / Tab 或点击确认，Esc 关闭。候选打开时 Enter 不发送消息；中文输入法确认和 Shift + Enter 保持原行为。
- 确认后移除搜索片段，保留其余文字，并显示可移除的引用标签。每条消息最多引用 8 项。
- 附件引用绑定 `File` 对象。移除附件会移除其引用；发送前按实际附件顺序重新计算索引。同名附件不会串用。
- 搜索防抖并取消过期请求，失败时保留输入并提供重试。附件候选无需等待文章搜索。
- 排队保存引用标识与附件快照；有引用的失败消息保留在暂停队列，供用户显式重试。切换文章清空原队列，首次保存草稿时沿用已有迁移机制。

## 内部接口

`GET /api/admin/article/ai/references?query=...` 返回 `ApiStandardResponse<KnowledgeModels.SearchResult>`。关键词最多 200 字符，返回最近匹配的前 8 篇文章，不缓存。入口要求 `article.assist`；复用 `KnowledgeService` 的账号权限及个人助手 `knowledgeScope`。读取关闭或没有文章读取权限时返回空候选。

`POST /api/admin/article/ai` 的 JSON 请求新增可选 `references`，省略时为 `[]`，不影响旧客户端：

```json
{
  "input": "比较这些材料",
  "articleId": 7,
  "files": [{"name": "notes.txt", "data": "bm90ZXM="}],
  "references": [
    {"type": "article", "articleId": 42},
    {"type": "file", "fileIndex": 0}
  ]
}
```

服务端拒绝未知类型、重复标识、无效文章 ID、越界附件索引、混用字段及超过 8 项的请求。文章在发送时按实际 ID 重新校验权限并读取，客户端标题和正文不作为读取依据。每篇初始提供最多约 4000 字符（保留完整 Unicode 代理对），超过部分由 `nextOffset` 标识，模型可继续通过既有 `read_article` 读取。附件索引从 0 开始，对应本次 `files` 数组，并在持久任务中绑定实际生成的附件 ID。

引用内容作为不可信参考数据提供给模型；当前编辑器快照仍是写作目标。文章来源进入现有回复来源记录，引用标签与文章链接保留在消息正文；原始引用内容仅进入已有任务上下文，不复制到可见会话历史。引用与技能组合通过统一对话接口发送，保留技能名称。

接口仅服务后台内部交互，不加入公开 OpenAPI。`Reference` / `ResolvedReference` 放在 `AIChatModels`，由现有 `getDeclaredClasses()` 的 Native/Gson 注册覆盖。

## 验证

- 前端：`article-ai-mentions`、`article-ai-assistant-skill-content`、`use-article-ai-queue`、`article-ai-assistant-button`、`use-article-chat` 测试，类型检查和生产构建。
- 后端：`AIReferenceServiceTest`，以及现有对话、文件输入和写作工具测试。
- 浏览器验收：桌面/移动端、中英文、明暗主题下的候选列表、长标题、引用标签、键盘焦点、中文输入和失败重试。
