# Swiffle — Onboarding, M3 Expressive Loading Screens & Zero-Lag Page Transitions Plan

Deliver a complete post-authentication **Interactive 3-Step Feature Tour + All Set Screen**, unified **Material 3 Expressive Morphing Shape Loading Screens** over the `#440E03` Maroon Cloth, and **60fps zero-lag Shared-Axis Z transitions with an anchored hero emblem** across the entire onboarding and workspace entry lifecycle.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices incorporate your confirmed preferences from our design alignment:

- **Confirmed Decision 1 (Post-Auth Onboarding Experience)**: **Interactive 3-Step Feature Tour + All Set Screen** — After signing in (via Email or Google) or choosing *Continue as guest*, users flow into an interactive 3-card showcase highlighting Swiffle's flagship capabilities (*AST Vector PDF Studio*, *Swiffle AI Copilot*, and *PDF-to-Excel & Office Suite*), culminating in a personalized **"You're all set"** launch stage.
- **Confirmed Decision 2 (Loading Screen Style)**: **Material 3 Expressive Morphing Shape Loader on `#440E03` Maroon Cloth** — Both the workspace initialization stage and document opening/closing overlays replace legacy blue/slate spinners with an organic M3 Expressive morphing polygon/droplet loader (squircle ↔ 8-sided star ↔ liquid pill) paired with mild-white glassmorphism and `ClaudeSerif` / `ClaudeSans` typography.
- **Confirmed Decision 3 (Page-to-Page Motion Architecture)**: **Shared-Axis Z Depth Scale + Anchored Hero Logo** — All stage transitions replace full-width horizontal slides with RenderThread-accelerated Shared-Axis Z depth scaling (`0.94f → 1.00f` incoming, `1.00f → 1.04f` outgoing) while keeping the `#440E03` Maroon Cloth texture and Swiffle Origami emblem anchored on a single shared layer for zero frame drops.

---

## 1. Overview & Core Concept

- **What It Does**: Connects the **Splash Screen**, **Get Started Gateway**, and **Login / Signup Studio** into a cohesive 6-stage onboarding and loading journey before seamlessly dissolving into the **Home Workspace**—and unifies all in-app loading overlays (*Workspace Preparation*, *Opening Document*, and *Closing Document*) under the same Material 3 Expressive Maroon design language.
- **Target Audience / Persona**: Mobile knowledge workers, students, and executives who expect tactile, lag-free 60/120fps motion, editorial typography, and immediate clarity about what the app can do.
- **Key Value**: Eliminates visual jarring (legacy blue spinners, redundant background redraws, and abrupt screen cuts) and replaces them with a single continuous `#440E03` Maroon Cloth stage where only lightweight `graphicsLayer` transforms (`scaleX`, `scaleY`, `alpha`, `translationY`) animate.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Stage 1 → Stage 2 (Splash → Get Started Gateway)**:
   - The app opens directly on the `#440E03` Maroon Cloth Splash Screen (zero stock system splash flash).
   - After `1.6s`, the centered `112.dp` Swiffle emblem and `ClaudeSerif` `"Swiffle"` wordmark stay pixel-anchored while the subtitle, top glassmorphic language pill, and bottom mild-white glassmorphic action stack (*Get started* and *Continue as guest*) elevate into view via Shared-Axis Z (`0.94f → 1.0f`).
2. **Stage 2 → Stage 3 (Get Started → Login / Signup Studio)**:
   - Tapping **Get started** smoothly transitions the hero emblem to its compact top header anchor (`48.dp`) while the glassmorphic credential card scales forward along the Z-axis (`0.94f → 1.0f`).
   - Switching between **Sign in** and **Create account** glides the mild-white frosted glass pill thumb across the segmented track with spatial spring physics (`stiffness = 600f, damping = 0.75f`).
3. **Stage 3 → Stage 4 (Auth / Guest → Interactive 3-Step Feature Tour)**:
   - Completing authentication (or tapping *Continue as guest*) transitions along Shared-Axis Z into the **3-Step Interactive Feature Tour** on the `#440E03` Maroon Cloth:
     - **Step 1 — True Vector PDF Studio**: Highlights Chameleon AST text reflow, PaddleOCR scanning, and precision ink annotations with an interactive vector preview illustration.
     - **Step 2 — Swiffle AI Copilot**: Showcases instant document Q&A, smart summarization, and voice-guided analysis.
     - **Step 3 — Precision Excel & Word Suite**: Demonstrates 1-tap PDF/Image-to-Excel table reconstruction and sticky-grid spreadsheet editing.
   - Users can swipe or tap the mild-white glassmorphic **Next** pill (or **Skip** in the top-right corner) while morphing pill step indicators expand and contract smoothly.
4. **Stage 4 → Stage 5 (Feature Tour → Workspace Loading & "You're All Set" Screen)**:
   - Advancing past Step 3 enters the **Workspace Preparation & All Set Stage**:
     - First displays the **M3 Expressive Morphing Shape Loader** (fluidly morphing between a rounded squircle, an 8-lobe expressive flower, and a silk-ivory origami badge) with live status captions (*"Indexing local workspace..."*, *"Calibrating AI & OCR engines..."*).
     - Settles into the **"You're all set"** celebration state with an animated silk-ivory checkmark badge, personalized welcome headline in `ClaudeSerif`, and a mild-white glassmorphic **"Enter workspace"** primary pill button.
5. **Stage 5 → Stage 6 (All Set → Home Workspace)**:
   - Tapping **Enter workspace** triggers a top-level Shared-Axis Z scale-dissolve (`0.96f → 1.0f`) as the `#440E03` Maroon Cloth gently fades out to reveal the Home Workspace.

### Visual Identity & Theme

- **Aesthetic Direction**: Material 3 Expressive Editorial Sanctuary — combining tactile `#440E03` woven maroon cloth depth with luminous mild-white frosted glass controls and zero-overshoot opacity transitions.
- **Color Palette & Mood**:
  - **Canvas**: `#440E03` Deep Woven Maroon Cloth (`MaroonClothPrimary`) with `#380B02` warp/weft micro-grain and `#5E1506` subtle top radial sheen.
  - **Glassmorphic Controls**: Mild-white translucent linear gradients (`rgba(255, 255, 255, 0.27)` → `0.12` for primary pills; `0.14` → `0.06` for secondary pills and cards) with specular `1.dp–1.2.dp` borders (`rgba(255, 255, 255, 0.56)` → `0.16`).
  - **Typography & Content Tokens**: Silk-Ivory `#FFF8F6` (`MaroonIvoryText`) for headlines, active labels, and icons; Warm Blush `#EBD0CE` (`MaroonBlushSecondary`) for body copy and helper subtitles; Rose Accent `#D9807F` (`MaroonAccentRose`) for expressive highlights.
- **Typography & Hierarchy**:
  - **Display & Headlines (`ClaudeSerif`)**: Used for the `"Swiffle"` wordmark (`42.sp`), Auth titles (`34.sp`), Feature Tour step titles (`30.sp`), and the `"You're all set"` completion headline (`34.sp`).
  - **Body, Controls & Captions (`ClaudeSans`)**: Used for feature descriptions (`16.sp / 24.sp`), mild-white glass button labels (`16.sp SemiBold`), step badges (`13.sp Medium`), and loading telemetry (`14.sp`).

### Interactive Feedback & Motion

- **Dual-Domain Spring Physics (Guidelines Section 2.7)**:
  - **Spatial Fast (`stiffness = 800f, damping = 0.65f`)**: Button press morphing, segmented slider pill movement, and onboarding step dot expansion (`8.dp ↔ 28.dp`).
  - **Spatial Default (`stiffness = 380f, damping = 0.80f`)**: Shared-Axis Z scale transforms (`0.94f → 1.00f`) and feature tour card transitions.
  - **Effects Fast (`stiffness = 1600f, damping = 1.0f` / `DampingRatioNoBouncy`)**: Opacity crossfades and glass border luminance shifts with strictly zero overshoot.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Single Shared `#440E03` Cloth Layer vs. Per-Screen Backgrounds**
  - *Chosen Approach*: Render `SwiffleAuroraBackground` **once** at the root container level while all child stages (`Splash`, `GetStarted`, `Login/Signup`, `FeatureTour`, `AllSetLoading`) draw over `Color.Transparent`.
  - *Why*: Previously, nested screens each instantiated their own `SwiffleAuroraBackground`, causing redundant full-screen shader draws during page transitions. A single persistent background eliminates GPU overdraw and keeps the woven cloth texture completely stationary while UI layers glide in Z-space at 60/120fps.
  - *Alternatives Considered*: Animating background bitmaps inside each screen's `AnimatedContent` scope, which causes visible seam flashing and frame-time spikes.

- **Decision 2: Shared-Axis Z + GPU `graphicsLayer` Lambda Modifiers vs. Layout-Driven Slides**
  - *Chosen Approach*: Perform all page-to-page and step-to-step animations using `SwiffleMotion.sharedAxisZForward()` / `sharedAxisZBackward()` and `Modifier.graphicsLayer { ... }` lambda reads.
  - *Why*: Reading animated states inside `graphicsLayer` executes directly on the Android `RenderThread` without triggering Compose layout measurement or recomposition, guaranteeing zero jank even on mid-range hardware.
  - *Alternatives Considered*: Full-width horizontal offset animations (`100%` width slide), which feel disconnected from Material 3 Expressive depth hierarchy and cause heavy text rasterization churn.

- **Decision 3: Unified M3 Expressive Morphing Shape Loader Across Onboarding & Document Viewers**
  - *Chosen Approach*: Upgrade the full-screen loading overlay and document open/close pills (`PdfOpeningPill`, `PdfClosingPill`, `StockMaterialLogoLoadingScreen`) to use the M3 Expressive morphing polygon/droplet indicator with `#440E03` Maroon / Mild-White Glassmorphism and `ClaudeSerif` / `ClaudeSans`.
  - *Why*: Ensures visual continuity from the first launch all the way into opening PDFs, Excel spreadsheets, and Word documents.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   Root Stage Host (Single Shared Canvas)                   │
│  • Persistent #440E03 Maroon Cloth Texture (BitmapShader TileMode.REPEAT)  │
│  • RenderThread graphicsLayer Shared-Axis Z Transition Engine (60/120fps)  │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        ▼                             ▼                             ▼
┌───────────────────┐       ┌───────────────────┐       ┌───────────────────┐
│  Stage 1: Splash  │──────▶│ Stage 2: Gateway  │──────▶│ Stage 3: Auth     │
│  • 112dp Emblem   │       │ • Shared 112dp    │       │ • 48dp Top Emblem │
│  • ClaudeSerif    │       │   Emblem Anchor   │       │ • Glass Slider    │
│  • Glass Loader   │       │ • Glass Pill Menu │       │ • Email & Google  │
└───────────────────┘       └─────────┬─────────┘       └─────────┬─────────┘
                                      │ (Guest)                   │ (Signed In)
                                      └─────────────┬─────────────┘
                                                    ▼
                                    ┌───────────────────────────────┐
                                    │ Stage 4: 3-Step Feature Tour  │
                                    │ • Step 1: Vector PDF Studio   │
                                    │ • Step 2: Swiffle AI Copilot  │
                                    │ • Step 3: Excel & Word Suite  │
                                    │ • Morphing Glass Step Dots    │
                                    └───────────────┬───────────────┘
                                                    ▼
                                    ┌───────────────────────────────┐
                                    │ Stage 5: Expressive Loading   │
                                    │          & "All Set" Screen   │
                                    │ • M3 Morphing Shape Loader    │
                                    │ • Live Engine Status Steps    │
                                    │ • "You're all set" Launch CTA │
                                    └───────────────┬───────────────┘
                                                    ▼
                                    ┌───────────────────────────────┐
                                    │ Stage 6: Home Workspace       │
                                    │ • Seamless Cloth Dissolve     │
                                    │ • Unified Document Load Pills │
                                    └───────────────────────────────┘
```

### Data Model & State

- **`OnboardingStage` State Machine**:
  - `GATEWAY` (Get Started page with Language Selector, Hero Emblem, *Get started*, and *Continue as guest*)
  - `CREDENTIALS` (Login / Signup studio with glassmorphic segmented slider, email/password validation, and Google Sign-In)
  - `FEATURE_TOUR` (Interactive 3-step showcase with `currentStep: Int` in `0..2`, interactive vector art cards, *Skip*, *Back*, and *Next* glassmorphic controls)
  - `WORKSPACE_READY` (Two-phase completion screen: `isPreparingWorkspace = true` showing the M3 Expressive Morphing Shape Loader for `1.2s` with step telemetry, automatically transitioning into `isPreparingWorkspace = false` for the *"You're all set"* confirmation and *Enter workspace* CTA)
- **Persistence Keys**:
  - Persists `hasCompletedOnboarding_v2_maroon = true`, `is_user_logged_in = true`, and user profile metadata only when the user completes or skips into the workspace, ensuring back navigation works naturally across all onboarding stages.

### Interactive Component & State Mapping

- **Shared-Axis Z Transition Controller**:
  - Forward navigation (`stageIndex` increasing): Incoming stage scales `0.94f → 1.00f` (`SpringSpatial: stiffness 380f, damping 0.80f`) + fades in (`0f → 1f`, `220ms` `EmphasizedDecelerate`); outgoing stage scales `1.00f → 1.04f` + fades out (`1f → 0f`, `150ms` `EmphasizedAccelerate`, zero ghosting).
  - Backward navigation (System Back or top-left Back button): Incoming stage scales `1.04f → 1.00f` + fades in; outgoing stage recedes `1.00f → 0.94f` + fades out.
- **Anchored Hero Emblem Coordinator**:
  - Between `Splash` and `GATEWAY`, the `112.dp` Swiffle emblem and `42.sp` `ClaudeSerif` wordmark share identical vertical center coordinates so the transition is completely seamless with zero logo jump.
- **M3 Expressive Morphing Shape Loader**:
  - Continuously interpolates a Polar-coordinate Canvas path (`r(θ)`) between a soft rounded squircle, an 8-lobe expressive flower, and a dynamic liquid droplet arc at 60fps, rendered with a Silk-Ivory & Rose-Maroon gradient stroke and centered Swiffle emblem.
