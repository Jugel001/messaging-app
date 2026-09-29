import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Message } from '../models/message';

@Component({
  selector: 'app-message-list',
  imports: [DatePipe],
  templateUrl: './message-list.html',
  styleUrl: './message-list.css'
})
export class MessageList {
  messages = input.required<Message[]>();
  currentUserId = input.required<number>();
  recipientName = input.required<string>();
}
