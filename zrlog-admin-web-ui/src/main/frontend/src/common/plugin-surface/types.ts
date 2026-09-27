export type SurfaceStatus = "normal" | "processing" | "warning" | "error";
export type SurfaceActionStyle = "default" | "primary" | "danger";
export type SurfaceFieldType = "input" | "textarea" | "datetime" | "switch" | "select";
export type SurfaceActionRefreshStrategy = "replace" | "reload" | "message" | "openView";
export type SurfaceAdminRoute =
    | string
    | {
          route?: string;
          path?: string;
          url?: string;
          replace?: boolean;
      };

export interface SurfaceFieldOption {
    label: string;
    value: string;
}

export interface SurfaceField {
    name: string;
    label: string;
    type: SurfaceFieldType;
    required?: boolean;
    placeholder?: string;
    options?: SurfaceFieldOption[];
}

export interface SurfaceAction {
    label: string;
    actionRef: string;
    style?: SurfaceActionStyle;
    form?: SurfaceField[];
    adminRoute?: SurfaceAdminRoute;
    openAdminRoute?: SurfaceAdminRoute;
}

export interface SurfaceMetric {
    label: string;
    value: number | string;
    status?: SurfaceStatus;
}

export type SurfaceChartType = "line" | "bar" | "pie" | "donut";

export interface SurfaceChart {
    type: SurfaceChartType;
    title?: string;
    data: Record<string, string | number | null | undefined>[];
    xField?: string;
    yField?: string;
    seriesField?: string;
    nameField?: string;
    valueField?: string;
    unit?: string;
    height?: number;
}

export interface SurfaceItem {
    id: string;
    title: string;
    description?: string;
    status?: SurfaceStatus;
    actions?: SurfaceAction[];
}

export interface SurfaceViewLink {
    label: string;
    view: string;
    url?: string;
}

export interface InteractiveSurface {
    version: string;
    title: string;
    description?: string;
    status?: SurfaceStatus;
    metrics?: SurfaceMetric[];
    charts?: SurfaceChart[];
    items?: SurfaceItem[];
    actions?: SurfaceAction[];
    view?: SurfaceViewLink;
}

export interface SurfaceActionResponse {
    message?: string;
    surface?: InteractiveSurface;
    refreshSurface?: boolean;
    refreshStrategy?: SurfaceActionRefreshStrategy;
    openView?: boolean | SurfaceViewLink;
    adminRoute?: SurfaceAdminRoute;
    openAdminRoute?: SurfaceAdminRoute;
}

export interface StandardSurfaceResponse<T> {
    success: boolean;
    message?: string;
    data: T;
}
