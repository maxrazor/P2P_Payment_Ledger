package com.dfls.notification_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dfls.notification_service.service.NotificationService;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void shouldAcceptSubscriptionWhenHeaderIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter mockEmitter = new SseEmitter();
        
        when(notificationService.subscribe(userId)).thenReturn(mockEmitter);

        mockMvc.perform(get("/api/v1/notifications/stream")
                .header("X-Authenticated-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(notificationService, times(1)).subscribe(userId);
    }

    @Test
    void shouldReturnBadRequestWhenAuthHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_HEADER"));
    }

    @Test
    void shouldReturnBadRequestWhenAuthHeaderIsMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream")
                .header("X-Authenticated-User-Id", "not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER_FORMAT"));
    }
}