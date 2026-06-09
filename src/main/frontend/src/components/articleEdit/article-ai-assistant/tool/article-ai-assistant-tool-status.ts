import {getRes} from "../../../../utils/constants";

export const getSeoStatusColor = (status?: string) => {
    if (status === "good") {
        return "success";
    }
    if (status === "bad") {
        return "error";
    }
    return "warning";
};

export const getSeoStatusText = (status?: string) => {
    if (status === "good") {
        return getRes().articleEdit.assistant.statusGood;
    }
    if (status === "bad") {
        return getRes().articleEdit.assistant.statusBad;
    }
    return getRes().articleEdit.assistant.statusWarning;
};
