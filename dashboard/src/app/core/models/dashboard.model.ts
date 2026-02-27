export interface DashboardStats {
  totalLogs: number;
  totalErrors: number;
  totalWarnings: number;
  totalExceptionGroups: number;
  newExceptions: number;
  resolvedExceptions: number;
  logsByLevel: LogLevelCount[];
  logsOverTime: TimeSeriesPoint[];
  topExceptions: TopException[];
  projectStats: ProjectStats[] | null;
}

export interface LogLevelCount {
  level: string;
  count: number;
}

export interface TimeSeriesPoint {
  timestamp: string;
  count: number;
  errors: number;
}

export interface TopException {
  exceptionClass: string;
  message: string;
  count: number;
  lastSeen: string;
  status: string;
}

export interface ProjectStats {
  projectId: string;
  projectName: string;
  logCount: number;
  errorCount: number;
}
