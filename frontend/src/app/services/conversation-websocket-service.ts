import { Injectable, OnDestroy, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { ConversationMessageEvent } from '../models/conversation-message-event';

@Injectable({ providedIn: 'root' })
export class ConversationWebSocketService implements OnDestroy {
  readonly status = signal('Disconnected');
  private socket?: WebSocket;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private readonly conversationIds = new Set<number>();
  private readonly eventsSubject = new Subject<ConversationMessageEvent>();
  readonly events$ = this.eventsSubject.asObservable();

  connect(userId: number): void {
    if (this.socket) return;
    this.status.set('Connecting…');
    const url = new URL('/ws', window.location.href);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.searchParams.set('userId', String(userId));
    const socket = new WebSocket(url);
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.status.set('Connected');
      for (const id of this.conversationIds) this.sendSubscription(id);
    };
    socket.onmessage = event => {
      if (this.socket !== socket) return;
      try {
        const frame = JSON.parse(event.data) as ConversationMessageEvent;
        if (frame.type === 'MESSAGE' || frame.type === 'SUBSCRIBED') this.eventsSubject.next(frame);
      } catch { socket.close(); }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = undefined;
      this.status.set('Disconnected — retrying…');
      this.retryTimer = setTimeout(() => this.connect(userId), 2000);
    };
  }

  subscribeToConversation(conversationId: number): void {
    if (this.conversationIds.has(conversationId)) return;
    this.conversationIds.add(conversationId);
    this.sendSubscription(conversationId);
  }

  private sendSubscription(conversationId: number): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'SUBSCRIBE', conversationId }));
    }
  }

  disconnect(): void {
    clearTimeout(this.retryTimer);
    const socket = this.socket;
    // Old close callbacks must not reconnect after switching users.
    this.socket = undefined;
    socket?.close();
    this.conversationIds.clear();
    this.status.set('Disconnected');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
  }
}
