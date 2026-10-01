# AI 生成提示词

本目录是单次生成任务的提示词来源，仍由 `AIPromptVO` 注册，供 `AIToolService` 和 `AIImageService` 加载。它与 `ai/skills/*/SKILL.md` 分别服务于生成和聊天编排，完整调用关系见 [写作技能与页面交互](../../../../../docs/ai-writing-skills.md)。本 README 不会发送给模型。

## 文件约定

- `<prefix>_<locale>.md`：生成任务的系统提示词，维护内容要求与输出 JSON 格式。
- `<prefix>-input_<locale>.md`：文章字段输入模板，使用现有 `{{title}}`、`{{markdown}}` 等占位符。执行器负责替换，并追加用户要求等上下文。
- 支持 `zh_CN`、`en_US`；资源加载沿用当前后台语言，缺失时回退到中文。封面只有输入模板，提交到图片生成服务。

## 写作技能映射

| 业务技能键 | AIPromptVO 工具键与文件前缀 |
| --- | --- |
| title | article-title-generate |
| alias | article-alias-generate |
| digest | article-digest-generate |
| tags | article-tags-generate |
| rewrite | article-markdown-rewrite |
| score | article-score |
| publishCheck | article-publish-check |
| seo | article-seo-check |
| proofread | article-proofread |
| structure | article-structure-advice |
| questions | article-reader-questions |
| cover | article-cover-generate |

`website-description` 和 `ai-prompt-optimize` 分别用于站点描述和提示词优化，不属于文章编辑器的写作技能。

## 修改位置

例如，要调整标题的生成风格或长度，修改 `article-title-generate_*.md`；要调整“用户明确要求选标题后再写摘要”的步骤，修改 `ai/skills/title/SKILL.md`。不要在两个目录重复定义同一套内容规则，也不要在生成提示词中加入暂停聊天、渲染组件等编排指令。

提示词中的 JSON 格式是对模型的要求，执行器仍必须解析成已注册 DTO 并校验。新增输出字段或卡片类型时，需要同步修改后端与页面契约，仅修改 Markdown 不能让页面安全应用新内容。新增模板还需注册到 `AIPromptVO`，由 `AiNativeImageUtils.resources()` 纳入 Native 资源。
