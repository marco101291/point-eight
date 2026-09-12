import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Animated, Easing, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { SERIF } from "../components/Logo";
import { ApiError, fetchActiveMatchReveal, logout as apiLogout, type Reveal } from "../lib/api";
import { registerForPushNotificationsAsync } from "../lib/pushNotifications";
import { theme } from "../lib/theme";
import { clearTokens } from "../lib/tokenStorage";

// Layer 1 only, mirroring the point-eight domain's non-negotiable invariant (see the main repo's
// CLAUDE.md): no name field exists anywhere in the system, by design — the System never lets a
// user identify the other by anything but age/city/profession/hobbies. Backed by java-system's
// GET /api/matches/me/reveal (DEC-021) now, no longer a hardcoded mock.
type ScreenState =
  | { status: "loading" }
  | { status: "unauthenticated" }
  | { status: "no-match" }
  | { status: "error"; message: string }
  | { status: "ready"; reveal: Reveal };

export default function RevealScreen() {
  const router = useRouter();
  const [state, setState] = useState<ScreenState>({ status: "loading" });

  const load = useCallback(async () => {
    setState({ status: "loading" });
    try {
      const reveal = await fetchActiveMatchReveal();
      setState({ status: "ready", reveal });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        await clearTokens();
        setState({ status: "unauthenticated" });
        return;
      }
      if (e instanceof ApiError && e.status === 404) {
        setState({ status: "no-match" });
        return;
      }
      setState({
        status: "error",
        message: e instanceof ApiError ? e.message : "No se pudo contactar al Sistema.",
      });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace("/login");
    }
  }, [state.status, router]);

  useEffect(() => {
    // Covers relaunching the app with a session already stored — login.tsx only handles the
    // fresh-login path. Harmless to call again if it already ran there this session; it just
    // replaces the same token on file.
    if (state.status === "ready" || state.status === "no-match") {
      registerForPushNotificationsAsync();
    }
  }, [state.status]);

  async function handleLogout() {
    await apiLogout();
    await clearTokens();
    router.replace("/login");
  }

  if (state.status === "loading" || state.status === "unauthenticated") {
    return (
      <View style={[styles.root, styles.centered]}>
        <ActivityIndicator color={theme.muted} />
      </View>
    );
  }

  if (state.status === "no-match") {
    return (
      <View style={[styles.root, styles.centered]}>
        <SafeAreaView style={styles.emptyState}>
          <Text style={styles.eyebrow}>El Sistema</Text>
          <Text style={styles.emptyHeadline}>Todavía no hay coincidencia activa</Text>
          <Text style={styles.emptyBody}>El Sistema va a avisar cuando decida algo.</Text>
          <Pressable style={styles.retryButton} onPress={load}>
            <Text style={styles.retryText}>Actualizar</Text>
          </Pressable>
          <Pressable onPress={handleLogout}>
            <Text style={styles.logoutLink}>Cerrar sesión</Text>
          </Pressable>
        </SafeAreaView>
      </View>
    );
  }

  if (state.status === "error") {
    return (
      <View style={[styles.root, styles.centered]}>
        <SafeAreaView style={styles.emptyState}>
          <Text style={styles.emptyHeadline}>{state.message}</Text>
          <Pressable style={styles.retryButton} onPress={load}>
            <Text style={styles.retryText}>Reintentar</Text>
          </Pressable>
          <Pressable onPress={handleLogout}>
            <Text style={styles.logoutLink}>Cerrar sesión</Text>
          </Pressable>
        </SafeAreaView>
      </View>
    );
  }

  return <ActiveMatch reveal={state.reveal} onLogout={handleLogout} />;
}

function ActiveMatch({ reveal, onLogout }: { reveal: Reveal; onLogout: () => void }) {
  const router = useRouter();
  const countdown = useCountdown(reveal.expiresAt);
  const photoScale = useRef(new Animated.Value(1)).current;

  function handleOpenProfile() {
    Animated.sequence([
      Animated.timing(photoScale, {
        toValue: 1.08,
        duration: 180,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(photoScale, {
        toValue: 1,
        duration: 160,
        easing: Easing.in(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start(() => router.push("/match-profile"));
  }

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.topBar}>
        <Text style={styles.eyebrow}>Expediente activo</Text>
        <Pressable onPress={onLogout} hitSlop={12}>
          <Text style={styles.logoutLink}>Cerrar sesión</Text>
        </Pressable>
      </View>

      <View style={styles.photoWrap}>
        <Pressable onPress={handleOpenProfile}>
          <Animated.View style={[styles.photoRing, { transform: [{ scale: photoScale }] }]}>
            <Image
              source={{ uri: reveal.photoUrl }}
              style={styles.photo}
              contentFit="cover"
              transition={300}
            />
          </Animated.View>
        </Pressable>

        <Text
          style={styles.countdown}
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.5}
        >
          {countdown}
        </Text>
      </View>
    </SafeAreaView>
  );
}

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;
const AVG_MONTH_DAYS = 30.44;
const AVG_YEAR_DAYS = 365.25;

/** Live countdown until `expiresAtIso`, at a granularity that fits how far off it is — DEC-028
 * made real match duration range from 2 hours to ~2.7 years, and `HH:MM:SS` ticking every second
 * stops being legible (or even meaningful) past a day or two. Floors at 00:00:00 — this screen
 * doesn't poll on its own, so a match that actually expired while open just sits at zero until
 * the user navigates back and the next load() call finds it gone (404 -> "no-match").
 *
 * <p>Re-derives its own tick interval every time it fires (a chain of `setTimeout`s, not a fixed
 * `setInterval`) rather than picking one once at mount: a match with months left needs the label
 * refreshed only occasionally, but as it nears expiry the same countdown has to speed back up to
 * per-second ticks on its own, without remounting. */
export function useCountdown(expiresAtIso: string): string {
  const [label, setLabel] = useState(() => formatRemaining(expiresAtIso));

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>;

    function tick() {
      setLabel(formatRemaining(expiresAtIso));
      timeoutId = setTimeout(tick, tickIntervalFor(expiresAtIso));
    }

    tick();
    return () => clearTimeout(timeoutId);
  }, [expiresAtIso]);

  return label;
}

function tickIntervalFor(expiresAtIso: string): number {
  const remainingMs = new Date(expiresAtIso).getTime() - Date.now();
  if (remainingMs < 2 * DAY_MS) {
    return 1000;
  }
  if (remainingMs < 90 * DAY_MS) {
    return 60_000;
  }
  return HOUR_MS;
}

function formatRemaining(expiresAtIso: string): string {
  const remainingMs = new Date(expiresAtIso).getTime() - Date.now();
  if (remainingMs <= 0) {
    return "00:00:00";
  }

  // Under 2 days: HH:MM:SS, same precision the countdown always had — this is the window where
  // per-second urgency actually means something.
  if (remainingMs < 2 * DAY_MS) {
    const totalSeconds = Math.floor(remainingMs / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((n) => String(n).padStart(2, "0")).join(":");
  }

  const totalDays = Math.floor(remainingMs / DAY_MS);

  // Under ~3 months: days and hours. Seconds (or even minutes) stop being useful information at
  // this scale, and showing them would just make the countdown look like it's malfunctioning.
  if (totalDays < 90) {
    const hours = Math.floor((remainingMs % DAY_MS) / HOUR_MS);
    return `${totalDays}d ${String(hours).padStart(2, "0")}h`;
  }

  // Beyond ~3 months: months, then years+months once there's at least one full year.
  // Approximate on purpose — nobody needs day-level precision for "about 8 months left".
  const totalMonths = Math.round(totalDays / AVG_MONTH_DAYS);
  if (totalMonths < 12) {
    return totalMonths <= 1 ? "1 mes" : `${totalMonths} meses`;
  }

  let years = Math.floor(totalDays / AVG_YEAR_DAYS);
  let months = Math.round((totalDays - years * AVG_YEAR_DAYS) / AVG_MONTH_DAYS);
  if (months >= 12) {
    years += 1;
    months = 0;
  }

  const yearsLabel = years === 1 ? "1 año" : `${years} años`;
  if (months === 0) {
    return yearsLabel;
  }
  const monthsLabel = months === 1 ? "1 mes" : `${months} meses`;
  return `${yearsLabel} ${monthsLabel}`;
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: theme.bg,
  },
  centered: {
    justifyContent: "center",
    alignItems: "center",
  },
  emptyState: {
    alignItems: "center",
    paddingHorizontal: 32,
    gap: 10,
  },
  emptyHeadline: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 20,
    textAlign: "center",
  },
  emptyBody: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 15,
    textAlign: "center",
  },
  retryButton: {
    marginTop: 12,
    borderWidth: 1,
    borderColor: theme.line,
    borderRadius: 2,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  retryText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 14,
  },
  logoutLink: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 13,
    textDecorationLine: "underline",
  },
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 24,
    paddingTop: 12,
  },
  eyebrow: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 12,
    letterSpacing: 2,
    textTransform: "uppercase",
  },
  photoWrap: {
    alignItems: "center",
    marginTop: 56,
    paddingHorizontal: 24,
  },
  photoRing: {
    width: 200,
    height: 200,
    borderRadius: 100,
    borderWidth: 1,
    borderColor: theme.line,
    padding: 6,
  },
  photo: {
    flex: 1,
    borderRadius: 100,
  },
  countdown: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 56,
    letterSpacing: 2,
    marginTop: 28,
    fontVariant: ["tabular-nums"],
    width: "100%",
    textAlign: "center",
  },
});
