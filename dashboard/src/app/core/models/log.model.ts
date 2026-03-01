export interface LogEntry {
  id: string;
  projectId: string;
  tenantId: string;
  level: string;
  logger: string;
  message: string;
  stackTrace: string | null;
  exceptionClass: string | null;
  fingerprint: string | null;
  threadName: string | null;
  metadata: Record<string, unknown> | null;
  timestamp: string | number;
  traceId: string | null;
  spanId: string | null;
  hostName: string | null;
  environment: string | null;
}

export interface LogSearchResponse {
  logs: LogEntry[];
  totalHits: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface LogSearchRequest {
  query?: string;
  levels?: string[];
  projectId?: string;
  logger?: string;
  exceptionClass?: string;
  environment?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortOrder?: string;
}
