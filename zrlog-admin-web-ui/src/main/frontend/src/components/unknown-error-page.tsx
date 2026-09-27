import Result, { ExceptionStatusType } from "antd/es/result";
import { CSSProperties, FunctionComponent, ReactNode } from "react";

export type ErrorPageProps = {
    code: ExceptionStatusType;
    data: {
        message?: ReactNode;
    };
    style?: CSSProperties;
};

const UnknownErrorPage: FunctionComponent<ErrorPageProps> = ({ code, data, style }) => {
    return <Result status={code} title={code} subTitle={data.message} style={style} />;
};

export default UnknownErrorPage;
