import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/* ------------------------------------------------------------------ */
/*  Petits composants réutilisables pour l'espace Admin.               */
/*  Charte : mono, teal #0d7377, cartes blanches bord #e8eded.         */
/* ------------------------------------------------------------------ */

export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div>
      <h1 className="text-[30px] font-extrabold tracking-[-0.5px]">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-[#6b7c7c]">{subtitle}</p>}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-[14px] border border-[#e8eded] bg-white p-5 ${className}`}>
      {children}
    </div>
  );
}

export function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  dir,
}: {
  label: string;
  value: string;
  icon: LucideIcon;
  hint?: string;
  dir?: "up" | "down";
}) {
  const hintColor =
    dir === "up" ? "text-[#0d7377]" : dir === "down" ? "text-[#b91c1c]" : "text-[#94a3a3]";
  return (
    <Card>
      <div className="flex items-center justify-between">
        <span className="text-[13px] text-[#6b7c7c]">{label}</span>
        <span className="flex h-8 w-8 items-center justify-center rounded-[9px] bg-[#e6f2f2] text-[#0d7377]">
          <Icon className="h-[17px] w-[17px]" />
        </span>
      </div>
      <div className="mt-3.5 text-[30px] font-extrabold tracking-[-1px]">{value}</div>
      {hint && <div className={`mt-0.5 text-[11px] ${hintColor}`}>{hint}</div>}
    </Card>
  );
}

type BadgeTone = "green" | "amber" | "red" | "blue" | "purple" | "gray";
const TONES: Record<BadgeTone, string> = {
  green: "bg-[#e6f2f2] text-[#0d7377]",
  amber: "bg-[#fef3e2] text-[#b45309]",
  red: "bg-[#fde8e8] text-[#b91c1c]",
  blue: "bg-[#e6eff5] text-[#1e5b8f]",
  purple: "bg-[#efe9fc] text-[#6d28d9]",
  gray: "bg-[#eef2f2] text-[#6b7c7c]",
};

export function Badge({
  tone = "gray",
  children,
  dot = false,
}: {
  tone?: BadgeTone;
  children: ReactNode;
  dot?: boolean;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${TONES[tone]}`}
    >
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}

export function CardHead({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="mb-1 flex items-center justify-between">
      <div className="text-base font-bold">{title}</div>
      {action}
    </div>
  );
}

export function Avatar({ name }: { name: string }) {
  const initials = name
    .split(" ")
    .map((w) => w[0])
    .join("")
    .slice(0, 2);
  return (
    <div className="flex h-[34px] w-[34px] items-center justify-center rounded-lg bg-[#e6f2f2] text-xs font-bold text-[#0a5a5d]">
      {initials}
    </div>
  );
}

export function IconButton({ icon: Icon, onClick }: { icon: LucideIcon; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-lg border border-[#dbe4e4] bg-white text-[#6b7c7c] transition-colors hover:bg-[#f0f7f7] hover:text-[#0d7377]"
    >
      <Icon className="h-[15px] w-[15px]" />
    </button>
  );
}
