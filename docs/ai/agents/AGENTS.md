# AGENTS.md — HA Battery Widget

Android home-screen widget (Jetpack Glance) plus a settings screen that show a JK-BMS battery read from Home Assistant through `POST /api/template` (Admin token) or `GET /api/states[/<id>]` (User token).

Read all topic files below if you're new to this project.

Read `docs/ai/agents/architecture.md` [~97 lines] before changing or adding code
Read `docs/ai/agents/commands.md` [~41 lines] before building, testing or using the emulator
Read `docs/ai/agents/security.md` [~18 lines] when touching the HA token, signing or secrets
Read `docs/ai/agents/workflow.md` [~17 lines] before designing widget, sync or test code, or starting a branch
