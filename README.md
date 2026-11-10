# WildStore

Store link, purchase announcements and a GG wave for Paper and Folia 26.1+ (Java 25). No dependencies.

## Commands

| Command | Permission | |
|---|---|---|
| `/store` (`/shop`, `/online-shop`) | `wildstore.store` (everyone) | Shows the store link |
| `/storepurchase <player>` | `wildstore.purchase` (op, console) | Announces a purchase |
| `/wildstore reload` | `wildstore.reload` (op) | Reloads `config.yml` and `lang.yml` |

## How it works

Let your store run `storepurchase {username}` from the console for every purchase.

- Every online player sees eight chat lines next to the buyer's head in pixels (`purchase.head`), plus a sound.
- Several `/storepurchase` for the same player within `purchase.debounce` seconds are announced once.
- For `gg-wave.seconds` after the announcement, every player who writes one of `gg-wave.keywords` gets the action bar text and the console commands in `gg-wave.commands` run for them, once per purchase. `{player}` is the player, `{buyer}` the buyer.

## Files

| File | Content |
|---|---|
| `config.yml` | Store link, aliases, debounce, head, sounds, GG wave and reward commands |
| `lang.yml` | Every chat text (MiniMessage) |

## License

MIT, see [LICENSE](LICENSE).
