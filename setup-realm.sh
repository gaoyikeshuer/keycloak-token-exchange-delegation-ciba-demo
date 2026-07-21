#!/usr/bin/env bash
#
# Configures the "demo" realm for the CIBA token-exchange delegation demo (Scenario 2).
# Run this AFTER Keycloak is started with the required features (see README).
#
# Creates:
#   users:   user / user2 (customers), admin (support agent = actor, granted impersonation)
#   clients: demo-admin-app     (Admin's OIDC login; actor_token issuer; audience -> backend)
#            demo-support-backend(confidential; CIBA + token exchange; delegation optional scope)
#
# Requires: the Keycloak dist (for bin/kcadm.sh) and `jq`.
#
set -euo pipefail

# --------------------------------------------------------------------------- config
KC_HOME="${KC_HOME:?Set KC_HOME to your Keycloak distribution directory (the one containing bin/kcadm.sh)}"
KC_URL="${KC_URL:-http://localhost:8080}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"

REALM="${REALM:-demo}"
ADMIN_APP_SECRET="${ADMIN_APP_SECRET:-admin-app-secret}"
BACKEND_SECRET="${BACKEND_SECRET:-backend-secret}"
APP_URL="${APP_URL:-http://localhost:8081}"

KCADM="$KC_HOME/bin/kcadm.sh"
command -v jq >/dev/null || { echo "ERROR: jq is required (brew install jq)"; exit 1; }
[ -x "$KCADM" ] || { echo "ERROR: $KCADM not found/executable"; exit 1; }

echo "==> Logging in to $KC_URL as $KC_ADMIN"
"$KCADM" config credentials --server "$KC_URL" --realm master --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD"

# --------------------------------------------------------------------------- realm
if "$KCADM" get "realms/$REALM" >/dev/null 2>&1; then
  echo "==> Realm '$REALM' already exists — leaving it in place"
else
  echo "==> Creating realm '$REALM' (backchannel token delivery mode: poll)"
  "$KCADM" create realms \
    -s realm="$REALM" \
    -s enabled=true \
    -s 'attributes."cibaBackchannelTokenDeliveryMode"=poll' \
    -s 'attributes."cibaExpiresIn"=120' \
    -s 'attributes."cibaInterval"=5' \
    -s 'attributes."cibaAuthRequestedUserHint"=login_hint'
fi

# --------------------------------------------------------------------------- users
create_user() { # username  password  first  last
  local u="$1"
  if "$KCADM" get users -r "$REALM" -q username="$u" --fields id | jq -e '.[0]' >/dev/null 2>&1; then
    echo "==> User '$u' already exists"
  else
    echo "==> Creating user '$u'"
    "$KCADM" create users -r "$REALM" \
      -s username="$u" -s enabled=true \
      -s email="$u@demo.test" -s emailVerified=true \
      -s firstName="$3" -s lastName="$4"
    "$KCADM" set-password -r "$REALM" --username "$u" --new-password "$2"
  fi
}

create_user user user User Customer
create_user user2   user2   User2   Customer
create_user admin  admin  Admin  Support

echo "==> Granting 'admin' the realm-management 'impersonation' role (required to act for users)"
"$KCADM" add-roles -r "$REALM" --uusername admin --cclientid realm-management --rolename impersonation

# --------------------------------------------------------------------------- demo-admin-app
# Admin logs in here (authorization_code). His access token is the actor_token. The audience mapper
# puts demo-support-backend into that token's aud, which the delegation exchange requires.
if "$KCADM" get clients -r "$REALM" -q clientId=demo-admin-app --fields id | jq -e '.[0]' >/dev/null 2>&1; then
  echo "==> Client 'demo-admin-app' already exists"
  ADMIN_APP_ID=$("$KCADM" get clients -r "$REALM" -q clientId=demo-admin-app --fields id | jq -r '.[0].id')
else
  echo "==> Creating client 'demo-admin-app'"
  ADMIN_APP_ID=$("$KCADM" create clients -r "$REALM" -i \
    -s clientId=demo-admin-app \
    -s enabled=true \
    -s protocol=openid-connect \
    -s publicClient=false \
    -s secret="$ADMIN_APP_SECRET" \
    -s standardFlowEnabled=true \
    -s directAccessGrantsEnabled=true \
    -s "redirectUris=[\"$APP_URL/*\"]" \
    -s "webOrigins=[\"$APP_URL\"]" \
    -s 'attributes."post.logout.redirect.uris"=+')

  echo "    - adding audience mapper (aud += demo-support-backend)"
  "$KCADM" create "clients/$ADMIN_APP_ID/protocol-mappers/models" -r "$REALM" \
    -s name=audience-support-backend \
    -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper \
    -s 'config."included.client.audience"=demo-support-backend' \
    -s 'config."access.token.claim"=true' \
    -s 'config."id.token.claim"=false' \
    -s 'config."introspection.token.claim"=true'
fi

# --------------------------------------------------------------------------- demo-support-backend
# Confidential client this demo authenticates as to call CIBA + token exchange.
# consentRequired=true is REQUIRED: the built-in "delegation" scope is an "always consent" scope,
# and Keycloak rejects an always-consent scope requested by a non-consent client with a misleading
# "invalid_scope" error (even before any parameter/permission checks).
if "$KCADM" get clients -r "$REALM" -q clientId=demo-support-backend --fields id | jq -e '.[0]' >/dev/null 2>&1; then
  echo "==> Client 'demo-support-backend' already exists"
  BACKEND_ID=$("$KCADM" get clients -r "$REALM" -q clientId=demo-support-backend --fields id | jq -r '.[0].id')
else
  echo "==> Creating client 'demo-support-backend' (CIBA grant + standard token exchange enabled)"
  BACKEND_ID=$("$KCADM" create clients -r "$REALM" -i \
    -s clientId=demo-support-backend \
    -s enabled=true \
    -s protocol=openid-connect \
    -s publicClient=false \
    -s secret="$BACKEND_SECRET" \
    -s consentRequired=true \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s 'attributes."oidc.ciba.grant.enabled"=true' \
    -s 'attributes."standard.token.exchange.enabled"=true' \
    -s 'attributes."ciba.backchannel.token.delivery.mode"=poll')
fi

# Attach the built-in "delegation" client scope as an OPTIONAL scope so the backend may request
# scope=delegation:<username>. (The scope + its may_act mapper are created by the server when the
# parameterized-scopes / token-exchange-delegation features are enabled.)
echo "==> Attaching 'delegation' optional client scope to demo-support-backend"
DELEGATION_SCOPE_ID=$("$KCADM" get client-scopes -r "$REALM" --fields id,name \
  | jq -r '.[] | select(.name=="delegation") | .id')
if [ -z "${DELEGATION_SCOPE_ID:-}" ] || [ "$DELEGATION_SCOPE_ID" = "null" ]; then
  echo "ERROR: 'delegation' client scope not found in realm '$REALM'."
  echo "       Start Keycloak with --features=parameterized-scopes,token-exchange-delegation and re-run."
  exit 1
fi
"$KCADM" update "clients/$BACKEND_ID/optional-client-scopes/$DELEGATION_SCOPE_ID" -r "$REALM"

cat <<EOF

==> Done.

Realm ......... $REALM
Users ......... user/user, user2/user2 (customers) · admin/admin (support agent, actor)
Clients ....... demo-admin-app (secret: $ADMIN_APP_SECRET)
                demo-support-backend (secret: $BACKEND_SECRET)

Start the demo app with matching config, e.g.:
  KC_BASE_URL=$KC_URL KC_REALM=$REALM \\
  KC_ADMIN_CLIENT_ID=demo-admin-app       KC_ADMIN_CLIENT_SECRET=$ADMIN_APP_SECRET \\
  KC_BACKEND_CLIENT_ID=demo-support-backend KC_BACKEND_CLIENT_SECRET=$BACKEND_SECRET \\
  mvn spring-boot:run
EOF
