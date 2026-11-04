export type I18nShape<T> = {
    [K in keyof T]: T[K] extends string ? string : I18nShape<T[K]>;
};
