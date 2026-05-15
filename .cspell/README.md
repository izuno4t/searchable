# cspell Dictionary

Project-specific spelling dictionary. The main `cspell.json` at the repository
root references the term list in this folder.

Add a new term to `searchable-terms.txt` (one per line, alphabetical within a
section). Run locally with:

```bash
npx -y cspell --no-progress --no-summary "**/*.{md,java,xml,yml,yaml,json}"
```

CI runs cspell as part of the documentation lint job.
