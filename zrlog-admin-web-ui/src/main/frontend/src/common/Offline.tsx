import UnknownErrorPage from "../components/unknown-error-page";
import { FunctionComponent, PropsWithChildren } from "react";
import { getRes } from "../utils/constants";

const Offline: FunctionComponent<PropsWithChildren> = () => {
    return <UnknownErrorPage data={{ message: getRes().error.networkOffline }} code={"500"} />;
};

export default Offline;
