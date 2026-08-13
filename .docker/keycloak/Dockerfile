# Production Keycloak image (Railway) with a pre-built optimized server
# configuration for Postgres.
#
# Build from the repo root:
#   docker build -f .docker/keycloak/Dockerfile .
#
# Required runtime env (Railway service settings):
#   KC_DB_URL=jdbc:postgresql://<host>:<port>/<db>   (dedicated DB, not the app DB)
#   KC_DB_USERNAME / KC_DB_PASSWORD
#   KC_HOSTNAME=https://<public keycloak url>
#   KC_PROXY_HEADERS=xforwarded
#   KC_HTTP_ENABLED=true                    (TLS terminates at Railway's proxy)
#   KC_BOOTSTRAP_ADMIN_USERNAME / KC_BOOTSTRAP_ADMIN_PASSWORD  (first boot only)
#
# After first boot, import .docker/keycloak/realm-export.json via the admin
# console (adjust redirect URIs/secrets per environment; leave
# directAccessGrantsEnabled on skateboard-podcast-fe turned on — the FE's
# in-app login form depends on it, unlike the old hosted-page-only flow;
# configure SMTP for the hosted "Forgot password?" flow) and create users
# manually — either in the console or through the app's admin user
# management, which syncs to Keycloak.

FROM quay.io/keycloak/keycloak:26.1 AS builder

ENV KC_DB=postgres

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.1

COPY --from=builder /opt/keycloak/ /opt/keycloak/

# App-branded login theme (realm setting: loginTheme=skateboard)
COPY .docker/keycloak/themes/skateboard /opt/keycloak/themes/skateboard

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
