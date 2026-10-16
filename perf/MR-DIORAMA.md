# perf/MR-DIORAMA.md: is diorama mode cheaper than normal play?

Date 2026-10-16. Notes for the mixed-reality exploration ("QuestCraft Mixed-Reality Layer — Agent
Feasibility Brief", branch `claudecraft-mixed-reality-exploration` at `integration/mihail.4`).
**Reasoning only.** No code was read for this note and nothing was measured. Treat every claim as a
hypothesis for the analysis to confirm.

## Priorities (user, 2026-10-16)

- **Higher:** rendering the diorama.
- **Lowest:** dressing the real room as if it were made of Minecraft blocks (brief item 4, "Block-painted
  room").
- **User hypothesis:** stop the first-person render entirely while in diorama mode and render only what
  the server sends. That should be cheaper than normal play, but the user is not confident.

## Answer: cheaper only below a certain radius

**What sets the cost.** Our frames are limited by the CPU and the number of draw calls (CLAUDE.md).
Cost ≈ chunk sections drawn × 2 eyes. LTW turns each of Sodium's multi-draws into single draws, so each
section is roughly one draw call per eye. A smaller world scale makes fragments cheaper for the GPU, but
leaves the draw count unchanged.

**What diorama mode removes:**
- the first-person world pass in both eyes;
- sky, fog, clouds, weather, particles, hand and HUD (passthrough replaces the sky);
- entities outside the diorama radius.

**What it adds or loses:**
- **Culling is much weaker.** In first person, the frustum and Sodium's cave culling skip most loaded
  sections: everything behind you, beside you and underground. Looking down at a table, the whole disc is
  in view. Sodium's occlusion search is built for a camera inside the world, so it should skip far less from
  above (to verify).
- **Section count.** A radius-r diorama shows about πr² chunk columns, nearly all visible.
  - First person at render distance 6 loads 13² = 169 columns but draws only a fraction of them.
  - Radius 3–4 (≈30–50 columns): probably cheaper than normal play.
  - Radius 6+ (≈113+ columns): probably more expensive.
  - Floor scale ("giant") is more expensive still.
- **Still two eyes.** Stereo needs both views.
  - `sharedEyeCulling` still applies.
  - At small scale the eyes are far apart in world units: 63 mm IPD at 1:50 is ≈3 m. The shared frustum's
    fallback ("eyes in different sections") will trigger more often.

## "Render what the server sends" needs care

- **The server sends block data, not meshes.** Sodium meshes chunks on the Quest.
- **Chunks already loaded around the character** already have meshes. A diorama of those is a second draw
  of existing meshes with a scaled, moved camera: no re-meshing and no extra RAM.
- **Panning beyond the loaded area** costs meshing CPU on the Quest plus memory for every extra chunk held.
  That runs into the ≈6.3 GB kill limit (brief Q6).

## A separate and bigger win

The planned topology (Minecraft server on the Mac, Quest as a LAN client) takes the integrated server out
of the Quest process. That removes its CPU threads and a large share of the heap in **every** mode, not
only in diorama mode. Don't credit that saving to the diorama when measuring.

## How to answer it with data, before the headset

Draw-call and section counts do not depend on the device, so they can be measured on the desktop with the
`nullvr` provider (`client_vr/provider/nullvr/`).
1. Add a "diorama camera" toggle: camera above the character looking down, scaled view matrix, fog and
   sky off.
2. Log Sodium's visible section count and the draw calls per eye, in first person and in diorama mode at
   radius 2, 4 and 6.
3. The radius where the diorama matches first person is the crossover. Only then check frame time on the
   headset.

## Note for the rerun

The brief still names `integration/mihail.1` and the Vivecraft 1.2.5 base. The exploration branch is
`integration/mihail.4` on Vivecraft 1.3.15, and the brief should be updated to match.
