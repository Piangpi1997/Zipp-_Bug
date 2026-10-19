# LIKEFIGMA Studio Schema

A screen is an ordered JSON array of nodes.

Current node fields:
- id
- type
- label
- x
- y
- color
- radius

The schema is provider-independent. Planned generator targets:
- Android XML
- Jetpack Compose
- HTML/CSS

The visual studio is intended to become the source of truth for generated UI rather than a screenshot-cloning feature.
