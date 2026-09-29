import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit, viewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { ConversationService } from './services/conversation-service';
import { ConversationWebSocketService } from './services/conversation-websocket-service';
import { MessageInput } from './message-input/message-input';
import { MessageList } from './message-list/message-list';
import { Conversation, User } from './models/conversation';
import { Message } from './models/message';

@Component({
  selector: 'app-root',
  imports: [MessageInput, MessageList],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  private readonly conversationService = inject(ConversationService);
  readonly conversationWebSocket = inject(ConversationWebSocketService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly messageInput = viewChild(MessageInput);
  private requests = new Subscription();
  private historyRequest?: Subscription;
  private usersRequest?: Subscription;
  private socketSubscription?: Subscription;

  users: User[] = [];
  currentUser?: User;
  conversations: Conversation[] = [];
  loading = false;
  sending = false;
  error = '';
  sendError = '';

  get selectedConversation(): Conversation | undefined {
    return this.conversations[0];
  }

  ngOnInit(): void {
    this.socketSubscription = this.conversationWebSocket.events$.subscribe(event => {
      if (event.type === 'SUBSCRIBED') this.loadConversations();
      else this.addMessage(event.conversationId, event.message);
    });
    this.loadUsers();
  }

  loadUsers(): void {
    this.usersRequest?.unsubscribe();
    this.loading = true;
    this.error = '';
    this.usersRequest = this.conversationService.getUsers().subscribe({
      next: users => {
        this.users = users;
        this.loading = false;
        // sessionStorage keeps selections independent between separately opened windows.
        try {
          const saved = users.find(user => String(user.id) === sessionStorage.getItem('messaging-user'));
          if (saved) this.chooseUser(saved);
        } catch { /* Storage is optional. */ }
        this.changeDetectorRef.markForCheck();
      },
      error: () => this.showError('Could not load users. Check the server and try again.')
    });
  }

  chooseUser(user: User): void {
    this.currentUser = user;
    try { sessionStorage.setItem('messaging-user', String(user.id)); } catch { }
    this.conversationWebSocket.connect(user.id);
    this.loadConversations();
  }

  loadConversations(): void {
    if (!this.currentUser) return;
    // Cancel an older refresh so it cannot overwrite a newer response or user selection.
    this.historyRequest?.unsubscribe();
    this.loading = true;
    this.error = '';
    this.historyRequest = this.conversationService.getConversations(this.currentUser.id).subscribe({
      next: conversations => {
        this.conversations = conversations.map(conversation => ({
          ...conversation,
          messages: this.mergeMessages(
            this.conversations.find(existing => existing.id === conversation.id)?.messages ?? [],
            conversation.messages)
        }));
        for (const conversation of conversations) {
          this.conversationWebSocket.subscribeToConversation(conversation.id);
        }
        this.loading = false;
        if (!conversations.length) this.error = 'No conversation is available for this user.';
        this.changeDetectorRef.markForCheck();
      },
      error: () => this.showError('Could not load messages. Please try again.')
    });
    this.requests.add(this.historyRequest);
  }

  sendMessage(text: string): void {
    const conversation = this.selectedConversation;
    if (!this.currentUser || !conversation || this.sending) return;
    this.sending = true;
    this.sendError = '';
    this.requests.add(this.conversationService.sendMessage(this.currentUser.id, conversation.id, text).subscribe({
      next: message => {
        this.addMessage(conversation.id, message);
        this.messageInput()?.clear();
        this.sending = false;
        this.changeDetectorRef.markForCheck();
      },
      error: () => {
        this.sending = false;
        this.sendError = 'Could not confirm delivery. Your draft is kept; check the chat before retrying to avoid a duplicate.';
        this.changeDetectorRef.markForCheck();
      }
    }));
  }

  private addMessage(conversationId: number, message: Message): void {
    this.conversations = this.conversations.map(conversation => conversation.id === conversationId
      ? { ...conversation, messages: this.mergeMessages(conversation.messages, [message]) }
      : conversation);
    this.changeDetectorRef.markForCheck();
  }

  private mergeMessages(existing: Message[], incoming: Message[]): Message[] {
    const messages = [...existing];
    for (const message of incoming) {
      // HTTP responses, socket echoes, and history refreshes may contain the same saved message.
      if (!messages.some(saved => saved.id === message.id)) messages.push(message);
    }
    return messages.sort((a, b) => a.id - b.id).slice(-100);
  }

  switchUser(): void {
    if (this.sending) return;
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.conversationWebSocket.disconnect();
    this.currentUser = undefined;
    this.conversations = [];
    this.error = this.sendError = '';
    this.loading = false;
    try { sessionStorage.removeItem('messaging-user'); } catch { }
  }

  private showError(message: string): void {
    this.loading = false;
    this.error = message;
    this.changeDetectorRef.markForCheck();
  }

  ngOnDestroy(): void {
    this.usersRequest?.unsubscribe();
    this.requests.unsubscribe();
    this.socketSubscription?.unsubscribe();
    this.conversationWebSocket.disconnect();
  }
}
