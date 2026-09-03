import { proxyEngine } from "@/app/api/_lib/engine-proxy";

export async function GET() {
  return proxyEngine("/api/v1/markov-graph");
}
