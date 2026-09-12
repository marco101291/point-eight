import type { AttachmentStyle, CommunicationProfile } from "./api";

// DEC-026: the sign-up questionnaire that sources the Layer 2 baseline. Every question is
// indirect (a scenario, never "rate your attachment style") and every answer is a fixed option,
// never open text — free text would need AI/NLP classification to land on a trait, which is both
// unreliable and gameable with irrelevant input. A tap here maps to a value with no inference step.

type AttachmentLean = "secure" | "anxious" | "avoidant";
type Frequency = "nunca" | "a_veces" | "seguido";
type CommunicationDimension = keyof CommunicationProfile;

type AttachmentQuestion = {
  kind: "attachment";
  prompt: string;
  options: readonly { label: string; lean: AttachmentLean }[];
};

type CommunicationQuestion = {
  kind: "communication";
  dimension: CommunicationDimension;
  prompt: string;
  options: readonly { label: string; frequency: Frequency }[];
};

type IntensityQuestion = {
  kind: "intensity";
  prompt: string;
  options: readonly { label: string; value: number }[];
};

export type Question = AttachmentQuestion | CommunicationQuestion | IntensityQuestion;

// Order matters: the four communication questions follow CommunicationProfile's own field order
// (criticism, contempt, defensiveness, stonewalling), and the scoring below assumes this exact
// nine-question sequence.
export const QUESTIONS: readonly Question[] = [
  {
    kind: "attachment",
    prompt: "Tu pareja no te escribe en todo el día. ¿Qué es más parecido a ti?",
    options: [
      { label: "Supongo que está ocupada", lean: "secure" },
      { label: "Reviso el teléfono varias veces", lean: "anxious" },
      { label: "Empiezo a distanciarme yo también", lean: "avoidant" },
    ],
  },
  {
    kind: "attachment",
    prompt: 'Cuando alguien te dice "te quiero" por primera vez, ¿qué sientes primero?',
    options: [
      { label: "Me da gusto, lo digo de vuelta si es lo que siento", lean: "secure" },
      { label: "Me alivia, pero después dudo si lo dijo en serio", lean: "anxious" },
      { label: "Me incomoda un poco, necesito procesarlo", lean: "avoidant" },
    ],
  },
  {
    kind: "attachment",
    prompt: "Después de una pelea, ¿qué es lo que más te pasa?",
    options: [
      { label: "Quiero resolverlo pronto y seguir adelante", lean: "secure" },
      { label: "Necesito que me confirmen que todo está bien", lean: "anxious" },
      { label: "Prefiero tomar distancia unos días", lean: "avoidant" },
    ],
  },
  {
    kind: "attachment",
    prompt:
      "Tu pareja necesita un fin de semana para sí misma, sin dar explicaciones. ¿Cómo reaccionas?",
    options: [
      { label: "Lo entiendo, cada quien necesita su espacio", lean: "secure" },
      { label: "Me preocupa que esté enojada conmigo", lean: "anxious" },
      { label: "Prefiero tener mi propio espacio también, sin problema", lean: "avoidant" },
    ],
  },
  {
    kind: "communication",
    dimension: "criticism",
    prompt:
      "Durante una discusión, ¿qué tan seguido terminas hablando de cómo es tu pareja en general, en vez de hablar solo de lo que pasó?",
    options: [
      { label: "Nunca, me quedo en el tema", frequency: "nunca" },
      { label: "A veces se me sale un comentario así", frequency: "a_veces" },
      { label: "Seguido termino generalizando", frequency: "seguido" },
    ],
  },
  {
    kind: "communication",
    dimension: "contempt",
    prompt: "¿Qué tan seguido se te escapa un comentario sarcástico o burlón sin querer?",
    options: [
      { label: "Nunca", frequency: "nunca" },
      { label: "A veces", frequency: "a_veces" },
      { label: "Seguido", frequency: "seguido" },
    ],
  },
  {
    kind: "communication",
    dimension: "defensiveness",
    prompt:
      "Cuando alguien te señala algo que hiciste mal, ¿qué tan seguido te descubres explicando tu punto antes de terminar de escuchar?",
    options: [
      { label: "Nunca, primero escucho todo", frequency: "nunca" },
      { label: "A veces, depende de qué tan directo sea el reclamo", frequency: "a_veces" },
      { label: "Seguido, necesito explicarme casi de inmediato", frequency: "seguido" },
    ],
  },
  {
    kind: "communication",
    dimension: "stonewalling",
    prompt:
      "Cuando una discusión se pone tensa, ¿qué tan seguido prefieres cortarla y alejarte en vez de seguir?",
    options: [
      { label: "Nunca, prefiero quedarme hasta resolverlo", frequency: "nunca" },
      { label: "A veces, si siento que ya no vamos a ningún lado", frequency: "a_veces" },
      { label: "Seguido, necesito distancia para pensar", frequency: "seguido" },
    ],
  },
  {
    kind: "intensity",
    prompt: "Cuando algo te molesta de tu pareja, ¿qué tan rápido se te pasa?",
    options: [
      { label: "Rápido, no le doy tantas vueltas", value: 0.2 },
      { label: "Depende del día, a veces se me queda pensando", value: 0.5 },
      { label: "Se me queda dando vueltas un buen rato", value: 0.8 },
    ],
  },
] as const;

const FREQUENCY_WEIGHT: Record<Frequency, number> = { nunca: 0.1, a_veces: 0.4, seguido: 0.8 };

function scoreAttachmentStyle(leans: AttachmentLean[]): AttachmentStyle {
  const secure = leans.filter((lean) => lean === "secure").length;
  const anxious = leans.filter((lean) => lean === "anxious").length;
  const avoidant = leans.filter((lean) => lean === "avoidant").length;
  if (secure >= 3) return "SECURE";
  if (anxious > 0 && avoidant > 0) return "DISORGANIZED";
  if (anxious > avoidant) return "ANXIOUS";
  if (avoidant > anxious) return "AVOIDANT";
  return "SECURE";
}

/**
 * `selections[i]` is the chosen option index for `QUESTIONS[i]` — every entry must be answered.
 */
export function computeLayer2Baseline(selections: readonly number[]): {
  attachmentStyle: AttachmentStyle;
  attachmentIntensity: number;
  communicationProfile: CommunicationProfile;
} {
  const leans: AttachmentLean[] = [];
  const frequencies = {} as Record<CommunicationDimension, Frequency>;
  let intensity = 0.5;

  QUESTIONS.forEach((question, i) => {
    const optionIndex = selections[i];
    if (question.kind === "attachment") {
      leans.push(question.options[optionIndex].lean);
    } else if (question.kind === "communication") {
      frequencies[question.dimension] = question.options[optionIndex].frequency;
    } else {
      intensity = question.options[optionIndex].value;
    }
  });

  return {
    attachmentStyle: scoreAttachmentStyle(leans),
    attachmentIntensity: intensity,
    communicationProfile: {
      criticism: FREQUENCY_WEIGHT[frequencies.criticism],
      contempt: FREQUENCY_WEIGHT[frequencies.contempt],
      defensiveness: FREQUENCY_WEIGHT[frequencies.defensiveness],
      stonewalling: FREQUENCY_WEIGHT[frequencies.stonewalling],
    },
  };
}
