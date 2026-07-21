# Token Exchange Delegation — CIBA demo (Scenario 2)

A small Spring Boot app demoing Keycloak's RFC 8693 **delegation** token exchange, admin-initiated
via CIBA. Built for [keycloak#50725](https://github.com/keycloak/keycloak/issues/50725) — the sibling
of Simon's client-credentials version, [#51068](https://github.com/keycloak/keycloak/issues/51068).

The story: a support agent (**admin**) wants to act on a customer's (**user**) behalf. The customer
approves it on their "phone" (CIBA). Keycloak then issues a token that says *"admin acting on behalf
of user"* — the user's identity, with admin recorded in the `act` claim.

## How it works

```
admin logs in (OIDC)         -> admin's token = actor_token
admin: "act for user"        -> start CIBA (scope=delegation:admin, login_hint=user)
Keycloak                     -> pushes the approval to /device
user approves                -> Keycloak issues user's token (carries may_act=admin) = subject_token
app exchanges the two tokens -> delegated token: sub=user, act.sub=admin (transient, no refresh)
```

Two tokens meet in the exchange:

- **actor_token** — who acts (admin), from their OIDC login.
- **subject_token** — who's acted for (user), from CIBA; carries `may_act=admin`.

The exchange checks `may_act`, then rewrites it as `act` on the new token. That's the whole feature.

## Run it

Needs Java 21, Maven, `jq`, and a Keycloak build with the delegation feature (your local
`999.0.0-SNAPSHOT`). Use two terminals.

**1. Start Keycloak** (terminal 1 — unzip the dist once, then it blocks):

```bash
cd ~/IdeaProjects/keycloak/quarkus/dist/target
unzip -o keycloak-999.0.0-SNAPSHOT.zip
export KC_HOME=$PWD/keycloak-999.0.0-SNAPSHOT

KC_BOOTSTRAP_ADMIN_USERNAME=admin KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
"$KC_HOME/bin/kc.sh" start-dev \
  --features=parameterized-scopes,token-exchange-delegation \
  --spi-ciba-auth-channel--ciba-http-auth-channel--http-authentication-channel-uri=http://localhost:8081/ciba/request-authentication-channel
```

That last flag points Keycloak at this app's device endpoint — CIBA has no built-in approval UI, so
you have to provide one.

**2. Set up the realm** (terminal 2):

```bash
export KC_HOME=~/IdeaProjects/keycloak/quarkus/dist/target/keycloak-999.0.0-SNAPSHOT
./setup-realm.sh
```

Creates realm `demo`, users `user` / `user2` / `admin` (password = username), and the two clients.

**3. Run the app** (terminal 2):

```bash
mvn spring-boot:run
```

**4. Walk it in the browser.** Use two windows — one browser only holds one login at a time:

- **admin** → <http://localhost:8081>, log in `admin/admin`, type `user`, submit.
- **user** → <http://localhost:8081/device> in an incognito window, log in `user/user`, click Approve.
- Back on the admin window the delegated token shows up: `sub = user`, `act.sub = admin`, no refresh
  token. The card shows both tokens — `may_act` on the subject one, `act` on the delegated one.

## The sad path (proving it's enforced)

Delegation only works because admin is allowed to impersonate the user. Take that away:

```bash
"$KC_HOME/bin/kcadm.sh" remove-roles -r demo \
  --uusername admin --cclientid realm-management --rolename impersonation
```

Run the flow again. The user still approves, but their token no longer carries `may_act`, so the
exchange fails with `invalid_token — Invalid may_act claim`. Re-add the role to fix it.


## The code

Layered like a small service:

- `controller/` — thin web endpoints, hand off to services.
- `service/` (+ `impl/`) — the work: `DelegationService` orchestrates, `CibaService` drives CIBA,
  `TokenExchangeService` does the exchange, `DeviceApprovalService` is the device inbox.
- `configuration/` — security, the Keycloak HTTP client, config.
- `model/` — plain records.

The exchange itself is one call: `TokenExchangeService.delegate(subjectToken, actorToken)`. Everything
else just gathers those two tokens.
