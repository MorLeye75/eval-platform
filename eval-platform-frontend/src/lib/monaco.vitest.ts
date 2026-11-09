/**
 * Remplace src/lib/monaco.ts dans les tests Vitest (alias de vite.config.ts) : le vrai Monaco ne fonctionne pas dans
 * jsdom, et les tests simulent déjà l'éditeur (@monaco-editor/react). Les tests de bout en bout (Playwright), eux,
 * utilisent le vrai.
 */
export {}
