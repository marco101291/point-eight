import { LinearGradient } from "expo-linear-gradient";
import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { ApiError, fetchActiveMatchReveal, logout as apiLogout, type Reveal } from "../lib/api";
import { registerForPushNotificationsAsync } from "../lib/pushNotifications";
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
        message: e instanceof ApiError ? e.message : "Could not reach the System.",
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
        <ActivityIndicator color="#e8b4a0" />
      </View>
    );
  }

  if (state.status === "no-match") {
    return (
      <View style={[styles.root, styles.centered]}>
        <SafeAreaView style={styles.emptyState}>
          <Text style={styles.eyebrow}>El Sistema</Text>
          <Text style={styles.emptyHeadline}>Todavía no hay match activo</Text>
          <Text style={styles.emptyBody}>El Sistema te va a avisar cuando decida algo.</Text>
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

  const { reveal } = state;

  return (
    <View style={styles.root}>
      <Image
        source={{ uri: reveal.photoUrl }}
        style={StyleSheet.absoluteFill}
        contentFit="cover"
        transition={200}
      />
      <SafeAreaView edges={["top"]} style={styles.topBar}>
        <Pressable onPress={handleLogout} style={styles.logoutPill}>
          <Text style={styles.logoutPillText}>Cerrar sesión</Text>
        </Pressable>
      </SafeAreaView>
      <LinearGradient
        colors={["transparent", "rgba(0,0,0,0.85)"]}
        locations={[0.4, 1]}
        style={styles.gradient}
      >
        <SafeAreaView edges={["bottom"]} style={styles.content}>
          <Text style={styles.eyebrow}>Tu match</Text>
          <Text style={styles.headline}>
            {reveal.age} · {reveal.city}
          </Text>
          <Text style={styles.profession}>{reveal.profession}</Text>
          <View style={styles.hobbies}>
            {reveal.hobbies.map((hobby) => (
              <View key={hobby} style={styles.hobbyChip}>
                <Text style={styles.hobbyText}>{hobby}</Text>
              </View>
            ))}
          </View>
        </SafeAreaView>
      </LinearGradient>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: "#0b0b0c",
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
    color: "#fff",
    fontSize: 20,
    fontWeight: "600",
    textAlign: "center",
  },
  emptyBody: {
    color: "#c9c7c2",
    fontSize: 15,
    textAlign: "center",
  },
  retryButton: {
    marginTop: 12,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.35)",
    borderRadius: 10,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  retryText: {
    color: "#fff",
    fontSize: 14,
  },
  logoutLink: {
    marginTop: 20,
    color: "#8a8781",
    fontSize: 13,
    textDecorationLine: "underline",
  },
  topBar: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    alignItems: "flex-end",
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  logoutPill: {
    backgroundColor: "rgba(0,0,0,0.45)",
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  logoutPillText: {
    color: "#fff",
    fontSize: 12,
    letterSpacing: 1,
    textTransform: "uppercase",
  },
  gradient: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    height: "55%",
    justifyContent: "flex-end",
  },
  content: {
    paddingHorizontal: 24,
    paddingBottom: 16,
  },
  eyebrow: {
    color: "#e8b4a0",
    fontSize: 13,
    letterSpacing: 2,
    textTransform: "uppercase",
    marginBottom: 6,
  },
  headline: {
    color: "#fff",
    fontSize: 30,
    fontWeight: "600",
  },
  profession: {
    color: "#e8e6e1",
    fontSize: 17,
    marginTop: 4,
  },
  hobbies: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 18,
  },
  hobbyChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.35)",
  },
  hobbyText: {
    color: "#fff",
    fontSize: 13,
  },
});
