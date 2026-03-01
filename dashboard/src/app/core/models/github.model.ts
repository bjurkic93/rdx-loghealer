export interface GitHubConnection {
  connected: boolean;
  repositoryFullName?: string;
  githubUsername?: string;
  connectionId?: string;
}

export interface PullRequestResponse {
  id: string;
  prNumber: number;
  title: string;
  description: string;
  htmlUrl: string;
  branchName: string;
  status: string;
  exceptionGroupId: string;
  repositoryFullName: string;
  createdAt: string;
}

export interface GitHubRepository {
  id: number;
  fullName: string;
  name: string;
  private: boolean;
  defaultBranch: string;
  htmlUrl: string;
}

export interface RepoProjectInfo {
  found: boolean;
  artifactId?: string;
  groupId?: string;
  name?: string;
  description?: string;
  error?: string;
}
