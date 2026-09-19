import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { KnowledgeAskResponse, KnowledgeDocument } from './business-knowledge.models';

@Injectable({ providedIn: 'root' })
export class BusinessKnowledgeApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/knowledge`;

  list() {
    return this.http.get<KnowledgeDocument[]>(`${this.baseUrl}/documents`);
  }

  createText(title: string, text: string) {
    return this.http.post<KnowledgeDocument>(`${this.baseUrl}/text`, { title, text });
  }

  uploadText(title: string, file: File) {
    const data = new FormData();
    if (title.trim()) {
      data.append('title', title.trim());
    }
    data.append('file', file);
    return this.http.post<KnowledgeDocument>(`${this.baseUrl}/documents`, data);
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.baseUrl}/documents/${id}`);
  }

  ask(question: string) {
    return this.http.post<KnowledgeAskResponse>(`${this.baseUrl}/ask`, { question });
  }
}
