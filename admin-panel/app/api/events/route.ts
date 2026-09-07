import { proxySystem } from "@/app/api/_lib/backend-proxy";

export async function GET(request: Request) {
  const since = new URL(request.url).searchParams.get("since") ?? "0";
  return proxySystem(`/api/events?since=${encodeURIComponent(since)}`);
}
