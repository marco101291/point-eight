import { useRouter } from "expo-router";
import { useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Logo, SERIF } from "../components/Logo";
import { ApiError, login } from "../lib/api";
import { registerForPushNotificationsAsync } from "../lib/pushNotifications";
import { theme } from "../lib/theme";
import { setTokens } from "../lib/tokenStorage";

export default function LoginScreen() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const disabled = submitting || !email || !password;

  async function handleSubmit() {
    setError(null);
    setSubmitting(true);
    try {
      const tokens = await login(email.trim(), password);
      await setTokens(tokens);
      router.replace("/");
      // Fire-and-forget: the permission prompt (and everything after it) shouldn't hold up
      // navigating to the reveal screen — it fails soft on its own if anything goes wrong.
      registerForPushNotificationsAsync();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo contactar al Sistema.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <SafeAreaView style={styles.root}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.flex}
      >
        {/* Capped width so the same layout that fills a phone doesn't stretch edge to edge on a
            wide viewport (mobile-client also runs under `expo start --web` during development). */}
        <View style={styles.content}>
          <Logo size={72} color={theme.fg} />

          <Text style={styles.headline}>Iniciar sesión</Text>
          <Text style={styles.lede}>El Sistema necesita confirmar tu identidad.</Text>

          <View style={styles.form}>
            <View style={styles.field}>
              <Text style={styles.label}>Email</Text>
              <TextInput
                style={styles.input}
                placeholderTextColor={theme.muted}
                autoCapitalize="none"
                autoComplete="email"
                keyboardType="email-address"
                value={email}
                onChangeText={setEmail}
              />
            </View>
            <View style={styles.field}>
              <Text style={styles.label}>Contraseña</Text>
              <TextInput
                style={styles.input}
                placeholderTextColor={theme.muted}
                autoCapitalize="none"
                secureTextEntry
                value={password}
                onChangeText={setPassword}
              />
            </View>

            {error && <Text style={styles.error}>{error}</Text>}

            <Pressable
              style={({ pressed }) => [
                styles.button,
                pressed && !disabled && styles.buttonPressed,
                disabled && styles.buttonDisabled,
              ]}
              onPress={handleSubmit}
              disabled={disabled}
            >
              {({ pressed }) =>
                submitting ? (
                  <ActivityIndicator color={theme.fg} />
                ) : (
                  <Text
                    style={[styles.buttonText, pressed && !disabled && styles.buttonTextPressed]}
                  >
                    Entrar
                  </Text>
                )
              }
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: theme.bg,
  },
  flex: {
    flex: 1,
  },
  content: {
    flex: 1,
    width: "100%",
    maxWidth: 400,
    alignSelf: "center",
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 24,
  },
  headline: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 22,
    letterSpacing: 0.5,
    marginTop: 28,
  },
  lede: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 14,
    marginTop: 8,
    textAlign: "center",
  },
  form: {
    width: "100%",
    marginTop: 40,
    gap: 20,
  },
  field: {
    gap: 6,
  },
  label: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 12,
    letterSpacing: 1.5,
    textTransform: "uppercase",
  },
  input: {
    borderBottomWidth: 1,
    borderColor: theme.line,
    paddingVertical: 10,
    paddingHorizontal: 4,
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 16,
  },
  error: {
    fontFamily: SERIF,
    color: theme.down,
    fontSize: 14,
  },
  button: {
    borderWidth: 1,
    borderColor: theme.line,
    borderRadius: 2,
    paddingVertical: 14,
    alignItems: "center",
    marginTop: 8,
  },
  buttonPressed: {
    backgroundColor: theme.fg,
    borderColor: theme.fg,
  },
  buttonDisabled: {
    opacity: 0.4,
  },
  buttonText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 14,
    letterSpacing: 2,
    textTransform: "uppercase",
  },
  buttonTextPressed: {
    color: theme.bg,
  },
});
