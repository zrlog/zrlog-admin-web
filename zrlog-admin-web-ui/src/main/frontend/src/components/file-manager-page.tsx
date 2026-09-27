import { FunctionComponent } from "react";
import { AdminCommonProps } from "../type";
import FileManager, { FileManagerData } from "./file-manager";
import { getAppState } from "../base/ConfigProviderApp";

const FileManagerPage: FunctionComponent<AdminCommonProps<FileManagerData>> = ({ data }) => {
    const headerHeight = getAppState().compactMode ? 54 : 64;
    return <FileManager data={data} style={{ height: `calc(100vh - ${headerHeight + 60}px)` }} />;
};

export default FileManagerPage;
