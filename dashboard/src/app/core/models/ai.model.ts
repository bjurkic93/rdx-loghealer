export interface AiAnalysisRequest {
  exceptionGroupId: string;
  provider?: 'openai' | 'claude';
  generateFix?: boolean;
  analyzePerformance?: boolean;
  additionalContext?: string;
}

export interface AiAnalysisResponse {
  id: string;
  exceptionGroupId: string;
  provider: string;
  model: string;
  
  rootCause: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  impact: string;
  
  suggestedFix?: SuggestedFix;
  preventionTips: string[];
  similarPatterns: string[];
  
  performanceInsight?: PerformanceInsight;
  
  analyzedAt: string;
  tokensUsed: number;
  processingTimeMs: number;
}

export interface SuggestedFix {
  description: string;
  codeSnippet: string;
  language: string;
  fileName?: string;
  lineNumber?: number;
  confidence: number;
}

export interface PerformanceInsight {
  isPerformanceRelated: boolean;
  bottleneck?: string;
  optimization?: string;
  estimatedImprovement?: string;
}

export interface AiProviders {
  providers: string[];
  default: string;
  models: { [key: string]: string };
}
