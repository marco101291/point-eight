import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { SERIF } from "../components/Logo";
import { ApiError, fetchActiveMatchReveal, type Reveal } from "../lib/api";
import { theme } from "../lib/theme";

type ScreenState = { status: "loading" } | { status: "error" } | { status: "ready"; reveal: Reveal };

/**
 * The full record, one level below the countdown (app/index.tsx). A separate route rather than an
 * inline expand-in-place: closing it with a real "back" is what makes it feel like opening a file,
 * not just toggling a section — and it's what let the countdown screen go back to being just the
 * photo and the clock once this stopped living on top of it.
 */
export default function MatchProfileScreen() {
  const router = useRouter();
  const [state, setState] = useState<ScreenState>({ status: "loading" });

  const load = useCallback(async () => {
    setState({ status: "loading" });
    try {
      const reveal = await fetchActiveMatchReveal();
      setState({ status: "ready", reveal });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        router.replace("/login");
        return;
      }
      // A 404 here means the match ended while this screen was open — going back lets
      // index.tsx's own load() discover that and show "no hay coincidencia activa" properly,
      // rather than this screen trying to own that state too.
      setState({ status: "error" });
    }
  }, [router]);

  useEffect(() => {
    load();
  }, [load]);

  // canGoBack() guards a full reload landing directly on this route with no history behind it —
  // router.back() would otherwise silently do nothing, which is exactly what looked like "no way
  // back" from the countdown screen.
  function handleBack() {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace("/");
    }
  }

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.topBar}>
        <Pressable onPress={handleBack} style={styles.closeButton} hitSlop={12}>
          <Text style={styles.closeButtonText}>Volver</Text>
        </Pressable>
      </View>

      <View style={styles.centerLayer}>
        {state.status === "loading" && <ActivityIndicator color={theme.muted} />}

        {state.status === "error" && (
          <View style={styles.errorState}>
            <Text style={styles.body}>Esto ya no está disponible.</Text>
            <Pressable onPress={handleBack} style={styles.backButton}>
              <Text style={styles.backButtonText}>Volver</Text>
            </Pressable>
          </View>
        )}

        {state.status === "ready" && (
          <View style={styles.content}>
            <View style={styles.photoRing}>
              <Image
                source={{ uri: state.reveal.photoUrl }}
                style={styles.photo}
                contentFit="cover"
                transition={200}
              />
            </View>
            <Text style={styles.facts}>
              {state.reveal.age} años{"\n"}
              {state.reveal.city} · {state.reveal.profession}
            </Text>
            {state.reveal.hobbies.length > 0 && (
              <Text style={styles.hobbies}>{state.reveal.hobbies.join(" · ")}</Text>
            )}
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: theme.bg,
  },
  // Absolutely positioned so it floats over centerLayer instead of taking a row out of the flex
  // flow — otherwise its height shifts the "centered" content down from true screen-center.
  topBar: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 24,
    paddingTop: 12,
    zIndex: 1,
  },
  closeButton: {
    alignSelf: "flex-start",
    borderWidth: 1,
    borderColor: theme.fg,
    borderRadius: 2,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  closeButtonText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 13,
    letterSpacing: 1,
  },
  centerLayer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  errorState: {
    alignItems: "center",
    gap: 16,
  },
  body: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 15,
  },
  backButton: {
    borderWidth: 1,
    borderColor: theme.fg,
    borderRadius: 2,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  backButtonText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 14,
  },
  content: {
    alignItems: "center",
    paddingHorizontal: 40,
    gap: 28,
  },
  photoRing: {
    width: 176,
    height: 176,
    borderRadius: 88,
    borderWidth: 1,
    borderColor: theme.line,
    padding: 6,
  },
  photo: {
    flex: 1,
    borderRadius: 82,
  },
  facts: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 24,
    lineHeight: 32,
    textAlign: "center",
  },
  hobbies: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 14,
    letterSpacing: 0.5,
    textAlign: "center",
  },
});
