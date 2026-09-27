import {AliasToken} from "antd/es/theme/interface";

export const getScoreStrokeColor = (score: number, theme: AliasToken) => {
    if (score < 60) {
        return theme.colorError;
    }
    if (score < 80) {
        return theme.colorWarning;
    }
    return theme.colorSuccess;
};
