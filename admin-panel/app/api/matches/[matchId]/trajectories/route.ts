import { proxyEngine } from "@/app/api/_lib/engine-proxy";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ matchId: string }> },
) {
  const { matchId } = await params;
  return proxyEngine(`/api/v1/matches/${matchId}/trajectories`);
}
