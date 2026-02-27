package com.reddiax.loghealer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchLogRequest {

    @NotEmpty(message = "Logs list cannot be empty")
    @Size(max = 1000, message = "Maximum 1000 logs per batch")
    @Valid
    private List<LogEntryRequest> logs;
}
