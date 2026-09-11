import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Logo, SERIF } from "../components/Logo";
import {
  ApiError,
  login,
  register,
  type Gender,
  type SeekingType,
} from "../lib/api";
import { computeLayer2Baseline, QUESTIONS } from "../lib/layer2Questionnaire";
import { registerForPushNotificationsAsync } from "../lib/pushNotifications";
import { theme } from "../lib/theme";
import { setTokens } from "../lib/tokenStorage";

const GENDERS: { value: Gender; label: string }[] = [
  { value: "FEMALE", label: "Mujer" },
  { value: "MALE", label: "Hombre" },
  { value: "NON_BINARY", label: "No binario" },
];

const SEEKING_TYPES: { value: SeekingType; label: string }[] = [
  { value: "LONG_TERM", label: "Largo plazo" },
  { value: "SHORT_TERM", label: "Corto plazo" },
  { value: "CASUAL", label: "Casual" },
  { value: "UNDEFINED", label: "Prefiero no decir" },
];

const TOTAL_STEPS = 1 + QUESTIONS.length;

type ProfileForm = {
  email: string;
  password: string;
  age: string;
  gender: Gender | null;
  seekingGenders: Set<Gender>;
  seekingType: SeekingType;
  city: string;
  profession: string;
  hobbies: string;
  photoSeed: string;
};

function randomSeed(): string {
  return Math.random().toString(36).slice(2, 10);
}

function isProfileValid(form: ProfileForm): boolean {
  const age = Number(form.age);
  return (
    /\S+@\S+\.\S+/.test(form.email) &&
    form.password.length >= 8 &&
    Number.isInteger(age) &&
    age >= 18 &&
    age <= 120 &&
    form.gender !== null &&
    form.seekingGenders.size > 0 &&
    form.city.trim().length > 0 &&
    form.profession.trim().length > 0
  );
}

/**
 * Registration split into the Layer 1 form (identity) followed by the nine-question Layer 2
 * questionnaire (DEC-026), one question per screen — a real registration form up front, then the
 * System quietly profiling you afterward, without ever asking "rate your attachment style"
 * directly. computeLayer2Baseline turns the nine taps into SimulationParameters client-side;
 * POST /api/users already accepts all of it in one call (RegisterAccountUseCase), so this screen
 * needed no new backend work.
 */
export default function SignupScreen() {
  const router = useRouter();
  const [stepIndex, setStepIndex] = useState(0);
  const [profile, setProfile] = useState<ProfileForm>({
    email: "",
    password: "",
    age: "",
    gender: null,
    seekingGenders: new Set(),
    seekingType: "UNDEFINED",
    city: "",
    profession: "",
    hobbies: "",
    photoSeed: randomSeed(),
  });
  const [selections, setSelections] = useState<(number | null)[]>(
    Array(QUESTIONS.length).fill(null),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const stepOpacity = useRef(new Animated.Value(1)).current;

  const photoUrl = `https://picsum.photos/seed/${profile.photoSeed}/900/1400`;

  function animateToStep(next: number) {
    Animated.sequence([
      Animated.timing(stepOpacity, { toValue: 0, duration: 140, useNativeDriver: true }),
    ]).start(() => {
      setStepIndex(next);
      Animated.timing(stepOpacity, { toValue: 1, duration: 220, useNativeDriver: true }).start();
    });
  }

  function handleBack() {
    if (stepIndex === 0) {
      router.back();
    } else {
      animateToStep(stepIndex - 1);
    }
  }

  async function handleAnswer(optionIndex: number) {
    const questionIndex = stepIndex - 1;
    const next = [...selections];
    next[questionIndex] = optionIndex;
    setSelections(next);

    if (questionIndex < QUESTIONS.length - 1) {
      animateToStep(stepIndex + 1);
      return;
    }
    await submit(next as number[]);
  }

  async function submit(finalSelections: number[]) {
    setSubmitting(true);
    setError(null);
    try {
      const layer2 = computeLayer2Baseline(finalSelections);
      const email = profile.email.trim();
      await register({
        email,
        password: profile.password,
        age: Number(profile.age),
        gender: profile.gender as Gender,
        seekingGenders: Array.from(profile.seekingGenders),
        seekingType: profile.seekingType,
        city: profile.city.trim(),
        profession: profile.profession.trim(),
        hobbies: profile.hobbies
          .split(",")
          .map((hobby) => hobby.trim())
          .filter(Boolean),
        photoUrl,
        ...layer2,
      });
      const tokens = await login(email, profile.password);
      await setTokens(tokens);
      router.replace("/");
      // Fire-and-forget, same reasoning as login.tsx: shouldn't hold up entering the app.
      registerForPushNotificationsAsync();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo contactar al Sistema.");
      setSubmitting(false);
    }
  }

  function toggleSeekingGender(gender: Gender) {
    setProfile((prev) => {
      const seekingGenders = new Set(prev.seekingGenders);
      if (seekingGenders.has(gender)) {
        seekingGenders.delete(gender);
      } else {
        seekingGenders.add(gender);
      }
      return { ...prev, seekingGenders };
    });
  }

  const question = stepIndex > 0 ? QUESTIONS[stepIndex - 1] : null;

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.topBar}>
        <Pressable onPress={handleBack} style={styles.backButton} hitSlop={12}>
          <Text style={styles.backButtonText}>Atrás</Text>
        </Pressable>
        {stepIndex > 0 && (
          <Text style={styles.progress}>
            {stepIndex} / {QUESTIONS.length}
          </Text>
        )}
      </View>

      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.flex}
      >
        <Animated.View style={[styles.stepContent, { opacity: stepOpacity }]}>
          {question ? (
            <View style={styles.questionBlock}>
              <Text style={styles.questionPrompt}>{question.prompt}</Text>
              <View style={styles.optionList}>
                {question.options.map((option, i) => (
                  <Pressable
                    key={option.label}
                    style={({ pressed }) => [styles.optionButton, pressed && styles.optionPressed]}
                    onPress={() => handleAnswer(i)}
                    disabled={submitting}
                  >
                    {({ pressed }) => (
                      <Text style={[styles.optionText, pressed && styles.optionTextPressed]}>
                        {option.label}
                      </Text>
                    )}
                  </Pressable>
                ))}
              </View>
              {submitting && (
                <View style={styles.submittingRow}>
                  <ActivityIndicator color={theme.muted} />
                  <Text style={styles.submittingText}>El Sistema está registrando tu expediente…</Text>
                </View>
              )}
            </View>
          ) : (
            <ScrollView
              style={styles.flex}
              contentContainerStyle={styles.form}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
            >
              <Logo size={56} color={theme.fg} />
              <Text style={styles.headline}>Crear cuenta</Text>

              <View style={styles.photoRow}>
                <Image source={{ uri: photoUrl }} style={styles.photo} contentFit="cover" />
                <Pressable
                  onPress={() => setProfile((prev) => ({ ...prev, photoSeed: randomSeed() }))}
                  hitSlop={12}
                >
                  <Text style={styles.photoLink}>Nueva fotografía</Text>
                </Pressable>
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>Email</Text>
                <TextInput
                  style={styles.input}
                  autoCapitalize="none"
                  autoComplete="email"
                  keyboardType="email-address"
                  value={profile.email}
                  onChangeText={(email) => setProfile((prev) => ({ ...prev, email }))}
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.label}>Contraseña</Text>
                <TextInput
                  style={styles.input}
                  autoCapitalize="none"
                  secureTextEntry
                  value={profile.password}
                  onChangeText={(password) => setProfile((prev) => ({ ...prev, password }))}
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.label}>Edad</Text>
                <TextInput
                  style={styles.input}
                  keyboardType="number-pad"
                  value={profile.age}
                  onChangeText={(age) => setProfile((prev) => ({ ...prev, age }))}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>Género</Text>
                <View style={styles.chipRow}>
                  {GENDERS.map(({ value, label }) => (
                    <Chip
                      key={value}
                      label={label}
                      selected={profile.gender === value}
                      onPress={() => setProfile((prev) => ({ ...prev, gender: value }))}
                    />
                  ))}
                </View>
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>Busca</Text>
                <View style={styles.chipRow}>
                  {GENDERS.map(({ value, label }) => (
                    <Chip
                      key={value}
                      label={label}
                      selected={profile.seekingGenders.has(value)}
                      onPress={() => toggleSeekingGender(value)}
                    />
                  ))}
                </View>
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>Tipo de relación</Text>
                <View style={styles.chipRow}>
                  {SEEKING_TYPES.map(({ value, label }) => (
                    <Chip
                      key={value}
                      label={label}
                      selected={profile.seekingType === value}
                      onPress={() => setProfile((prev) => ({ ...prev, seekingType: value }))}
                    />
                  ))}
                </View>
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>Ciudad</Text>
                <TextInput
                  style={styles.input}
                  value={profile.city}
                  onChangeText={(city) => setProfile((prev) => ({ ...prev, city }))}
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.label}>Profesión</Text>
                <TextInput
                  style={styles.input}
                  value={profile.profession}
                  onChangeText={(profession) => setProfile((prev) => ({ ...prev, profession }))}
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.label}>Pasatiempos (sepáralos con comas)</Text>
                <TextInput
                  style={styles.input}
                  value={profile.hobbies}
                  onChangeText={(hobbies) => setProfile((prev) => ({ ...prev, hobbies }))}
                />
              </View>

              <Pressable
                style={({ pressed }) => [
                  styles.button,
                  pressed && isProfileValid(profile) && styles.buttonPressed,
                  !isProfileValid(profile) && styles.buttonDisabled,
                ]}
                onPress={() => animateToStep(1)}
                disabled={!isProfileValid(profile)}
              >
                {({ pressed }) => (
                  <Text
                    style={[
                      styles.buttonText,
                      pressed && isProfileValid(profile) && styles.buttonTextPressed,
                    ]}
                  >
                    Siguiente
                  </Text>
                )}
              </Pressable>
            </ScrollView>
          )}
        </Animated.View>
      </KeyboardAvoidingView>

      {error && <Text style={styles.error}>{error}</Text>}
    </SafeAreaView>
  );
}

function Chip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.chipSelected]}>
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{label}</Text>
    </Pressable>
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
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 24,
    paddingTop: 12,
  },
  backButton: {
    alignSelf: "flex-start",
    borderWidth: 1,
    borderColor: theme.fg,
    borderRadius: 2,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  backButtonText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 13,
    letterSpacing: 1,
  },
  progress: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 13,
    letterSpacing: 1,
    fontVariant: ["tabular-nums"],
  },
  stepContent: {
    flex: 1,
  },
  form: {
    flexGrow: 1,
    width: "100%",
    maxWidth: 420,
    alignSelf: "center",
    paddingHorizontal: 24,
    paddingVertical: 24,
    gap: 20,
  },
  headline: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 22,
    letterSpacing: 0.5,
  },
  photoRow: {
    alignItems: "center",
    gap: 10,
  },
  photo: {
    width: 96,
    height: 96,
    borderRadius: 48,
  },
  photoLink: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 13,
    textDecorationLine: "underline",
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
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chip: {
    borderWidth: 1,
    borderColor: theme.line,
    borderRadius: 2,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  chipSelected: {
    borderColor: theme.fg,
    backgroundColor: theme.fg,
  },
  chipText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 13,
  },
  chipTextSelected: {
    color: theme.bg,
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
  questionBlock: {
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 32,
    gap: 32,
  },
  questionPrompt: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 22,
    lineHeight: 30,
  },
  optionList: {
    gap: 12,
  },
  optionButton: {
    borderWidth: 1,
    borderColor: theme.line,
    borderRadius: 2,
    paddingVertical: 16,
    paddingHorizontal: 18,
  },
  optionPressed: {
    backgroundColor: theme.fg,
    borderColor: theme.fg,
  },
  optionText: {
    fontFamily: SERIF,
    color: theme.fg,
    fontSize: 15,
    lineHeight: 21,
  },
  optionTextPressed: {
    color: theme.bg,
  },
  submittingRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  submittingText: {
    fontFamily: SERIF,
    color: theme.muted,
    fontSize: 13,
  },
  error: {
    fontFamily: SERIF,
    color: theme.down,
    fontSize: 14,
    textAlign: "center",
    paddingHorizontal: 24,
    paddingBottom: 16,
  },
});
