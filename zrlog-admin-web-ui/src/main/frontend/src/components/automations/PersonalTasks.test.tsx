import { afterEach, expect, it, jest } from "@jest/globals";
import { act } from "react";
import { createRoot } from "react-dom/client";
import PersonalTasks from "./PersonalTasks";
import type { Overview, Task } from "./types";

jest.mock("antd", () => ({
    Button: ({ children, onClick, disabled, "aria-label": label }: any) => (
        <button onClick={onClick} disabled={disabled} aria-label={label}>
            {children}
        </button>
    ),
    Dropdown: ({ children, menu }: any) => (
        <div>
            {children}
            {menu.items
                .filter((item: any) => item.key)
                .map((item: any) => (
                    <button key={item.key} onClick={item.onClick} disabled={item.disabled}>
                        {item.label}
                    </button>
                ))}
        </div>
    ),
    Empty: ({ description }: any) => <p>{description}</p>,
    Pagination: () => null,
    Space: ({ children }: any) => <span>{children}</span>,
    Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));
jest.mock("@zrlog/ui/antd/Tag", () => ({ __esModule: true, default: ({ children }: any) => <span>{children}</span> }));
jest.mock("@zrlog/ui/feedback", () => ({ useUiApp: () => ({ modal: { confirm: () => undefined } }) }));
jest.mock("./TaskIcon", () => ({ __esModule: true, default: () => null }));
jest.mock("./assistant-styles", () => ({ useAssistantStyles: () => ({ styles: {} }) }));
jest.mock("../../utils/constants", () => ({
    getRes: () => ({
        assistant: {
            tasks: "Tasks",
            taskCount: "{count} tasks",
            create: "Add",
            edit: "Edit plan",
            archived: "Schedule deleted",
            moreActions: "More",
            run: "Run",
            delete: "Delete",
            enable: "Enable",
            disable: "Pause",
            never: "Never",
        },
        common: { cancel: "Cancel" },
    }),
}));

(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
const roots: ReturnType<typeof createRoot>[] = [];
afterEach(() => {
    act(() => roots.splice(0).forEach((root) => root.unmount()));
    document.body.innerHTML = "";
});

it("opens the task's existing conversation while keeping plan actions separate", () => {
    const onOpen = jest.fn(),
        onEdit = jest.fn();
    const task = { id: "task-1", name: "Weekly review", enabled: true, revision: 1 } as Task;
    const archived = { ...task, id: "task-2", name: "Archived review", archived: true, enabled: false };
    const overview = { tasks: [task, archived], total: 2, definitions: [], aiAvailable: false } as unknown as Overview;
    const container = document.body.appendChild(document.createElement("div"));
    const root = createRoot(container);
    roots.push(root);
    act(() =>
        root.render(
            <PersonalTasks
                overview={overview}
                disabled={false}
                page={1}
                onPage={jest.fn()}
                onEdit={onEdit}
                onPropose={jest.fn()}
                onOpen={onOpen}
                onMutate={async () => undefined}
                renderStatus={() => null}
            />
        )
    );
    const rows = container.querySelectorAll("article");
    act(() => rows[0].querySelector("button")!.click());
    expect(onOpen).toHaveBeenCalledWith(task);
    act(() =>
        Array.from(rows[0].querySelectorAll("button"))
            .find((button) => button.textContent === "Edit plan")!
            .click()
    );
    expect(onEdit).toHaveBeenCalledWith(task);
    expect(onOpen).toHaveBeenCalledTimes(1);
    act(() => rows[1].querySelector("button")!.click());
    expect(onOpen).toHaveBeenLastCalledWith(archived);
    expect(rows[1].querySelectorAll("button")).toHaveLength(1);
});
