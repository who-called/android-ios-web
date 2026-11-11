import type { Dict } from "./fr";

/**
 * A draft locale may translate any subset of the keys. Objects are merged
 * key-by-key over English; arrays (e.g. guides.items) are replaced whole, so
 * translate a list completely or not at all.
 */
export type DeepPartial<T> = T extends readonly unknown[]
  ? T
  : T extends object
    ? { [K in keyof T]?: DeepPartial<T[K]> }
    : T;

export type PartialDict = DeepPartial<Dict>;
