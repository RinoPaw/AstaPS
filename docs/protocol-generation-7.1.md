# 7.1 protocol generation

The base 7.1 protocol schema is stored as `protocol/7.1/protocol.desc` with its file list in `protocol/7.1/files.txt`. The descriptor set was captured from the previously checked-in 7.1 generated Java descriptors. Its SHA-256 is `ea552a609a51006f1237a932c8af786472de3efeba97ded4ccbb1b09e561f6a7`, and it contains 2059 protocol files.

`protocol/7.1/compat` contains protocol files that were added on `play/rino` after that descriptor set was captured. These schemas are recovered from the `descriptorData` embedded in the corresponding generated Java classes, so their field numbers, wire types, dependencies, and enum values remain pinned to the previous 7.1 implementation.

Two recovered files import protocol types that already exist only inside the base descriptor set. `protocol/7.1/compat-imports` provides build-only empty declarations for those imported type names. `protoc` first compiles the recovered sources to a supplemental descriptor set without `--include_imports`, so the empty import stubs are never copied into the result. The generator then concatenates the serialized base and supplemental `FileDescriptorSet` messages. This is valid protobuf wire merging because `FileDescriptorSet` contains only the repeated `file` field. The merged descriptor set therefore resolves the recovered dependencies against the real base descriptors.

`generateProtocolJava` uses protoc 4.36.2 to regenerate the combined 2059 base descriptors plus the recovered additions into `build/generated/sources/protocol/7.1/java/main`. Normal `compileJava`, `jar`, and `sourcesJar` builds depend on this task. The generated code is compiled against protobuf-java 4.36.2 from the main build.

`lintGeneratedProtocolJava` compiles only regenerated protocol sources with `-Xlint:all -Werror`. This keeps generated-code warning regressions separate from existing handwritten warning debt.

The checked-in generated Java dump is legacy material and is not a protocol source of truth. Future protocol recovery should update the canonical descriptor set or the recovered `.proto` additions and regenerate Java instead of editing generated Java by hand.
