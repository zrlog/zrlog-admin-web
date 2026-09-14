# AI 模型目录与定时同步

模型推荐数据由 admin-web 的 `src/main/resources/ai/models.json` 管理。提供商身份、默认 API 地址和请求协议仍由 Java 管理；新增模型不再修改枚举。后台 API 的 `allProviders`、`allImageProviders`、`models` 和 `modelEntries` 字段保持原有结构。

运行时优先读取 ZrLog runtime root 下的 `conf/ai-models.json`，也可以用 JVM 参数 `-Dzrlog.ai.modelCatalog=/absolute/path/ai-models.json` 指定文件。下一次后台 AI 配置查询或图像模型校验会加载更新，无需重启、编译或重新构建前端。缺少外部文件时使用内置目录，非法更新保留本进程上次有效目录。删除外部文件可恢复内置目录。

目录仅提供候选模型和能力校验，不写数据库，不切换用户已经选择的模型，不修改 Base URL 或 API Key。自定义文本模型及自定义图像服务继续使用现有规则。此次范围是已有的 DeepSeek、OpenAI、通义千问、Gemini，不新增提供商协议适配。

## GitHub Actions 定时同步

`.github/workflows/sync-ai-models.yml` 每天北京时间 04:23（UTC 20:23）在 GitHub 执行，也支持在 Actions 页面选择 **Sync AI model catalog → Run workflow** 手动运行。工作流进入默认分支后，GitHub 才会启用定时触发；计划时间可能因 GitHub 队列而延迟。

工作流先运行 Python 同步测试，再直接执行 `scripts/sync-ai-models.py --output src/main/resources/ai/models.json`。校验结果后，通过 `peter-evans/create-pull-request` 创建或更新 `automation/ai-model-catalog` 分支上的 PR，只包含模型 JSON。有新模型、顺序、能力或来源变化才提交；目录不保存检查时间，无需特殊的日期过滤逻辑。同一分支复用已有 PR，合并后后续有变化再创建新 PR。

使用内置 `GITHUB_TOKEN`，无需配置模型提供商密钥或额外 PAT。仓库需要在 **Settings → Actions → General → Workflow permissions** 允许 **Allow GitHub Actions to create and approve pull requests**，并允许工作流声明的 `contents: write`、`pull-requests: write` 权限。此工作流只创建 PR，合并由维护者处理。GitHub 默认不会为 `GITHUB_TOKEN` 创建的 PR 触发其他 `push` / `pull_request` 工作流；此任务自身会执行 Python 测试和目录校验，若需要已有的完整 Test Coverage 检查，可手动在更新分支上运行该工作流。

工作流仅在四家同步和目录校验全部成功后创建或更新 PR。任一家同步失败时，任务直接失败并记录原因，不修改已有 PR，避免临时抓取失败覆盖或关闭待合并的更新。PR 合并只更新仓库的内置快照，不会主动修改已部署实例；实例可随下次发布获取新快照，或由现有部署流程将合并后的 JSON 复制到 `conf/ai-models.json` 热加载。仓库不再提供服务器 crontab 条目。

## 手动同步与部署

`scripts/sync-ai-models.py` 在 Linux/Unix 上使用 Python 3.8+ 标准库读取四家官方公开模型文档，无需 API Key。只识别已有协议支持的模型系列，区分文本与图像生成，过滤音频、向量、视频、专用受限模型。新型号排在前面，原有条目保留，避免目录变化使已有图像配置失效；已下线模型仍可能保留，需要维护者按官方退役公告清理。新系列或接口协议变化仍需调整规则，不能保证所有未来模型自动兼容。

```shell
# 在仓库中更新内置快照（后续版本的离线兜底）
python3 scripts/sync-ai-models.py --output src/main/resources/ai/models.json

# 在运行服务器上更新热加载目录，--seed 指向随脚本部署的内置快照
python3 /opt/zrlog-admin-web/scripts/sync-ai-models.py \
  --seed /opt/zrlog-admin-web/src/main/resources/ai/models.json \
  --output /opt/zrlog/conf/ai-models.json
```

同步按提供商独立处理：某家请求失败、响应过大、页面结构不匹配或提取结果异常时保留该家旧数据，其他成功结果正常更新，并返回非零退出码供任务监控发现。全部失败时不覆盖文件。输出使用同目录临时文件和原子替换，任务使用文件锁避免重叠运行；不把抓到一半的 JSON 暴露给后台。可用 `--check` 核对差异而不写文件（有差异返回 1，失败返回 2）。

## 数据契约与验证

JSON 使用 `schemaVersion: 1`，`providers` 必须包含四个已支持提供商且不能重复。每项包含 `name`、官方 `source` 以及 `models`；模型包含 `name` 和非空 `capabilities`（`TEXT` 或 `IMAGE_GENERATION`）。每家至少有一个文本模型；目前只为 OpenAI、Gemini 接受图像能力。完整文件限制为 1 MiB，每家最多 512 个模型，拒绝重复、未知能力和非法模型 ID。`source` 仅用于溯源，不会被 Java 当作请求地址。

检查时间与成功、失败状态以 Actions 运行记录为准，模型目录只保存模型数据。模型顺序沿用官方页面推荐顺序，未出现在当次页面的历史模型追加到末尾。当前内置快照包含 `deepseek-flash`、`gpt-6-astra`、`gpt-image-2.5-sunburst`、`gpt-image-2.5-flare`、`qwen3.8-max`、`qwen3.8-flash`、`gemini-3.8-flash` 等公开模型 ID。

内置 JSON 已注册到 native-image 资源列表，目录 DTO 与嵌套类型加入 Gson 注册。验证分为官方页面提取与失败回退、Java 目录校验与热加载、原有后台配置接口回归三部分。

```shell
python3 -m unittest discover -s scripts -p test_sync_ai_models.py -v
./mvnw -q test
scripts/check-admin-guardrails.sh
cd src/main/frontend && yarn type-check && yarn build
```

官方来源：

- DeepSeek：https://api-docs.deepseek.com/quick_start/pricing
- OpenAI：https://developers.openai.com/api/docs/models
- 通义千问：https://help.aliyun.com/zh/model-studio/models
- Gemini：https://ai.google.dev/gemini-api/docs/models
