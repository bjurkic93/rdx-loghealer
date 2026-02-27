package com.reddiax.loghealer.dto;

import com.reddiax.loghealer.document.LogEntryDocument;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogSearchResponse {

    private List<LogEntryDocument> logs;
    private long totalHits;
    private int page;
    private int size;
    private int totalPages;

    public static LogSearchResponse of(List<LogEntryDocument> logs, long totalHits, int page, int size) {
        return LogSearchResponse.builder()
            .logs(logs)
            .totalHits(totalHits)
            .page(page)
            .size(size)
            .totalPages((int) Math.ceil((double) totalHits / size))
            .build();
    }
}
