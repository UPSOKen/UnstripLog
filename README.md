Fork note
---------
This fork adds support for Pale Oak logs and wood on newer Minecraft/Paper servers, including `PALE_OAK_LOG`, `PALE_OAK_WOOD`, `STRIPPED_PALE_OAK_LOG`, and `STRIPPED_PALE_OAK_WOOD`.

Version 1.11.2 also adds Poplar logs and wood (including their stripped variants), copper axes, and copper shovels. These materials use optional runtime lookups, so servers that do not provide them retain their existing supported materials and tools without requiring the new enum constants.

Deferred undo now checks that the block's full `BlockData` still matches the clicked state before changing it. If its type or state has changed before the next-tick callback, undo is skipped instead of modifying the replacement. A replacement with exactly the same state cannot be distinguished by this check. Existing protection-cancellation handling is unchanged.

These changes require no new configuration. See [the 1.11.2 testing guide](TESTING-1.11.2.md) for regression checks and runtime-testing limits.

It also removes the optional JEFF Media Spigot update checker because the Maven repository used by the original project is no longer resolvable during builds.

This fork changes UnstripLog's permission defaults so `unstriplog.wood` and `unstriplog.path` are disabled by default. Grant those permissions explicitly when players should be able to destrip logs or undo grass paths.

UnstripLog
===================
A Free Resource by Alex_qp.
------------------------------

UnstripLog is a Minecraft plugin designed to allow unstripping/unpathing of any wood/glass. Please see the [plugin's page on Spigot](https://www.spigotmc.org/resources/unstriplog-1-13-x-1-19-x.62738/) for more information.


Information
------------
[UnstripLog](https://www.spigotmc.org/resources/unstriplog-1-13-x-1-19-x.62738/) is a Minecraft plugin developed by [Alex_qp](https://www.spigotmc.org/resources/authors/alex_qp.306806/). Its first release was on Nov 26, 2018 and was constantly improved and updated since then. On Jan 3, 2023 the source code was published under the GNU General Public License v3.0.

Releases will only be marked on Spigot.

Source
------
Source code is currently available on [GitHub](https://github.com/Alex39099/UnstripLog). Please respect the license.

Contributing
------
If you contribute something via pull requests and do not get an answer from me, please [contact me via Spigot](https://www.spigotmc.org/resources/authors/alex_qp.306806/). Significant contributing (judged by me) will result in a reference also on Spigot.
