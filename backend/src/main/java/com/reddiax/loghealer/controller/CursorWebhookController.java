package com.reddiax.loghealer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.repository.jpa.CursorAgentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class CursorWebhookController {

    private final CursorAgentTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Value("${loghealer.ai.cursor.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/cursor-agent")
    public ResponseEntity<Void> handleCursorWebhook(
            @RequestHeader(value = "X-Cursor-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        log.info("Received Cursor webhook");

        if (webhookSecret != null && webhookSecret.length() >= 32 && signature != null) {
            if (!verifySignature(payload, signature)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(401).build();
            }
        }

        try {
            JsonNode webhookData = objectMapper.readTree(payload);
            String agentId = webhookData.path("id").asText();
            String status = webhookData.path("status").asText();
            String summary = webhookData.path("summary").asText(null);
            String prUrl = webhookData.path("target").path("prUrl").asText(null);

            log.info("Cursor agent {} status changed to: {}", agentId, status);

            taskRepository.findByCursorAgentId(agentId).ifPresent(task -> {
                task.setStatus(status);
                if (summary != null) task.setSummary(summary);
                if (prUrl != null) task.setPrUrl(prUrl);
                task.setUpdatedAt(Instant.now());
                taskRepository.save(task);
                
                log.info("Updated task {} with status {}, PR: {}", 
                        task.getId(), status, prUrl);
            });

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to process Cursor webhook", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);
            return signature.equalsIgnoreCase(expectedSignature);
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }
}
