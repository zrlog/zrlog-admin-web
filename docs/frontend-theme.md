# 前端主题规范

本文档用于约束后台前端的主题相关实现，避免组件样式脱离全局主题配置。

## 总原则

- 后台前端的可配置视觉属性应尽量复用主题 token，不要在业务组件里直接写死。
- 优先复用 Ant Design / `antd-style` 暴露的主题值，例如 `theme.borderRadius`、`theme.borderRadiusSM`、`theme.borderRadiusLG`、`theme.colorPrimary`、`theme.colorLink`。
- 当样式写在 `styled-components`、封装布局组件或跨组件公共样式里时，需要把主题值显式传入，不要重新写一套固定值。

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
- 图标如果承担“链接入口”或“主要跳转动作”的视觉语义，也应与主题色保持一致。
- 状态色、告警色、成功色不属于链接色规则范围；仅在表达状态语义时使用，不要替代主题链接色。

## Drawer 关闭按钮规则

- 所有使用默认关闭按钮的 `Drawer`，关闭按钮统一放在右侧。
- 优先在全局 `ConfigProvider` 中配置 `drawer={{ closable: { placement: "end" } }}` 作为默认行为，不要在每个页面重复声明。
- 只有确实需要覆盖全局默认时，才在单个 `Drawer` 上显式写 `closable`。
- 如果 `Drawer` 使用自定义标题区并手动渲染关闭按钮，也应保持关闭按钮位于右上角。

## 推荐写法

组件内联样式：

```tsx
import { useTheme } from "antd-style";

const theme = useTheme();

<div style={{ borderRadius: theme.borderRadiusLG }} />
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
rg -n "borderRadius:\\s*[0-9]+|border-radius:\\s*[0-9]+px" src/main/frontend/src/components src/main/frontend/src/layout
```

排查结果时：

- `50%` 的圆形元素可以保留
- `0` 的直角元素按设计判断是否合理
- 其余硬编码圆角原则上应替换为主题 token
