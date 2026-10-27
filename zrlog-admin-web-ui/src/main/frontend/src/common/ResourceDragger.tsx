import { ResourceDragger as SharedResourceDragger } from "@zrlog/ui/resources";
import type { ResourceDraggerProps as SharedResourceDraggerProps } from "@zrlog/ui/resources";
import type { UploadConfig } from "@zrlog/editor/dist/editor/editor.types";
import type { AxiosInstance } from "axios";
import { getRes, tryAppendBackendServerUrl } from "../utils/constants";
import FileManagerPicker from "../components/file-manager/picker";

export type DraggerUploadResponse = { data: { url: string } };

type ResourceDraggerProps = Omit<SharedResourceDraggerProps, "upload" | "onSuccess" | "resourcePicker"> & {
    axiosInstance?: AxiosInstance;
    buildUploadUrl?: UploadConfig["buildUploadUrl"];
    type?: string;
    onSuccess?: (response: DraggerUploadResponse) => void;
    resourcePicker?: {
        buttonText?: string;
        disabled?: boolean;
        onlyImage?: boolean;
        onSelectFile: (path: string) => void;
        title?: string;
    };
};

const ResourceDragger = ({
    axiosInstance,
    buildUploadUrl,
    type = "image",
    onSuccess,
    resourcePicker,
    ...props
}: ResourceDraggerProps) => {
    const upload: SharedResourceDraggerProps["upload"] = axiosInstance
        ? async (file, context) => {
              const form = new FormData();
              form.append("imgFile", file);
              const { data } = await axiosInstance.post<DraggerUploadResponse & { error?: number; message?: string }>(
                  buildUploadUrl?.(type) ?? `/api/admin/upload?dir=${encodeURIComponent(type)}`,
                  form,
                  {
                      signal: context.signal,
                      onUploadProgress: ({ loaded, total }) => {
                          if (total) context.onProgress(Math.round((loaded / total) * 100));
                      },
                  }
              );
              if (data.error || !data.data?.url) throw new Error(data.message || getRes().fileManager.uploadFailed);
              return {
                  url: tryAppendBackendServerUrl(data.data.url),
                  name: file.name,
                  size: file.size,
                  contentType: file.type,
              };
          }
        : undefined;
    return (
        <SharedResourceDragger
            {...props}
            upload={upload}
            onSuccess={(resource) => onSuccess?.({ data: { url: resource.url } })}
            resourcePicker={
                resourcePicker
                    ? {
                          title: resourcePicker.title ?? getRes().common.chooseFromAssets,
                          buttonText: resourcePicker.buttonText ?? getRes().common.chooseFromAssets,
                          disabled: resourcePicker.disabled,
                          render: (close) => (
                              <FileManagerPicker
                                  style={{ height: "100%" }}
                                  onlyImage={resourcePicker.onlyImage}
                                  onSelectFile={(path) => {
                                      resourcePicker.onSelectFile(path);
                                      close();
                                  }}
                              />
                          ),
                      }
                    : undefined
            }
        />
    );
};

export default ResourceDragger;
