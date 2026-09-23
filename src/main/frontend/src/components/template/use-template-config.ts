import { useCallback, useEffect, useRef, useState } from "react";
import { message } from "antd";
import type { ApiResponse } from "../../type";
import { getRes } from "../../utils/constants";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import {
    ConfigValues,
    getConfigValues,
    hasConfigChanges,
    hasConfigFields,
    TemplateConfigData,
} from "./template-config-model";

export const useTemplateConfig = (data?: TemplateConfigData, disabled = false) => {
    const [values, setValues] = useState(() => getConfigValues(data));
    const [savedValues, setSavedValues] = useState(() => getConfigValues(data));
    const [saving, setSaving] = useState(false);
    const savingRef = useRef(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const dirty = hasConfigChanges(values, savedValues);

    useEffect(() => {
        const next = getConfigValues(data);
        setValues(next);
        setSavedValues(next);
    }, [data]);

    useEffect(() => {
        if (!dirty && !saving) return;
        const beforeUnload = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = "";
        };
        window.addEventListener("beforeunload", beforeUnload);
        return () => window.removeEventListener("beforeunload", beforeUnload);
    }, [dirty, saving]);

    const changeValues = useCallback((changes: ConfigValues) => {
        setValues((previous) => ({ ...previous, ...changes }));
    }, []);

    const save = async () => {
        if (!data || disabled || savingRef.current || !hasConfigFields(data)) return;
        const submitted = { ...values, template: data.template || values.template };
        savingRef.current = true;
        setSaving(true);
        try {
            const response = await postRefreshCacheSse<ApiResponse<unknown>>("/api/admin/template/config", {
                body: submitted,
                messageApi,
                messageKey: "templateConfigRefreshCache",
                backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().websiteTemplate.title,
            });
            if (response.error) {
                void messageApi.error(response.message || getRes().templateConfig.saveFailed);
                return;
            }
            // Only the submitted snapshot is saved; edits made during the request remain dirty.
            setSavedValues(submitted);
            void messageApi.success(response.message || getRes().templateConfig.saved);
        } catch {
            void messageApi.error(getRes().templateConfig.saveFailed);
        } finally {
            savingRef.current = false;
            setSaving(false);
        }
    };

    return { values, changeValues, dirty, saving, save, contextHolder };
};
