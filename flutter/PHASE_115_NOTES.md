# Phase 115 — team surfaces: coverage correction and first tests

Lane C. In progress. See the final section for defects found.

## Coverage measurement (correction to the brief)

The brief said `lib/ui/teams/teams_screen.dart` "is referenced by exactly one
test file — the thinnest coverage-to-size ratio left in the port". Measuring by
symbol reference rather than filename gives a different answer: **two team files
are referenced by no test at all**, and the one file that references
`teams_screen.dart` covers only one of its two screens.

Details to follow.
