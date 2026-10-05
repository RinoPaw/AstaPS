# 7.1 protocol generation

The base 7.1 protocol schema is stored as `protocol/7.1/protocol.desc` with its file list in `protocol/7.1/files.txt`. The descriptor set was captured from the previously checked-in 7.1 generated Java descriptors. Its SHA-256 is `ea552a609a51006f1237a932c8af786472de3efeba97ded4ccbb1b09e561f6a7`, and it contains 2059 protocol files.

`protocol/7.1/compat` contains protocol files that were added on `play/rino` after that descriptor set was captured, plus legacy protocol files required by those additions but absent from the base descriptor set. These schemas are recovered from the `descriptorData` embedded in the corresponding generated Java classes, so their field numbers, wire types, dependencies, and enum values remain pinned to the previous 7.1 implementation.

`TpsEquipChangeNotify.proto` imports `SceneWeaponInfo.proto`, which already exists inside the base descriptor set. `protocol/7.1/compat-imports` provides a build-only empty `SceneWeaponInfo` declaration so protoc can parse the recovered source. Protoc writes the recovered source files to a supplemental descriptor set without `--include_imports`, so this import stub never enters the combined descriptor set.

`ProtocolJavaGenerator` parses both descriptor sets and merges files by proto file name. Supplemental files replace a base descriptor only when they use the same file name. Before code generation, the generator also restores TPS semantics on six wire slots that already exist in the base descriptor under obfuscated names: `AvatarInfo.tps_weapon_list` field 37, `SceneAvatarInfo.tps_weapon_list` field 31, `SceneTeamAvatar.tps_weapon_list` field 382, `AvatarEnterSceneInfo.tps_weapon_list` field 13, `SceneWeaponInfo.ammunition_list` field 12, and `_TpsWeapon.accessory_id_list` field 2. Each replacement validates the old field number, label, wire type, and message type before changing the descriptor. Generation fails if the captured descriptor shape differs from the expected 7.1 shape.

`generateProtocolJava` uses protoc 4.36.2 to regenerate the combined base descriptors and recovered additions into `build/generated/sources/protocol/7.1/java/main`. Normal `compileJava`, `jar`, and `sourcesJar` builds depend on this task. The generated code is compiled against protobuf-java 4.36.2 from the main build.

`lintGeneratedProtocolJava` compiles only regenerated protocol sources with `-Xlint:all -Werror`. This keeps generated-code warning regressions separate from existing handwritten warning debt.

`ProtocolGenerationTest` checks a protobuf round trip, base descriptor dependencies, recovered TPS descriptors, and the six restored TPS field numbers. The protocol-generation workflow runs this test and the generated-source lint gate on Linux and Windows.

The checked-in generated Java dump is legacy material and is not a protocol source of truth. Future protocol recovery should update the canonical descriptor set or the recovered `.proto` additions and regenerate Java instead of editing generated Java by hand.
