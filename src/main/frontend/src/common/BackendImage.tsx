import { Image, ImageProps } from "antd";
import { tryAppendBackendServerUrl } from "../utils/constants";

const shouldResolveBackendUrl = (src?: string) => {
    if (!src) {
        return false;
    }
    return !src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("//") && !src.includes(":");
};

export const resolveBackendImageSrc = (src?: string) => {
    if (!src) {
        return src;
    }
    if (!shouldResolveBackendUrl(src)) {
        return src;
    }
    return tryAppendBackendServerUrl(src);
};

type BackendImageProps = Omit<ImageProps, "src"> & {
    src?: string;
};

const BackendImage = ({ src, ...props }: BackendImageProps) => {
    return <Image {...props} src={resolveBackendImageSrc(src)} />;
};

export default BackendImage;
