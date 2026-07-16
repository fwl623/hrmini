import { create } from 'zustand';

interface UserState {
  currentUser: API.CurrentUser | null;
  setCurrentUser: (user: API.CurrentUser | null) => void;
  clearUser: () => void;
}

export const useUserStore = create<UserState>((set) => ({
  currentUser: null,
  setCurrentUser: (currentUser) => set({ currentUser }),
  clearUser: () => set({ currentUser: null }),
}));
