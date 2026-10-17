import { useEffect, useRef } from 'react';
import { createNotificationConnection } from '../api/notificationApi.js';

export function useNotifications(user, { onNotification, onError } = {}) {
  const handlers = useRef({ onNotification, onError });
  useEffect(() => {
    handlers.current = { onNotification, onError };
  }, [onNotification, onError]);

  useEffect(() => {
    if (!user) return undefined;
    const source = createNotificationConnection({
      onNotification: (notification) => handlers.current.onNotification?.(notification),
      onError: (event) => {
        // EventSource automatically retries; don't treat a transient disconnect as logout.
        handlers.current.onError?.(event);
      },
    });
    return () => source.close();
  }, [user]);
}
