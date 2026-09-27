import Form from "antd/es/form";
import { useTheme } from "antd-style";
import Input from "antd/es/input";
import { getRes } from "../../utils/constants";
import Select from "antd/es/select";
import { useEffect, useState } from "react";
import { InputNumber } from "antd";
import { Admin } from "./index";
import FaviconUpload from "./FaviconUpload";
import zh_CN from "antd/es/locale/zh_CN";
import en_US from "antd/es/locale/en_US";
import AdminAppearanceFields from "../common/AdminAppearanceFields";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type PaginationLocaleWithItems = {
    items_per_page?: string;
};

const BlogForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: Admin;
    offlineData: boolean;
    offline: boolean;
    onSubmit: (data: Admin) => void;
    loading?: boolean;
}) => {
    const [state, setState] = useState<Admin>(data);
    const [form] = Form.useForm();
    const { formLayout } = useResponsiveFormLayout(layout);
    const theme = useTheme();

    const getItemsPerPage = () => {
        const paginationLocale = getRes().lang === "zh_CN" ? zh_CN.Pagination : en_US.Pagination;
        return (paginationLocale as PaginationLocaleWithItems | undefined)?.items_per_page ?? "";
    };

    const onValueChange = (value: any) => {
        setState({ ...state, ...value });
    };

    useEffect(() => {
        setState(data);
        form.setFieldsValue({
            ...data,
            admin_theme: data.admin_theme ?? "default",
            backend_server_url: data.backend_server_url ?? "",
        });
    }, [data, form]);

    return (
        <Form
            {...formLayout}
            form={form}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(nv) => {
                onValueChange(nv);
            }}
            onFinish={(nv) => onSubmit({ ...state, ...nv })}
        >
            <div
                style={{ color: theme.colorText, fontSize: theme.fontSizeLG, fontWeight: 600, margin: "8px 0 16px 0" }}
            >
                {getRes().websiteAdmin.basicSettings}
            </div>
            <Form.Item
                name="backend_server_url"
                label={getRes().websiteAdmin.backendServer.label}
                tooltip={getRes().websiteAdmin.backendServer.help}
                extra={getRes().websiteAdmin.backendServer.changeHelp}
                normalize={(value: string) => value.trim()}
                rules={[
                    {
                        validator: (_, value?: string) => {
                            if (!value) return Promise.resolve();
                            try {
                                const url = new URL(value);
                                const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
                                const path = decodeURIComponent(value.replace(/^[a-z]+:\/\/[^/]+/i, ""));
                                if (
                                    value.length <= 2048 &&
                                    /^https?:\/\//i.test(value) &&
                                    !url.username &&
                                    !url.password &&
                                    !value.includes("?") &&
                                    !value.includes("#") &&
                                    !value.includes("\\") &&
                                    !Array.from(value + path).some(
                                        (char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127
                                    ) &&
                                    !/%(?:2f|5c|25)/i.test(value) &&
                                    !path.includes("//") &&
                                    !path.split("/").some((part) => part === "." || part === "..") &&
                                    url.port !== "0" &&
                                    (url.protocol === "https:" || (local && url.protocol === "http:"))
                                ) {
                                    return Promise.resolve();
                                }
                            } catch {
                                /* Show the field's validation message. */
                            }
                            return Promise.reject(new Error(getRes().websiteAdmin.backendServer.invalid));
                        },
                    },
                ]}
            >
                <Input
                    allowClear
                    style={{ maxWidth: 440 }}
                    placeholder={getRes().websiteAdmin.backendServer.placeholder}
                />
            </Form.Item>
            <Form.Item
                name="admin_static_resource_base_url"
                label={getRes().websiteAdmin.staticResource.url}
                tooltip={getRes().websiteAdmin.staticResource.urlHelp}
            >
                <Input style={{ maxWidth: 300 }} placeholder={getRes().websiteAdmin.staticResource.urlTips} />
            </Form.Item>
            <Form.Item
                name="session_timeout"
                label={getRes().websiteAdmin.session.timeout}
                tooltip={getRes().websiteAdmin.session.timeoutHelp}
                rules={[{ required: true }]}
            >
                <InputNumber
                    suffix={getRes().websiteAdmin.session.timeoutUnit}
                    style={{ minWidth: 120 }}
                    max={99999}
                    type={"number"}
                    min={5}
                    placeholder=""
                />
            </Form.Item>
            <AdminAppearanceFields
                names={{
                    language: "language",
                    theme: "admin_theme",
                    darkMode: "admin_darkMode",
                    compactMode: "admin_compactMode",
                    colorPrimary: "admin_color_primary",
                }}
            />
            <div
                style={{ color: theme.colorText, fontSize: theme.fontSizeLG, fontWeight: 600, margin: "24px 0 16px 0" }}
            >
                {getRes().websiteAdmin.moreSettings}
            </div>
            <Form.Item
                name="admin_article_page_size"
                label={getRes().websiteAdmin.article.pageSize}
                tooltip={getRes().websiteAdmin.article.pageSizeTip}
            >
                <Select
                    style={{ maxWidth: 120 }}
                    options={[
                        {
                            value: 10,
                            label: "10 " + getItemsPerPage(),
                        },
                        {
                            value: 20,
                            label: "20 " + getItemsPerPage(),
                        },
                        {
                            value: 50,
                            label: "50 " + getItemsPerPage(),
                        },
                        {
                            value: 100,
                            label: "100 " + getItemsPerPage(),
                        },
                    ]}
                />
            </Form.Item>
            <div
                style={{ color: theme.colorText, fontSize: theme.fontSizeLG, fontWeight: 600, margin: "24px 0 16px 0" }}
            >
                {getRes().websiteAdmin.pwa.title}
            </div>
            <Form.Item
                name="favicon_png_pwa_192_base64"
                label={getRes().websiteAdmin.pwa.icon192}
                tooltip={getRes().websiteAdmin.pwa.icon192Help}
            >
                <FaviconUpload
                    url={state.favicon_png_pwa_192_base64}
                    onChange={(e) => {
                        setState({ ...state, favicon_png_pwa_192_base64: e ? e : "" });
                    }}
                />
            </Form.Item>
            <Form.Item
                name="favicon_png_pwa_512_base64"
                label={getRes().websiteAdmin.pwa.icon512}
                tooltip={getRes().websiteAdmin.pwa.icon512Help}
            >
                <FaviconUpload
                    url={state.favicon_png_pwa_512_base64}
                    onChange={(e) => {
                        setState({ ...state, favicon_png_pwa_512_base64: e ? e : "" });
                    }}
                />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default BlogForm;
