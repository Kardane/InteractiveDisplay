# Sample UI design system

This baseline comes from the October 8, 2026 sample redesign. The observed intermediate screenshots were assessed at 82/100. Subsequent refinements targeted 95/100, but no final screenshot confirmed that score. Treat both the palette and dimensions as editable defaults.

## Reference family and source of truth

Inspect the current actual definitions first:

- `run/config/interactivedisplay/windows/main_menu.yaml`: basic text/button example.
- `run/config/interactivedisplay/windows/sample_index.yaml`: sample selection.
- `run/config/interactivedisplay/windows/display_showcase.yaml`: item/block/text comparison.
- `run/config/interactivedisplay/windows/font_showcase.yaml`: nine Pretendard weights.

These are operator-owned, ignored runtime files and may be absent in a fresh checkout. They are examples, not a required dependency of this skill. Use the tracked bundled definitions and the principles here when runtime files are unavailable; do not claim an existing server was updated.

## Visual roles

| Role | Established value | Intent |
| --- | --- | --- |
| Outer border | `#FF365369` | Quiet panel boundary |
| Main surface | `#F2182938` | Dark backdrop with slight transparency |
| Content surface | `#FF243A4B` | Group a sample or text example |
| Primary button / hover | `#FF285975` / `#FF347391` | Navigation emphasis |
| Secondary button / hover | `#FF2B3F50` / `#FF3C566A` | Secondary action or close |
| Heading text | `#EAF4FA` | High-priority reading |
| Helper and caption text | `#C0D0DB` | Readable supporting information |
| Text specimen accent | `#FFE2A6` | Distinguish specimen from interface labels |
| Divider | `#FF365064` | Separate footer without heavy ornament |

Colors use quoted ARGB values. Standard button labels are white in the current rendering path; do not invent an unsupported button text-color field.

Keep the primary button's color meaningful. Do not make a harmless close button red merely because the earlier sample did.

## Size and spacing starting points

Values are world/layout units, not screen pixels. Their projected appearance depends on distance, FOV, pitch, resolution, and mode.

| Element | Current starting point |
| --- | --- |
| Compact menu outer / inner size | `2.84 x 2.20` / `2.80 x 2.16` |
| Showcase outer / inner size | `4.24 x 2.44` / `4.20 x 2.40` |
| Font showcase outer / inner size | `4.24 x 2.90` / `4.20 x 2.86` |
| Menu / showcase forward offset | `2.8` / `3.0` |
| Heading font scale | `0.48-0.52` |
| Supporting text font scale | `0.30-0.34` |
| Main button label scale | `0.46-0.48` |
| Main menu button size | `2.36 x 0.30` |
| Button padding | horizontal `0.12`, vertical `0.04` |
| Hover scale | `1.015`, adjusted for actual margins |
| Showcase sample areas | Three `1.16 x 1.30` surfaces at x `-1.3, 0, 1.3` |
| Footer divider height | `0.008` |
| Typical depth order | border `0`, surface `0.01-0.02`, controls/text `0.04-0.06` |

These ranges support a compact sample UI, not every possible window. Do not shrink text solely to fit a long label; shorten wording or enlarge the control where appropriate.

Use a narrow border, moderate gutters, and enough bottom padding. Compute gaps from actual rendered bounds, including hover enlargement. Keep dividers away from button surfaces.

## Lessons from the redesign

- An oversized panel with sparse, disconnected content reads as unfinished rather than spacious.
- Unfilled buttons look like ordinary labels before hover.
- Buttons protruding below the background make the panel's geometry ambiguous.
- A full emerald-block square outweighs a diamond's transparent sprite at the same scale. Adjust visible size while preserving center alignment.
- A text specimen benefits from a larger Latin example and a separate readable Korean line, rather than one small mixed-font block.
- Enlarging and shortening Korean helper text improved the intended hierarchy; inspect actual glyphs to confirm.
- A family-wide style change must include the basic window reached from the sample menu. The earlier redesign initially missed `main_menu`; screenshot content exposed that omission.

## Review and score

Use this transparent rubric when a numeric score is requested; adapt it if the user supplies another standard:

| Criterion | Points |
| --- | --- |
| Information hierarchy and task clarity | 20 |
| Layout, spacing, and visible sample balance | 25 |
| Typography and readability | 20 |
| Control recognition and action hierarchy | 20 |
| Family consistency and fit with Minecraft | 15 |

Explain the main deductions with visible evidence. Pixel fonts and perspective are not defects by themselves. Do not infer broken clicks from a screenshot, or accessibility compliance from chosen hex colors alone.

An 80-level screen is coherent and clearly operable. A 95-level target also requires convincing micro-spacing, balanced artwork, readable Korean, consistent sibling screens, and no visible boundary mistakes at the intended viewing conditions. Do not self-certify the target before observing the delivered screen.
