import { Platform } from "react-native";
import Svg, { G, Text as SvgText } from "react-native-svg";

// Georgia isn't bundled on Android; "serif" resolves to the platform's own (Noto Serif on most
// devices) — close enough to the admin panel's actual Georgia without shipping a font file.
export const SERIF =
  Platform.select({ web: "Georgia, 'Times New Roman', serif", ios: "Georgia" }) ?? "serif";

/**
 * Port of admin-panel's Logo (admin-panel/app/logo.tsx, itself section 10 of the architecture
 * doc's lockup spec) so both surfaces of the System share one mark instead of the mobile client
 * inventing its own: "0·8" rotated 90° as a single unit, "POINT EIGHT" underneath. `fill` takes an
 * explicit color prop rather than the web version's `currentColor` — react-native-svg has no CSS
 * cascade to inherit from.
 */
export function Logo({ size = 140, color = "#e8e6e1" }: { size?: number; color?: string }) {
  return (
    <Svg width={size} height={size * 1.25} viewBox="0 0 140 175">
      <G transform="rotate(90 70 70)">
        <SvgText
          x="70"
          y="70"
          textAnchor="middle"
          alignmentBaseline="central"
          fontFamily={SERIF}
          fontSize="90"
          letterSpacing="-4"
          fill={color}
        >
          0·8
        </SvgText>
      </G>
      <SvgText
        x="70"
        y="152"
        textAnchor="middle"
        fontFamily={SERIF}
        fontSize="17"
        letterSpacing="3.5"
        fill={color}
      >
        POINT EIGHT
      </SvgText>
    </Svg>
  );
}
