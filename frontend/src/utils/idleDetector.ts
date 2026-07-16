import { forceLogout } from '@/utils/authSession';

const IDLE_MS = 30 * 60 * 1000;
const EVENTS = ['mousedown', 'keydown', 'scroll', 'touchstart'] as const;

let timer: ReturnType<typeof setTimeout> | null = null;
let started = false;

function resetTimer() {
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => {
    forceLogout('长时间未操作，请重新登录');
  }, IDLE_MS);
}

export function startIdleDetector() {
  if (started) return;
  started = true;
  EVENTS.forEach((event) => window.addEventListener(event, resetTimer, { passive: true }));
  resetTimer();
}

export function stopIdleDetector() {
  if (!started) return;
  started = false;
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  EVENTS.forEach((event) => window.removeEventListener(event, resetTimer));
}
