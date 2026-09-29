# 前端主题实现

本文维护后台主题的源码入口、Ant Design / `antd-style` 用法与代码示例。

产品范围与通用规则从 [Ops UI 总入口](../../zrlog-ops/docs/ui-design-guide.md) 读取；M3 视觉与隔离规则在 [专项规范](../../zrlog-ops/docs/material3-agent-guide.md) 维护，检查矩阵在 [后台验收契约](../../zrlog-ops/acceptance/zrlog-admin-web.yaml) 维护。文案表达见 [产品文案规范](../../zrlog-ops/docs/content-writing-guide.md)，资源实现见 [i18n 规则](i18n.md)。

## 总原则

- 后台前端的可配置视觉属性应尽量复用主题 token，不要在业务组件里直接写死。
- 优先复用 Ant Design / `antd-style` 暴露的主题值，例如 `theme.borderRadius`、`theme.borderRadiusSM`、`theme.borderRadiusLG`、`theme.colorPrimary`、`theme.colorLink`。
- 当样式写在 `styled-components`、封装布局组件或跨组件公共样式里时，需要把主题值显式传入，不要重新写一套固定值。

## 多主题实现边界

隔离与验收规则统一见 [Ops 后台默认主题隔离规则](../../zrlog-ops/docs/material3-agent-guide.md#后台默认主题的隔离规则)。本地前端路径以 `zrlog-admin-web-ui/src/main/frontend/src/` 为根：

- `utils/theme-utils.tsx` 的 `useThemeConfig` 负责分派，当前 `desk`、`antd` 等为显式分支，默认分支返回 `base/theme/muiTheme.ts` 的结果。
- `utils/admin-themes.ts` 是主题标识、展示顺序、明暗与自定义主色能力的唯一数据来源；个人外观、站点后台设置、审查页和初始化读取它。显示名称仍由 i18n 管理，不在各页面手写主题数组或能力白名单。
- `themeAlgorithms` 组合 light / dark / compact 算法；`base/ConfigProviderApp.tsx` 承载当前 AppState。
- 共用组件使用 `useTheme` 或由 shell 显式传入主题值，具体写法见下文。产品隔离要求引用 ops，不在本地另写一份。
- 默认主题的 `Spin` 由 `muiTheme.ts` 的 `spin.indicator` 注入 `MaterialSpinIndicator.tsx`，尺寸使用 `Spin` token，圆弧动画在 `DefaultGlobalStyle.tsx`；不使用全局 `Spin.setDefaultIndicator`。顶部加载条和按钮 loading 图标各自沿用现有实现。
- 默认主题控件配置在 `material-component-config.ts`，通过 Ant Design 公开语义槽注入样式类；`MaterialControlsStyle.tsx` 仅由 `DefaultGlobalStyle` 挂载。字段保留独立 label 与布局，Input / InputNumber / Select 只补交互状态，不引入浮动标签。
- 全局 `ConfigProviderApp` 为普通密度显式传 `componentSize="medium"`，紧凑密度传 `small`。不要改回普通密度 `undefined`，否则 Ant Design 会插入/移除 SizeContext，重建整个页面并触发未保存预览的回滚。
- `AdminDashboardRouter` 为同一会话、同一路由且内容未变的数据保留引用，避免外观重绘时缓存反序列化产生新对象，触发表单重新填值。路由、会话或实际数据变化仍更新快照。
- `applyUserPreferences` 是有效外观与语言的统一应用入口，负责资源、React 状态、文档语言和偏好请求版本。`useAppearancePreview` 供站点后台设置和审查页临时预览、按会话恢复；个人设置保留自己的保存与请求生命周期。站点表单始终保存站点字段，不把个人偏好混入提交。

## 开发 UI 审查页

登录具备 `system.manage` 权限的账号，直接访问 `<contextPath>/admin/dev/ui`。例如本地 MemoryApplication 为 `http://localhost:18080/sub/admin/dev/ui`。这是在线开发工具，不加入菜单、全局搜索、原有 `/dev` 数据页面、PWA 预缓存或静态发布清单。

- 页面源码：`src/components/dev-ui.tsx`，懒加载路由：`admin-dashboard-routes.tsx`；后端由 `AdminUiWebSetup` 显式注册，`AdminDevController.ui()` 提供只读初始化标记，不读取缓存条目与锁列表。
- 页面复用当前全局主题，组件示例不能用局部 CSS 伪造适配。临时外观预览不写个人/站点偏好，退出页面恢复进入时的主题、主色、深浅和密度；重置示例只重置本地交互状态。
- 状态清单是人工维护的工程进度，随组件实现一起更新。`已适配`、`仅交互适配`、`仅主题基础` 不代表自动通过视觉验收，也不代表覆盖 Ant Design 的全部组件与变体。完整 Chip 家族、基础数据与反馈组件仍需按实际场景补充。
- 审查可通过稳定的 `#ui-controls`、`#ui-navigation`、`#ui-buttons`、`#ui-progress`、`#ui-fields`、`#ui-overlays`、`#ui-foundation`、`#ui-feedback` 定位；字段和触发器有可访问名称。示例只使用本地数据，不提交业务操作、启用开发模式或上传文件。
- Agent / UI 审核先检查本页的明暗、紧凑、主题切换、键盘焦点、错误与禁用状态，再检查真实业务页面的布局和内容。规则仍读取 Ops，不能以本页截图替代业务验收。

## 圆角规则

- 普通矩形块、卡片、面板、输入框、列表项、弹层容器、标签容器等圆角，统一使用主题配置。
- 不要在业务组件里直接写 `borderRadius: 8/10/12/14/16/18/24/999` 或 `border-radius: 8px/12px/...` 这类硬编码值。
- 优先按语义选择：
  - 小型元素使用 `theme.borderRadiusSM`
  - 默认块级元素使用 `theme.borderRadius`
  - 卡片、较大容器、强调型块使用 `theme.borderRadiusLG`
- 圆形、头像、纯圆点、`shape="circle"` 一类明确要求“正圆”的元素，可以继续使用 `50%`，这类属于形状语义，不属于普通主题圆角配置。
- 明确需要直角的地方可以使用 `0`，例如裁切型编辑器区域、拼接式输入区。

## 链接颜色规则

- 普通链接、链接按钮、可点击文字、卡片右上角 `extra`、伪链接操作等颜色，应优先跟随主题配置。
- 优先使用组件默认主题行为；如果需要显式指定颜色，使用 `theme.colorPrimary`、`theme.colorLink` 或项目当前主题主色，不要写死 `#1677ff`、`blue`、`#1890ff` 等固定值。
- 表单、设置页、弹窗里的轻量辅助操作入口，例如“编辑提示词”“优化简介”“优化提示词”，应尽量使用主题主色表达可点击性；不要用默认黑灰文字削弱入口识别，也不要写死固定颜色。
- 图标如果承担“链接入口”或“主要跳转动作”的视觉语义，也应与主题色保持一致。
- 状态色、告警色、成功色不属于链接色规则范围；仅在表达状态语义时使用，不要替代主题链接色。

## 边框规则

- 普通矩形块、卡片、面板、列表项、预览区、分割线等边框，线宽、线型、颜色都应来自主题 token。
- 不要直接写 `1px solid #f0f0f0`、`1px solid rgba(...)`、`1px solid ${theme.colorBorder}` 这类硬编码线宽/线型；应组合使用 `theme.lineWidth`、`theme.lineType`、`theme.colorBorder` 或 `theme.colorBorderSecondary`。
- 推荐在组件内先定义语义化变量，再用于 `border`、`borderBottom`、`borderLeft` 等属性：

```tsx
const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
```

- 虚线边框也应只把线型作为语义差异：

```tsx
const dashedBorder = `${theme.lineWidth}px dashed ${theme.colorBorderSecondary}`;
```

- 只有确实表达品牌、状态或图表语义时，才可以使用业务色边框；线宽和线型仍优先使用主题 token。

## Drawer 关闭按钮配置

- 关闭入口位置按 [Ops 后台 UI 规则](../../zrlog-ops/docs/ui-design-guide.md#zrlog-admin-web)。
- 优先在全局 `ConfigProvider` 中配置 `drawer={{ closable: { placement: "end" } }}` 作为默认行为，不要在每个页面重复声明。
- 只有确实需要覆盖全局默认时，才在单个 `Drawer` 上显式写 `closable`。

## 推荐写法

组件内联样式：

```tsx
import { useTheme } from "antd-style";

const theme = useTheme();

<div style={{ borderRadius: theme.borderRadiusLG }} />
<div style={{ border: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}` }} />
<Button type="link" style={{ color: theme.colorPrimary }} />
```

`styled-components` 或布局壳组件：

```tsx
type StyledProps = {
    borderRadius: number;
    borderRadiusLG: number;
};
```

```tsx
<StyledLayout
    borderRadius={theme.borderRadius}
    borderRadiusLG={theme.borderRadiusLG}
/>
```

```tsx
border-radius: ${(props) => props.borderRadiusLG}px;
```

## 不推荐写法

```tsx
<div style={{ borderRadius: 12 }} />
<div style={{ borderRadius: 999 }} />
<div style={{ border: "1px solid #f0f0f0" }} />
<div style={{ borderBottom: `1px solid ${theme.colorBorderSecondary}` }} />
<a style={{ color: "#1890ff" }} />
```

```css
border-radius: 16px;
border-radius: 50px;
```

上面这些写法会导致不同主题下圆角风格不一致，后续调整主题时也无法统一生效。

## 自检建议

涉及主题或样式改动时，提交前至少检查：

```bash
rg -n "borderRadius:\\s*[0-9]+|border-radius:\\s*[0-9]+px|1px solid|border:\\s*[\"']|borderBottom:\\s*[\"']|borderTop:\\s*[\"']|borderLeft:\\s*[\"']|borderRight:\\s*[\"']" zrlog-admin-web-ui/src/main/frontend/src/components zrlog-admin-web-ui/src/main/frontend/src/layout
```

排查结果时：

- `50%` 的圆形元素可以保留
- `0` 的直角元素按设计判断是否合理
- 其余硬编码圆角原则上应替换为主题 token
- `none`、`transparent`、纯布局重置类边框可以保留；普通 UI 边框原则上应替换为主题 token 组合
