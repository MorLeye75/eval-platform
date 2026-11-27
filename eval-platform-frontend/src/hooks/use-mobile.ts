import * as React from "react"

const MOBILE_BREAKPOINT = 768

function largeurMobile() {
  return typeof window !== "undefined" && window.innerWidth < MOBILE_BREAKPOINT
}

/**
 * Écran de téléphone (moins de 768 px). Juste dès le premier rendu (pas d'éclair de la version ordinateur), puis
 * suivi des changements de taille. Sans matchMedia (jsdom des tests), le redimensionnement de la fenêtre suffit.
 */
export function useIsMobile() {
  const [isMobile, setIsMobile] = React.useState<boolean>(largeurMobile)

  React.useEffect(() => {
    const onChange = () => setIsMobile(largeurMobile())
    onChange()
    if (typeof window.matchMedia !== "function") {
      window.addEventListener("resize", onChange)
      return () => window.removeEventListener("resize", onChange)
    }
    const mql = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`)
    mql.addEventListener("change", onChange)
    return () => mql.removeEventListener("change", onChange)
  }, [])

  return isMobile
}
