import { LinearGradient } from "expo-linear-gradient";
import { Image } from "expo-image";
import { StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

// Layer 1 only, mirroring the point-eight domain's non-negotiable invariant (see the main repo's
// CLAUDE.md): no name field exists anywhere in the system, by design — the System never lets a
// user identify the other by anything but age/city/profession/hobbies. Hardcoded until the real
// reveal endpoint exists on java-system (needs auth + a photo field on Profile first).
type Reveal = {
  photoUrl: string;
  age: number;
  city: string;
  profession: string;
  hobbies: string[];
};

const MOCK_REVEAL: Reveal = {
  photoUrl: "https://picsum.photos/seed/pointeight/900/1400",
  age: 29,
  city: "Buenos Aires",
  profession: "Diseñadora",
  hobbies: ["fotografía", "escalada", "cocina"],
};

export default function RevealScreen() {
  const reveal = MOCK_REVEAL;

  return (
    <View style={styles.root}>
      <Image
        source={{ uri: reveal.photoUrl }}
        style={StyleSheet.absoluteFill}
        contentFit="cover"
        transition={200}
      />
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
