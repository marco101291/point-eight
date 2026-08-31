/**
 * Lockup vertical de 0.8 (sección 10 del doc de arquitectura).
 * El "0·8" se rota 90° como una sola unidad; `dominantBaseline="central"`
 * evita que el glyph se descuadre al centrarlo antes de rotar.
 */
export function Logo({ size = 140 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size * 1.25}
      viewBox="0 0 140 175"
      role="img"
      aria-label="0.8 — Point Eight"
    >
      <g transform="rotate(90 70 70)">
        <text
          x="70"
          y="70"
          textAnchor="middle"
          dominantBaseline="central"
          fontFamily="Georgia, 'Times New Roman', serif"
          fontSize="90"
          letterSpacing="-4"
          fill="currentColor"
        >
          0·8
        </text>
      </g>
      <text
        x="70"
        y="152"
        textAnchor="middle"
        fontFamily="Georgia, 'Times New Roman', serif"
        fontSize="17"
        letterSpacing="3.5"
        fill="currentColor"
      >
        POINT EIGHT
      </text>
    </svg>
  );
}
