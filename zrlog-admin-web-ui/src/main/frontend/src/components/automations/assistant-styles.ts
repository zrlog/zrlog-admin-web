import { createStyles } from "antd-style";

export const useAssistantStyles = createStyles(({ token, css }) => ({
    page: css`
        max-width: 1280px;
        margin: 0 auto;
        padding: ${token.padding}px;
        @media (max-width: ${token.screenSM}px) {
            padding: ${token.paddingXS}px;
        }
    `,
    header: css`
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: ${token.marginSM}px;
        margin-bottom: ${token.marginLG}px;
    `,
    headerText: css`
        flex: 1;
        min-width: 0;
    `,
    workspace: css`
        display: grid;
        grid-template-columns: minmax(0, 1fr) 310px;
        gap: ${token.marginLG}px;
        align-items: start;
        @media (max-width: ${token.screenLG}px) {
            grid-template-columns: minmax(0, 1fr);
        }
    `,
    settingsLink: css`
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex: 0 0 auto;
        width: ${token.controlHeightLG}px;
        height: ${token.controlHeightLG}px;
        color: ${token.colorLink};
        font-size: ${token.fontSizeHeading4}px;
        border-radius: ${token.borderRadius}px;
        &:hover {
            color: ${token.colorLinkHover};
            background: ${token.colorFillTertiary};
        }
        &:focus-visible {
            outline: ${token.lineWidthBold}px solid ${token.colorPrimary};
            outline-offset: ${token.lineWidthBold}px;
        }
    `,
    panel: css`
        min-width: 0;
        background: ${token.colorBgContainer};
        border: ${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary};
        border-radius: ${token.borderRadiusLG}px;
    `,
    panelHeader: css`
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: ${token.marginSM}px;
        padding: ${token.padding}px;
        flex-wrap: wrap;
    `,
    taskRow: css`
        padding: ${token.padding}px;
        border-top: ${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary};
        min-width: 0;
    `,
    identity: css`
        display: flex;
        align-items: flex-start;
        gap: ${token.marginSM}px;
        min-width: 0;
    `,
    icon: css`
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex: 0 0 auto;
        width: ${token.controlHeight}px;
        height: ${token.controlHeight}px;
        background: ${token.colorPrimaryBg};
        color: ${token.colorPrimaryText};
        border-radius: ${token.borderRadius}px;
        font-size: ${token.fontSizeHeading4}px;
    `,
    taskHeading: css`
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: ${token.marginSM}px;
    `,
    systemHeading: css`
        display: grid;
        grid-template-columns: ${token.controlHeight}px minmax(0, 1fr) auto;
        column-gap: ${token.marginSM}px;
        row-gap: ${token.marginXXS}px;
        align-items: start;
    `,
    systemIcon: css`
        grid-row: 1 / 3;
    `,
    systemDescription: css`
        grid-column: 2 / -1;
    `,
    title: css`
        margin: 0;
        font-size: ${token.fontSizeLG}px;
        font-weight: ${token.fontWeightStrong};
        line-height: ${token.lineHeight};
        overflow-wrap: anywhere;
    `,
    taskBody: css`
        margin-top: ${token.marginXS}px;
        margin-inline-start: calc(${token.controlHeight}px + ${token.marginSM}px);
        @media (max-width: ${token.screenSM}px) {
            margin-inline-start: 0;
        }
    `,
    metadata: css`
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        column-gap: ${token.marginLG}px;
        row-gap: ${token.marginXXS}px;
        font-size: ${token.fontSizeSM}px;
        color: ${token.colorTextSecondary};
    `,
    details: css`
        display: grid;
        gap: ${token.marginXXS}px;
        min-width: 0;
    `,
    footer: css`
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: ${token.marginSM}px;
    `,
    actions: css`
        margin-inline-start: auto;
    `,
    goals: css`
        padding: ${token.paddingLG}px;
        min-width: 0;
    `,
    goal: css`
        padding-block: ${token.padding}px;
        border-top: ${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary};
        &:last-child {
            padding-bottom: 0;
        }
    `,
    toolbar: css`
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: ${token.marginSM}px;
        margin-bottom: ${token.margin}px;
    `,
}));
