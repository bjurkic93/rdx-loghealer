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
  fullName: string;
  name: string;
  private: string;
  defaultBranch: string;
}
