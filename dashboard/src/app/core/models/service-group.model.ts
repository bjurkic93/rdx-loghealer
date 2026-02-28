export interface Project {
  id: string;
  name: string;
  repoUrl?: string;
  gitProvider?: 'GITHUB' | 'GITLAB' | 'BITBUCKET';
  defaultBranch?: string;
  packagePrefix?: string;
  apiKey: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  name: string;
  repoUrl?: string;
  gitProvider?: string;
  defaultBranch?: string;
  packagePrefix?: string;
}

export interface DiscoveryRequest {
  logger?: string;
  message?: string;
  stackTrace?: string;
}

export interface DiscoveryResponse {
  found: boolean;
  project?: Project;
  suggestedPackagePrefix?: string;
}

export interface ServiceGroup {
  id: string;
  name: string;
  description?: string;
  projects: ProjectSummary[];
  databases: DatabaseConnection[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectSummary {
  id: string;
  name: string;
  repoUrl?: string;
  gitProvider?: string;
}

export interface DatabaseConnection {
  id?: string;
  name: string;
  dbType: 'POSTGRESQL' | 'MYSQL' | 'MARIADB' | 'MONGODB' | 'REDIS' | 'ELASTICSEARCH';
  host?: string;
  port?: number;
  databaseName?: string;
  schemaName?: string;
}

export interface ServiceGroupRequest {
  name: string;
  description?: string;
  projectIds?: string[];
  databases?: DatabaseConnectionRequest[];
}

export interface DatabaseConnectionRequest {
  name: string;
  dbType: string;
  host?: string;
  port?: number;
  databaseName?: string;
  schemaName?: string;
}

export interface TraceTimeline {
  traceId: string;
  startTime: number;
  endTime: number;
  durationMs: number;
  totalEvents: number;
  events: TraceEvent[];
  servicesInvolved: string[];
  hasError: boolean;
  rootCauseService?: string;
}

export interface TraceEvent {
  id: string;
  timestamp: number;
  serviceName: string;
  projectId: string;
  level: string;
  message: string;
  logger?: string;
  spanId?: string;
  parentSpanId?: string;
  durationMs?: number;
  isError: boolean;
  exceptionType?: string;
  stackTrace?: string;
}
