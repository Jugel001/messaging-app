import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { Conversation, User } from '../models/conversation';
import { Message } from '../models/message';

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);

  getUsers() {
    return this.http.get<User[]>('/api/users').pipe(timeout(10000));
  }

  getConversations(userId: number) {
    return this.http.get<Conversation[]>('/api/conversations', {
      headers: { 'X-User-Id': String(userId) }
    }).pipe(timeout(10000));
  }

  sendMessage(userId: number, conversationId: number, text: string) {
    return this.http.post<Message>(`/api/conversations/${conversationId}/messages`, { text }, {
      headers: { 'X-User-Id': String(userId) }
    }).pipe(timeout(10000));
  }
}
