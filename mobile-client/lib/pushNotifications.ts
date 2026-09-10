import Constants from "expo-constants";
import * as Notifications from "expo-notifications";
import { Platform } from "react-native";
import { registerPushToken } from "./api";

// Foreground behavior: without this, expo-notifications shows nothing while the app is open.
// The System already has the reveal screen for anything worth the user's attention while they're
// in the app — a banner on top of it would just be noise.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: false,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/**
 * Requests permission and registers this device's Expo push token with java-system, so the
 * System can reach it when a match activates (M7). Called after login and on every launch while
 * already authenticated — never assumes the token from last time is still the one on file.
 *
 * <p>Remote push doesn't work in Expo Go at all since SDK 53 — only in a development build. This
 * fails soft everywhere it can (no EAS project configured yet, no permission granted, running in
 * Expo Go) rather than throwing, since none of those should block using the rest of the app.
 */
export async function registerForPushNotificationsAsync(): Promise<void> {
  if (Platform.OS === "android") {
    // Required before requesting a token on Android 13+, regardless of whether permission is
    // granted yet.
    await Notifications.setNotificationChannelAsync("default", {
      name: "default",
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;
  if (existingStatus !== "granted") {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }
  if (finalStatus !== "granted") {
    console.warn("[push] notification permission not granted, skipping token registration");
    return;
  }

  const projectId =
    Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    console.warn("[push] no EAS project configured yet, skipping token registration");
    return;
  }

  try {
    const { data: expoPushToken } = await Notifications.getExpoPushTokenAsync({ projectId });
    await registerPushToken(expoPushToken);
  } catch (e) {
    console.warn("[push] could not register push token", e);
  }
}
