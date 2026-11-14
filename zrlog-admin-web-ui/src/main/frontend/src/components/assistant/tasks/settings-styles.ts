import { createStyles } from "antd-style";

export const useAssistantSettingsStyles = createStyles(({ token, css }) => ({
    page: css`
        display: grid;
        gap: ${token.marginXL}px;
        min-width: 0;
    `,
    section: css`
        display: grid;
        gap: ${token.marginSM}px;
        min-width: 0;
    `,
    title: css`
        margin: 0;
        font-size: ${token.fontSizeLG}px;
        font-weight: ${token.fontWeightStrong};
        line-height: ${token.lineHeightLG};
    `,
    row: css`
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: ${token.margin}px;
        min-width: 0;
    `,
    label: css`
        display: grid;
        gap: ${token.marginXXS}px;
        min-width: 0;
        overflow-wrap: anywhere;
    `,
    help: css`
        margin: 0;
        color: ${token.colorTextSecondary};
        font-size: ${token.fontSize}px;
        font-weight: normal;
    `,
    metadata: css`
        color: ${token.colorTextSecondary};
        font-size: ${token.fontSizeSM}px;
        overflow-wrap: anywhere;
    `,
    toggle: css`
        flex-shrink: 0;
    `,
    details: css`
        display: grid;
        gap: ${token.margin}px;
        min-width: 0;
    `,
    field: css`
        display: grid;
        gap: ${token.marginXS}px;
        min-width: 0;
    `,
    taskTitle: css`
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: ${token.marginXS}px;
        min-width: 0;
    `,
}));
