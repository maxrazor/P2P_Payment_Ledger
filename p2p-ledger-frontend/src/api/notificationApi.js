import { API_BASE_URL } from './http.js';

// Native EventSource automatically retries after a disconnect. Cookies are included
// cross-origin because withCredentials is enabled; the gateway must allow credentials.
export function createNotificationConnection({ onNotification, onConnect, onError }) {
  const source = new EventSource(`${API_BASE_URL}/notifications/stream`, { withCredentials: true });
  source.addEventListener('connect', (event) => onConnect?.(event.data));
  source.addEventListener('notification', (event) => {
    try {
      onNotification?.(JSON.parse(event.data));
    } catch (error) {
      console.error('Could not parse notification SSE payload.', error);
    }
  });
  source.onerror = (event) => onError?.(event);
  return source;
}
