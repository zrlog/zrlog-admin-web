import { Button, Form, Input, theme } from "antd";
import { getBackendServerUrl } from "../../utils/constants";
import { LoginOutlined } from "@ant-design/icons";
import { classes, StyledLoginPage } from "../login";
import { FunctionComponent, useState } from "react";
import zh_CN from "antd/es/locale/zh_CN";
import en_US from "antd/es/locale/en_US";
import { getAppState } from "../../base/ConfigProviderApp";
import Title from "antd/es/typography/Title";
import { getAdminI18n } from "../../i18n/admin";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 14 },
};

type InitProps = {
    lang: string;
    onSubmit: (backendServerUrl: string) => void;
};

const Init: FunctionComponent<InitProps> = ({ onSubmit, lang }) => {
    const res = getAdminI18n(lang);

    const getNextDesc = () => {
        if (lang === "zh_CN") {
            return zh_CN.Tour?.Next;
        }
        return en_US.Tour?.Next;
    };

    const defaultUrl = getBackendServerUrl() != "/" ? getBackendServerUrl() : window.location.origin;
    const [url, setUrl] = useState<string>(defaultUrl);

    const onOk = () => {
        if (url.endsWith("/")) {
            onSubmit(url);
            return;
        }
        onSubmit(url + "/");
    };

    const { token } = theme.useToken();

    return (
        <StyledLoginPage
            dark={getAppState().dark}
            colorBgContainer={token.colorBgContainer}
            colorBgLayout={token.colorBgLayout}
            mainColor={getAppState().colorPrimary}
            desk={getAppState().theme === "desk"}
            theme={token}
        >
            <div className={classes.container}>
                <div className={classes.sideImage}></div>
                <div className={classes.formSection}>
                    <Title level={3} className={classes.title}>
                        {res.login.submit}
                    </Title>
                    <Form
                        {...layout}
                        layout="vertical"
                        initialValues={{
                            backendServerUrl: url,
                        }}
                        onFinish={() => {
                            onOk();
                        }}
                        onValuesChange={(e) => {
                            setUrl(e["backendServerUrl"]);
                        }}
                    >
                        <Form.Item
                            label={res.login.backendServerUrl}
                            name={"backendServerUrl"}
                            rules={[{ required: true }]}
                        >
                            <Input styles={{ input: { width: "100%" } }} />
                        </Form.Item>
                        <Button type="primary" style={{ maxWidth: 208 }} htmlType="submit">
                            <LoginOutlined /> {getNextDesc()}
                        </Button>
                    </Form>
                </div>
            </div>
        </StyledLoginPage>
    );
};

export default Init;
