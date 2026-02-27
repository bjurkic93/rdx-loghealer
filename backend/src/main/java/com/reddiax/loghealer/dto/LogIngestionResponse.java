package com.reddiax.loghealer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogIngestionResponse {

    private int accepted;
    private int rejected;
    private String message;

    public static LogIngestionResponse success(int count) {
        return LogIngestionResponse.builder()
            .accepted(count)
            .rejected(0)
            .message("Logs accepted for processing")
            .build();
    }

    public static LogIngestionResponse partial(int accepted, int rejected) {
        return LogIngestionResponse.builder()
            .accepted(accepted)
            .rejected(rejected)
            .message("Some logs were rejected")
            .build();
    }
}
