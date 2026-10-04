package emu.grasscutter.tools.protocol;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Regenerates Java protocol sources from the canonical 7.1 descriptor set and recovered additions. */
public final class ProtocolJavaGenerator {
    private static final int FILES_PER_PROTOC_INVOCATION = 128;

    private ProtocolJavaGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                    "usage: ProtocolJavaGenerator <protoc> <descriptor-set> <file-list> <supplemental-proto-dir> <supplemental-import-dir> <java-out>");
        }

        var protoc = Path.of(args[0]).toAbsolutePath().normalize();
        var descriptorSet = Path.of(args[1]).toAbsolutePath().normalize();
        var fileList = Path.of(args[2]).toAbsolutePath().normalize();
        var supplementalProtoDir = Path.of(args[3]).toAbsolutePath().normalize();
        var supplementalImportDir = Path.of(args[4]).toAbsolutePath().normalize();
        var javaOut = Path.of(args[5]).toAbsolutePath().normalize();

        requireRegularFile(protoc, "protoc executable");
        requireRegularFile(descriptorSet, "descriptor set");
        requireRegularFile(fileList, "protocol file list");
        requireDirectory(supplementalProtoDir, "supplemental proto directory");
        requireDirectory(supplementalImportDir, "supplemental import stub directory");

        var protocolFiles = Files.readAllLines(fileList, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .sorted()
                .toList();
        if (protocolFiles.isEmpty()) {
            throw new IllegalStateException("protocol file list is empty: " + fileList);
        }

        var supplementalProtoFiles = listProtoFiles(supplementalProtoDir);
        var allProtocolFiles = new TreeSet<String>();
        allProtocolFiles.addAll(protocolFiles);
        allProtocolFiles.addAll(supplementalProtoFiles);
        var overlappingFiles = protocolFiles.stream().filter(supplementalProtoFiles::contains).toList();
        var expectedJavaFileCount = allProtocolFiles.size();

        recreateDirectory(javaOut);

        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("astaps-protocol-descriptors-");
            var supplementalDescriptorSet = temporaryDirectory.resolve("supplemental.desc");
            var combinedDescriptorSet = temporaryDirectory.resolve("combined.desc");

            FileDescriptorSet supplementalDescriptors = FileDescriptorSet.getDefaultInstance();
            if (!supplementalProtoFiles.isEmpty()) {
                runSupplementalDescriptorProtoc(
                        protoc,
                        supplementalProtoDir,
                        supplementalImportDir,
                        supplementalDescriptorSet,
                        supplementalProtoFiles);
                supplementalDescriptors =
                        FileDescriptorSet.parseFrom(Files.readAllBytes(supplementalDescriptorSet));
            }

            var baseDescriptors = FileDescriptorSet.parseFrom(Files.readAllBytes(descriptorSet));
            var combinedDescriptors = mergeAndPatchDescriptors(baseDescriptors, supplementalDescriptors);
            Files.write(combinedDescriptorSet, combinedDescriptors.toByteArray());

            var filesToGenerate = List.copyOf(allProtocolFiles);
            for (int offset = 0; offset < filesToGenerate.size(); offset += FILES_PER_PROTOC_INVOCATION) {
                var end = Math.min(offset + FILES_PER_PROTOC_INVOCATION, filesToGenerate.size());
                runDescriptorProtoc(
                        protoc, combinedDescriptorSet, javaOut, filesToGenerate.subList(offset, end));
            }
        } finally {
            if (temporaryDirectory != null) {
                deleteDirectory(temporaryDirectory);
            }
        }

        long javaFileCount;
        try (Stream<Path> generatedFiles = Files.walk(javaOut)) {
            javaFileCount = generatedFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
        }
        if (javaFileCount != expectedJavaFileCount) {
            throw new IllegalStateException(
                    "protoc generated "
                            + javaFileCount
                            + " Java files; expected "
                            + expectedJavaFileCount
                            + " from "
                            + protocolFiles.size()
                            + " base descriptor files and "
                            + supplementalProtoFiles.size()
                            + " supplemental proto files ("
                            + overlappingFiles.size()
                            + " overrides)");
        }

        System.out.printf(
                "Generated %,d Java files from %,d base descriptors and %,d supplemental proto files (%,d overrides).%n",
                javaFileCount,
                protocolFiles.size(),
                supplementalProtoFiles.size(),
                overlappingFiles.size());
    }

    private static FileDescriptorSet mergeAndPatchDescriptors(
            FileDescriptorSet base, FileDescriptorSet supplemental) {
        var filesByName = new LinkedHashMap<String, FileDescriptorProto>();
        for (var file : base.getFileList()) {
            filesByName.put(file.getName(), file);
        }
        for (var file : supplemental.getFileList()) {
            filesByName.put(file.getName(), file);
        }

        var result = FileDescriptorSet.newBuilder();
        for (var file : filesByName.values()) {
            result.addFile(applyPlayProtocolPatches(file));
        }
        return result.build();
    }

    /**
     * The canonical descriptor dump predates several TPS fields that were added on play/rino.
     * Preserve every recovered base field and add only the missing 7.1 fields pinned by the old
     * generated Java descriptors.
     */
    private static FileDescriptorProto applyPlayProtocolPatches(FileDescriptorProto file) {
        return switch (file.getName()) {
            case "AvatarInfo.proto" ->
                    addMessageField(
                            file,
                            "AvatarInfo",
                            repeatedMessageField("tps_weapon_list", 37, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "SceneAvatarInfo.proto" ->
                    addMessageField(
                            file,
                            "SceneAvatarInfo",
                            repeatedMessageField("tps_weapon_list", 31, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "SceneWeaponInfo.proto" ->
                    addMessageField(
                            file,
                            "SceneWeaponInfo",
                            repeatedMessageField(
                                    "ammunition_list", 12, ".TpsWeaponAmmunitionInfo"),
                            "TpsWeaponAmmunitionInfo.proto");
            case "_TpsWeapon.proto" ->
                    addMessageField(
                            file,
                            "_TpsWeapon",
                            FieldDescriptorProto.newBuilder()
                                    .setName("accessory_id_list")
                                    .setNumber(2)
                                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                                    .setType(FieldDescriptorProto.Type.TYPE_UINT32)
                                    .build());
            default -> file;
        };
    }

    private static FieldDescriptorProto repeatedMessageField(
            String name, int number, String typeName) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(typeName)
                .build();
    }

    private static FileDescriptorProto addMessageField(
            FileDescriptorProto file,
            String messageName,
            FieldDescriptorProto field,
            String... dependencies) {
        var fileBuilder = file.toBuilder();
        var messageIndex = -1;
        for (int index = 0; index < fileBuilder.getMessageTypeCount(); index++) {
            if (fileBuilder.getMessageType(index).getName().equals(messageName)) {
                messageIndex = index;
                break;
            }
        }
        if (messageIndex < 0) {
            throw new IllegalStateException(
                    "protocol patch target message " + messageName + " was not found in " + file.getName());
        }

        var messageBuilder = fileBuilder.getMessageTypeBuilder(messageIndex);
        var sameName = messageBuilder.getFieldList().stream()
                .filter(existing -> existing.getName().equals(field.getName()))
                .findFirst();
        if (sameName.isPresent()) {
            if (!sameName.get().equals(field)) {
                throw new IllegalStateException(
                        "protocol patch field "
                                + file.getName()
                                + ":"
                                + messageName
                                + "."
                                + field.getName()
                                + " already exists with a different descriptor");
            }
        } else {
            var conflictingNumber = messageBuilder.getFieldList().stream()
                    .filter(existing -> existing.getNumber() == field.getNumber())
                    .findFirst();
            if (conflictingNumber.isPresent()) {
                throw new IllegalStateException(
                        "protocol patch field number "
                                + field.getNumber()
                                + " in "
                                + file.getName()
                                + ":"
                                + messageName
                                + " is already used by "
                                + conflictingNumber.get().getName());
            }
            messageBuilder.addField(field);
        }

        for (var dependency : dependencies) {
            if (!fileBuilder.getDependencyList().contains(dependency)) {
                fileBuilder.addDependency(dependency);
            }
        }
        return fileBuilder.build();
    }

    private static List<String> listProtoFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".proto"))
                    .map(directory::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void runSupplementalDescriptorProtoc(
            Path protoc,
            Path supplementalProtoDir,
            Path supplementalImportDir,
            Path supplementalDescriptorSet,
            List<String> supplementalProtoFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(supplementalProtoFiles.size() + 5);
        command.add(protoc.toString());
        command.add("--proto_path=" + supplementalProtoDir);
        command.add("--proto_path=" + supplementalImportDir);
        command.add("--descriptor_set_out=" + supplementalDescriptorSet);
        command.addAll(supplementalProtoFiles);

        runProtoc(command, "supplemental 7.1 descriptor recovery");
        requireRegularFile(supplementalDescriptorSet, "supplemental descriptor set");
    }

    private static void runDescriptorProtoc(
            Path protoc, Path descriptorSet, Path javaOut, List<String> protocolFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(protocolFiles.size() + 3);
        command.add(protoc.toString());
        command.add("--descriptor_set_in=" + descriptorSet);
        command.add("--java_out=" + javaOut);
        command.addAll(protocolFiles);

        runProtoc(command, protocolFiles.getFirst() + " .. " + protocolFiles.getLast());
    }

    private static void runProtoc(List<String> command, String description)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "protoc failed with exit code "
                            + exitCode
                            + " while generating "
                            + description
                            + (output.isBlank() ? "" : ":\n" + output));
        }
        if (!output.isBlank()) {
            System.out.print(output);
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        deleteDirectory(directory);
        Files.createDirectories(directory);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void requireRegularFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }

    private static void requireDirectory(Path path, String description) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }
}
