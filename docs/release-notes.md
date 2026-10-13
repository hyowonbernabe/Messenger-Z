# Messenger Z

Patched Messenger. Non-root: no root, no Xposed install. Updates over any previous Messenger Z
(same signing key, so you stay logged in). Open settings by long-pressing the **Messenger** title
at the top of the chat list.

### 👻 Ghost mode
- **Hide seen**: read messages without sending "Seen".
- **Hide typing**: no "…" bubble while you write.
- **Hide story views**: watch stories without appearing in the viewer list.
- **Per-chat control**: apply ghost mode to everyone, everyone except chosen chats, or only chosen chats.

### 💬 Messages
- **Unsent message log**: when someone unsends, the original text stays in the chat as "🗑 original text" and is saved in the log.
- **Edit history**: earlier versions of edited messages are kept (Edited tab in the log).
- Tap a log entry to copy it.

### 📖 Stories
- **Stop auto-swipe**: stay on each story until you tap.
- **Story download**: a ⬇ button in the story viewer saves the story (photo or video) to Downloads/MessengerZ.

### 🔗 Links & presence
- **Remove link tracking**: links open without Facebook's redirect and `fbclid` / `utm` tags.
- **Appear offline** (beta, off by default): hide your "Active now" while still seeing others. Restart Messenger after turning it on.
- **Spoof version**: report a newer Messenger version (troubleshooting).

### 🛠️ Tools
- Restart Messenger, clear Messenger's cache, reset learned data.
- Debug Console for maintainers.

The unsent message log recognizes English Messenger only. Unsent photos and videos aren't logged.

# Facebook Z

Patched Facebook, same approach. It replaces the Facebook app and picks up your account from
Messenger Z automatically. Open settings by long-pressing the **facebook** logo at the top of the feed.

### 📖 Stories
- **Hide story views**: watch stories without appearing in the viewer list.
- **Stop auto-swipe** (off by default): stay on each story until you tap.
- **Story download**: a ⬇ button in the story viewer saves the story to Downloads/FacebookZ.
- **Skip story ads**: ads between stories are skipped automatically.

### 🚫 Ads
- **Hide sponsored posts**: no ads in your feed.

### 🔒 Privacy
- **Appear offline** (beta, off by default): hide your "Active now". Restart Facebook after turning it on.

### 🛠️ Tools
- Restart Facebook, clear Facebook's cache.
