export interface KnowledgeDocument {
  id: string;
  title: string;
  sourceType: 'TEXT' | 'TXT';
  originalFilename?: string | null;
  contentType?: string | null;
  status: 'READY' | 'FAILED' | 'DELETED';
  createdAt: string;
  updatedAt: string;
  indexedAt?: string | null;
  version: number;
}

export interface KnowledgeSourceReference {
  documentId: string;
  chunkId: string;
  title: string;
  chunkIndex: number;
  score: number;
  excerpt: string;
}

export interface KnowledgeAskResponse {
  answer: string;
  grounded: boolean;
  aiNarrativeAvailable: boolean;
  sources: KnowledgeSourceReference[];
  warnings: string[];
}
