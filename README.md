# Create: Essentials

NeoForge mod for Minecraft **1.21.1** adding Create-inspired Mechanical, Netherite Mechanical, and Magnetic Backpacks along with Rotational and Precision Magnets, persistent DataComponent storage, Create filter/Backtank support, and optional Curios API integration.

## Requirements
- **Minecraft:** 1.21.1
- **NeoForge:** 21.1.219+
- **Create:** 6.0.9+
- **Curios API (Optional):** 9.2.0+1.21.1

---

## Core Features
- **Mechanical & Netherite Mechanical Backpacks:** Placeable and wearable 27-slot backpacks (Chest slot or Curios `back` slot) with auto-restock, quick-transfer, and sorting.
- **Magnetic Backpack:** Combines a Netherite Mechanical Backpack with a Precision Magnet—pulls nearby items directly into storage with 3 Create filter slots, Whitelist/Blacklist mode, Backtank & kinetic charging, and live open-GUI sync.
- **Rotational & Precision Magnets:** Tier 1 (5-block pull radius, no GUI) and Tier 2 (10-block pull radius, 3 filter slots, Whitelist/Blacklist mode, live timer readout), rechargeable kinetically or from Create Backtanks.

---

## Recent Updates & Changelog

### UI Unification & Redesign
- **Uniform Dark Gray Theme (`DarkGuiStyling`, `DarkTextButton`):** Unified all screens (**Precision Magnet**, **Mechanical Backpack**, **Netherite Mechanical Backpack**, and **Magnetic Backpack**) with a consistent dark gray beveled panel palette (`#4A4A4A`), recessed dark slots (`#2E2E2E`), warm gold drop-shadowed headers, and beveled dark controls.
- **Precision Magnet (`RotationalMagnetScreen` / `RotationalMagnetMenu`):** Compact layout with centered title, `Magnet: ON/OFF` and `Blacklist`/`Whitelist` controls, centered `Item Filter` section with 3 filter slots + 30% opacity Create filter ghost icons when empty, and `Time remaining: MMm SSs` readout.
- **Magnetic Backpack (`MagneticBackpackScreen` / `MagneticBackpackMenu`):**
  - Replaced the central filter row with a seamless **attached left-side filter panel** holding **3 vertical filter slots** and 30% opacity Create filter ghost hints (hidden when occupied), plus `hasClickedOutside` handling so clicking the left tab never drops held items.
  - Added dark beveled `Magnet: ON/OFF` and `Blacklist`/`Whitelist` controls wired to existing serverbound packets.
- **Mechanical & Netherite Backpacks (`BackpackScreen`):** Unified dark gray panel and recessed 9×3 storage + player inventory grids.

### Phase 1 — Real-Time Inventory Sync & Magnet Rendering Fixes
- **Live Open-GUI Inventory Sync:**
  - Unified `MagneticBackpackTickHandler` and `MagneticBackpackMenu` onto a single shared `BackpackInventory` instance when the player has the backpack open (from hand, player inventory, chest slot, or Curios `back` slot).
  - Eliminated stale handler overwrites and prevented item loss/duplication when clicking slots or closing the GUI while the magnet is actively pulling items.
  - Triggered immediate server-to-client `broadcastChanges()` sync on both `containerMenu` and `inventoryMenu` (`MagneticBackpackEquipment`, `BackpackInventory`, `MagneticBackpackMenu`).
- **Hand/Arm Visibility & Dynamic Enchantment Glint (`MagneticBackpackItem`, `AllPackets`):**
  - Overrode `shouldCauseReequipAnimation(...)` on `MagneticBackpackItem` so per-tick `CHARGE` / component updates no longer force `ItemInHandRenderer` to push the held backpack off-screen when Magnet is ON or charging, keeping first-person and third-person rendering smooth and visible in all states.
  - Overrode `isFoil(ItemStack)` on `MagneticBackpackItem` (`ENABLED || super.isFoil(stack)`) so the enchantment glint dynamically turns ON when the magnet is active, cleanly turns OFF when disabled (without leaving `ENCHANTMENT_GLINT_OVERRIDE` stuck), and still preserves glint on genuinely enchanted backpacks.
  - Updated `AllPackets.handleToggle` to target an open item-backed `MagneticBackpackMenu` first when toggled via the GUI button and sync glint changes immediately across first-person, third-person, inventory, and Curios contexts.

### Phase 2 — Quick-Transfer & Inventory Sorting (All Backpacks)
- **Top-Right Utility Controls (`DarkIconButton`, `BackpackScreen`, `MagneticBackpackScreen`):**
  - **Green Up Arrow (`Transfer matching to storage`):** Moves matching items from the player's inventory into the backpack's 27 storage slots (merging into existing stacks first, then empty storage slots), never moving the open backpack itself, disallowed nested containers, or touching filter slots.
  - **Red Down Arrow (`Transfer matching to inventory`):** Moves matching items from the backpack's 27 storage slots into the player's inventory (merging first, then empty slots) while preserving exact remainders if inventory fills up and never pulling from filter slots.
  - **Sort Button (`Sort`):** Consolidates fragmented identical stackable stacks up to `maxStackSize` and deterministically sorts strictly the 27 storage slots using the active sort mode.
  - **Sort Mode Toggle (`A` / `M` / `#`):** Cycles between **Name**, **Mod**, and **Count** sort modes with localized hover tooltips showing the active mode (`Sort by: Name / Mod / Count`).
- **Serverbound Networking & Persistence:**
  - Added `BackpackActionPayload` (`TRANSFER_TO_STORAGE`, `TRANSFER_TO_INVENTORY`, `SORT`, `SET_SORT_MODE`) registered in `AllPackets` with strict server-side menu/validity/enum validation.
  - Added `SortMode` enum (`NAME`, `MOD`, `COUNT`) and `ExtraDataComponents.SORT_MODE` DataComponent (plus NBT persistence on `MechanicalBackpackBlockEntity` and `MagneticBackpackBlockEntity`), synced live to open menus via `DataSlot`.
  - Implemented zero-loss/zero-dupe inventory routines in `BackpackInventoryOps` and shared `IBackpackMenu` interface across `BackpackMenu` and `MagneticBackpackMenu`.

### Phase 3 — Native Backpack Hover Tooltip Preview & Per-Color Tinting
- **Zero-Dependency Minimalist Hover Preview (`BackpackTooltipData`, `ClientBackpackTooltip`):**
  - Implemented `Item#getTooltipImage(ItemStack)` across **Mechanical Backpack**, **Netherite Mechanical Backpack**, and **Magnetic Backpack**, registered via NeoForge's `RegisterClientTooltipComponentFactoriesEvent`.
  - Safely reads strictly the **27 storage slots (9×3)** from `AllDataComponents.BACKPACK_INVENTORY` without mutating stacks, allocating capabilities, or leaking the Magnetic Backpack's 3 filter slots.
  - Renders a clean, minimalist beveled **9×3 slot grid** matching the uniform dark gray mod UI (no header clutter, no progress bar, no slot counter); empty/fresh backpacks gracefully display a subtle `"Empty"` line.
- **Per-Color Background Tinting (`BackpackTooltipPalette`, `ClientEvents`):**
  - Default/unpainted backpacks use the uniform dark gray panel style, while all **16 `DyeColor` variants** and custom `DataComponents.DYED_COLOR` backpacks cleanly tint the tooltip panel background and border gradient while keeping inner slots dark and readable.

### QA Polish & Bug Fixes
- **Magnet Filter Persistence & Matching (`RotationalMagnetMenu`, `MagneticBackpackMenu`, `MagnetTickHandler`, `MagneticBackpackTickHandler`):**
  - Filter slot changes now persist immediately to `AllDataComponents.MAGNET_FILTERS` / `BACKPACK_FILTERS` and sync to the client on click (not just on close), preserving exact 3-slot positions.
  - Both Precision Magnet and Magnetic Backpack now accept both standard items and Create filter items (`FilterItem` / `ListFilter` / `AttributeFilter`) in all 3 filter slots and strictly enforce Whitelist/Blacklist mode.
- **Independent Multi-Item Toggle & Open Targeting (`AllPackets`, `BlacklistTogglePayload`, `MagnetEquipment`, `MagneticBackpackEquipment`, `MagneticBackpackTickHandler`):**
  - Clicking `Magnet: ON/OFF` or `Blacklist`/`Whitelist` inside a Precision Magnet or Magnetic Backpack GUI now toggles **exclusively** the exact open item instance, synced live via `DataSlot`.
  - Magnet toggle keybind uses strict context-aware priority (**Main hand → Offhand → Curios/Equipped → Rest of inventory**) so multiple magnets or magnetic backpacks toggle and operate independently without interfering with each other.

### Phase 4 — Portable Create Toolbox Integration & 16-Color Variant Readiness
- **Codec-Safe 8-Compartment Portable Toolbox (`PortableToolboxInventory`, `ExtraDataComponents`):**
  - Integrated an 8-compartment Create-style toolbox across **Mechanical**, **Netherite Mechanical**, and **Magnetic Mechanical** backpacks (up to **256 items** per compartment via 8 × 4 backing `ItemStack`s + 8 persistent reserved filter slots, completely avoiding Minecraft 1.21.1's `ItemStack.CODEC` count-99 limit).
  - Persists seamlessly across DataComponents (`TOOLBOX_CONTENTS`, `TOOLBOX_FILTERS`, `TOOLBOX_RESTOCK`), placed block entities, and upgrades.
  - Hardened anti-nesting (`BackpackHelper.isNestedContainer`) to block backpacks, shulker boxes, Create toolboxes (`#create:toolboxes`), bundles, and items with `CONTAINER`/`BUNDLE_CONTENTS` or `Capabilities.ItemHandler.ITEM`.
- **GUI & Radial Menu (`BackpackScreen`, `MagneticBackpackScreen`, `PortableToolboxRadialScreen`, `PortableToolboxHandler`, `BackpackToolboxPayload`):**
  - Added a seamless attached right-side `2×4` Toolbox panel with compact 3-digit counts (up to `256`), empty reserved-filter ghost hints (click to clear), and a persisted per-backpack **Restock ON/OFF** toggle.
  - Strictly isolated toolbox slots from Phase 2 quick-transfer arrows, sorting, and Magnetic Backpack filter slots.
  - Added `Alt` (`AllKeys.TOOLBELT`) radial wheel supporting 32-item equip, live auto-refill while Restock is ON, center **Return**, full `ExtendoGripItem` mainhand/offhand protection, and one-click handoff to nearby placed Create Toolboxes.
- **Minimalist Hover Tooltip Toolbox Row & Priority Magnet Routing:**
  - Extended `BackpackTooltipData` / `ClientBackpackTooltip` with a cleanly separated minimalist 8-slot toolbox row below the 9×3 grid (counts up to `256`, reserved ghost markers, no progress bars or `X/27` clutter) and centralized color resolution (`BackpackColorHelper`) ready for 16 `DyeColor` variants.
  - Updated `MagneticBackpackTickHandler` to route magnetized items (after Whitelist/Blacklist filtering) into matching/reserved toolbox compartments first (with live open-GUI sync), falling back to the 27 main storage slots.
- **Crafting & Upgrade Progression:**
  - Updated `mechanical_backpack.json` to require `#create:toolboxes`, and updated `MagneticBackpackRecipe` (alongside smithing to Netherite) to preserve all backpack DataComponents (`BACKPACK_INVENTORY`, `TOOLBOX_CONTENTS`, `TOOLBOX_FILTERS`, `TOOLBOX_RESTOCK`, `SORT_MODE`, `DYED_COLOR`) and magnet filter/blacklist state.

### Phase 5 — Toolbox Layout Polish, Create-Style Ghost Slots, Tooltip Fix & Real-Time Radial Sync
- **Toolbox Bar Repositioning & Pixel-Perfect Alignment (`BackpackMenu`, `BackpackScreen`, `MagneticBackpackMenu`, `MagneticBackpackScreen`, `RotationalMagnetScreen`):**
  - Moved the 8-slot horizontal **Toolbox** bar (including the `"Toolbox"` label and `"Restock: ON/OFF"` toggle) directly beneath the 27-slot backpack storage grid and above the player's `"Inventory"` section inside a single unified beveled panel (`176×213` for Mechanical/Netherite, `176×232` + flush-top left filter tab for Magnetic), with updated slot coordinates and `hasClickedOutside` bounds.
  - Refined **Magnet ON/OFF** and **Whitelist/Blacklist** button proportions (`79×14`, `4px` center gap) and aligned all top-right utility buttons (`12×12`), dividers, and `"Restock: ON/OFF"` (`74×14`) flush with the 9-column slot edges (`7..169`).
- **Create-Style Ghost/Reserved Slot Previews (`PortableToolboxInventory`, `BackpackInventoryOps`, `BackpackTooltipData`, `ClientBackpackTooltip`):**
  - Removing the last item from a toolbox compartment now preserves a persistent translucent ghost preview (`TOOLBOX_FILTERS` DataComponent + BlockEntity NBT) so priority routing and hotbar restock links remain intact.
  - Clicking or shift-clicking an empty reserved slot with an empty cursor explicitly clears the ghost preview; inserting a different item into an empty reserved slot safely replaces the reservation; non-empty slots can never be accidentally cleared, overwritten, or truncated (strict `1` / `64` / `256` stack caps enforced with zero item loss or duplication).
- **Backtank Recharge Tooltip Clipping Fix (`TooltipWidthGuard`, `ClientEvents`, `en_us.json`, `MagnetTickHandler`):**
  - Replaced the Unicode ellipsis in `createessentials.tooltip.charging` with crisp ASCII `"Charging..."` and added a zero-height `TooltipWidthGuard` via `RenderTooltipEvent.GatherComponents` so Precision Magnet and Magnetic Backpack hover tooltips never clip, wrap awkwardly, or touch the right border while recharging from a Create Backtank.
  - Added immediate `MagnetEquipment.syncMagnet` calls on charge/charging state transitions.
- **Real-Time Radial Menu (`Alt`) Sync & Stale Slot Fix (`SyncToolboxLinksPayload`, `BackpackToolboxPayload`, `PortableToolboxHandler`, `PortableToolboxOverlay`, `PortableToolboxRadialScreen`, `CuriosBackpackIntegration`, `AllPackets`):**
  - Extended `SyncToolboxLinksPayload` to carry the full server-authoritative 8-compartment live snapshot (`displayItems`, exact `0..256` counts, `hasBackpack`, locked hotbar bitmask, and Restock state) pushed immediately on every GUI mutation, menu close, suction routing, equip/return, and explicit `REQUEST_SYNC` when opening the radial wheel.
  - Added automatic hotbar-link re-binding (`rebindMovedLinks`) when items are moved or sorted between toolbox compartments, unified `B` (`OpenBackpackPayload`) and `Alt` (`PortableToolboxHandler`) backpack targeting priority (**Curios → Chest → Main → Off → Inv**), hardened Curios `"back"` sync, added empty-hotbar fallback when holding the backpack, and matched `AllKeys.TOOLBELT` key release via `InputConstants.getKey`.
- **Automated Unit Tests (`PortableToolboxSafetyAndSyncTest`, `BacktankChargeStabilityTest`):**
  - Enabled ModDevGradle JUnit 5 testing and added 13 unit tests verifying `1`/`64`/`256` stack caps, ghost-slot zero-loss click/clear/replace/swap safety, DataComponent & BlockEntity NBT round-trips, sorting count conservation, slot-move link re-binding/live overlay synchronization, and Backtank charge/hysteresis stability.

### Backtank Recharge Stability & Tooltip Jitter Fix
- **Same-Tick Backtank Sustainment & Hysteresis (`BacktankChargeController`, `BacktankSupport`, `MagnetTickHandler`, `MagneticBackpackTickHandler`):**
  - Extracted a shared per-stack `BacktankChargeController` that sustains active 1-tick operational drain directly from available Backtank air in the same tick (eliminating `04:59 ↔ 05:00` oscillation at 100% charge), isolates fractional air remainders per item stack, uses single-Backtank reference capacity (`BacktankSupport.referenceMaxAir`), and applies a 25-tick hysteresis window so `CHARGING` never flickers between ticks.
- **Constant-Height Inline Charging Tag (`ChargeDisplay`, `RotationalMagnetItem`, `MagneticBackpackItem`, `en_us.json`):**
  - Moved the hover tooltip charging indicator inline onto the `Status:` line (`Status: Enabled (Charging)`) instead of dynamically inserting/removing a separate vertical `"Charging..."` line, completely eliminating vertical tooltip and backpack preview grid jitter.

### 3D Blockbench Assets & 16x Pixel-Art Textures (`create_essentials_assets/`)
- **Editable Blockbench Projects, Java Block JSONs, and Zero-Overlap 64×64 Atlases:**
  - Added complete, Blockbench-ready `.bbmodel` (v4.10 `java_block`), Minecraft Java `.json`, and 16x pixel-density `.png` texture deliverables under `create_essentials_assets/{bbmodel,models,textures}/` with `[0, 16]` block bounds, organized outliner groups, zero UV overlap, and exact `[8, 8, 8]` Create shaft centering:
    - **`mechanical_backpack` & `netherite_mechanical_backpack`:** Sculpted leather/brass and Netherite-armored backpacks with visible 3D recessed rear cogwheels, upper side gear windows, bellowed side pockets, and front-mounted Create-style toolboxes (**no shoulder straps, no kinetic shaft**).
    - **`rotational_magnet` & `precision_magnet`:** Curved U-shaped crimson/cobalt and Tier 2 Netherite/gold-trimmed horseshoe magnets with open front U-arches and rear/back curved-face 3D Create brass cogwheel housings + centered `4×4` spinning shafts (`X: [6, 10], Y: [6, 10], Z: [12.5, 16]`).
    - **`magnetic_backpack`:** Netherite-accented backpack with integrated side polarity indicator plates, sculpted lower side U-magnet pods, front Create-style toolbox, **no shoulder straps**, and a **top-mounted vertical spinning shaft** + horizontal 3D cogwheel input (`X: [6, 10], Y: [13.75, 16], Z: [6, 10]`).
- **Live Mod Resource Integration (`src/main/resources/assets/createessentials/`, `AllBlocks`):**
  - Wired all 5 `64×64` textures into `textures/block/` (`mechanical_backpack.png`, `netherite_backpack.png`, `rotational_magnet.png`, `precision_magnet.png`, `magnetic_backpack.png`) and updated all `models/block/*.json` texture references (`createessentials:block/<name>`) and `models/item/*.json` parent bindings.
  - Updated `blockstates/rotational_magnet.json`, `blockstates/precision_magnet.json`, and `blockstates/magnetic_backpack.json` with full `facing` orientation rotations while preserving `netherite=false/true` variants on `blockstates/mechanical_backpack.json`.
  - Added `.noOcclusion()` to backpack and magnet block properties in `AllBlocks` so non-full-cube models render cleanly without culling adjacent block faces.

### Rebuilt Industrial Magnets & Live Kinetic Shaft Rotation
- **Clean 20-Cube Horseshoe Silhouette (`rotational_magnet`, `precision_magnet`):**
  - Synced Tier 1 (`rotational_magnet`) directly from the refined 20-cube `rotational_magnet.bbmodel` (14 static + 6 rotating, strictly within `[0, 16]` bounds) and aligned Tier 2 (`precision_magnet`) to the exact same clean 20-cube silhouette in dark volcanic Netherite, forged iron shoes, gilded brass accents, and subtle inner polarity indicators.
  - Preserved clean 4-group Blockbench outliners (`magnet_poles`, `magnet_arms`, `static_housing`, `rotating_shaft`), `64×64` 16x pixel-art textures, and single rear Create cogwheel + `4×4` kinetic shaft flush to `Z = 16`.
- **Split Kinematics & In-Game Rotation (`AllPartialModels`, `RotationalMagnetVisual`, `RotationalMagnetRenderer`, `ClientEvents`, `RotationalMagnetBlock`):**
  - Split in-world block models (`models/block/rotational_magnet.json`, `precision_magnet.json`) into 14-cube static bodies plus 6-cube rotating partial models (`models/block/rotational_magnet_shaft.json`, `precision_magnet_shaft.json`) while keeping complete 20-cube assembled item models in `models/item/`.
  - Implemented GPU-instanced Flywheel rotation (`RotationalMagnetVisual`) and `KineticBlockEntityRenderer` fallback (`RotationalMagnetRenderer`), registered both in `ClientEvents`, and aligned `RotationalMagnetBlock.hasShaftTowards` strictly with the rear `facing.getOpposite()` shaft face.
- **Handheld Display Transform Polish (`models/item/`, `.bbmodel`):**
  - Updated `firstperson_righthand`, `firstperson_lefthand` (`rotation: [76, 18, -16]`, `translation: [1, 2.75, 0]`, `scale: [0.52, 0.52, 0.52]`) and `thirdperson_righthand`, `thirdperson_lefthand` (`rotation: [78, 8, 0]`, `translation: [0, 2, 0.75]`, `scale: [0.45, 0.45, 0.45]`) on both magnets so the upright horseshoe silhouette and metallic pole tips read cleanly in-hand while tucking the rear gear/shaft into the grip.

### Placed-Block Shadow Fix & Upward (`+Y`) Magnet Charging Orientation
- **Eliminated Dark Placement Shadows (`AllBlocks`, `RotationalMagnetBlock`, `MechanicalBackpackBlock`, `MagneticBackpackBlock`, `RotationalMagnetRenderer`):**
  - Added `.isViewBlocking((s, l, p) -> false)`, `.isSuffocating((s, l, p) -> false)`, and `.isRedstoneConductor((s, l, p) -> false)` alongside `.noOcclusion()` in `AllBlocks`.
  - Replaced default full-cube hitboxes with accurate non-full `VoxelShape`s and overrode `getShadeBrightness` (`1.0F`), `propagatesSkylightDown` (`true`), and `getLightBlock` (`0`) across magnets and backpacks so placed models never darken adjacent blocks or themselves.
  - Updated `RotationalMagnetRenderer` to sample clean packed light directly at `be.getBlockPos()` via `LevelRenderer.getLightColor(...)`.
- **Fixed Upward (`+Y`) Kinetic Charging Orientation (`RotationalMagnetBlock`, `RotationalMagnetVisual`, `RotationalMagnetRenderer`, `blockstates/rotational_magnet.json`, `blockstates/precision_magnet.json`):**
  - Locked magnet placement (`getStateForPlacement`), default state (`FACING = DOWN`), rotation axis (`Direction.Axis.Y`), shaft connection (`hasShaftTowards` → `Direction.UP` only), Flywheel/BER partial transforms (`Direction.UP`), and magnet blockstates (`"x": 90`) so both magnets always stand upright with their charging shaft input facing upward (`+Y`).
  - Added JUnit 5 coverage in `BlockLightingAndMagnetOrientationTest`.
- **Refined 15-Cube Boxy-Back & Thick-Cog Magnet Geometry (`rotational_magnet`, `precision_magnet`):**
  - Replaced the tapered stepped rear arch with a cleaner square/boxy Minecraft U-bridge (`rear_bridge_left`, `rear_bridge_right`), flattened pole ends completely flush with the arm cross-section (`3.25×4.0`, zero bulging step), thickened the rotating rear cogwheel body (`2.0` voxels along Z) with shorter, stubbier prongs (`radius 3.6`), synced `RotationalMagnetBlock.SHAPE` (`8` static + `7` rotating cubes, zero-overlap `64×64` textures), and standardized all Tier 1 workspace assets under `rotational_magnet`.
  - Synced the latest updated `create_essentials_assets/models/rotational_magnet.json` and `textures/rotational_magnet.png` into `src/main/resources/assets/createessentials/` (`textures/block/rotational_magnet.png`, split static `models/block/rotational_magnet.json` + rotating `models/block/rotational_magnet_shaft.json`, and complete `models/item/rotational_magnet.json`).
- **Shared Rear Wheel Assembly & Subtle Pixel-Art Corner Chamfer (`rotational_magnet`, `precision_magnet`):**
  - Transferred `rotational_magnet`'s exact `static_housing` and `rotating_shaft` geometry, pivots, UV rects, and `408` wheel pixels into `precision_magnet` (pruning `124` leftover unreferenced pixels from both `64×64` atlases).
  - Applied a crisp 1-voxel outer rear corner chamfer (`rear_bridge_*_main` + `rear_bridge_*_back`, `16` total cubes = `10` static + `6` rotating) with exact proportional UVs across all `.bbmodel`, block, shaft partial, item `.json`, and `.png` files in both `create_essentials_assets/` and `src/main/resources/assets/createessentials/`.
  - Recolored only the `64` upper `kinetic_shaft` texels on both `rotational_magnet.png` and `precision_magnet.png` (plus embedded `.bbmodel` textures) to match Create's `netherite_backtank.png` shaft cap and slate-plum side palette with zero geometry, UV, JSON, or code changes.
  - Added dedicated opaque `16×16` metallic break/hit particle textures (`textures/block/rotational_magnet_particle.png` in brushed steel and `textures/block/precision_magnet_particle.png` in forged Netherite with subtle brass flecks) and wired them to the `"particle"` key on both magnets' block, shaft, and item models.
- **Codebase Cleanup, Safe Stack Sync & Magnetic Backpack GUI Charge Readout:**
  - Removed the 4 unused decompiled Create reference files tracked outside `src/` (`assets/create/lang/en_us.json`, `com/simibubi/create/...`).
  - Removed the dead `EquipmentSlot.CHEST` check from `MagnetEquipment.syncMagnet` and added `StackSyncHelper` three-tier slot matching (exact reference → unique same-components → unique same-item, refusing ambiguous writes) across `MagnetEquipment`, `MagneticBackpackEquipment`, and `CuriosBackpackIntegration`.
  - Added live `DataSlot` synchronization for `charge`, `maxCharge`, and `charging` in `MagneticBackpackMenu` and rendered a centered `Time remaining: MMm SSs` readout, color-coded horizontal charge bar (`DarkGuiStyling.drawHorizontalBar`), and detailed hover tooltip below the `Magnet: ON/OFF` controls in `MagneticBackpackScreen`.
  - Deduplicated kinetic charging, particle/chime effects, and Goggles tooltip lines into `KineticChargeHelper` (`RotationalMagnetBlockEntity`, `MagneticBackpackBlockEntity`) and filter evaluation into `MagnetFilterHelper` (`MagnetTickHandler`, `MagneticBackpackTickHandler`, `MagneticBackpackBlockEntity`), covered by new JUnit 5 tests in `CleanupAndHelpersTest`.

