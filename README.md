# Empiric
Multi-loader component attachment system that's quick, modular, and easy to use.

## Why empiric?
#### One attachment system does everything
- Use modules to attach your own data and logic to anything: the server, levels, blocks, block entities, entities, oct cells, item stacks, spatial networks, and Sable sub-levels.
- A module is a regular java class, without the boilerplate. Mark a field ``@Data`` and it will automatically be serialized, saved, and synced to clients without any packets or codecs.
- Access is easy, simply register your module, attach with ``MODULE.attach()``, and fetch with ``MODULE.get(level, pos)`` or any other type of access.
- Automatically attach modules with rules, "every dirt block," "every pig," "every sub-level," and more.

#### Execute behaviors without any wasted effort
- Add tick, render, block-change, item-render, network, and even sub-level split logic just by implementing an interface.
- Behaviors are indexed, only execute what is active, not what is idle.

#### Networking without extra steps
- Players are sent the full state when they begin tracking a module, and are sent updates only sending what changes.
- Networked data is written as raw binary, without extra information.
- ``@Data(watch = true)`` dirties a field whenever it is modified.
- ``@Data(clientMutable = true)`` allows clients to modify the module, leaving the server to have the final say.

#### Saved data built on the system you already use
- Saved data is keyed by id, and versioned with optional data fixers, so your data always stays accurate.
- Data that isn't used is left for later, and can be restored by giving it a field.
- Drop policies like ``UNLOAD``, ``DEATH``, ``LEVEL_CHANGE``, ``INVENTORY_CLEAR`` decide when a module is destroyed.

#### You only pay for what you use
- Unused systems stay dormant until a mod subscribes to them. Dormant systems don't tick, save, or sync.

## Additional Features
#### Built in simulations and systems
- **Multiblock and Octs** 1/8th voxels and a multiblock system that allow for true scalable multiblocks.
- **Spatial Networks** Network system allowing for pipes, cables, and other connectable blocks. Includes behaviors for splitting and merging as well.
- **Temperature** at 1/8th block resolution, including conductivity and dimension properties.
- **Atmosphere** cached sealed rooms that connect to the world when broken. Includes pressure and heat exchange with surfaces.
- **Chemistry** chemicals and reactions defined in data driven files including phase changes with temperature and pressure. Includes volumes and works with atmospheres for deep integration.

#### A simple UI system, built to work for any application
- A screen is just access to a modules synced fields, all viewers edit a live synced state, keeping the server in charge.
- Any UI can work as a simple Screen, a container menu with vanilla slots, a surface projected and interactable in the world, or as a floating tooltip attached to a module.
- UIs feature seamless player interaction supporting multiple editors, and presence cursors.
- Layouts can exist as a plain java class, or as a customizable json layout that can be edited using an in game editor.

#### Sable support
- Modules can attach to sub-levels, are saved to the sublevel, are synced to the players tracking it, and keep working across sublevel loads and unloads.
- Includes sub-level ``SplitBehavior`` that lets each module decide what happens when a sublevel splits.
- Sable is completely optional of course, Empiric will run totally fine without it, and the related code is only loaded when Sable is installed.

#### Built for both modders and develoeprs
- Empiric currently runs on both NeoForge and Fabric on 1.21.1 (planned support for future versions!).
- Almost every system has both game tests, and ``/empiric`` debug commands, ensuring the system stays stable and easy to debug.
- Data driven information is also synchronized when players join and on ``/reload``
- The core engine ``empiric-core`` is completely independent of Minecraft, allowing you to use it any java project, not just a mod!

*P.S. if I missed anything important, or if you encounter any major issues, please dm me on discord @modogthedev and or [join the discord server](discord.gg/smTR7d5muz)!