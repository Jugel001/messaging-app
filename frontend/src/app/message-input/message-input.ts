import { Component, ElementRef, input, output, viewChild } from '@angular/core';

@Component({
  selector: 'app-message-input',
  templateUrl: './message-input.html',
  styleUrl: './message-input.css'
})
export class MessageInput {
  sending = input(false);
  messageSent = output<string>();
  private readonly messageInput = viewChild.required<ElementRef<HTMLInputElement>>('messageInput');

  send(event?: Event): void {
    if ((event instanceof KeyboardEvent && event.isComposing) || this.sending()) return;
    const text = this.messageInput().nativeElement.value.trim();
    if (text && text.length <= 2000) this.messageSent.emit(text);
  }

  // The parent calls this only after HTTP confirms the message was saved.
  clear(): void {
    this.messageInput().nativeElement.value = '';
  }
}
