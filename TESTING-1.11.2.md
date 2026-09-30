# UnstripLog 1.11.2: local test build

## Scope

Based on UPSOKen/UnstripLog master commit `c1130287891f2249eb91ba3aa8665f79406044e4`, verified on 2026-09-30.

- Recognizes `POPLAR_LOG` and `POPLAR_WOOD` when those runtime materials exist. Their stripped variants use the existing `STRIPPED_` lookup.
- Recognizes `COPPER_AXE` and `COPPER_SHOVEL` when those runtime materials exist.
- Immediately before deferred undo, compares the current full `BlockData` with an independent snapshot of the clicked state. A different block type or changed log axis aborts the undo, avoiding writes over a replacement or casts of a replacement to the wrong block-data interface.
- Patch version bump from 1.11.1 to 1.11.2 across the Maven modules and filtered plugin metadata.

The new material names use optional string lookup, not references to new enum constants. Existing old-version providers, Java 17 bytecode target, compile APIs, runtime dependencies, configuration, permission defaults, timing, main-hand priority, and undo orientation are unchanged. The separate protection-cancellation change is deliberately excluded: event priority, cancellation handling, and the existing timing of undo-window/metadata consumption have not changed.

The state guard detects a different state at callback time. It cannot distinguish a replacement that has exactly the same full `BlockData`, or a change that is reversed before the callback. It is not a new claim/protection integration or a block-history tracker. An aborted timed undo is not automatically retried.

## Install on a staging copy first

1. Stop the staging server cleanly. Back up the current UnstripLog JAR, `plugins/UnstripLog` configuration, and the test world.
2. Replace the existing plugin JAR with `UnstripLog_v1.11.2.jar`. Keep only one UnstripLog version in the plugins directory. Start normally; do not hot-swap or use `/reload`.
3. Confirm the loaded version is 1.11.2 and the console has no UnstripLog enable errors. There is no configuration or data migration.
4. Use an explicitly authorized test player with `unstriplog.wood` and `unstriplog.path`; these permissions remain disabled by default, including for operators unless a permission plugin grants them.
5. Keep the backup and use a copied/non-production world until the checklist passes. To roll back, stop the server, restore the previous JAR, and restart. Restore the test-world backup if you want to revert the test block changes themselves.

## Manual target-server checklist

Run on the actual Paper 26.3 build and its supported Java runtime. Record `/version`, Java version, installed plugin version, configuration, exact steps, and relevant console output with any failure.

1. **Poplar and copper:** for both poplar log and poplar wood, test each X/Y/Z orientation with a copper axe. Strip, then sneak-right-click within the configured window. The original poplar type and orientation should return. Repeat using iron and netherite axes. Test a copper shovel on grass, dirt, coarse dirt, mycelium, podzol, and rooted dirt; timed undo should restore each original type.
2. **Infinite mode:** on staging, set `timeframe_wood: -1` and `timeframe_path: -1`, then restart. Pre-place stripped poplar log/wood and paths with no recorded history. Copper-tool undo should restore the poplar type/axis and turn a path into dirt, matching existing infinite behavior.
3. **Timing:** use a short positive timeframe, verify undo inside the window succeeds and undo after expiry does nothing. Set each timeframe to 0 and verify its undo is disabled. Restore the desired values and restart between configurations.
4. **Sneaking and permissions:** test both values of `must_sneak_unstrip`; test `must_sneak_strip: true` with copper and existing tools on poplar and paths. Without the appropriate permission, undo must fail; granting it should enable undo. Compare off-hand interactions with the existing main-hand priority behavior.
5. **Existing materials:** repeat normal timed and infinite undo for oak, birch wood, nether stems/hyphae, mangrove, cherry, bamboo, pale oak, and existing axe/shovel tiers supported by the test server. Check log orientation and ordinary path restoration.
6. **Deferred replacement:** this requires a staging-only diagnostic listener/debugger because the interval is one server tick. After the plugin handles a valid undo click but before its callback runs, replace the block with stone, air, a different stripped log, or an ordinary log; also rotate a stripped log without changing its material. Leave the replacement in place. After the callback, it must remain unchanged and no ClassCastException should be logged. Repeat with path undo, including a timed path with a recorded original. With no intervening change, the normal undo must still succeed.
7. **Other deployed versions:** smoke-test plugin enable plus normal log/path undo on any older server versions you actually support. No new material is required on versions that do not provide it. Build-time compatibility is not a substitute for these runtime checks.

## Automated verification

Build with a full JDK 21 (used here) and Maven 3.9.9: `mvn clean verify`. Production bytecode remains targeted at Java 17. The seven-module reactor compiles each legacy provider against its original declared Spigot API and packages the shaded distribution.

The core module has 28 JUnit cases (including parameterized cases):

- 3 material-provider tests: the declared older API omits new material names without null entries and preserves its 21 log/wood originals and six tool tiers; simulated newer runtime lookups include both poplar variants, both stripped counterparts, and both copper tools; a missing stripped counterpart is ignored
- 25 event/callback tests: all three axes; unchanged timed/infinite log/path undo; stone/air/different log/path replacements; same-type axis change; recorded-original path replacement; expiry; disabled undo; permission and sneaking checks; main-hand priority; wrong tool; unchanged listener annotation and explicitly unchanged late-cancellation behavior

Tests use real Bukkit event/ItemStack classes with mocked server/player/block/scheduler services and a full-state BlockData test double. Optional new constants are simulated on the old compile API. They do not run a Minecraft server, model vanilla interaction/tool damage, load protection plugins, or certify Paper 26.3 gameplay. Mockito is supplied as a test-only Java agent; no test libraries are included in the deliverable JAR.

Verification here: all 28 cases passed; the same tests against the two original production files failed in 12 cases, reproducing missing optional materials and unsafe delayed replacement behavior. The shaded JAR was checked for version 1.11.2, Java 17 plugin bytecode, all four legacy providers, relocated Commons, and absence of test dependencies. Aside from the two changed production classes and the listener’s two inner classes, class bytes and `config.yml` match the baseline build.
