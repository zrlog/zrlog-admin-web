import { FunctionComponent } from "react";
import { AdminCommonProps } from "../type";
import FileManager, { FileManagerData } from "./file-manager";
import { getAppState } from "../base/ConfigProviderApp";

const FileManagerPage: FunctionComponent<AdminCommonProps<FileManagerData>> = ({ data }) => {
    const headerHeight = getAppState().compactMode ? 54 : 64;
    // Match the editor: admin header plus 12px content padding on each side vertically.
    return <FileManager data={data} style={{ height: `calc(100vh - ${headerHeight + 24}px)` }} />;
};

export default FileManagerPage;
