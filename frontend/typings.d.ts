/// <reference types="@umijs/max" />

declare namespace API {
  /** 统一响应结构（与后端约定） */
  interface Result<T = unknown> {
    code: number;
    message: string;
    data: T;
  }
}
