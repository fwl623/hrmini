/**
 * RBAC 权限骨架 — Sprint 1 对接 GET /auth/profile 后补全
 * 见系分 §2.3、access.ts
 */
export default function access() {
  return {
    canAdmin: false,
    canHr: false,
    canPortal: false,
  };
}
