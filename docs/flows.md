# App flows (platform-neutral)

How the apps create an account, log in (with the account number or a QR code
from the dashboard), keep the session alive, log out, work
with aliases and recipient addresses, and manage devices and the account. The
Android app implements this (`android/…/data/session/SessionManager.kt`,
`android/…/ui/aliases/`, `ui/recipients/`, `ui/devices/`); the iOS app should
do the same. The API models are in `api/`. Auth paths are relative to
the Auth origin, `/api/*` to the AMS origin (both
`https://oneclickalias.com` in production).

## Account number

- 16 ASCII digits. Displayed in groups of four (`1234 5678 9012 3456`) in
  Geist Mono; screen readers get it digit by digit per group.
- Input: keep ASCII digits only (pasted spaces, dashes, NBSPs are dropped),
  cap at 16. The field behaves like a password: masked by default with a
  show/hide toggle, password autofill type, numeric password keyboard, no
  autocorrect, no keyboard learning.
- **Never stored on the device**, never logged, never put into saved UI
  state (Android: no `SavedStateHandle`/Bundle; iOS: no state restoration).
- Screen capture is blocked while a number is visible (Android
  `FLAG_SECURE`; iOS: hide the number when the app resigns active and in the
  app-switcher snapshot, detect screen recording). On sign-up the protection
  starts while the number is still being created: the flag takes a frame to
  apply, so setting it when the number arrives lets one frame through.

## Create account

1. `POST /auth/account-number` (no body, no auth) → `{accountNumber, createdAt}`.
   The account starts **unconfirmed**: login is refused until step 3.
2. Show the number once, with: "Your account number is the only way to log
   in. We can't show it again or recover it — without it, your account is
   lost." If a password manager is set up, offer **Save to password
   manager** right below the number (the main way to keep it): hands the
   number to it as a password with no username, through the system's save
   dialog (Android: autofill framework, any autofill service, no Play
   Services; iOS: Password AutoFill). Hidden when no password manager is set
   up. It is an extra way, not a replacement: the checkbox below stays.
   Copying is a copy icon inside the number's frame ("Copy account number";
   a check, "Account number copied", for 3 s after a copy): mark the clip
   sensitive, clear it after 60 s if it still holds the number (iOS:
   `UIPasteboard` with `expirationDate` and `localOnly`).
3. Checkbox "I saved my account number" enables **Continue** →
   `POST /auth/confirm-registration {accountNumber}` (idempotent) →
   `POST /auth/login` (below). The number stays in screen memory until the
   login succeeded, so a failed login can be retried without re-typing.
4. If the app dies while the number is shown, it is gone. On restore, do not
   create a second account silently: say the number cannot be shown again and
   offer "Log in" or "Create a new account". (The first account stays
   unconfirmed; logging in with it later hits `registration_not_confirmed`.)

## Log in

The login screen is the **first screen** without a session (there is no
separate welcome screen): logo and name, the account-number field, **Log in**,
"Scan code from dashboard" and, at the bottom of the page, "No account yet?"
with **Create account**. A notice above the field says why the user was signed
out, if they were. The field does **not** take the focus by itself: the
keyboard stays closed, so all ways in are visible. The keyboard's Done/Enter
key logs in (only with 16 digits), like the button. No hint line under the
field (the other number fields keep "16 digits"). While the keyboard is
open (little height), the tagline is dropped so field, error and button stay
in view.

`POST /auth/login {accountNumber}` → `{accessToken, refreshToken, expiresIn, deviceId}`.
The server picks a random device name; send no device details.

- `403 registration_not_confirmed`: show the same "I saved my account
  number" checkbox under a quiet explanation (not an error): "This account
  was just created. Confirm that you saved its number to finish signing
  up."; the button becomes "Confirm and log in" and runs
  confirm-registration, then login.
- Store `accessToken`, `refreshToken`, `deviceId` and the absolute access
  expiry (`now + expiresIn`) encrypted (Android Keystore AES-256-GCM; iOS
  Keychain, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, no sync).
- Let password managers save the number only after a successful login. The
  field accepts a filled-in value with or without spaces (digits are kept,
  capped at 16).
- `409 device_limit_reached`: no error line; instead a card "Device limit
  reached" with the website's text "Every device slot for this account is in
  use. You can sign out all devices — including any you can't physically
  reach — and then log in again on this one." and a destructive button **Sign
  out all devices & log in**: `POST /auth/devices/revoke-all
  {accountNumber}` with the typed number (no token), then the login again.
  Editing the number withdraws the card. Same as the website's login card.

## Log in with a code (QR login)

Someone signed in on the website signs in on the phone without typing the
number: the dashboard ("Sign in on your phone") shows a QR code, the app scans
it, both show the same random device name, and the user approves in the
dashboard.

- **Entry:** "Scan code from dashboard" on the login screen, below the number
  field and **Log in**. Hidden on phones without a camera
  (Android: no `FEATURE_CAMERA_ANY`); the camera is optional for installing.
- **Camera permission** only on a tap: the scanner screen first says "The app
  uses the camera only to read this code. Nothing is recorded or sent." and
  **Allow camera** opens the system dialog. Refused: "No camera access —
  Without the camera, the code can't be scanned. You can log in with your
  account number instead." with **Allow camera** again. Refused for good:
  "Camera access is off — Allow the camera for OneClickAlias in the app
  settings, or log in with your account number." with **Open settings**.
  Every state has **Use account number** (back to the login screen).
- **Scanner:** live preview, frames decoded on the device (QR format only);
  frames are never stored or sent. The text is checked exactly as scanned
  (no trimming, no case change) against `^OCA-PAIR-1-[A-Z2-7]{32}$`:
  - starts with `OCA-PAIR-` but not `OCA-PAIR-1-`: "Update the app — this
    sign-in code is from a newer version of OneClickAlias."
  - anything else: "This is not a OneClickAlias sign-in code."
  Both show under the preview while scanning goes on. A valid code stops the
  camera at once.
- **Claim:** `POST /auth/pairing/claim {code}` (no token) →
  `{pickupSecret, deviceName, expiresAt, pollInterval}`. The pickup secret
  stays in memory only (never stored, logged or put into saved UI state). The
  screen shows "Confirm in your browser", the device name large, "This phone
  signs in as “<name>”. Approve the sign-in in your browser only if it shows
  the same name." and "Waiting for your confirmation…" with **Cancel**.
- **Polling:** `POST /auth/pairing/token {pickupSecret}` one `pollInterval`
  after the claim, then every `pollInterval` while the answer is
  `pairing_pending`. `pairing_slow_down` adds 5 s to the interval for the rest
  of the pairing. Network errors and `429` keep polling until the claim's
  `expiresAt` (converted to a monotonic deadline on the phone, at most 5
  minutes); after it the last error is shown. `pairing_pending` keeps polling
  past `expiresAt` (the approved state has its own deadline on the server),
  but never longer than a pairing lives (5 minutes). Leaving the screen stops
  polling.
- **Approved:** the first pickup returns `{accessToken, refreshToken,
  expiresIn, deviceId}` and is stored exactly like a login (same session
  storage, same rules). The pickup that returns tokens is not cancelled even
  if the screen is left meanwhile, so a device the server registered keeps
  its tokens.
- **No screen-capture block** on these screens: nothing secret is visible
  (the device name is shown in the dashboard as well, and a code in the
  preview is claimed, so worthless to anyone else, before the camera stops).
- **Errors** (claim and pickup; the screen says "Not logged in", the text,
  **Scan again** and **Use account number**):

| Code / status | Text |
|---|---|
| `pairing_expired` | This code has expired. Show a new one in the dashboard. |
| `pairing_used` | This code was already used. If you did not do this, deny the request in the dashboard. |
| `pairing_denied` | The sign-in was denied in the dashboard. |
| `unauthorized` (pickup: secret unknown) | This sign-in has ended. Show a new code in the dashboard. |
| `rate_limited` / 429, 503, 5xx, no connection, timeout | as in the auth table (429 and network only after polling gave up) |
| anything else (incl. `invalid_request`) | Signing in with the code failed — please try again. |

## Sign in a browser (reverse QR login)

Signed in, Account → **Sign in
a browser** (only on phones with a camera) opens the scanner.

1. The browser (oneclickalias.com → Log in → "Sign in with your phone")
   shows a code `OCA-LOGIN-1-<code>` and a device name.
2. Scan: `OCA-LOGIN-1-…` → `POST /auth/pairing/login-request/claim {code}`
   (Bearer). `OCA-PAIR-…` (a code for signing in the app) → "This code signs
   in the app…"; another `OCA-LOGIN-` version → update the app; anything
   else → "This is not a OneClickAlias sign-in code."
3. The app shows the browser's name with **Approve** and **Deny** and the
   warning to approve only a browser the user opened on oneclickalias.com
   just now, showing the same name.
4. **Approve**: with app lock on, the system prompt first (cancel → nothing
   happens); then `POST /auth/pairing/{pairingId}/approve` → "<name> is signed
   in". **Deny** → `…/deny`. Leaving the screen before deciding sends the
   deny too, so the request cannot be approved later.
5. Errors: `pairing_expired` → "This code has expired. Show a new code in
   your browser and scan it again."; `pairing_used`, `pairing_denied`,
   `device_limit_reached` (at approve) have their own texts; **Scan again**.

## Using and refreshing tokens

`POST /auth/refresh {refreshToken, deviceId}` → `{accessToken, refreshToken, expiresIn}`.
The refresh token **rotates on every use**; presenting a spent one after the
~30 s grace window revokes the whole token family. Therefore:

- **Single flight:** one refresh at a time (mutex). Requests waiting for it
  reuse the new pair instead of refreshing again.
- **Store before continuing:** persist the new pair before any waiting
  request uses it. Do not let a cancelled caller abort a running refresh.
  If storing fails (disk, keychain/Keystore), delete the stored pair and keep
  the new one in memory for this run: the old refresh token is spent, so a
  restart should start signed out rather than present it.
- **Proactive:** refresh before a call if the access token expires within
  60 s. **Reactive:** on `401` from any API, refresh once and retry once.
- **No refresh at start or on foreground.** A refresh on every app open
  would rotate the token each time (risk when the answer is lost, IP rate
  limit on mobile networks) for nothing: the AMS checks every access token
  synchronously by introspection, which also checks that the device still
  exists. The alias list reloads at every start and every return to the
  foreground; a device signed out elsewhere gets `401` there, the reactive
  refresh is refused, and the session ends (below).
- Refresh `400`/`401` → delete the local session, go to the login screen with
  "You have been signed out. Log in again with your account number."
- Network errors, `429`, `5xx` during refresh → keep the session; the
  failed call reports its error.
- Unreadable stored session (corrupt data, lost key) → delete it, start at
  the login screen. Never crash.

## Log out

First deliver pending claims of copied aliases (at most 10 s; "New alias"
tab below): a claim that does not arrive leaves that alias inactive. Then
`DELETE /devices/{deviceId}` with `Authorization: Bearer <accessToken>`
(through the refresh rules above), then delete all local session data.

- `204`, `404` (already removed) or `401` (session already dead) → done.
- Network or server error → **still delete locally** and tell the user:
  "Signed out on this device. The server could not be reached, so this device
  may still be listed under your devices until you remove it from another
  device." The device keeps a slot until removed elsewhere or its refresh
  token expires (30 days).

## Error handling

Error bodies are `{error, message}`. Read `error` as a raw string, tolerant of
codes the app does not know yet (generated enums are strict — do not decode
the body with them). Never show `message`. Decide by `error`, then by status:

| Code / status | Text |
|---|---|
| `unauthorized` (login) | This account number does not exist. |
| `invalid_request` | Invalid account number — check that it has exactly 16 digits. |
| `registration_not_confirmed` | This account was just created. Confirm that you saved its number to finish signing up. (an explanation, not an error; plus checkbox) |
| `device_limit_reached` (409) | the device-limit card (above); the text "Device limit reached — sign out another device first, or sign out all devices." only if it comes back after revoking |
| `account_blocked` | Your account is suspended — changes are not possible. You can still contact support. |
| `rate_limited` / 429 | Too many requests — please wait a moment and try again. |
| 503 (unknown code) | Service temporarily unavailable — please try again later. |
| `internal_error` / other 5xx | Server error — please try again later. |
| no connection | No connection — check your internet connection and try again. |
| timeout | The server is not responding — please try again. |
| anything else (login) | Login failed — please try again. |
| anything else (create) | Your account could not be created — please try again. |

A `2xx` whose body does not match the model counts as an unknown error.

## Aliases

Home has three tabs in a bar at the **bottom** of the screen, where a thumb
reaches them (icon above label, the open one marked): **New alias** (start),
**Aliases** and **Account** (below, "Account"). Each tab starts with
its name as the heading. System back on another tab leads to "New alias"
first. The open tab survives the lock screen and screens opened from it; a
new session starts at "New alias". Reads (`GET`) of the alias list work for every signed-in account;
everything on the "New alias" tab and every write needs a paid account; writes
count towards a rate limit of 30 per minute and account. Every call goes
through the token rules above.

### New alias tab (ready aliases)

The server keeps one **ready
alias** per (account, confirmed recipient, device). It belongs to this device
only and is **not active** (receives no mail, not in any list) until it is
**claimed**; a claim makes it a normal alias (`created_at` = now).

- Load at every start, every return to the foreground and on pull-to-refresh
  (one at a time): first deliver waiting claims (below), then `GET
  /api/recipients`, `GET /api/aliases/ready` and `GET /auth/account` in
  parallel, then `POST /api/aliases/ready {recipient_id}` one after the other
  for every confirmed recipient without a ready alias.
- Under the heading: "Tap an alias to copy it. A fresh one takes its place."
- One card per confirmed recipient, in server order, laid out like an
  authenticator app's entries: on top a small square with the first letter of
  the recipient's mail provider (`outlook.de` → O), the recipient address
  small and muted, and a copy icon at the end (a check in the primary color,
  and a primary border, while a copy is being activated); below it the
  **whole alias** in Geist Mono in one line, as large as fits (24sp down to
  12sp). The whole card is the button. Screen readers: "Alias <address>,
  forwards to <email>", tap action "copy".
- **Placement:** the cards stay in server order and sit together in the
  vertical middle of the space between heading and tab bar. When there are
  more than fit, they start right under the heading and the page scrolls.
  All cards look alike.
- First load: placeholder cards in the cards' shape (static blocks, no
  shimmer; screen readers: "Loading your aliases").
- "Copied to clipboard", "removed" and a failed reload (with **Retry**) show
  in a line pinned above the tab bar, with an icon: a check for done, an
  info sign for "removed", an error sign for errors. Done and info lines
  close after 3 s; errors stay.
- Recipients still waiting for their link are counted under the cards ("1
  inbox is waiting for confirmation"); a tap opens the recipient screen.
- **Copy**, without waiting for the network:
  1. remember the alias id as a **pending claim** (persisted; Android:
     `appPreferences` DataStore; iOS: `UserDefaults`; not secret; deleted when
     the session ends),
  2. put the address on the clipboard as plain text (Android 13+ confirms
     itself; older Android shows "Copied to clipboard" for 3 s),
  3. `POST /api/aliases/{id}/claim`:
     - `200` → drop the pending claim; the card shows `next` (or asks for one).
     - `404`/`invalid_alias_id` → drop it; "The alias you copied was removed —
       copy the new one." and a new ready alias.
     - anything else (offline, `5xx`, `429`, `403`) → keep it; the card keeps
       the copied alias with "Copied. Go online to activate this alias — until
       then it doesn't receive mail. Tap to copy it again." (in the normal
       text colour with an info sign: a state, not an error; a tap copies the
       same address, no second claim). Delivery is retried in the background as soon as the
       phone is online (Android: WorkManager, network constraint, exponential
       backoff), at every load and at the start of a logout (at most 10 s).
- A pending claim found at load time shows its card in the "copied" state.
- Unpaid (`paid: false` or `403 payment_required`) → payment notice instead
  of cards. No confirmed recipient → "Add an inbox to get started" with two
  numbered steps ("Add the inbox your aliases forward to", "Open the
  confirmation link we email you") and **Add recipient**; when one waits for
  its link, the title is "Confirm your inbox", the first step shows a check
  and the button is **Open recipients**. Both sit in the middle of the
  screen, like the cards.
- A card that could not get a ready alias shows the alias error text (table
  below) and "Tap to try again."; a tap reloads (tap action "retry").

### Aliases tab

- `GET /api/aliases` at every start, return to the foreground and
  pull-to-refresh; only one load at a time.
- Order on screen: **newest first** by `created_at` (the server lists oldest
  first). Ties and unreadable timestamps keep the reversed server order.
  Parse timestamps as offset date-times (they may carry `Z` or `+hh:mm` and
  fractions).
- First load: placeholder group heads (static blocks; screen readers:
  "Loading your aliases"). If it fails: the error
  text (table below) and **Retry**. A failed reload keeps the list visible and
  shows the error with Retry above it.
- **Grouped by recipient:** aliases with the same destination address
  (compared case-insensitively) form a group card. Its head shows the
  recipient's letter square as on "New alias", the address, the number of
  aliases in a small badge and a chevron. Groups start **closed**: a tap on
  the head opens or closes it (instantly, no animation); open groups stay
  open across reloads and tab switches, not across app starts. A lone group
  is always open and its head is no button. An open group's head stays at
  the top while its rows scroll. Inside a group the order above holds, and
  the groups follow their newest alias (the recipient used last comes
  first). Screen readers: head "Forwards to <destination>, <n> aliases" (a
  heading), state "Expanded"/"Collapsed", tap action "expand"/"collapse".
- Under the tab's heading: "Tap an alias to copy it." once there are
  aliases. With none: in the middle of the screen a list icon, "No aliases
  yet", "Aliases you copy under New alias show up here, grouped by the inbox
  they forward to." and **Go to New alias** (switches the tab). An unpaid
  new account sees the payment notice there instead, and "No aliases yet.
  Copy your first one under New alias." right under the heading.
- Unpaid: the payment notice is pinned above the tab bar while there are
  aliases; with none (a new account) it sits in the middle of the screen, as
  on "New alias".
- Row: alias address in Geist Mono at 13sp, the `@domain` part muted, so the
  longest diceware aliases (`word-word-NNNN@domain`, words of up to 9 letters)
  fit in one line inside a group card on a 384dp phone; a longer one wraps
  right before the `@` (break opportunity there, none inside the domain), at
  most two lines, then an ellipsis. An alias with tracker blocking off shows a
  small "Trackers allowed" badge under the address (nothing when blocking is
  on, the default). 1dp dividers between the rows of a card, starting where
  the text starts. Screen readers:
  "Alias <address>, forwards to <destination>" (plus ", trackers allowed"),
  tap action "copy",
  long-press action "show actions"; a separate "⋯" button "Actions for
  <address>". A tap copies the address (plain text).
- There is no "New alias" button: new aliases come from the "New alias" tab.

### Actions per alias

Tap on a row copies it (same confirmation). "⋯" or a long press opens a sheet
headed by the address and "Forwards to <destination>", with:

- **Block trackers** switch: `PATCH /api/aliases/{id} {block_trackers}`. It
  shows the saved value only: disabled while the request runs, then the
  server's answer.
- **Copy alias**.
- **Delete alias** (destructive text, no fill): asks first, with the
  website's text without repeating the alias (it heads the sheet): "It stops
  receiving mail right away. This can't be undone, and the name won't be
  reused." → `DELETE /api/aliases/{id}` (`204`); only this confirm button is
  filled. `404` counts as deleted (gone already). Afterwards "Alias deleted"
  for 3 s.

Errors of these actions show inside the sheet; `payment_required` closes it
and shows the payment notice; `404` on the switch closes it, says "This
alias no longer exists." and reloads. Changing the recipient of an existing
alias (`PUT /api/aliases/{id}`) exists in the API but is not in the apps yet.

### Payment notice

A write refused with `403 payment_required` (aliases and recipient addresses)
shows a calm card "Your account is not active yet" with "Creating and
changing aliases needs an active account. You can activate it on the
website." (recipient screen: "Adding and changing recipient addresses needs
an active account. …") and **Open billing**, which
opens `WEB_BASE/dashboard/billing` in the system browser (no web view; if no
browser is installed, say "No browser app found — open <url> in a
browser."). The alias and recipient screens also ask `GET /auth/account` (Auth,
with the access token, through the refresh rules) at every load, in parallel
with their lists: `paid: false` shows the card right away, before any write
(a new account sees it next to "Add recipient"); `paid: true` hides it. If
that call fails (no connection, an older server answering `404`), the card is
hidden and appears only after a refused write, as before; the next load (e.g.
back from the browser) decides again.

### Alias errors

Decide by `error`, then by status; never show `message`.

| Code / status | Text |
|---|---|
| `rate_limited` / 429 | Too many changes in a short time — up to 30 per minute are possible. Please wait a moment and try again. |
| `payment_required` | payment notice (above) |
| `account_blocked` | Your account is suspended — changes are not possible. You can still contact support. |
| `recipient_not_verified` | This recipient address is not confirmed — confirm it from the email we sent, or choose another one. |
| `invalid_recipient_id`, `not_found` (create) | This recipient address no longer exists — choose another one. |
| `not_found`, `invalid_alias_id` (switch, delete) | This alias no longer exists. |
| `alias_generation_failed` | No free alias name could be generated — please try again. |
| `auth_service_unavailable` / 503 | Service temporarily unavailable — please try again later. |
| `internal_error` / other 5xx | Server error — please try again later. |
| `unauthorized` / 401 | handled by the session (refresh, else signed out to the login screen) |
| no connection | No connection — check your internet connection and try again. |
| timeout | The server is not responding — please try again. |
| anything else | per action: "Your aliases could not be loaded / The alias could not be created / Tracker blocking could not be changed / The alias could not be deleted — please try again." |

## Recipients

The addresses aliases forward to (`/api/recipients`, AMS). Reached from
Account → Recipients and from the alias screen's recipient notice. Writes
need a paid account and count towards the same 30-per-minute write limit as
aliases.

- **List:** `GET /api/recipients` at every start/foreground of the screen and
  on pull-to-refresh, in server order (oldest first). Row: address, below it
  a small badge, "Confirmed" (with a check) or "Pending" (with a clock);
  screen readers hear "Recipient <email>, confirmed" or "…, waiting for
  confirmation". First load: placeholder rows. Failed first load (text +
  Retry) and failed reload (list stays, error + Retry) as for aliases.
  Empty: a mail icon, "No recipient addresses yet" and "Your aliases forward
  mail to these addresses. A new address gets a confirmation email; it can
  receive mail once you open the link in it." (the pinned **Add recipient**
  is the action).
- **Add:** pinned **Add recipient** opens a sheet with the website's text
  "Enter the inbox that alias mail should be forwarded to. We'll send a
  confirmation email to verify the address.", an email field (no autofill, no
  autocorrect) and **Send confirmation email** → `POST /api/recipients
  {email}` (trimmed) → `201 Recipient` (unconfirmed). The address joins the
  list and a line says "Confirmation email sent to <email>" for 3 s. The
  link opens on the website (`/confirm-email`); the apps never confirm
  themselves.
- **Actions** (tap or "⋯"): for an unconfirmed address the hint "The link
  works for 24 hours; a new one replaces it." and **Resend confirmation
  email** → `POST /api/recipients/{id}/resend` (`204`), then the same "sent"
  line. The sheet is headed by the address and its badge. **Delete
  recipient** (destructive text, no fill) asks first ("The address is
  removed from your account. An address that aliases still forward to can't
  be deleted.") →
  `DELETE /api/recipients/{id}` (`204`; `404` counts as deleted), then
  "Address deleted" for 3 s.
- Errors of the add sheet and the actions show inside the sheet;
  `payment_required` closes it and shows the payment notice;
  `already_verified`, `not_found` and `invalid_recipient_id` close it, show
  the text and reload the list.
- The remembered recipient for new aliases is not touched here: if it was
  deleted, the alias screen's decision table handles it on its next load.

### Recipient errors

| Code / status | Text |
|---|---|
| `invalid_dest_email`, `invalid_request` | Invalid email address — check the format. |
| `already_exists` | This address is already on your list. |
| `already_verified` | This address is already confirmed. |
| `recipient_in_use` (409, delete) | This address is still used by one or more aliases — delete those aliases or move them to another address first. |
| `not_found`, `invalid_recipient_id` | This address no longer exists. |
| `rate_limited` / 429 | the alias rate-limit text (30 changes per minute) |
| `payment_required` | payment notice |
| `account_blocked`, `auth_service_unavailable` / 503, `internal_error` / 5xx, network | as for aliases |
| anything else | "Your recipient addresses could not be loaded / The address could not be added / The confirmation email could not be sent / The address could not be deleted — please try again." |

The AMS has no limit on the number of addresses yet, so there is no
"limit reached" text.

## Account

Account (the third tab of home; no back arrow; same side margin as the two
alias tabs, so the heading stays in place between tabs) starts with a
**status card** from `GET /auth/account` (read at every start of the tab),
worded like the website's header: "Active until <MMM d, yyyy>" (phone's time
zone), "Expired on <date>" with **Renew on the website**, or "Not active
yet" with **Activate on the website** (both open billing). Until the first
answer a placeholder keeps the card's place; without an answer there is no
card. Then sections under small uppercase labels: **Mail** (Recipients),
**Security** (Devices, Sign in a browser, **App lock** switch, below),
**Account** (**Billing**, "Opens the website": `WEB_BASE/dashboard/billing`
in the system browser, same fallback text as the payment notice) and, apart
at the end, **Danger zone** (**Delete account** in the destructive colour),
then **Log out** and the app version.

## App lock

Optional, **off by default**: whoever holds the unlocked phone sees no aliases
and can change nothing without the owner's fingerprint, face or screen lock.
It is a screen guard, **not extra encryption**: the session key stays without
user authentication (Android Keystore key without
`setUserAuthenticationRequired`; iOS Keychain item without an access-control
flag), because a key bound to biometrics is invalidated by every newly
enrolled fingerprint or face, and the session would be lost each time.

- **Switch** in Account: "App lock", "Asks for your fingerprint, face or
  screen lock when you open the app." Turning it **on** runs the prompt first
  (proves it works on this phone); cancelled = stays off. Turning it **off**
  needs no prompt (the app is unlocked then). A phone without a screen lock
  cannot turn it on: the switch is disabled with "Set up a screen lock on this
  phone to use app lock."
- **Prompt:** biometrics with the device's screen lock as fallback. Android:
  AndroidX Biometric with `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, the only
  biometric-plus-credential combination valid from API 26 to 37
  (`DEVICE_CREDENTIAL` alone needs API 30, `BIOMETRIC_STRONG or
  DEVICE_CREDENTIAL` fails on API 28–29); on Android 8.0–9 the library only
  shows the system's PIN/pattern/password check (below). iOS: `LAContext`
  with `.deviceOwnerAuthentication` (Face ID/Touch ID, device passcode as
  fallback).
- **When it locks:** at every cold start and when the app was in the
  background for **more than 60 seconds** (monotonic clock). Shorter switches
  (pasting an alias in the mail app, opening a confirmation link in the
  browser) do not lock. Time spent in the prompt itself does not count.
- **Only signed in:** Login and Create account are never locked.
- **Locked:** a plain screen with the wordmark, "OneClickAlias is locked.
  Unlock it with your fingerprint, face or screen lock.", **Unlock** and
  **Log out**. The prompt opens by itself once per lock; after a cancel the
  screen stays and **Unlock** opens it again. The app behind it is not built
  at all (no content, no navigation, no sheets, nothing for screen readers)
  and loads nothing; after unlocking the open screen comes back and reloads.
- **Hidden in the app switcher:** while app lock is on, the window is
  protected (Android `FLAG_SECURE`: blank preview in recent apps, no
  screenshots; iOS: cover the window when the app resigns active).
- **Way out:** **Log out** is the normal logout. If the phone's screen lock
  was removed while app lock is on, the app stays locked and only offers Log
  out: "OneClickAlias is locked, and this phone no longer has a screen lock to
  unlock it. Log out, then log in again with your account number." Never
  unlock silently.
- **Setting:** a plain boolean (Android: `appPreferences` DataStore; iOS:
  `UserDefaults`), reset to off whenever the session ends (same hook as the
  remembered recipient), so the next account starts with it off.

## Devices

`GET /devices` (Auth, with the access token, through the refresh rules) at
every start/foreground of the screen and on pull-to-refresh. This device
(`current: true`) comes first and is marked "This device"; each row shows the
server's random name and "Signed in <MMM d, yyyy>" (`createdAt` in the
phone's time zone; the server keeps nothing else about devices). Device ids
are never shown or logged. First load: placeholder rows.

A successful list **without this device** (no `current: true`, also an empty
list) means it was signed out elsewhere: Auth still answers `GET /devices` to
the access token for up to 15 minutes, so no `401` comes. The app then ends
the session locally like after a `401` (no logout call, the device is gone)
and lands at the login screen with "You have been signed out. Log in again with your
account number." Failed loads never count as signed out.

- **Sign out one device:** its sign-out icon button ("Sign out <name>")
  asks first ("<name> is
  signed out right away. To log in there again, you'll need your account
  number.") → `DELETE /devices/{deviceId}` (`204`; `404` counts as done),
  then "<name> signed out" for 3 s. For this device the question is "Sign out
  this device?" and the action is the normal logout (above).
- **Sign out all devices** (website: Sessions page, "Lost access to a
  device?"): a screen with the website's text "This signs out every device on
  your account, including this phone. You'll need your account number to log
  back in. Enter it below to confirm.", the account-number field (same rules
  as login: masked, screen capture blocked while revealed, never stored) and
  a destructive button → `POST /auth/devices/revoke-all {accountNumber}`
  (no token; the number is the credential). The server signs out **every**
  device of the account the number belongs to. That may be another account
  than the one signed in here, so on `204` the app also signs this device out
  with its own access token (`DELETE /devices/{deviceId}`, no refresh, result
  ignored: with the own number the device is gone already and the call fails
  harmlessly), then deletes its local data and lands at the login screen with "All
  devices have been signed out. Log in again with your account number." There is no "all other devices" call; signing out
  the others one by one is the per-device button.

## Delete account

A screen like "Sign out all devices", with the website's text: "Your
account will be permanently deleted in 12 hours, together with all aliases
and recipients. Your aliases keep working until then. All devices are signed
out right away. If you log back in within 12 hours, the deletion is
cancelled; after that it can't be undone. Enter your account number to
confirm." → `DELETE /auth/account {accountNumber}` with the access token
(the number is a re-authentication; a stolen token alone cannot delete).

- `204`: the server has signed out all devices; delete all local data
  (session, remembered recipient) without a logout call and land at the login screen
  with "Your account will be deleted in 12 hours, together with all aliases
  and recipients. Log in with your account number within that time to cancel
  the deletion."
- A login with the same number within the 12 hours succeeds and **cancels**
  the deletion (server rule); after the purge the number is unknown
  (`unauthorized`).

### Device and account errors

| Code / status | Text |
|---|---|
| `forbidden` (delete account) | This doesn't match your current account number. |
| `unauthorized` (revoke-all, no token) | This account number does not exist. |
| `invalid_request` (revoke-all, delete account) | Invalid account number — check that it has exactly 16 digits. |
| `not_found`, `invalid_device_id` (sign out one) | This device is no longer signed in. |
| `unauthorized` (with token) | handled by the session |
| `rate_limited` / 429, `account_blocked`, 503, 5xx, network | as in the auth table |
| anything else | "Your devices could not be loaded / The device could not be signed out / Your devices could not be signed out / Your account could not be deleted — please try again." |

## Never log

Account numbers, tokens, pairing codes, pickup secrets, device ids, request
or response bodies, camera frames. State and session types print redacted
(`toString`); no HTTP logging.
