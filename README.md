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
