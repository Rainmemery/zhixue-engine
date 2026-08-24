import { MainLayout } from "@/components/layout/main-layout";

export default function MainRouteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <MainLayout>{children}</MainLayout>;
}
