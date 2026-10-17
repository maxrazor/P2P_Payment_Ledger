package com.dfls.notification_service.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dfls.notification_service.dto.NotificationMessage;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationService {

    private final Map<UUID, Set<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters
                .computeIfAbsent(
                        userId,
                        id -> ConcurrentHashMap.newKeySet()
                )
                .add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
            log.debug("SSE client connected userId={}", userId);
        } catch (IOException e) {
            remove(userId, emitter);
            throw new IllegalStateException("Failed to initialize SSE connection stream", e);
        }

        return emitter;
    }

    public void notifyUser(
            UUID userId,
            NotificationMessage notification
    ) {

        if (userId == null) {
            log.warn("Cannot send notification: userId is null");
            return;
        }

        Set<SseEmitter> userEmitters = emitters.get(userId);

        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("notification")
                        .data(notification)
                );

            } catch (IOException e) { 
                log.debug(
                    "Removing disconnected SSE client userId={}",
                    userId
                );

                remove(userId, emitter);
            } catch (Exception e) {
                log.error("Unexpected error sending SSE to userId={}", userId, e);
                remove(userId, emitter);
            }
        }
    }

    private void remove(
            UUID userId,
            SseEmitter emitter
    ) {
        emitters.computeIfPresent(userId, (id, userEmitters) -> {
            userEmitters.remove(emitter);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }
}
