import { Stack } from "expo-router";

export default function RootLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="login" />
      <Stack.Screen name="signup" />
      {/* fade_from_bottom over the default horizontal slide: this screen is meant to feel like
          the record surfacing from underneath the countdown, not a sideways step to a new page. */}
      <Stack.Screen name="match-profile" options={{ animation: "fade_from_bottom" }} />
    </Stack>
  );
}
