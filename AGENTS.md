# Agent 说明

## i18n

后台 i18n 相关工作必须遵守 `docs/i18n.md`。

关键约束：

- 前端可见 UI 文案统一放在 `src/main/frontend/src/i18n/admin.ts`。
- 使用 `getRes().admin.user.info` 这类带类型检查的属性访问；不要使用 `getRes()["admin.user.info"]` 或 `res["title"]`。
- 不要把前端文案加到后端 `.properties` 文件。
- 后端 i18n 只服务后端自己输出的消息，使用 `admin_backend_*.properties`。
- 前端组件里不要保留硬编码可见文案，也不要保留 `getRes().x || "保存"` 这类 fallback 字面量。

## 前端主题

后台前端主题相关工作必须遵守 `docs/frontend-theme.md`。

关键约束： 

- 普通矩形块、卡片、面板、输入框、列表项、弹层容器等圆角统一使用主题配置，不要硬编码 `8/10/12/14/16/18/24/999` 这类值。
- 链接、链接按钮、可点击文字的颜色应优先复用主题色或组件默认主题行为，不要直接写死蓝色或其他固定颜色值。
- `styled-components`、布局壳组件、跨组件公共样式如果需要圆角或链接颜色，也要通过主题值传入，不要在样式文件里单独维护固定值。
- 