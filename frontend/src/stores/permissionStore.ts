import { create } from 'zustand';

interface PermissionState {
  permissions: string[];
  setPermissions: (permissions: string[]) => void;
  hasPermission: (code: string) => boolean;
  clear: () => void;
}

export const usePermissionStore = create<PermissionState>((set, get) => ({
  permissions: [],
  setPermissions: (permissions) => set({ permissions }),
  hasPermission: (code) => get().permissions.includes(code),
  clear: () => set({ permissions: [] }),
}));
