"use client";

import { useEffect, useRef, useState } from "react";

type UserSummary = {
  id: string;
  city: string | null;
  profession: string | null;
};

type RecentEvent = {
  sequence: number;
  type: string;
  matchId: string;
  userA: UserSummary;
  userB: UserSummary;
  expiryDurationSeconds: number | null;
  occurredAt: string;
};

const POLL_INTERVAL_MS = 2500;
const MAX_VISIBLE = 50;

// The domain has no name field at all — just enough Layer 1 (city/profession) to make an id
// readable, snapshotted server-side at the moment the event fired (see UserSummary on the Java
// side). Falls back to the bare id if the user was deleted since.
function describeUser(user: UserSummary): string {
  const short = user.id.slice(0, 8);
  if (!user.city && !user.profession) return short;
  return `${short} (${[user.city, user.profession].filter(Boolean).join(", ")})`;
}

function formatDuration(seconds: number): string {
  const hours = seconds / 3600;
  if (hours < 24) return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}h`;
  return `${(hours / 24).toFixed(1)}d`;
}

function describe(event: RecentEvent): string {
  const a = describeUser(event.userA);
  const b = describeUser(event.userB);
  if (event.type === "MatchAssignedEvent") {
    const duration =
      event.expiryDurationSeconds !== null ? formatDuration(event.expiryDurationSeconds) : "?";
    return `El Sistema asignó un match entre ${a} y ${b} — vence en ${duration}`;
  }
  if (event.type === "MatchExpiredEvent") {
    return `El match entre ${a} y ${b} expiró`;
  }
  return event.type;
}

export function LiveFeed() {
  const [events, setEvents] = useState<RecentEvent[]>([]);
  const [connected, setConnected] = useState(true);
  // A ref, not state: the poll loop reads it every tick and must see the latest value without
  // re-subscribing the interval on every event received.
  const lastSeen = useRef(0);
  // Guards against a slow request still in flight when the next 2.5s tick fires — without it,
  // two overlapping polls can both see the same `since` and each append the same events.
  const isPolling = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      if (isPolling.current) return;
      isPolling.current = true;
      try {
        const res = await fetch(`/api/events?since=${lastSeen.current}`, { cache: "no-store" });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const incoming = (await res.json()) as RecentEvent[];
        if (cancelled) return;
        setConnected(true);
        if (incoming.length === 0) return;
        lastSeen.current = incoming[incoming.length - 1].sequence;
        setEvents((prev) => [...incoming].reverse().concat(prev).slice(0, MAX_VISIBLE));
      } catch {
        if (!cancelled) setConnected(false);
      } finally {
        isPolling.current = false;
      }
    }

    poll();
    const id = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  return (
    <div>
      <p className="lede">
        {connected
          ? `Consultando cada ${POLL_INTERVAL_MS / 1000}s.`
          : "El Sistema no responde — reintentando…"}
      </p>
      {events.length === 0 && (
        <p className="lede">
          Sin eventos todavía. Asigná un match manualmente o esperá a que uno expire para ver algo
          acá.
        </p>
      )}
      <ul className="event-feed">
        {events.map((e) => (
          <li
            key={e.sequence}
            className={`event ${e.type === "MatchExpiredEvent" ? "expired" : "assigned"}`}
          >
            <span className="event-time">{new Date(e.occurredAt).toLocaleTimeString("es")}</span>
            <span className="event-text">{describe(e)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
