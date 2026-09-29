import { FunctionComponent } from "react";
import { AdminCommonProps } from "../type";
import FileManager, { FileManagerData } from "./file-manager";

const FileManagerPage: FunctionComponent<AdminCommonProps<FileManagerData>> = ({ data }) => {
    return <FileManager data={data} style={{ height: "var(--admin-content-height)" }} />;
};

export default FileManagerPage;
