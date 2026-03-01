export interface CodeFixRequest {
  exceptionGroupId: string;
  projectId: string;
  provider?: string;
  conversationId?: string;
  userMessage?: string;
}

export interface CodeFixResponse {
  conversationId: string;
  status: 'ANALYZED' | 'PR_CREATED' | 'ERROR';
  message: string;
  analysis?: CodeFixAnalysis;
  changes?: FileChange[];
  pullRequest?: PullRequestInfo;
}

export interface CodeFixAnalysis {
  rootCause: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  explanation: string;
  affectedFiles?: string[];
}

export interface FileChange {
  filePath: string;
  language: string;
  oldCode: string;
  newCode: string;
  startLine: number;
  endLine: number;
  changeDescription: string;
}

export interface PullRequestInfo {
  prNumber: number;
  title: string;
  htmlUrl: string;
  branchName: string;
  createdAt?: string;
}

export interface ConversationMessage {
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  timestamp?: Date;
}
