import { clearTokens, getAccessToken, getRefreshToken, setTokens, type TokenPair } from "./tokenStorage";

// java-system publishes 8080 to the host (see the main repo's CLAUDE.md), but "the host" means
// something different depending on where this app is actually running: `localhost` only resolves
// to java-system for the iOS simulator and web; the Android emulator needs 10.0.2.2, and a
// physical device needs the dev machine's LAN IP. EXPO_PUBLIC_API_URL overrides the default for
// those cases — see .env.example.
const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function parseProblemDetail(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return body.detail ?? response.statusText;
  } catch {
    return response.statusText;
  }
}

/**
 * Login and refresh (DEC-023) both return this shape — a short-lived access token plus the
 * refresh token that renews it, so the caller never needs to ask for the password again just
 * because the access token expired.
 */
async function requestTokenPair(path: string, body: unknown): Promise<TokenPair> {
  const response = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await parseProblemDetail(response));
  }
  return response.json();
}

export async function login(email: string, password: string): Promise<TokenPair> {
  return requestTokenPair("/api/auth/login", { email, password });
}

export type Gender = "FEMALE" | "MALE" | "NON_BINARY";
export type SeekingType = "CASUAL" | "SHORT_TERM" | "LONG_TERM" | "UNDEFINED";
export type AttachmentStyle = "SECURE" | "ANXIOUS" | "AVOIDANT" | "DISORGANIZED";

export type CommunicationProfile = {
  criticism: number;
  contempt: number;
  defensiveness: number;
  stonewalling: number;
};

export type RegisterRequest = {
  email: string;
  password: string;
  age: number;
  gender: Gender;
  seekingGenders: Gender[];
  seekingType: SeekingType;
  city: string;
  profession: string;
  hobbies: string[];
  photoUrl: string;
  // Layer 2 — computed client-side from the sign-up questionnaire (DEC-026), never typed in
  // directly by the user. infidelityHistory/relationshipHistory/activeAddiction and
  // stressBaseline/commitmentPaceExpectation are omitted on purpose: the System derives or
  // defaults those on its own (see RegisterUserRequest.toSimulationParameters on the server).
  attachmentStyle: AttachmentStyle;
  attachmentIntensity: number;
  communicationProfile: CommunicationProfile;
};

/**
 * Creates both the dating profile (Layer 1 + the Layer 2 baseline) and the login credentials in
 * one call (RegisterAccountUseCase, DEC-022). Returns void, not a token pair — POST /api/users
 * only ever returns a UserResponse, so the caller logs in right after with the same credentials.
 */
export async function register(request: RegisterRequest): Promise<void> {
  const response = await fetch(`${API_URL}/api/users`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await parseProblemDetail(response));
  }
}

/**
 * Deletes the refresh token server-side (DEC-023) so it can't be used to silently mint new access
 * tokens anymore. Doesn't throw on failure — if the server is unreachable, the caller still wants
 * to clear local state and go back to the login screen, not get stuck.
 */
export async function logout(): Promise<void> {
  const refreshToken = await getRefreshToken();
  if (refreshToken) {
    try {
      await fetch(`${API_URL}/api/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    } catch {
      // Best-effort: the local tokens get cleared by the caller regardless.
    }
  }
}

/**
 * The retry-once-after-refresh dance every authenticated request needs: send the access token,
 * and if the server says it's no good anymore (expired — 15 min TTL, DEC-023), trade the refresh
 * token for a new pair and try exactly once more. If that fails too, the session is really over —
 * clear everything and let the caller send the user back to login.
 */
async function authenticatedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const accessToken = await getAccessToken();
  if (!accessToken) {
    throw new ApiError(401, "Not logged in");
  }

  const attempt = (token: string) =>
    fetch(`${API_URL}${path}`, {
      ...init,
      headers: { ...init.headers, Authorization: `Bearer ${token}` },
    });

  let response = await attempt(accessToken);
  if (response.status !== 401) {
    return response;
  }

  const refreshToken = await getRefreshToken();
  if (!refreshToken) {
    await clearTokens();
    throw new ApiError(401, "Session expired");
  }

  try {
    const refreshed = await requestTokenPair("/api/auth/refresh", { refreshToken });
    await setTokens(refreshed);
    response = await attempt(refreshed.accessToken);
  } catch {
    await clearTokens();
    throw new ApiError(401, "Session expired");
  }

  return response;
}

/**
 * Tells java-system which device to push to (M7) — tied to the caller's own account via the
 * access token, never a userId in the body. Safe to call repeatedly: the server just replaces
 * whatever token was on file (see Account#registerPushToken).
 */
export async function registerPushToken(pushToken: string): Promise<void> {
  const response = await authenticatedFetch("/api/auth/push-token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pushToken }),
  });
  if (!response.ok) {
    throw new ApiError(response.status, await parseProblemDetail(response));
  }
}

export type Reveal = {
  photoUrl: string;
  age: number;
  city: string;
  profession: string;
  hobbies: string[];
  /** ISO 8601 — when the match itself expires, for the reveal screen's countdown. */
  expiresAt: string;
};

/**
 * The counterpart's Layer 1 + photo for the caller's active match, per the token identifying
 * the caller — never a path parameter (see java-system's MatchController.revealActiveMatch).
 * Throws ApiError(404) when there's no active match yet, ApiError(401) when the session (access
 * token, and the refresh token behind it) is no longer valid.
 */
export async function fetchActiveMatchReveal(): Promise<Reveal> {
  const response = await authenticatedFetch("/api/matches/me/reveal");
  if (!response.ok) {
    throw new ApiError(response.status, await parseProblemDetail(response));
  }
  return response.json();
}
