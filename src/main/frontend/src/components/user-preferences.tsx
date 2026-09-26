import SettingsSubmitBar from "./common/SettingsSubmitBar";
import { useEffect, useRef, useState } from "react";
import { Alert, Button, Form, Select, Typography, message, theme } from "antd";
import { useLocation } from "react-router-dom";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getSsDate } from "../base/SsData";
import { getRes } from "../utils/constants";
import { getPageDataCacheKey, putCache } from "../utils/cache";
import { useResponsiveFormLayout } from "../utils/responsive-form";
import {
    applyUserPreferences,
    mergeUserPreferenceChanges,
    replaceUserPreferencePage,
    resolveUserPreferences,
    UserPreferences,
    UserPreferencesResponse,
} from "../utils/user-preferences";
import type { AdminCommonProps, ApiResponse } from "../type";
import AdminAppearanceFields from "./common/AdminAppearanceFields";
import type { UserPreferencePage } from "../utils/account-page-routes";

const UserPreferencesForm = ({
    data: initialData,
    offline,
    updateCache,
    activePage = "appearance",
}: Pick<AdminCommonProps<UserPreferencesResponse>, "data" | "offline" | "updateCache"> & {
    activePage?: UserPreferencePage;
}) => {
    const axios = useAxiosBaseInstance();
    const location = useLocation();
    const [form] = Form.useForm<UserPreferences>();
    const [data, setData] = useState(initialData);
    const dataRef = useRef(initialData);
    const [draft, setDraft] = useState(initialData.overrides);
    const draftRef = useRef(initialData.overrides);
    const savedRef = useRef(initialData.effective);
    const previewing = useRef(false);
    const mounted = useRef(false);
    const session = useRef(getSsDate().key);
    const savingRef = useRef(false);
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
        dataRef.current = result;
        savedRef.current = result.effective;
        draftRef.current = result.overrides;
        setDraft(result.overrides);
        setData(result);
        form.setFieldsValue(result.effective);
    };

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            restorePreview();
        };
    }, []);

    useEffect(() => {
        if (offline) restorePreview();
    }, [offline]);

    useEffect(() => {
        if (savingRef.current) return;
        const hasDraft = JSON.stringify(draftRef.current) !== JSON.stringify(dataRef.current.overrides);
        dataRef.current = initialData;
        savedRef.current = initialData.effective;
        setData(initialData);
        const nextDraft = hasDraft
            ? replaceUserPreferencePage(initialData.overrides, draftRef.current, activePage)
            : initialData.overrides;
        draftRef.current = nextDraft;
        setDraft(nextDraft);
        const effective = resolveUserPreferences(initialData.defaults, nextDraft);
        if (!offline) {
            form.setFieldsValue(effective);
            if (previewing.current) applyUserPreferences(effective);
        }
    }, [initialData, offline, form, activePage]);

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
        const nextDraft = replaceUserPreferencePage(data.overrides, overrides, activePage);
        draftRef.current = nextDraft;
        setDraft(nextDraft);
        const effective = resolveUserPreferences(data.defaults, nextDraft);
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
                updateCache?.(response.data, getPageDataCacheKey(location));
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

    return (
        <div style={{ maxWidth: 800, width: "100%" }}>
            {contextHolder}
            {offline ? (
                <Alert type="info" title={res.offline} />
            ) : (
                <Form
                    {...formLayout}
                    form={form}
                    initialValues={data.effective}
                    disabled={saving}
                    onValuesChange={(changes) => preview(mergeUserPreferenceChanges(draftRef.current, changes))}
                    onFinish={() => void save()}
                >
                    {
                        [
                            {
                                key: "appearance",
                                label: res.appearanceTitle,
                                children: (
                                    <AdminAppearanceFields
                                        names={{
                                            language: "language",
                                            theme: ["appearance", "theme"],
                                            darkMode: ["appearance", "darkMode"],
                                            compactMode: ["appearance", "compactMode"],
                                            colorPrimary: ["appearance", "colorPrimary"],
                                        }}
                                    />
                                ),
                            },
                            {
                                key: "writing",
                                label: res.writingTitle,
                                children: (
                                    <>
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
                                    </>
                                ),
                            },
                            {
                                key: "assistant",
                                label: res.assistantTitle,
                                children: (
                                    <Form.Item
                                        name={["assistant", "knowledgeScope"]}
                                        label={res.knowledge}
                                        extra={res.knowledgeHelp}
                                    >
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
                                ),
                            },
                        ].find((item) => item.key === activePage)?.children
                    }
                    <SettingsSubmitBar
                        loading={saving}
                        disabled={!dirty}
                        label={res.save}
                        actions={
                            <>
                                <Button disabled={!dirty} onClick={() => preview(data.overrides, true)}>
                                    {res.undo}
                                </Button>
                                <Button type="link" onClick={() => preview({}, true)}>
                                    {res.reset}
                                </Button>
                            </>
                        }
                    >
                        <Typography.Paragraph type="secondary" style={{ marginTop: token.marginSM, marginBottom: 0 }}>
                            {res.previewHint}
                        </Typography.Paragraph>
                    </SettingsSubmitBar>
                </Form>
            )}
        </div>
    );
};

export default UserPreferencesForm;
