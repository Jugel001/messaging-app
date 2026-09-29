import { Message } from './message';

export type ConversationMessageEvent =
  | { type: 'SUBSCRIBED'; conversationId: number }
  | { type: 'MESSAGE'; conversationId: number; message: Message };
