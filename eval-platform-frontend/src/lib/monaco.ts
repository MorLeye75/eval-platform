/**
 * Éditeur de code livré avec l'application. Sans cette configuration, @monaco-editor/react télécharge Monaco depuis
 * un CDN (jsDelivr) au moment de l'épreuve : si le réseau du campus le bloque ou le ralentit, l'éditeur ne s'affiche
 * pas pendant l'examen. Importé par SaisieCode seulement : Monaco n'est chargé qu'avec l'écran d'épreuve.
 */
import { loader } from "@monaco-editor/react"
import * as monaco from "monaco-editor"
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker"
import TsWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker"

self.MonacoEnvironment = {
  // JavaScript utilise le service de langage TypeScript ; C, C++, Java et Python, le worker de base
  getWorker(_id: string, label: string) {
    return label === "typescript" || label === "javascript" ? new TsWorker() : new EditorWorker()
  },
}

loader.config({ monaco })
