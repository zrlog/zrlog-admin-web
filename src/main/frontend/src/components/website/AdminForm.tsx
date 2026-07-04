import Form from "antd/es/form";
import { useTheme } from "antd-style";
import Input from "antd/es/input";
import Switch from "antd/es/switch";
import { getPreset, getRes } from "../../utils/constants";
import Select from "antd/es/select";
import { useEffect, useState } from "react";
import { ColorPicker, InputNumber } from "antd";
import { Admin } from "./index";
import FaviconUpload from "./FaviconUpload";
import { colorPickerBgColors } from "../../utils/helpers";
import zh_CN from "antd/es/locale/zh_CN";
import en_US from "antd/es/locale/en_US";
import { changeAppState, getAppState } from "../../base/ConfigProviderApp";
import { getColorByTheme, isDarkByTheme, isSupportDarkMode } from "../../base/AppInit";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const namedPresetColorDefs = [
    { key: "azureBlue", color: "rgb(22, 119, 255)" },
    { key: "skyBlue", color: "rgb(3, 169, 244)" },
    { key: "deepBlue", color: "rgb(47, 84, 235)" },
    { key: "indigoBlue", color: "rgb(63, 81, 181)" },
    { key: "violetPurple", color: "rgb(114, 46, 209)" },
    { key: "deepPurple", color: "rgb(156, 39, 176)" },
    { key: "midPurple", color: "rgb(171, 71, 188)" },
    { key: "magentaPink", color: "rgb(233, 30, 99)" },
    { key: "fuchsiaPink", color: "rgb(235, 47, 150)" },
    { key: "cyanTeal", color: "rgb(19, 194, 194)" },
    { key: "iceBlue", color: "rgb(0, 188, 212)" },
    { key: "deepTeal", color: "rgb(0, 150, 136)" },
    { key: "green", color: "rgb(82, 196, 26)" },
    { key: "lightGreen", color: "rgb(139, 195, 74)" },
    { key: "yellowGreen", color: "rgb(160, 217, 17)" },
    { key: "lime", color: "rgb(205, 220, 57)" },
    { key: "brightYellow", color: "rgb(250, 219, 20)" },
    { key: "lemonYellow", color: "rgb(255, 235, 59)" },
    { key: "goldenYellow", color: "rgb(250, 173, 20)" },
    { key: "mustardYellow", color: "rgb(255, 193, 7)" },
    { key: "orange", color: "rgb(250, 140, 22)" },
    { key: "amberOrange", color: "rgb(255, 152, 0)" },
    { key: "orangeRed", color: "rgb(250, 84, 28)" },
    { key: "burntOrange", color: "rgb(255, 87, 34)" },
    { key: "red", color: "rgb(245, 34, 45)" },
    { key: "tomatoRed", color: "rgb(244, 67, 54)" },
    { key: "brown", color: "rgb(121, 85, 72)" },
    { key: "slateBlueGray", color: "rgb(96, 125, 139)" },
    { key: "charcoalGray", color: "rgb(33, 33, 33)" },
] as const;

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

    const getRealState = () => {
        return {
            admin_darkMode: getAppState().dark,
            admin_compactMode: getAppState().compactMode,
            admin_color_primary: getAppState().colorPrimary,
            language: getAppState().lang,
            admin_theme: getAppState().theme,
        };
    };

    useEffect(() => {
        form.setFieldsValue({
            ...data,
            ...getRealState(),
        });
    }, [data]);

    const namedColorLabelMap = new Map<string, string>(
        namedPresetColorDefs.map((item) => [
            item.color
                .replace(/\s+/g, "")
                .replace("rgb(", "")
                .replace(")", "")
                .split(",")
                .map((x) => Number(x).toString(16).padStart(2, "0"))
                .join("")
                .toLowerCase(),
            getRes().websiteAdmin.color.preset[item.key],
        ])
    );

    return (
        <Form
            {...formLayout}
            form={form}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(nv) => {
                onValueChange(nv);
            }}
            onFinish={(nv) => onSubmit({ ...state, ...nv, ...getRealState() })}
        >
            <div
                style={{ color: theme.colorText, fontSize: theme.fontSizeLG, fontWeight: 600, margin: "8px 0 16px 0" }}
            >
                {getRes().websiteAdmin.basicSettings}
            </div>
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
            <Form.Item name="language" label={getRes().websiteAdmin.language.label}>
                <Select
                    style={{ maxWidth: 120 }}
                    onChange={(lang: "zh_CN" | "en_US") => {
                        changeAppState({
                            lang: lang,
                        });
                    }}
                    options={[
                        { value: "zh_CN", label: getRes().websiteAdmin.language.chinese },
                        { value: "en_US", label: getRes().websiteAdmin.language.english },
                    ]}
                />
            </Form.Item>
            <Form.Item name={"admin_theme"} label={getRes().websiteAdmin.theme.label}>
                <Select
                    style={{ maxWidth: 120 }}
                    onChange={(theme: string) => {
                        changeAppState({
                            theme: theme,
                            colorPrimary: getColorByTheme(theme),
                            dark: isDarkByTheme(theme),
                        });
                    }}
                    options={[
                        { label: getRes().websiteAdmin.theme.option.default, value: "default" },
                        { label: getRes().websiteAdmin.theme.option.desk, value: "desk" },
                        { label: getRes().websiteAdmin.theme.option.antd, value: "antd" },
                        { label: getRes().websiteAdmin.theme.option.bootstrap, value: "bootstrap" },
                        { label: getRes().websiteAdmin.theme.option.geek, value: "geek" },
                        { label: getRes().websiteAdmin.theme.option.cartoon, value: "cartoon" },
                        { label: getRes().websiteAdmin.theme.option.glass, value: "glass" },
                        { label: getRes().websiteAdmin.theme.option.shadcn, value: "shadcn" },
                        { label: getRes().websiteAdmin.theme.option.illustration, value: "illustration" },
                    ]}
                />
            </Form.Item>
            {isSupportDarkMode(getAppState().theme) && (
                <Form.Item valuePropName="checked" name="admin_darkMode" label={getRes().websiteAdmin.dark.mode}>
                    <Switch
                        onChange={(admin_darkMode) => {
                            changeAppState({
                                dark: admin_darkMode,
                            });
                        }}
                    />
                </Form.Item>
            )}
            <Form.Item valuePropName="checked" name="admin_compactMode" label={getRes().websiteAdmin.compact.mode}>
                <Switch
                    onChange={(admin_compactMode) => {
                        changeAppState({ compactMode: admin_compactMode });
                    }}
                />
            </Form.Item>
            <Form.Item label={getRes().websiteAdmin.color.primary}>
                <ColorPicker
                    value={getAppState().colorPrimary}
                    onChange={(color) => {
                        changeAppState({
                            colorPrimary: color.toHexString(),
                        });
                    }}
                    showText={(color) => {
                        const hex = color.toHexString();
                        const key = hex.replace("#", "").toLowerCase();
                        return namedColorLabelMap.get(key) || hex;
                    }}
                    disabledAlpha={true}
                    presets={[
                        {
                            defaultOpen: true,
                            label: getPreset(),
                            colors: colorPickerBgColors,
                        },
                    ]}
                />
            </Form.Item>
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
