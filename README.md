# Excavation, Quarry and Chunk Eater

Fabric area-mining enchantments for Minecraft Java 26.3. Apply one to a pickaxe or shovel and break a block to mine the centered cube around it.

| Enchantment | Levels | Cube size | Tool durability |
| --- | ---: | ---: | --- |
| Excavation | I–VII | 3×3×3 through 15×15×15 | Normal |
| Quarry | I–VIII | 17×17×17 through 31×31×31 | None |
| Chunk Eater | I–XVI | 33×33×33 through 63×63×63 | None |

Each level increases the side length by two blocks. The enchantments are mutually exclusive. Excavation has a common enchanting weight; Quarry and Chunk Eater are increasingly rare and more expensive to combine on an anvil.

The extra blocks use Minecraft's normal player block-breaking path, including Silk Touch, Fortune, protection checks, and appropriate tool requirements. Unbreakable blocks, air, and blocks in unloaded chunks are skipped. Quarry and Chunk Eater send item drops to your inventory; excess drops fall on the ground. Regular Excavation uses normal drops and durability. Experience still drops normally.

A mining operation scans at most 256 positions each server tick. It stops if you change tools or disconnect, and it does not start another operation for that player until the first one completes. A very large cube can take some time to finish and may span multiple chunks; unloaded chunks are skipped rather than forcibly loaded.

Requires Fabric Loader, Fabric API, and Java 25.
