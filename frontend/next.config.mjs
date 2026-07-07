/** @type {import('next').NextConfig} */
const nextConfig = {
  // Standalone output keeps the Docker image small.
  output: "standalone",
  reactStrictMode: true,
};

export default nextConfig;
