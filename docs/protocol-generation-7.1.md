# 7.1 protocol generation

The canonical 7.1 protocol schema is stored as `protocol/7.1/protocol.desc` with the file list in `protocol/7.1/files.txt`.

`generateProtocolJava` uses protoc 3.25.9 and regenerates the Java protocol sources into `build/generated/sources/protocol/7.1/java/main`. Normal `compileJava`, `jar`, and `sourcesJar` builds depend on this task.

The descriptor set was captured from the previously checked-in 7.1 generated Java descriptors. Its SHA-256 is `ea552a609a51006f1237a932c8af786472de3efeba97ded4ccbb1b09e561f6a7`, and it contains 2059 protocol files.

The checked-in generated Java dump is legacy material and is not a protocol source of truth. Future protocol recovery should update the canonical descriptor set (or recovered `.proto` sources when they are complete) and regenerate Java instead of editing generated Java by hand.
