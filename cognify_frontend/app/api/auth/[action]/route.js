import { handleAuth } from "../../../../lib/auth-proxy.mjs";
export async function POST(request, context) {
  return handleAuth(request, (await context.params).action);
}
