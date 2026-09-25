import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Form, Select, Space, Spin, Typography, message, theme } from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getSsDate } from "../base/SsData";
import { getRes } from "../utils/constants";
import { putCache } from "../utils/cache";
import { useResponsiveFormLayout } from "../utils/responsive-form";
import {
    applyUserPreferences,
    mergeUserPreferenceChanges,
    resolveUserPreferences,
    UserPreferences,
    UserPreferencesResponse,
} from "../utils/user-preferences";
import type { ApiResponse } from "../type";
import AdminAppearanceFields from "./common/AdminAppearanceFields";

const UserPreferencesForm = ({ offline }: { offline: boolean }) => {
    const axios = useAxiosBaseInstance();
    const [form] = Form.useForm<UserPreferences>();
    const [data, setData] = useState<UserPreferencesResponse>();
    const [draft, setDraft] = useState<UserPreferences>({});
    const draftRef = useRef<UserPreferences>({});
    const savedRef = useRef<UserPreferences>();
    const previewing = useRef(false);
    const mounted = useRef(false);
    const loadSequence = useRef(0);
    const session = useRef(getSsDate().key);
    const savingRef = useRef(false);
    const [failed, setFailed] = useState(false);
    const [saving, setSaving] = useState(false);
    const [messageApi, contextHolder] = message.useMessage();
    const { formLayout } = useResponsiveFormLayout();
    const { token } = theme.useToken();
    const res = getRes().user.preferences;
    const dirty = !!data && JSON.stringify(draft) !== JSON.stringify(data.overrides);

    const restorePreview = () => {
        if (previewing.current && savedRef.current && session.current === getSsDate().key) {
            applyUserPreferences(savedRef.current);
        }
        previewing.current = false;
    };

    const acceptSaved = (result: UserPreferencesResponse) => {
        savedRef.current = result.effective;
        draftRef.current = result.overrides;
        setDraft(result.overrides);
        setData(result);
    };

    const load = () => {
        const sequence = ++loadSequence.current;
        setFailed(false);
        axios
            .get<ApiResponse<UserPreferencesResponse>>("/api/admin/user/preferences")
            .then(({ data: response }) => {
                if (!mounted.current || sequence !== loadSequence.current || session.current !== getSsDate().key)
                    return;
                if (response.error !== 0) {
                    setFailed(true);
                    messageApi.error(response.message);
                    return;
                }
                acceptSaved(response.data);
            })
            .catch(() => {
                if (mounted.current && sequence === loadSequence.current) setFailed(true);
            });
    };

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            loadSequence.current++;
            restorePreview();
        };
    }, []);

    useEffect(() => {
        if (!offline) load();
        else {
            loadSequence.current++;
            restorePreview();
        }
    }, [offline]);

    useEffect(() => {
        if (data && !offline) form.setFieldsValue(data.effective);
    }, [data, offline, form]);

    useEffect(() => {
        if (!dirty && !saving) return;
        const beforeUnload = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = "";
        };
        window.addEventListener("beforeunload", beforeUnload);
        return () => window.removeEventListener("beforeunload", beforeUnload);
    }, [dirty, saving]);

    const preview = (overrides: UserPreferences, updateForm = false) => {
        if (!data || savingRef.current || offline) return;
        draftRef.current = overrides;
        setDraft(overrides);
        const effective = resolveUserPreferences(data.defaults, overrides);
        if (updateForm) form.setFieldsValue(effective);
        previewing.current = true;
        applyUserPreferences(effective);
    };

    const save = async () => {
        if (savingRef.current || offline || !data) return;
        savingRef.current = true;
        setSaving(true);
        try {
            const { data: response } = await axios.post<ApiResponse<UserPreferencesResponse>>(
                "/api/admin/user/updatePreferences",
                draftRef.current
            );
            if (response.error !== 0) {
                if (mounted.current) messageApi.error(response.message);
                return;
            }
            // A request may finish after navigation; never apply it to another logged-in account.
            savedRef.current = response.data.effective;
            previewing.current = false;
            if (session.current === getSsDate().key) {
                putCache({});
                applyUserPreferences(response.data.effective);
            }
            if (mounted.current) {
                acceptSaved(response.data);
                messageApi.success(getRes().user.preferences.saved);
            }
        } catch {
            if (mounted.current) messageApi.error(getRes().user.preferences.saveFailed);
        } finally {
            savingRef.current = false;
            if (mounted.current) setSaving(false);
        }
    };

    const knowledgeScope = Form.useWatch(["assistant", "knowledgeScope"], form);
    const canReadOthers = ["owner", "admin", "editor"].includes(getSsDate().user?.role || "");
    const pageSize = Form.useWatch("articlePageSize", form);
    const pageSizes = Array.from(new Set([10, 20, 50, 100, ...(pageSize ? [pageSize] : [])])).sort((a, b) => a - b);
    const headingStyle = { marginTop: token.margin, marginBottom: token.marginLG };
    const divider = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;

    return (
        <Card title={res.title} style={{ maxWidth: 800, width: "100%" }}>
            {contextHolder}
            {offline ? (
                <Alert type="info" title={res.offline} />
            ) : failed ? (
                <Space orientation="vertical">
                    <Alert type="error" title={res.loadFailed} />
                    <Button onClick={load}>{res.retry}</Button>
                </Space>
            ) : !data ? (
                <Spin />
            ) : (
                <Form
                    {...formLayout}
                    form={form}
                    disabled={saving}
                    onValuesChange={(changes) => preview(mergeUserPreferenceChanges(draftRef.current, changes))}
                    onFinish={() => void save()}
                >
                    <Typography.Paragraph type="secondary">{res.description}</Typography.Paragraph>
                    <Typography.Title level={5} style={headingStyle}>
                        {res.appearanceTitle}
                    </Typography.Title>
                    <AdminAppearanceFields
                        names={{
                            language: "language",
                            theme: ["appearance", "theme"],
                            darkMode: ["appearance", "darkMode"],
                            compactMode: ["appearance", "compactMode"],
                            colorPrimary: ["appearance", "colorPrimary"],
                        }}
                    />
                    <Typography.Title level={5} style={headingStyle}>
                        {res.writingTitle}
                    </Typography.Title>
                    <Form.Item name="articlePageSize" label={res.articlePageSize}>
                        <Select
                            style={{ width: 200, maxWidth: "100%" }}
                            options={pageSizes.map((value) => ({
                                value,
                                label: res.articlesPerPage.replace("{count}", String(value)),
                            }))}
                        />
                    </Form.Item>
                    <Form.Item name={["editor", "autoSaveInterval"]} label={res.autoSaveInterval}>
                        <Select
                            style={{ width: 200, maxWidth: "100%" }}
                            options={[2, 5, 10].map((value) => ({
                                value,
                                label: res.seconds.replace("{seconds}", String(value)),
                            }))}
                        />
                    </Form.Item>
                    <Typography.Title level={5} style={headingStyle}>
                        {res.assistantTitle}
                    </Typography.Title>
                    <Form.Item name={["assistant", "knowledgeScope"]} label={res.knowledge} extra={res.knowledgeHelp}>
                        <Select
                            style={{ width: 360, maxWidth: "100%" }}
                            options={[
                                { value: "off", label: res.scopeOff },
                                { value: "own_public", label: res.scopeOwnPublic },
                                { value: "own_all", label: res.scopeOwnAll },
                                ...[
                                    {
                                        value: "accessible_public",
                                        label: res.scopeAccessiblePublic,
                                        disabled: !canReadOthers,
                                    },
                                    {
                                        value: "accessible_all",
                                        label: res.scopeAccessibleAll,
                                        disabled: !canReadOthers,
                                    },
                                ].filter((option) => canReadOthers || option.value === knowledgeScope),
                            ]}
                        />
                    </Form.Item>
                    <div style={{ borderTop: divider, paddingTop: token.padding, marginTop: token.marginLG }}>
                        <Space wrap>
                            <Button type="primary" htmlType="submit" loading={saving} disabled={!dirty}>
                                {res.save}
                            </Button>
                            <Button disabled={!dirty} onClick={() => preview(data.overrides, true)}>
                                {res.undo}
                            </Button>
                            <Button type="link" onClick={() => preview({}, true)}>
                                {res.reset}
                            </Button>
                        </Space>
                        <Typography.Paragraph type="secondary" style={{ marginTop: token.marginSM, marginBottom: 0 }}>
                            {res.previewHint}
                        </Typography.Paragraph>
                    </div>
                </Form>
            )}
        </Card>
    );
};

export default UserPreferencesForm;
