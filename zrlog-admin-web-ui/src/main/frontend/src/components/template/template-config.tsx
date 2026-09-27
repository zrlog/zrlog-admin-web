import { useId } from "react";
import { Button, Card, Col, Divider, Row } from "antd";
import { useTheme } from "antd-style";
import { getRes } from "../../utils/constants";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import TemplateConfigForm from "./template-config-form";
import { hasConfigFields, isLargeConfig, TemplateConfigData } from "./template-config-model";
import { useTemplateConfig } from "./use-template-config";

export type { ConfigParam } from "./template-config-model";

// Compatibility page for /template-config[.html]; retain for the next several releases.
const TemplateConfig = ({
    data,
    offline,
    offlineData,
}: {
    data: TemplateConfigData;
    offline: boolean;
    offlineData: boolean;
}) => {
    const theme = useTheme();
    const { narrow } = useResponsiveFormLayout();
    const formId = useId();
    const disabled = offline || offlineData;
    const editor = useTemplateConfig(data, disabled);
    const large = isLargeConfig(data);
    const content = (
        <>
            <TemplateConfigForm
                data={data}
                values={editor.values}
                onChange={editor.changeValues}
                onSubmit={editor.save}
                formId={formId}
                disabled={disabled}
            />
            {hasConfigFields(data) && (
                <>
                    <Divider />
                    <Button type="primary" htmlType="submit" form={formId} loading={editor.saving} disabled={disabled}>
                        {getRes().templateConfig.save}
                    </Button>
                </>
            )}
        </>
    );
    return (
        <>
            {editor.contextHolder}
            <Row>
                <Col xs={24} style={{ maxWidth: large ? 900 : 600, width: "100%" }}>
                    {large ? (
                        content
                    ) : (
                        <Card
                            title={data.name || data.shortTemplate || getRes().templateConfig.title}
                            styles={{ body: { padding: narrow ? theme.padding : theme.paddingLG } }}
                        >
                            {content}
                        </Card>
                    )}
                </Col>
            </Row>
        </>
    );
};

export default TemplateConfig;
