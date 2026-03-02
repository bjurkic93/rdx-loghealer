import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, CursorAgentResponse, CursorConversationMessage } from '../../core/services/api.service';
import { ExceptionGroup } from '../../core/models/exception.model';
import { AiAnalysisResponse } from '../../core/models/ai.model';
import { GitHubConnection, PullRequestResponse } from '../../core/models/github.model';
import { Project } from '../../core/models/service-group.model';
import { CodeFixResponse, FileChange, ConversationMessage } from '../../core/models/codefix.model';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-exception-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './exception-detail.component.html',
  styleUrl: './exception-detail.component.scss'
})
export class ExceptionDetailComponent implements OnInit, OnDestroy {
  @ViewChild('chatContainer') chatContainer!: ElementRef;
  
  private route = inject(ActivatedRoute);
  private apiService = inject(ApiService);
  private pollingSubscription?: Subscription;

  exception: ExceptionGroup | null = null;
  loading = true;
  error: string | null = null;
  
  // AI Analysis (legacy)
  aiAnalysis: AiAnalysisResponse | null = null;
  aiLoading = false;
  aiError: string | null = null;
  selectedProvider: 'openai' | 'claude' = 'claude';

  // GitHub Integration
  githubConnection: GitHubConnection | null = null;
  pullRequest: PullRequestResponse | null = null;
  prLoading = false;
  prError: string | null = null;

  // Auto-discovered project
  discoveredProject: Project | null = null;
  discoveringProject = false;

  // Codex-style conversation
  conversationId: string | null = null;
  codeFixResponse: CodeFixResponse | null = null;
  conversationMessages: ConversationMessage[] = [];
  chatInput = '';
  chatLoading = false;
  showCodeFixPanel = false;

  // Cursor Agent
  cursorAgentId: string | null = null;
  cursorAgentStatus: string | null = null;
  cursorAgentUrl: string | null = null;
  cursorConversation: CursorConversationMessage[] = [];
  showCursorPanel = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadException(id);
    }
    this.checkGitHubConnection();
  }

  checkGitHubConnection(): void {
    this.apiService.getGitHubConnection('demo-project').subscribe({
      next: (connection) => {
        this.githubConnection = connection;
      },
      error: (err) => {
        console.error('Failed to check GitHub connection', err);
      }
    });
  }

  loadException(id: string): void {
    this.loading = true;
    this.error = null;

    this.apiService.getException(id).subscribe({
      next: (exception) => {
        this.exception = exception;
        this.loading = false;
        this.discoverProject(exception);
      },
      error: (err) => {
        this.error = 'Failed to load exception details';
        this.loading = false;
        console.error(err);
      }
    });
  }

  discoverProject(exception: ExceptionGroup): void {
    if (!exception.projectId) {
      this.discoveringProject = false;
      return;
    }

    this.discoveringProject = true;
    
    this.apiService.getProject(exception.projectId).subscribe({
      next: (project) => {
        this.discoveredProject = project;
        this.discoveringProject = false;
      },
      error: () => {
        this.discoveringProject = false;
      }
    });
  }

  getStatusClass(status: string): string {
    return status?.toLowerCase().replace('_', '-') || 'new';
  }

  formatTimestamp(ts: string | number | null | undefined): string {
    if (!ts) return '-';
    try {
      let date: Date;
      
      if (typeof ts === 'number') {
        date = new Date(ts);
      } else if (typeof ts === 'string') {
        const numValue = Number(ts);
        if (!isNaN(numValue) && numValue > 1000000000000) {
          date = new Date(numValue);
        } else {
          date = new Date(ts);
        }
      } else {
        return '-';
      }
      
      if (isNaN(date.getTime())) return '-';
      return date.toLocaleString();
    } catch {
      return '-';
    }
  }

  // AI Analysis methods
  analyzeWithAi(provider: 'openai' | 'claude' = 'claude'): void {
    if (!this.exception?.id) return;
    
    this.selectedProvider = provider;
    this.aiLoading = true;
    this.aiError = null;

    this.apiService.analyzeException(this.exception.id, provider, true).subscribe({
      next: (response) => {
        this.aiAnalysis = response;
        this.aiLoading = false;
      },
      error: (err) => {
        this.aiError = 'AI analysis failed. Please check API keys configuration.';
        this.aiLoading = false;
        console.error('AI analysis error:', err);
      }
    });
  }

  getSeverityClass(severity: string): string {
    return `severity-${severity?.toLowerCase() || 'medium'}`;
  }

  getConfidencePercent(confidence: number): string {
    return `${Math.round((confidence || 0) * 100)}%`;
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(() => {
      // Could show a toast notification here
    });
  }

  // GitHub Integration
  connectGitHub(): void {
    this.apiService.getGitHubAuthUrl('demo-project').subscribe({
      next: (response) => {
        window.location.href = response.authorizationUrl;
      },
      error: (err) => {
        console.error('Failed to get GitHub auth URL', err);
      }
    });
  }

  createPullRequest(): void {
    if (!this.exception?.id) return;
    
    const projectId = this.discoveredProject?.id || 'demo-project';
    
    this.prLoading = true;
    this.prError = null;

    this.apiService.analyzeAndCreatePr(this.exception.id, projectId, this.selectedProvider).subscribe({
      next: (response) => {
        this.aiAnalysis = response.analysis;
        this.pullRequest = response.pullRequest;
        this.prLoading = false;
      },
      error: (err) => {
        this.prError = err.error?.message || 'Failed to create Pull Request. Make sure GitHub is connected and project has a repo configured.';
        this.prLoading = false;
        console.error('PR creation error:', err);
      }
    });
  }

  get canCreatePr(): boolean {
    return !!(this.discoveredProject?.repoUrl) || !!(this.githubConnection?.connected);
  }

  // Codex-style AI Fix with conversation
  startCodexFix(): void {
    if (!this.exception?.id || !this.discoveredProject?.id) return;

    this.showCodeFixPanel = true;
    this.chatLoading = true;
    this.conversationMessages = [];
    this.codeFixResponse = null;

    const systemMsg: ConversationMessage = {
      role: 'SYSTEM',
      content: `Analyzing exception in ${this.discoveredProject.name}...`,
      timestamp: new Date()
    };
    this.conversationMessages.push(systemMsg);

    this.apiService.analyzeAndFixCode({
      exceptionGroupId: this.exception.id,
      projectId: this.discoveredProject.id,
      provider: 'claude',
      userMessage: 'Please analyze this exception and suggest a fix.'
    }).subscribe({
      next: (response) => {
        this.conversationId = response.conversationId;
        this.codeFixResponse = response;
        this.chatLoading = false;

        const assistantMsg: ConversationMessage = {
          role: 'ASSISTANT',
          content: this.formatAiResponse(response),
          timestamp: new Date()
        };
        this.conversationMessages.push(assistantMsg);
        this.scrollChatToBottom();
      },
      error: (err) => {
        this.chatLoading = false;
        const errorMsg: ConversationMessage = {
          role: 'SYSTEM',
          content: `Error: ${err.error?.message || err.message || 'Failed to analyze'}`,
          timestamp: new Date()
        };
        this.conversationMessages.push(errorMsg);
      }
    });
  }

  sendMessage(): void {
    if (!this.chatInput.trim() || !this.conversationId || this.chatLoading) return;

    const userMessage = this.chatInput.trim();
    this.chatInput = '';

    const userMsg: ConversationMessage = {
      role: 'USER',
      content: userMessage,
      timestamp: new Date()
    };
    this.conversationMessages.push(userMsg);
    this.scrollChatToBottom();

    this.chatLoading = true;

    this.apiService.continueFixConversation(this.conversationId, userMessage).subscribe({
      next: (response) => {
        this.codeFixResponse = response;
        this.chatLoading = false;

        const assistantMsg: ConversationMessage = {
          role: 'ASSISTANT',
          content: this.formatAiResponse(response),
          timestamp: new Date()
        };
        this.conversationMessages.push(assistantMsg);
        this.scrollChatToBottom();
      },
      error: (err) => {
        this.chatLoading = false;
        const errorMsg: ConversationMessage = {
          role: 'SYSTEM',
          content: `Error: ${err.error?.message || 'Failed to get response'}`,
          timestamp: new Date()
        };
        this.conversationMessages.push(errorMsg);
      }
    });
  }

  applyChangesAndCreatePr(): void {
    if (!this.conversationId || !this.codeFixResponse?.changes?.length) return;

    this.prLoading = true;
    this.prError = null;

    this.apiService.createCodeFixPullRequest(this.conversationId, this.codeFixResponse.changes).subscribe({
      next: (response) => {
        this.codeFixResponse = response;
        this.prLoading = false;

        if (response.pullRequest) {
          this.pullRequest = {
            id: response.conversationId,
            prNumber: response.pullRequest.prNumber,
            title: response.pullRequest.title,
            description: '',
            htmlUrl: response.pullRequest.htmlUrl,
            branchName: response.pullRequest.branchName,
            status: 'open',
            exceptionGroupId: this.exception?.id || '',
            repositoryFullName: this.discoveredProject?.repoUrl?.replace('https://github.com/', '') || '',
            createdAt: response.pullRequest.createdAt || new Date().toISOString()
          };

          const prMsg: ConversationMessage = {
            role: 'SYSTEM',
            content: `Pull Request #${response.pullRequest.prNumber} created! View at: ${response.pullRequest.htmlUrl}`,
            timestamp: new Date()
          };
          this.conversationMessages.push(prMsg);
          this.scrollChatToBottom();
        }
      },
      error: (err) => {
        this.prLoading = false;
        this.prError = err.error?.message || 'Failed to create PR';

        const errorMsg: ConversationMessage = {
          role: 'SYSTEM',
          content: `Error creating PR: ${this.prError}`,
          timestamp: new Date()
        };
        this.conversationMessages.push(errorMsg);
      }
    });
  }

  private formatAiResponse(response: CodeFixResponse): string {
    let content = '';

    if (response.analysis) {
      content += `**Root Cause:** ${response.analysis.rootCause}\n\n`;
      content += `**Severity:** ${response.analysis.severity}\n\n`;
      if (response.analysis.explanation) {
        content += `**Explanation:** ${response.analysis.explanation}\n\n`;
      }
    }

    if (response.changes?.length) {
      content += `**Proposed Changes (${response.changes.length}):**\n`;
      response.changes.forEach((change, i) => {
        content += `\n${i + 1}. \`${change.filePath}\`: ${change.changeDescription}`;
      });
    }

    if (response.message && response.status === 'ERROR') {
      content = `Error: ${response.message}`;
    }

    return content || response.message || 'No response';
  }

  private scrollChatToBottom(): void {
    setTimeout(() => {
      if (this.chatContainer) {
        const el = this.chatContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      }
    }, 100);
  }

  closeCodeFixPanel(): void {
    this.showCodeFixPanel = false;
  }

  handleChatKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  // Cursor Agent Methods
  launchCursorAgent(): void {
    if (!this.exception?.id || !this.discoveredProject?.id) return;

    this.showCursorPanel = true;
    this.chatLoading = true;
    this.cursorConversation = [];

    this.apiService.launchCursorAgent(this.exception.id, this.discoveredProject.id).subscribe({
      next: (response) => {
        this.cursorAgentId = response.agentId;
        this.cursorAgentStatus = response.status;
        this.cursorAgentUrl = response.agentUrl || null;
        this.chatLoading = false;

        this.cursorConversation.push({
          id: 'system-1',
          type: 'system',
          text: `Agent launched: ${response.message || 'Analyzing code...'}`
        });

        if (response.agentUrl) {
          this.cursorConversation.push({
            id: 'system-2',
            type: 'system',
            text: `View agent progress: ${response.agentUrl}`
          });
        }

        this.startPollingAgentStatus();
        this.scrollChatToBottom();
      },
      error: (err) => {
        this.chatLoading = false;
        this.cursorConversation.push({
          id: 'error',
          type: 'system',
          text: `Error: ${err.error?.message || err.message || 'Failed to launch agent'}`
        });
      }
    });
  }

  private startPollingAgentStatus(): void {
    if (!this.cursorAgentId) return;

    this.pollingSubscription = interval(5000).subscribe(() => {
      if (!this.cursorAgentId) {
        this.stopPolling();
        return;
      }

      this.apiService.getCursorAgentStatus(this.cursorAgentId).subscribe({
        next: (response) => {
          const prevStatus = this.cursorAgentStatus;
          this.cursorAgentStatus = response.status;

          if (response.status !== prevStatus) {
            this.cursorConversation.push({
              id: `status-${Date.now()}`,
              type: 'system',
              text: `Status: ${response.status}`
            });
          }

          if (response.status === 'FINISHED') {
            this.stopPolling();
            this.loadCursorConversation();
            
            if (response.prUrl) {
              this.pullRequest = {
                id: this.cursorAgentId!,
                prNumber: 0,
                title: 'Auto-fix by Cursor Agent',
                description: response.summary || '',
                htmlUrl: response.prUrl,
                branchName: response.branchName || '',
                status: 'open',
                exceptionGroupId: this.exception?.id || '',
                repositoryFullName: this.discoveredProject?.repoUrl || '',
                createdAt: new Date().toISOString()
              };

              this.cursorConversation.push({
                id: 'pr-created',
                type: 'system',
                text: `✅ Pull Request created: ${response.prUrl}`
              });
            }

            if (response.summary) {
              this.cursorConversation.push({
                id: 'summary',
                type: 'assistant_message',
                text: response.summary
              });
            }
          }

          if (response.status === 'ERROR' || response.status === 'FAILED') {
            this.stopPolling();
            this.cursorConversation.push({
              id: 'error-status',
              type: 'system',
              text: `❌ Agent failed: ${response.message || 'Unknown error'}`
            });
          }

          this.scrollChatToBottom();
        }
      });
    });
  }

  private stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
  }

  private loadCursorConversation(): void {
    if (!this.cursorAgentId) return;

    this.apiService.getCursorAgentConversation(this.cursorAgentId).subscribe({
      next: (response) => {
        if (response.conversation) {
          this.cursorConversation = [
            ...this.cursorConversation.filter(m => m.type === 'system'),
            ...response.conversation
          ];
          this.scrollChatToBottom();
        }
      }
    });
  }

  sendCursorFollowUp(): void {
    if (!this.chatInput.trim() || !this.cursorAgentId || this.chatLoading) return;

    const message = this.chatInput.trim();
    this.chatInput = '';
    this.chatLoading = true;

    this.cursorConversation.push({
      id: `user-${Date.now()}`,
      type: 'user_message',
      text: message
    });
    this.scrollChatToBottom();

    this.apiService.sendCursorAgentFollowUp(this.cursorAgentId, message).subscribe({
      next: () => {
        this.chatLoading = false;
        this.cursorAgentStatus = 'RUNNING';
        this.startPollingAgentStatus();
      },
      error: (err) => {
        this.chatLoading = false;
        this.cursorConversation.push({
          id: 'error-followup',
          type: 'system',
          text: `Error: ${err.error?.message || 'Failed to send follow-up'}`
        });
      }
    });
  }

  closeCursorPanel(): void {
    this.showCursorPanel = false;
    this.stopPolling();
  }

  handleCursorChatKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendCursorFollowUp();
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }
}
