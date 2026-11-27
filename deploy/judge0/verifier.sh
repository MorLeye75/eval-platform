#!/usr/bin/env bash
# Vérifie qu'un Judge0 démarré avec ce dossier exécute vraiment du code (DS-12).
# Usage : JUDGE0_TOKEN=<jeton> ./verifier.sh [http://localhost:2358]
# Contrôle : la liste des langages, puis un programme Python qui lit l'entrée et doit répondre « true ».
set -euo pipefail

URL="${1:-http://localhost:2358}"
JETON="${JUDGE0_TOKEN:?définir JUDGE0_TOKEN (la valeur de AUTHN_TOKEN de judge0.conf)}"
b64() { printf '%s' "$1" | base64 | tr -d '\n'; }

echo "1. Langages attendus par la plateforme (c 50, cpp 54, java 62, javascript 63, python 71) :"
curl -fsS -H "X-Auth-Token: $JETON" "$URL/languages" \
  | grep -oE '"id":(50|54|62|63|71),"name":"[^"]*"' || { echo "   ÉCHEC : langages introuvables"; exit 1; }

echo "2. Exécution d'un palindrome en Python (entrée « radar », sortie attendue « true ») :"
CODE=$(b64 $'s = input()\nprint(\'true\' if s == s[::-1] else \'false\')')
JETON_SOUMISSION=$(curl -fsS -H "X-Auth-Token: $JETON" -H 'Content-Type: application/json' \
  -d "{\"language_id\":71,\"source_code\":\"$CODE\",\"stdin\":\"$(b64 radar)\",\"expected_output\":\"$(b64 true)\",\"enable_network\":false}" \
  "$URL/submissions?base64_encoded=true" | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)

for _ in $(seq 1 30); do
  sleep 1
  REPONSE=$(curl -fsS -H "X-Auth-Token: $JETON" "$URL/submissions/$JETON_SOUMISSION?base64_encoded=true&fields=status")
  STATUT=$(printf '%s' "$REPONSE" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
  [ "$STATUT" = "1" ] || [ "$STATUT" = "2" ] || break
done

case "$STATUT" in
  3)  echo "   OK : statut 3 « Accepted ». Judge0 est prêt pour la plateforme." ;;
  13) echo "   ÉCHEC : statut 13 « Internal Error ». Le bac à sable ne démarre pas : hôte en cgroups v2 ? (voir README.md)"; exit 1 ;;
  *)  echo "   ÉCHEC : statut $STATUT ($REPONSE)"; exit 1 ;;
esac
