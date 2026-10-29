#!/usr/bin/env bash
# Legt den Inhalt eines Ordners als einzelnen Commit auf einen Branch (ohne Verlauf, wird jedes Mal ersetzt).
# Übernommen aus Mental Math Plus. Lesen: git fetch origin <branch> && git show origin/<branch>:<datei>
publish_branch() {
  local branch="$1" src="$2" msg="$3"
  if [ -z "${GH_TOKEN:-}" ] || [ -z "${GITHUB_REPOSITORY:-}" ]; then
    echo "Kein Token – nichts hochgeladen."
    return 0
  fi
  local tmp
  tmp="$(mktemp -d)"
  cp -r "$src"/. "$tmp"/
  (
    cd "$tmp" &&
    git init -q &&
    git checkout -q -b "$branch" &&
    git add -A &&
    git -c user.name="ci" -c user.email="ci@users.noreply.github.com" commit -q -m "$msg" &&
    git push -q --force "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$branch"
  ) || echo "Hochladen auf ${branch} fehlgeschlagen."
}
