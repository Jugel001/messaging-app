import { Message } from './message';

export interface User { id: number; displayName: string; }

export interface Conversation {
  id: number;
  participantId: number;
  participantName: string;
  messages: Message[];
}
