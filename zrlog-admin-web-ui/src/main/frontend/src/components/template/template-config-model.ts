export type ConfigValue = string | number | boolean | null;
export type ConfigValues = Record<string, ConfigValue>;

export type ConfigParam = {
    label: string;
    type: string;
    value: ConfigValue;
    previewValue?: string;
    htmlElementType: string;
    contentType: string;
    placeholder?: string;
};

export type TemplateConfigData = {
    name?: string;
    shortTemplate?: string;
    template: string;
    config: Record<string, ConfigParam>;
};

export const getConfigValues = (data?: TemplateConfigData): ConfigValues => {
    const values: ConfigValues = {};
    Object.entries(data?.config || {}).forEach(([key, param]) => {
        values[key] = param.value;
    });
    if (data?.template) values.template = data.template;
    return values;
};

export const hasConfigChanges = (values: ConfigValues, saved: ConfigValues): boolean =>
    Object.keys(values).length !== Object.keys(saved).length ||
    Object.keys(values).some((key) => values[key] !== saved[key]);

export const isLargeConfig = (data?: TemplateConfigData): boolean =>
    Object.values(data?.config || {}).some((param) => param.type === "yml");

export const hasConfigFields = (data?: TemplateConfigData): boolean =>
    Object.values(data?.config || {}).some((param) => param.type !== "hidden");
