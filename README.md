# Spring Boot + Supabase Auth (OAuth 2.1)

A Spring Boot 4 app that uses a Supabase project as its **OAuth 2.1 / OpenID Connect authorization server**, the same way you would use Keycloak:

- `/`: "Continue with Supabase" (authorization code + PKCE), then a token inspector styled like the Supabase dashboard: access, ID and refresh tokens, the project's discovery document and public keys.
- `/oauth/consent`: the login + consent page Supabase redirects to. **Served by this app**, because Supabase doesn't host one.
- `/api/me`: a stateless API that accepts Supabase access tokens (`Authorization: Bearer ...`), validated with the project's JWKS.

## What UI does Supabase give you?

| You get from Supabase | You build yourself |
| --- | --- |
| Dashboard: users, OAuth apps (clients), providers, email templates, logs | The login page |
| Auth emails (confirmation, magic link, password reset) | The consent page ("App X wants access to your account") |
| Endpoints: authorize, token, JWKS, OIDC discovery, dynamic client registration | Any account / profile page |

Unlike Keycloak, there is no hosted login screen. Supabase redirects to `<Site URL>/oauth/consent` and expects you to sign the user in there with supabase-js and approve or deny the request. `src/main/resources/templates/consent.html` is that page: plain HTML + supabase-js, no build step.

## 1. Set up Supabase (free plan is enough)

1. Create a project at [supabase.com](https://supabase.com/dashboard).
2. **Use asymmetric JWT signing keys.** Project Settings > JWT Keys: if the project still signs with the legacy HS256 secret, create an asymmetric key (ES256 or RS256) and rotate to it. ID tokens can't be issued with HS256, and Spring validates tokens with the public keys from the JWKS.
3. **URL configuration.** Authentication > URL Configuration:
   - Site URL: `http://localhost:8080`
   - Redirect URLs: add `http://localhost:8080/**` (for magic links, email confirmation and GitHub sign-in coming back to the consent page)
4. **Enable the OAuth server.** Authentication > OAuth Server: enable it and set the authorization path to `/oauth/consent`.
5. **Register this app as a client.** Authentication > OAuth Apps > Add a new client:
   - Name: `Spring demo` (shown on the consent page)
   - Redirect URI: `http://localhost:8080/login/oauth2/code/supabase`
   - Client type: **Confidential**
   - Copy the client ID and client secret.
6. Optional:
   - Authentication > Sign In / Providers > Email: turn off **Confirm email** to test sign-up without checking your inbox.
   - Enable the GitHub provider to try "Continue with GitHub".

## 2. Run the app

Requirements: Java 21+, Maven.

```bash
cp .env.example .env   # then fill in the 4 values
mvn spring-boot:run
```

`SUPABASE_URL` and the publishable key are under Project Settings > API Keys (or the **Connect** button). Open <http://localhost:8080>.

The app reads `${SUPABASE_URL}/auth/v1/.well-known/openid-configuration` at startup, so it won't start without valid values.

## 3. Try it

1. Click **Continue with Supabase**.
2. On the consent page, create an account or sign in, then **Approve**.
3. You land on the access token: expiry, client, scopes and every claim with what it means. Note `client_id`: it's how RLS policies or your API can tell OAuth clients apart.
4. Click **Send request** in the console to call `/api/me` with the access token.
5. Use the sidebar for the ID token, the refresh token, the discovery document and the public keys (JWKS).
6. **Sign out** ends both the Spring session and the Supabase browser session, so you can try another user.

## 4. Deploy on Render (free)

1. Render dashboard > **New > Web Service** > connect this GitHub repo.
2. Language: **Docker** (Render builds the `Dockerfile`). Instance type: **Free**.
3. **Environment variables**: the same 4 as `.env` (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_OAUTH_CLIENT_ID`, `SUPABASE_OAUTH_CLIENT_SECRET`). Don't set `PORT`: Render provides it.
4. Create the service and note its URL, e.g. `https://<your-app>.onrender.com`.
5. In Supabase, point everything at that URL:
   - Authentication > URL Configuration > Site URL: `https://<your-app>.onrender.com`
   - Redirect URLs: add `https://<your-app>.onrender.com/**`
   - Authentication > OAuth Apps > your client > Redirect URIs: add `https://<your-app>.onrender.com/login/oauth2/code/supabase`

Supabase has a single Site URL, so the consent page is served by whichever app it points to: set it to the Render URL in production, or back to `http://localhost:8080` for local work (or use a second Supabase project for local).

Free instances sleep after 15 minutes without traffic; the first request after that takes about a minute.

## How it fits together

```text
Browser ──► Spring /oauth2/authorization/supabase
        ──► Supabase /auth/v1/oauth/authorize          (client_id, PKCE, scope=openid email profile)
        ──► Spring /oauth/consent?authorization_id=…   (supabase-js: sign in, approve)
        ──► Spring /login/oauth2/code/supabase?code=…  (Spring exchanges the code for tokens)
        ──► Spring /                                    (claims of the ID token and access token)
```

| File | Role |
| --- | --- |
| `SecurityConfig.java` | Two filter chains: OAuth login (with PKCE) for pages, JWT resource server for `/api/**` |
| `InspectorController.java` | The inspector pages; decodes the access token with the same `JwtDecoder` as the API |
| `TemplateRenderingTest.java` | Renders every page with sample data (writes `target/preview/*.html`) |
| `ConsentController.java` + `consent.html` | The consent page Supabase redirects to |
| `ApiController.java` | `/api/me`, the protected API |
| `application.yml` | Client registration and issuer (`${SUPABASE_URL}/auth/v1`) |

## Notes

- **Beta.** Supabase's OAuth 2.1 server is in beta; endpoints and dashboard labels may change.
- **Audience.** Supabase access tokens carry `aud: authenticated` by default. This demo doesn't validate the audience. To issue tokens for a specific API, customize `aud` with a [Custom Access Token Hook](https://supabase.com/docs/guides/auth/auth-hooks/custom-access-token-hook) and validate it in Spring.
- **Publishable key.** It's sent to the browser on purpose. It identifies the project; Row Level Security protects the data.

## References

- [Supabase: OAuth 2.1 Server](https://supabase.com/docs/guides/auth/oauth-server)
- [Supabase: Getting started with the OAuth 2.1 Server](https://supabase.com/docs/guides/auth/oauth-server/getting-started)
- [Supabase: Token security and RLS](https://supabase.com/docs/guides/auth/oauth-server/token-security)
- [Spring Security: OAuth 2.0 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
