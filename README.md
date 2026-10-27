# Empiric
Multi-loader component attachment and simulations systems that are quick, modular, and easy to use.

## Why use Empiric?
Do you ever get tired of writing the same boiler plate over and over again? Maybe the same Map<Level,Object> for every system? Or ever writing the same packets that do the exact same things as the last, every single time you want to add one feature?

Well I did, so I made this project to make that slog a thing of the past (I hope...). Empiric is similar to a component system, and it allows you to attach "moules" to many different "attachments." Never again will you have to write the same system code, or the same networking, or the same saving. I could go on and on, but instead heres what it does:

## What does it do?
#### Unified attachment system
- Attach "Modules" to many different attachment types like the server, levels, blocks, block entities, entities, oct cells, item stacks, spatial networks, and Sable sub-levels.
- A module is basically a component that can attach to something, and execute behaviors
- Modules can be manually attached or set to attach automatically with rules like "dirt blocks," "all pigs," "all sublevels," etc.
- Fields can be annotated ``@Data`` and their information will be automatically serialized, saved, and synced to tracking clients.

#### Behavior interfaces
- Add tick, render, block-change, item-render, network, sub-level split logic, and more by implementing an interface.
- Behaviors are indexed, and only execute when active.

#### Data tracking and networking
- Players are sent the full state when they begin tracking a module, and are sent updates only sending what changes.
- Networked data is written as raw binary, without extra information.
- ``@Data(watch = true)`` dirties a field whenever it is modified.
- ``@Data(clientMutable = true)`` allows clients to modify the field, while the server still has the final say.

#### Saved data
- Saved data is keyed by id, and versioned with optional data fixers, so your data always stays accurate.
- Data that isn't used is left for later, and can be restored by giving it a field, or using key aliases.
- Drop policies like ``UNLOAD``, ``DEATH``, ``LEVEL_CHANGE``, ``INVENTORY_CLEAR`` decide when a module is destroyed.

#### Dormant systems
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
- Layouts can exist as a plain java class with annotations, or as a customizable json layout that can be edited using an in game editor.

#### Data driven loading
- Register a ``DataEntryType`` for any class with ``@Data`` fields, and any json file in its folder will become its own entry you can fetch by id.
- Entries are loaded by data packs and reloaded with ``/reload``. They are automatically sent to players joining, and are resent when data is reloaded.
- There are extra keys like ``blocks`` and ``dimensions`` and types like ``BlockDataTypes`` that allow you to register information that is attached to other data.
- Chemicals, Reactions, Conductivity, Level Atmospheres, UI Themes, and Layouts, all use these types, allowing you to use these examples to build your own systems.

#### Sable support
- Modules can attach to sub-levels, are saved to the sublevel, are synced to the players tracking it, and keep working across sublevel loads and unloads.
- Includes sub-level ``SplitBehavior`` that lets each module decide what happens when a sublevel splits.
- Sable is completely optional of course, Empiric will run totally fine without it, and the related code is only loaded when Sable is installed.

#### Built for both modders and developers alike
- Empiric currently runs on both NeoForge and Fabric on 1.21.1 (+planned support for future versions!).
- Almost every system has both game tests, and ``/empiric`` debug commands, ensuring the systems stays stable and easy to debug.
- Data driven information is also synchronized when players join and on ``/reload``
- The core engine ``empiric-core`` is completely independent of Minecraft, allowing you to use it any java project, not just a mod!

*P.S. This project is still WIP, so if I missed anything important, or if you encounter any major issues, please dm me on discord @modogthedev and or [join the discord server](https://discord.gg/smTR7d5muz)!
