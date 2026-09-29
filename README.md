# Messaging MVP

A small extension of the original Angular/Spring Boot app: choose Woody or Jugel, open their shared conversation, and exchange messages live. MySQL stores the users, conversation, and messages. Everything runs locally.

## Run

Start Docker, then run from this directory:

```sh
docker compose up --build
```

Open **http://localhost:8080** in two independently opened windows and choose a different user in each. Use **Switch user** if a duplicated tab inherited the first window’s selection.

If port 8080 is occupied, run `APP_PORT=8081 docker compose up --build` and open http://localhost:8081. The installed standalone `docker-compose` command also works. Another device on the same network can use your computer’s LAN IP instead of localhost.

The app container serves Angular, REST, and WebSocket traffic. The MySQL container uses a named volume; `docker compose down` preserves messages, while `docker compose down -v` deletes them. The first build downloads dependencies; runtime needs no external services. Optional settings are in `.env.example`.

## Follow the code

```text
frontend/src/app/
  app.*                           User selection, load/send, duplicate check
  message-list/                   Existing message display component
  message-input/                  Existing input component; clears after success
  services/conversation-service.ts
  services/conversation-websocket-service.ts
  models/
backend/src/main/java/com/socialnet/messaging/
  controller/ConversationController.java
  repository/ConversationRepository.java
  websocket/ConversationWebSocketHandler.java
  config/WebSocketConfig.java
  dto/
```

Sending follows the original flow:

1. `MessageInput` emits the text; `App` calls `ConversationService`.
2. `ConversationController` validates the selected user, membership, and text.
3. `ConversationRepository` inserts into MySQL and returns after its transaction commits.
4. The controller broadcasts the saved message and returns it through HTTP.
5. `App` checks the database message ID before adding it, so the HTTP response and WebSocket echo appear once. Other windows belonging to the sender also receive the broadcast.

The three tables are `users`, `conversations` (two participant IDs), and `messages`. Flyway creates them and seeds Woody/Jugel once. Names and participant IDs come from the database; adding more one-to-one conversations does not require changing message routing.

The original WebSocket `SUBSCRIBE` flow is retained. The server checks membership before acknowledging with `SUBSCRIBED`. The browser then reloads history and merges it by message ID, avoiding a gap between initial history loading and live delivery. Closed sockets reconnect every two seconds and resubscribe. Switching users cancels pending requests and closes the previous connection.

## API

- `GET /api/users`: demo users.
- `GET /api/conversations`: the selected user’s conversations, each with its latest 100 messages.
- `POST /api/conversations/{id}/messages`: `{ "text": "Hello" }`, returning the saved message with `201`.
- `GET /actuator/health`: application/database health.
- `WS /ws?userId={id}`: connect as the selected demo user, then send `{"type":"SUBSCRIBE","conversationId":1}`.

Conversation HTTP requests include `X-User-Id`. Messages contain `id`, `senderId`, `text`, and `createdAt`. WebSocket message events also contain `conversationId`. Unknown users return `401`, nonmembers `403`, unknown conversations `404`, invalid text `400`, and database failures `503`.

## Not implemented

- Account registration and password login; users select a demo identity.
- Creating new conversations or group chats.
- Editing or deleting messages in the app.
- Loading older messages beyond the latest 100.

## Local development

Prerequisites: Java 21, Node 24, and MySQL 8.4.

To run just MySQL through Docker with a localhost port:

```sh
docker compose -f compose.yaml -f compose.dev.yaml up -d db
```

Then run `./mvnw spring-boot:run` in `backend`, and `npm ci` followed by `npm start` in `frontend`. Open http://localhost:4200. Angular proxies `/api` and `/ws` to the backend on port 8080. The backend accepts `DB_URL`, `DB_USER`, and `DB_PASSWORD`; defaults match Compose. Maven does not automatically read the Compose `.env` file.

Build with `./mvnw package` in `backend` and `npm run build` in `frontend`. Angular’s optional disk cache is disabled because its native module crashed on the development Mac.

No frontend or backend test files are included, as requested. To check manually: send in both directions and in two tabs for one user, refresh for saved history, restart the app for reconnect recovery, and stop the server during a send to confirm that the draft remains.

The frontend and backend builds were checked locally. Docker/MySQL execution still needs verification with a running Docker daemon; the local Colima daemon was stopped during implementation.
