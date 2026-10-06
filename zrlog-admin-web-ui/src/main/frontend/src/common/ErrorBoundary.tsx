import { FunctionComponent, PropsWithChildren, useEffect, useState } from "react";
import UnknownErrorPage from "../components/unknown-error-page";
import { getRes } from "../utils/constants";

const ErrorBoundary: FunctionComponent<PropsWithChildren> = ({ children }) => {
    const [hasError, setHasError] = useState(false);

    useEffect(() => {
        const handleError = () => {
            setHasError(true);
        };

        window.addEventListener("error", handleError);

        return () => {
            window.removeEventListener("error", handleError);
        };
    }, []);

    if (hasError) {
        return <UnknownErrorPage data={{ message: getRes().error.pageError }} code={"500"} />;
    }

    return <>{children}</>;
};

export default ErrorBoundary;
