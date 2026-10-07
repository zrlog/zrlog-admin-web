import {EditorIconProvider} from "@zrlog/editor/icons";
import type {EditorIconMap} from "@zrlog/editor/icons";
import type {PropsWithChildren} from "react";
import loading from "@zrlog/ui/icons/loading";
import info from "@zrlog/ui/icons/info";
import success from "@zrlog/ui/icons/success";
import error from "@zrlog/ui/icons/error";
import warning from "@zrlog/ui/icons/warning";
import close from "@zrlog/ui/icons/close";
import clear from "@zrlog/ui/icons/clear";
import up from "@zrlog/ui/icons/arrow-up";
import down from "@zrlog/ui/icons/chevron-down";
import check from "@zrlog/ui/icons/check";
import copy from "@zrlog/ui/icons/copy";
import settings from "@zrlog/ui/icons/settings";
import user from "@zrlog/ui/icons/user";
import send from "@zrlog/ui/icons/arrow-up";
import openai from "@zrlog/editor/brands/openai";
import deepseek from "@zrlog/editor/brands/deepseek";
import qwen from "@zrlog/editor/brands/qwen";
import gemini from "@zrlog/editor/brands/gemini";

export const sharedEditorIcons = { loading, info, success, error, warning, close, clear, up, down, check, copy, settings, user, send } satisfies Partial<EditorIconMap>;
const brands = { OPEN_AI: openai, DEEP_SEEK: deepseek, QWEN: qwen, GOOGLE_GEMINI: gemini };

export default function AdminEditorIconProvider({children}: PropsWithChildren) {
    return <EditorIconProvider icons={sharedEditorIcons} brands={brands}>{children}</EditorIconProvider>;
}
