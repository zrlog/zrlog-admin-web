import { ColorPicker, Form, Select, Switch } from "antd";
import type { FormItemProps } from "antd";
import { getPreset, getRes } from "../../utils/constants";
import { colorPickerBgColors } from "../../utils/helpers";
import { isSupportDarkMode } from "../../base/AppInit";

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

type AppearanceField = "language" | "theme" | "darkMode" | "compactMode" | "colorPrimary";
export type AppearanceFieldNames = Record<AppearanceField, FormItemProps["name"]>;

const AdminAppearanceFields = ({ names }: { names: AppearanceFieldNames }) => {
    const form = Form.useFormInstance();
    const selectedTheme = Form.useWatch(names.theme, form) ?? "default";
    const res = getRes().websiteAdmin;
    const selectStyle = { width: 200, maxWidth: "100%" };
    const themes = [
        "default",
        "desk",
        "antd",
        "bootstrap",
        "geek",
        "cartoon",
        "glass",
        "shadcn",
        "illustration",
    ] as const;
    const customColor = ["default", "antd", "bootstrap", "glass"].includes(selectedTheme);
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
        <>
            <Form.Item name={names.language} label={res.language.label}>
                <Select
                    style={selectStyle}
                    options={[
                        { value: "zh_CN", label: res.language.chinese },
                        { value: "en_US", label: res.language.english },
                    ]}
                />
            </Form.Item>
            <Form.Item name={names.theme} label={res.theme.label}>
                <Select
                    style={selectStyle}
                    options={themes.map((value) => ({ value, label: res.theme.option[value] }))}
                />
            </Form.Item>
            {isSupportDarkMode(selectedTheme) && (
                <Form.Item name={names.darkMode} label={res.dark.mode} valuePropName="checked">
                    <Switch />
                </Form.Item>
            )}
            <Form.Item name={names.compactMode} label={res.compact.mode} valuePropName="checked">
                <Switch />
            </Form.Item>
            {customColor && (
                <Form.Item
                    name={names.colorPrimary}
                    label={res.color.primary}
                    getValueFromEvent={(color) => color.toHexString()}
                    getValueProps={(value) => ({ value: value || "#1677ff" })}
                >
                    <ColorPicker
                        disabledAlpha
                        showText={(color) => {
                            const hex = color.toHexString();
                            return namedColorLabelMap.get(hex.replace("#", "").toLowerCase()) || hex;
                        }}
                        presets={[{ defaultOpen: true, label: getPreset(), colors: colorPickerBgColors }]}
                    />
                </Form.Item>
            )}
        </>
    );
};

export default AdminAppearanceFields;
