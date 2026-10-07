import { Alert, Button, Card, Checkbox, ColorPicker, Drawer, Dropdown, Empty, Flex, Form, Modal, Radio, Segmented, Select, Slider, Spin, Tabs, Typography, Upload, theme } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import DatePicker from "@zrlog/ui/antd/DatePicker";
import Progress from "@zrlog/ui/antd/Progress";
import Switch from "@zrlog/ui/antd/Switch";
import Tree from "@zrlog/ui/antd/Tree";
import Input from "@zrlog/ui/antd/Input";
import InputNumber from "@zrlog/ui/antd/InputNumber";
import Pagination from "@zrlog/ui/antd/Pagination";
import Table from "@zrlog/ui/antd/Table";
import { useUiApp } from "@zrlog/ui/feedback";
import EditIcon from "@zrlog/ui/icons/edit";
import AddIcon from "@zrlog/ui/icons/add";
import UploadIcon from "@zrlog/ui/icons/upload";
import { useRef, useState } from "react";
import type { ReactNode } from "react";


import { getAppState } from "../base/ConfigProviderApp";
import { getColorByTheme, isDarkByTheme } from "../base/AppInit";
import { getRes } from "../utils/constants";
import type { AdminTheme } from "../utils/constants";
import { useAppearancePreview } from "../utils/use-appearance-preview";
import { getAdminThemeOptions, supportsDarkMode, supportsCustomPrimary } from "../utils/admin-themes";

// This is an engineering inventory, not a second source of design rules or a compliance score.
// Use the real theme provider; never style a fixture to make its screenshot pass.
const ReviewSection = ({
    id,
    title,
    components,
    status,
    children,
}: {
    id: string;
    title: string;
    components: string;
    status: string;
    children: ReactNode;
}) => {
    const { token } = theme.useToken();
    return (
        <section id={id} aria-labelledby={`${id}-title`} style={{ minWidth: 0 }}>
            <Card style={{ height: "100%" }}>
                <Flex wrap gap={token.marginXS} align="center" justify="space-between">
                    <Typography.Title
                        level={2}
                        id={`${id}-title`}
                        style={{ fontSize: token.fontSizeHeading4, margin: 0 }}
                    >
                        {title}
                    </Typography.Title>
                    <Tag>{status}</Tag>
                </Flex>
                <Typography.Paragraph type="secondary" style={{ marginTop: token.marginXS }}>
                    {components}
                </Typography.Paragraph>
                <Flex vertical gap={token.marginLG}>
                    {children}
                </Flex>
            </Card>
        </section>
    );
};

const AppearancePreview = () => {
    const { token } = theme.useToken();
    const initial = useRef({ ...getAppState() }).current;
    const [preview, setPreview] = useState(initial);
    const appearancePreview = useAppearancePreview();
    const res = getRes();
    const restore = () => {
        setPreview(initial);
        appearancePreview.restore();
    };
    const update = (next: Partial<typeof preview>) => {
        const value = { ...preview, ...next };
        setPreview(value);
        appearancePreview.preview({
            language: value.lang,
            appearance: {
                theme: value.theme as AdminTheme,
                darkMode: value.dark,
                compactMode: value.compactMode,
                colorPrimary: value.colorPrimary,
            },
        });
    };
    return (
        <Card>
            <Typography.Title level={2} style={{ marginTop: 0, fontSize: token.fontSizeHeading4 }}>
                {res.dev.ui.preview}
            </Typography.Title>
            <Typography.Paragraph type="secondary">{res.dev.ui.previewNote}</Typography.Paragraph>
            <Form layout="vertical">
                <Flex wrap gap="middle" align="end">
                    <Form.Item label={res.websiteAdmin.language.label} htmlFor="ui-preview-language">
                        <Select
                            id="ui-preview-language"
                            value={preview.lang}
                            style={{ width: 160 }}
                            options={[
                                { value: "zh_CN", label: res.websiteAdmin.language.chinese },
                                { value: "en_US", label: res.websiteAdmin.language.english },
                            ]}
                            onChange={(lang) => update({ lang })}
                        />
                    </Form.Item>
                    <Form.Item label={res.websiteAdmin.theme.label} htmlFor="ui-preview-theme">
                        <Select
                            id="ui-preview-theme"
                            value={preview.theme}
                            style={{ width: 180 }}
                            options={getAdminThemeOptions(res.websiteAdmin.theme.option)}
                            onChange={(value) =>
                                update({
                                    theme: value,
                                    dark: supportsDarkMode(value) ? preview.dark : isDarkByTheme(value),
                                    colorPrimary: supportsCustomPrimary(value)
                                        ? initial.colorPrimary
                                        : getColorByTheme(value),
                                })
                            }
                        />
                    </Form.Item>
                    <Form.Item label={res.websiteAdmin.dark.mode} htmlFor="ui-preview-dark">
                        <Switch
                            id="ui-preview-dark"
                            checked={preview.dark}
                            disabled={!supportsDarkMode(preview.theme)}
                            onChange={(dark) => update({ dark })}
                        />
                    </Form.Item>
                    <Form.Item label={res.websiteAdmin.compact.mode} htmlFor="ui-preview-compact">
                        <Switch
                            id="ui-preview-compact"
                            checked={preview.compactMode}
                            onChange={(compactMode) => update({ compactMode })}
                        />
                    </Form.Item>
                    <Form.Item label={res.websiteAdmin.color.primary}>
                        <ColorPicker
                            value={preview.colorPrimary}
                            disabledAlpha
                            showText
                            disabled={!supportsCustomPrimary(preview.theme)}
                            onChangeComplete={(color) => update({ colorPrimary: color.toHexString() })}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Button onClick={restore}>{res.dev.ui.restore}</Button>
                    </Form.Item>
                </Flex>
            </Form>
        </Card>
    );
};

const Fixtures = () => {
    const r = getRes().dev.ui;
    const { token } = theme.useToken();
    const { message, notification } = useUiApp();
    const [dialog, setDialog] = useState(false);
    const [drawer, setDrawer] = useState(false);
    const [added, setAdded] = useState(false);
    const [menuResult, setMenuResult] = useState(r.none);
    const [slider, setSlider] = useState(35);
    const choices = [
        { value: "published", label: r.published },
        { value: "draft", label: r.drafts },
        { value: "disabled", label: r.disabled, disabled: true },
    ];
    const sample = (name: string, control: ReactNode) => (
        <Flex vertical gap="small" align="start" key={name}>
            <Typography.Text type="secondary">{name}</Typography.Text>
            {control}
        </Flex>
    );
    return (
        <>
            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 360px), 1fr))",
                    gap: token.marginLG,
                }}
            >
                <ReviewSection
                    id="ui-controls"
                    title={r.controls}
                    components="Switch · Checkbox · Radio"
                    status={r.adapted}
                >
                    <Flex wrap gap="large">
                        {sample(r.off, <Switch aria-label={r.off} />)}
                        {sample(r.on, <Switch aria-label={r.on} defaultChecked />)}
                        {sample(r.disabled, <Switch aria-label={r.disabled} disabled />)}
                        {sample(r.selectedDisabled, <Switch aria-label={r.selectedDisabled} disabled defaultChecked />)}
                        {sample(r.loading, <Switch aria-label={r.loading} loading defaultChecked />)}
                        {sample(r.small, <Switch aria-label={r.small} size="small" />)}
                        {sample(
                            r.textSwitch,
                            <Switch aria-label={r.textSwitch} checkedChildren={r.on} unCheckedChildren={r.off} />
                        )}
                    </Flex>
                    <Flex wrap gap="small">
                        <Checkbox>{r.off}</Checkbox>
                        <Checkbox defaultChecked>{r.on}</Checkbox>
                        <Checkbox indeterminate>{r.mixed}</Checkbox>
                        <Checkbox disabled>{r.disabled}</Checkbox>
                        <Checkbox disabled defaultChecked>
                            {r.selectedDisabled}
                        </Checkbox>
                        <Checkbox disabled indeterminate>
                            {r.mixedDisabled}
                        </Checkbox>
                    </Flex>
                    <Radio.Group aria-label="Radio" defaultValue="published" options={choices} />
                    <Radio.Group
                        aria-label="Radio.Button"
                        defaultValue="published"
                        optionType="button"
                        options={choices}
                    />
                </ReviewSection>
                <ReviewSection
                    id="ui-navigation"
                    title={r.navigation}
                    components="Segmented · Tabs · Tag"
                    status={r.adapted}
                >
                    <Segmented
                        aria-label="Segmented"
                        options={[{ value: "all", label: r.all }, ...choices]}
                        defaultValue="all"
                    />
                    <Tabs
                        items={[
                            {
                                key: "overview",
                                label: r.overview,
                                children: r.tabContent.replace("{name}", r.overview),
                            },
                            { key: "details", label: r.details, children: r.tabContent.replace("{name}", r.details) },
                            { key: "disabled", label: r.disabled, disabled: true },
                        ]}
                    />
                    <Flex wrap gap="small">
                        <Tag>{r.metadata}</Tag>
                        <Tag color="success">{r.success}</Tag>
                        <Tag color="warning">{r.warning}</Tag>
                        <Tag color="error">{r.error}</Tag>
                        <Tag closable>{r.removable}</Tag>
                        <Tag icon={<AddIcon />} onClick={() => setAdded(!added)}>
                            {added ? r.added : r.addTag}
                        </Tag>
                        <Tag disabled onClick={() => setAdded(!added)}>
                            {r.disabled}
                        </Tag>
                    </Flex>
                    <Typography.Text type="secondary">{r.chipNote}</Typography.Text>
                </ReviewSection>
                <ReviewSection id="ui-buttons" title={r.buttons} components="Button" status={r.adapted}>
                    <Flex wrap gap="small">
                        <Button type="primary">{r.filled}</Button>
                        <Button color="primary" variant="filled">
                            {r.tonal}
                        </Button>
                        <Button>{r.outlined}</Button>
                        <Button type="text">{r.text}</Button>
                        <Button danger type="primary">
                            {r.danger}
                        </Button>
                        <Button disabled>{r.disabled}</Button>
                        <Button type="primary" disabled>
                            {r.selectedDisabled}
                        </Button>
                        <Button icon={<EditIcon />} aria-label={r.edit} />
                        <Button type="text" icon={<EditIcon />} aria-label={r.edit} />
                        <Button loading>{r.loading}</Button>
                    </Flex>
                </ReviewSection>
                <ReviewSection
                    id="ui-progress"
                    title={r.progress}
                    components="Slider · Progress · Spin"
                    status={r.adapted}
                >
                    <Slider value={slider} onChange={setSlider} ariaLabelForHandle={r.slider} />
                    <Slider defaultValue={35} disabled ariaLabelForHandle={r.disabled} />
                    <Progress percent={slider} />
                    <Progress percent={70} status="exception" />
                    <Flex wrap align="center" gap="large">
                        <Progress percent={slider} type="circle" size={72} />
                        <Spin size="small" />
                        <Spin />
                        <Spin size="large" />
                    </Flex>
                </ReviewSection>
                <ReviewSection
                    id="ui-fields"
                    title={r.fields}
                    components="Input · InputNumber · Select"
                    status={r.interactionOnly}
                >
                    <Typography.Text type="secondary">{r.fieldsNote}</Typography.Text>
                    <Form layout="vertical">
                        <Form.Item label={r.textField} htmlFor="ui-text">
                            <Input id="ui-text" placeholder={r.placeholder} allowClear />
                        </Form.Item>
                        <Form.Item label={r.numberField} htmlFor="ui-number">
                            <InputNumber id="ui-number" defaultValue={4} min={0} max={10} style={{ width: "100%" }} />
                        </Form.Item>
                        <Form.Item label={r.selectField} htmlFor="ui-select">
                            <Select id="ui-select" showSearch defaultValue="published" options={choices} />
                        </Form.Item>
                        <Form.Item
                            label={r.error}
                            htmlFor="ui-error"
                            validateStatus="error"
                            help={<span id="ui-error-help">{r.fieldError}</span>}
                        >
                            <Input
                                id="ui-error"
                                aria-invalid
                                aria-describedby="ui-error-help"
                                defaultValue={r.exampleValue}
                            />
                        </Form.Item>
                        <Form.Item label={r.disabledField} htmlFor="ui-disabled">
                            <Input id="ui-disabled" disabled defaultValue={r.exampleValue} />
                        </Form.Item>
                        <Flex wrap gap="small">
                            <InputNumber status="error" aria-label={`${r.numberField} ${r.error}`} defaultValue={-1} />
                            <Select
                                status="error"
                                aria-label={`${r.selectField} ${r.error}`}
                                style={{ minWidth: 150 }}
                                options={choices}
                                placeholder={r.selectField}
                            />
                            <InputNumber disabled aria-label={`${r.numberField} ${r.disabled}`} defaultValue={4} />
                            <Select
                                disabled
                                aria-label={`${r.selectField} ${r.disabled}`}
                                style={{ minWidth: 150 }}
                                options={choices}
                                defaultValue="published"
                            />
                        </Flex>
                    </Form>
                </ReviewSection>
                <ReviewSection
                    id="ui-overlays"
                    title={r.overlays}
                    components="Modal · Drawer · Dropdown"
                    status={r.adapted}
                >
                    <Flex wrap gap="small">
                        <Button onClick={() => setDialog(true)}>{r.openDialog}</Button>
                        <Button onClick={() => setDrawer(true)}>{r.openDrawer}</Button>
                        <Dropdown
                            trigger={["click"]}
                            menu={{
                                items: [
                                    { key: "action", label: r.menuAction },
                                    { key: "disabled", label: r.disabled, disabled: true },
                                    { key: "danger", label: r.danger, danger: true },
                                ],
                                onClick: ({ key }) => setMenuResult(key === "danger" ? r.danger : r.menuAction),
                            }}
                        >
                            <Button>{r.openMenu}</Button>
                        </Dropdown>
                    </Flex>
                    <Typography.Text aria-live="polite">{r.menuResult.replace("{name}", menuResult)}</Typography.Text>
                    <Typography.Text type="secondary">{r.overlayContent}</Typography.Text>
                </ReviewSection>
                <ReviewSection
                    id="ui-foundation"
                    title={r.foundation}
                    components="Table · Tree · DatePicker · Pagination"
                    status={r.tokensOnly}
                >
                    <Typography.Text type="secondary">{r.foundationNote}</Typography.Text>
                    <Table
                        size="small"
                        pagination={false}
                        rowKey="name"
                        dataSource={[{ name: r.exampleValue, status: r.published }]}
                        columns={[
                            { title: r.name, dataIndex: "name" },
                            { title: r.status, dataIndex: "status" },
                        ]}
                    />
                    <Pagination size="small" defaultCurrent={1} total={30} showSizeChanger={false} />
                    <Form layout="vertical">
                        <Form.Item label={r.date} htmlFor="ui-date">
                            <DatePicker id="ui-date" style={{ width: "100%" }} />
                        </Form.Item>
                    </Form>
                    <Tree
                        checkable
                        defaultExpandedKeys={["root"]}
                        treeData={[
                            {
                                key: "root",
                                title: r.treeRoot,
                                children: [
                                    { key: "leaf", title: r.treeLeaf },
                                    { key: "disabled", title: r.disabled, disabled: true },
                                ],
                            },
                        ]}
                    />
                </ReviewSection>
                <ReviewSection
                    id="ui-feedback"
                    title={r.notification}
                    components="Alert · Message · Notification · Upload · Empty"
                    status={r.tokensOnly}
                >
                    <Alert type="info" showIcon title={r.feedback} />
                    <Alert type="error" showIcon title={r.fieldError} />
                    <Flex wrap gap="small">
                        <Button onClick={() => message.info(r.feedback)}>{r.showMessage}</Button>
                        <Button onClick={() => notification.info({ title: r.notification, description: r.feedback })}>
                            {r.showNotification}
                        </Button>
                    </Flex>
                    <Upload disabled beforeUpload={() => false}>
                        <Button disabled icon={<UploadIcon />}>
                            {r.upload}
                        </Button>
                    </Upload>
                    <Typography.Text type="secondary">{r.uploadNote}</Typography.Text>
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={r.empty} />
                </ReviewSection>
            </div>
            <Modal
                title={r.dialogTitle}
                open={dialog}
                onCancel={() => setDialog(false)}
                onOk={() => setDialog(false)}
                okText={r.confirm}
                cancelText={r.close}
            >
                <Typography.Paragraph>{r.overlayContent}</Typography.Paragraph>
            </Modal>
            <Drawer
                title={r.drawerTitle}
                open={drawer}
                onClose={() => setDrawer(false)}
                footer={
                    <Button type="primary" onClick={() => setDrawer(false)}>
                        {r.close}
                    </Button>
                }
            >
                <Typography.Paragraph>{r.overlayContent}</Typography.Paragraph>
            </Drawer>
        </>
    );
};

const DevUi = () => {
    const r = getRes().dev.ui;
    const { token } = theme.useToken();
    const [revision, setRevision] = useState(0);
    return (
        <div data-ui-review style={{ minWidth: 0 }}>
            <Flex vertical gap={token.marginLG}>
                <div>
                    <Flex wrap justify="space-between" align="center" gap="small">
                        <Typography.Title level={1} style={{ fontSize: token.fontSizeHeading2, margin: 0 }}>
                            {r.title}
                        </Typography.Title>
                        <Button onClick={() => setRevision((current) => current + 1)}>{r.reset}</Button>
                    </Flex>
                    <Typography.Paragraph style={{ marginTop: token.marginSM }}>{r.description}</Typography.Paragraph>
                    <Typography.Paragraph type="secondary">{r.inventoryNote}</Typography.Paragraph>
                    <Typography.Text type="secondary">{r.adaptedNote}</Typography.Text>
                </div>
                <AppearancePreview />
                <Fixtures key={revision} />
                <Typography.Paragraph type="secondary">{r.excludedNote}</Typography.Paragraph>
            </Flex>
        </div>
    );
};

export default DevUi;
