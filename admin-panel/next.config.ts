import type { NextConfig } from "next";

// Ojo: no declarar `env` acá. Ese bloque inlinea los valores en build time, lo que
// hornearía las URLs de desarrollo en la imagen. Las URLs de servicio se leen del
// entorno en runtime, dentro del server component (ver app/page.tsx).
const nextConfig: NextConfig = {
  output: "standalone",
};

export default nextConfig;
