import type { NextConfig } from "next";

const isTauriBuild = process.env.TAURI_BUILD === "true";
const isProdDeploy = process.env.PROD_DEPLOY === "true";

const nextConfig: NextConfig = {
  output: isTauriBuild ? "export" : isProdDeploy ? "standalone" : undefined,
  distDir: isTauriBuild ? "out" : ".next",

  images: {
    unoptimized: true,
  },

  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1',
  },

  reactStrictMode: true,

  experimental: {},
};

export default nextConfig;
