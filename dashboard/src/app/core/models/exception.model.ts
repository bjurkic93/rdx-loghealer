export interface ExceptionGroup {
  id: string;
  projectId: string;
  tenantId: string;
  fingerprint: string;
  exceptionClass: string;
  message: string;
  sampleStackTrace: string;
  firstSeen: string;
  lastSeen: string;
  count: number;
  status: ExceptionStatus;
  lastAnalysisId: string | null;
  environment: string | null;
}

export type ExceptionStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'IGNORED';
