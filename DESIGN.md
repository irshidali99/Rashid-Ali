---
name: App Cloner Design System
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#c7c4d7'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#908fa0'
  outline-variant: '#464554'
  surface-tint: '#c0c1ff'
  primary: '#c0c1ff'
  on-primary: '#1000a9'
  primary-container: '#8083ff'
  on-primary-container: '#0d0096'
  inverse-primary: '#494bd6'
  secondary: '#c3c0ff'
  on-secondary: '#1d00a5'
  secondary-container: '#3626ce'
  on-secondary-container: '#b3b1ff'
  tertiary: '#4edea3'
  on-tertiary: '#003824'
  tertiary-container: '#00885d'
  on-tertiary-container: '#000703'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#e2dfff'
  secondary-fixed-dim: '#c3c0ff'
  on-secondary-fixed: '#0f0069'
  on-secondary-fixed-variant: '#3323cc'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#2d3449'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  title-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.15px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.4px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  gutter-tablet: 1.25rem
  margin: 1rem
  margin-mobile: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers a high-utility, precision-engineered Android experience built on modern Material Design 3 principles. It caters to power users, system administrators, and privacy-conscious individuals who require robust multi-instance application management, sandbox configuration, and parallel environment orchestration. 

The aesthetic is clean, technical, and dependable, evoking immediate trust and technical authority. It moves away from overly playful consumer interfaces in favor of structured density, tactile micro-feedback, and legible system status indicators.

Key aesthetic anchors include:
- **Utility-First Architecture:** Clear information hierarchy prioritizing toggles, process metrics, clone instance statuses, and security boundaries.
- **Tonal Depth:** Layered slate containers that replace pure black voids with rich, dimensional dark mode structures.
- **Material 3 Expressiveness:** Tactile rounded corners (16px to 24px), dynamic pill-shaped status pills, and authentic Android system chrome (edge-to-edge transparent navigation, floating bottom app bars, and adaptive contextual actions).

## Colors

The palette balances deep system slate backgrounds with electric indigo illumination. It adheres to an authentic system-level utility aesthetic while ensuring strict WCAG 2.1 AAA contrast for critical operational indicators.

### Key Roles
- **Primary (`#6366F1` / Electric Indigo):** The focal interactive accent, used for high-emphasis call-to-actions, active clone toggles, bottom navigation selection pills, and floating action triggers.
- **Secondary (`#4F46E5` / Deep Indigo):** Used for elevated interactive states, active container borders, progress tracking fills, and focused state rings.
- **Tertiary (`#10B981` / Emerald):** Operational affirmative role. Indicates healthy sandbox virtualization, successful clone compilation, synchronized storage, and online container status.
- **Neutral (`#0F172A` / Dark Slate):** The root background canvas for dark mode, producing lower eye strain and higher perceived contrast than raw `#000000`.

### State & Container Tokens
- **Surface Crisp / Elevated (`#1E293B`):** System cards, list items, dynamic drawer sheets, and modal bottom sheets.
- **Surface Container High (`#334155`):** Chip outlines, inactive switch tracks, divider rules, and hover/pressed overlays.
- **Warning Accent (`#F59E0B` / Amber):** Memory pressure, sandboxed permission requests, and storage warnings.
- **Danger Accent (`#EF4444` / Rose):** Broken instance hooks, force-kill triggers, cache eviction, and clone deletion actions.
- **Text Primary (`#F8FAFC`):** Crisp high-contrast surface text for primary labels and headers.
- **Text Secondary (`#94A3B8`):** Subtitles, meta-tags, instance timestamps, and bundle IDs.

## Typography

The typographic hierarchy uses `Roboto Flex` for prominent structural headlines and `Inter` for system body copy, technical data, and UI labels. 

- **Roboto Flex:** Brings native Android mechanical discipline and adjustable variable-width legibility to top-level app bars, dashboard summaries, and instance counts.
- **Inter:** Ensures extreme legibility for monospace-adjacent system metadata (package names like `com.whatsapp.clone2`, storage sizes, sandbox memory allocations).
- **Line Heights & Tracking:** Tight negative tracking on display headlines delivers a compact, purposeful punch. Labels use wider tracking (`0.5px`) for legibility within small rounded pill badges, status tags, and navigation icons.

## Layout & Spacing

The layout model is driven by an 8pt structural grid tailored to native Android device viewports, with a strict 4pt baseline sub-grid for icons and inline chips.

### Grid & Density
- **Mobile Handsets (<600dp):** Single-column fluid view with `1rem` outer canvas margins and `0.75rem` vertical separation between instance cards.
- **Foldables & Tablets (600dp - 840dp):** Dual-pane or 2-column masonry grid. The primary navigation converts from a bottom bar to an adaptive side navigation rail.
- **Large Tablet & Desktop (>840dp):** 3-column system with persistent master-detail drawer, `2rem` canvas margins, and `1.25rem` card gutters.

### Edge-to-Edge System Rules
Content scrolls beneath translucent system status bars and gesture navigation bars. Bottom content containers must reserve standard Android insets (`navigationBarInsets + space-xl`) to prevent FAB or navigation overlap.

## Elevation & Depth

Depth in this system avoids harsh drop shadows. Instead, it relies on Material Design 3 surface tinting coupled with diffused, low-opacity ambient shadows tinted with the primary hue.

### Elevation Hierarchy
- **Level 0 (Surface Base - `#0F172A`):** The canvas floor. Flat, no drop shadow.
- **Level 1 (Card Container - `#1E293B`):** Standard app cards and list rows. Ambient shadow: `0 2px 8px -2px rgba(15, 23, 42, 0.45)`.
- **Level 2 (Elevated Card / Dropdowns - `#27354A`):** Active or swiped items, filter sheets. Ambient shadow: `0 4px 16px -4px rgba(15, 23, 42, 0.6)`.
- **Level 3 (Modal Bottom Sheets / FAB - `#334155`):** Floating action buttons and floating modals. Casts an indigo-tinted glow: `0 8px 24px -4px rgba(99, 102, 241, 0.25)`.
- **Level 4 (System Overlays / Dialogs):** High-priority alerts and confirmation dialogs centered over a 60% scrim (`#0F172A` at 0.6 opacity).

## Shapes

The design system adopts a modern 16px to 24px corner radius language, adhering to Material 3's expressive rounded form factors.

- **Primary Cards & Virtualized Containers:** `rounded-lg` (16px / 1rem) creates a friendly yet firm structural module.
- **Bottom Sheets & Floating Panels:** `rounded-xl` (24px / 1.5rem) on top-leading and top-trailing corners.
- **Action Buttons & Micro Chips:** Complete pill contours (`9999px`) for quick-scan status labels, category filters, and quick toggles.
- **Input Fields:** `rounded-lg` (16px / 1rem) to match card geometry.

## Components

### Buttons & FAB
- **Filled Primary Button:** Height 48px, fully rounded pill (`9999px`). Background `#6366F1`, label text `#FFFFFF` (Inter Label-LG). On press, state layer applies 12% `#FFFFFF` overlay.
- **Tonal Button:** Height 48px, pill shape. Background `#1E293B`, border `1px solid #334155`, text `#F8FAFC`.
- **Floating Action Button (FAB):** 56x56px container with `16px` rounded corners (Material 3 squircle-pill). Background `#6366F1`, icon `#FFFFFF` (24px). Positioned `16px` above the bottom navigation bar.

### Chips & Pill Badges
- **Status Pills:** Height 24px, pill-shaped (`9999px`), internal padding `0 10px`. 
  - *Active Clone:* Background `rgba(16, 185, 129, 0.15)`, text `#10B981` (Emerald).
  - *Sandbox Standby:* Background `rgba(99, 102, 241, 0.15)`, text `#6366F1` (Indigo).
  - *Storage Alert:* Background `rgba(245, 158, 11, 0.15)`, text `#F59E0B` (Amber).
- **Filter Chips:** 32px height, `8px` rounded corners or pill, stroke `1px solid #334155`. When active, fills with `#6366F1` and text `#FFFFFF`.

### Cards (Clone Instance Rows)
- **Container:** Background `#1E293B`, corner radius `16px`, padding `16px`. Border `1px solid rgba(255, 255, 255, 0.05)`.
- **Layout:** Leading 44x44px application icon (with cloned badge overlay on bottom right), followed by clone name and package title in the center stack, terminating with an Android MD3 Switch on the trailing side.

### Inputs & Search Bars
- **Docked Search Bar:** Pill shape (52px height), background `#1E293B`, `16px` horizontal padding. Leading search icon `#94A3B8`, trailing profile/settings avatar.
- **Text Inputs:** Outlined format with `12px` rounded corners. Default border `#334155`, active focused border `#6366F1` (2px thickness). Floating label transitioning from `body-md` to `label-sm`.

### Checkboxes & Switches
- **MD3 Switch:** Track 52x32px, `9999px` pill. Unchecked track `#334155`, thumb `#94A3B8` (16px). Checked track `#6366F1`, thumb `#FFFFFF` (24px with checkmark vector).
- **Selection Controls:** 20x20px with 4px border radius. Checked state fills with `#6366F1`.

### Bottom Navigation Bar
- Height 80px, background `#0F172A` with top border `1px solid rgba(255, 255, 255, 0.06)`.
- Active item uses an electric indigo pill indicator (64x32px, `#6366F1` at 20% opacity) surrounding the active icon, with the text label below rendered in `label-sm` bold.