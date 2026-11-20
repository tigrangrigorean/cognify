export default {
  async rewrites() {
    // Local dynamic auth handlers must run before the backend catch-all proxy.
    return {
      fallback: [
        {
          source: "/api/:path*",
          destination: `${process.env.BACKEND_URL || "http://localhost:8080"}/api/:path*`,
        },
      ],
    };
  },
};
