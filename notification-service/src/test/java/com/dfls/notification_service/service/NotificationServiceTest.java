package com.dfls.notification_service.service;

import com.dfls.notification_service.dto.NotificationMessage;
import com.dfls.notification_service.dto.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationService notificationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        userId = UUID.randomUUID();
    }

    @Test
    void shouldRegisterEmitter() {
        SseEmitter emitter = notificationService.subscribe(userId);
        assertNotNull(emitter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendNotificationToConnectedUser() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);

        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(mockEmitter);
        privateMap.put(userId, testSet);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
        );

        notificationService.notifyUser(userId, notification);

        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldDoNothingWhenUserHasNoEmitters() {
        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
        );

        assertDoesNotThrow(() ->
                notificationService.notifyUser(userId, notification)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveDisconnectedEmitter() throws Exception {
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection closed by client")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);

        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(brokenEmitter);
        privateMap.put(userId, testSet);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
        );

        notificationService.notifyUser(userId, notification);

        assertFalse(testSet.contains(brokenEmitter));
    }

    @Test
    void shouldNotThrowWhenUserIdIsNull() {
        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
        );

        assertDoesNotThrow(() ->
                notificationService.notifyUser(null, notification)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldContinueBroadcastingToOtherTabsIfOneTabFails() throws Exception {
        SseEmitter brokenTab = mock(SseEmitter.class);
        SseEmitter healthyTab = mock(SseEmitter.class);

        doThrow(new IOException("Connection dropped")).when(brokenTab).send(any(SseEmitter.SseEventBuilder.class));

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);

        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(brokenTab);
        testSet.add(healthyTab);
        privateMap.put(userId, testSet);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED, 
                UUID.randomUUID(), 
                "Transfer completed"
        );
        
        notificationService.notifyUser(userId, notification);

        verify(brokenTab, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthyTab, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertFalse(testSet.contains(brokenTab));
        assertTrue(testSet.contains(healthyTab));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCleanUpMapKeyWhenEmitterTimesOut() throws Exception {

        SseEmitter mockEmitter = mock(SseEmitter.class);

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);
        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(mockEmitter);
        privateMap.put(userId, testSet);

        ReflectionTestUtils.invokeMethod(notificationService, "remove", userId, mockEmitter);

        assertFalse(privateMap.containsKey(userId));
    }

    @Test
    void shouldHandleHeavyConcurrentSubscriptionsAndNotificationsWithoutCrashing() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
            );

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    SseEmitter emitter = notificationService.subscribe(userId);
                    notificationService.notifyUser(userId, notification);
                    
                    if (index % 2 == 0) {
                        emitter.completeWithError(new IOException("Async drop"));
                    }
                } catch (Exception e) {
                    fail("Concurrency test encountered an unexpected exception", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertDoesNotThrow(() -> notificationService.notifyUser(userId, notification));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBroadcastToMultipleEmittersForSameUser() throws Exception {
        SseEmitter mockTab1 = mock(SseEmitter.class);
        SseEmitter mockTab2 = mock(SseEmitter.class);

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);
        
        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(mockTab1);
        testSet.add(mockTab2);
        privateMap.put(userId, testSet);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED, 
                UUID.randomUUID(), 
                "Transfer completed"
        );
        notificationService.notifyUser(userId, notification);

        verify(mockTab1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockTab2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveKeyFromMapWhenLastEmitterIsRemoved() throws Exception {
        SseEmitter brokenTab = mock(SseEmitter.class);
        doThrow(new IOException("Connection dropped")).when(brokenTab).send(any(SseEmitter.SseEventBuilder.class));

        Map<UUID, Set<SseEmitter>> privateMap = 
            (Map<UUID, Set<SseEmitter>>) ReflectionTestUtils.getField(notificationService, "emitters");
        assertNotNull(privateMap);

        Set<SseEmitter> testSet = ConcurrentHashMap.newKeySet();
        testSet.add(brokenTab);
        privateMap.put(userId, testSet);

        NotificationMessage notification = new NotificationMessage(
                NotificationType.TRANSFER_COMPLETED,
                UUID.randomUUID(),
                "Transfer completed"
        );

        notificationService.notifyUser(userId, notification);

        assertFalse(privateMap.containsKey(userId));
    }
}